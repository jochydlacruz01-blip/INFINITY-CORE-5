package com.infinity.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the remote-session heartbeat independently of Compose. Session secrets stay in process
 * memory and are never copied into an Intent, notification, saved state, or persistent storage.
 * A process recreation without the in-memory grant fails closed and requires fresh consent.
 */
internal class InfinityConnectSessionSupervisorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: kotlinx.coroutines.Job? = null
    private var revokeRequested = false
    private var sequence = 0L
    private var retrySeconds = INITIAL_RETRY_SECONDS

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = InfinityConnectSessionSupervisorRuntime.current.get()
        if (config == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REVOKE) revokeRequested = true
        startAsForeground("Sesión remota autorizada · supervisando conexión")
        if (worker?.isActive != true) worker = scope.launch { supervise(config, startId) }
        return START_NOT_STICKY
    }

    private suspend fun supervise(config: InfinityConnectSessionConfig, startId: Int) {
        while (scope.isActive) {
            if (revokeRequested) {
                val confirmed = runCatching {
                    withContext(Dispatchers.IO) {
                        InfinityConnectMtlsTransport().revokeIdentitySession(
                            config.baseUrl, config.clientKeyAlias, config.serverPin,
                            config.sessionId, config.sessionToken, config.expectedDeviceId
                        )
                    }
                }.getOrDefault(false)
                if (confirmed) {
                    InfinityConnectSessionSupervisorRuntime.clear(config.sessionId)
                    updateNotification("Sesión revocada por Windows · conexión cerrada")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return
                }
                updateNotification("Desconexión pendiente · esperando confirmación de Windows")
                delay(retrySeconds * 1_000L)
                retrySeconds = (retrySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
                continue
            }

            val activityAt = InfinityConnectSessionSupervisorRuntime.lastActivityElapsed.get()
            val now = android.os.SystemClock.elapsedRealtime()
            if (activityAt > 0L && now - activityAt <= MAX_IDLE_MILLIS) {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        InfinityConnectMtlsTransport().heartbeatSession(
                            config.baseUrl, config.clientKeyAlias, config.serverPin,
                            config.sessionId, config.sessionToken, config.expectedDeviceId,
                            ++sequence, userActivity = true
                        )
                    }
                }
                if (result.isSuccess) {
                    retrySeconds = INITIAL_RETRY_SECONDS
                    updateNotification("Conectado · autorización renovada por Windows")
                } else {
                    updateNotification("Conexión interrumpida · reintentando autorización")
                    delay(retrySeconds * 1_000L)
                    retrySeconds = (retrySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
                }
            } else {
                updateNotification("Sesión inactiva · no se renueva la autorización")
            }
            delay(HEARTBEAT_INTERVAL_MILLIS)
        }
    }

    private fun startAsForeground(message: String) {
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun buildNotification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("INFINITY CONNECT")
            .setContentText(message)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sesión remota INFINITY", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal const val ACTION_REVOKE = "com.infinity.core.connect.REVOKE_SESSION"
        private const val CHANNEL_ID = "infinity_connect_session"
        private const val NOTIFICATION_ID = 7401
        private const val HEARTBEAT_INTERVAL_MILLIS = 30_000L
        private const val MAX_IDLE_MILLIS = 120_000L
        private const val INITIAL_RETRY_SECONDS = 5L
        private const val MAX_RETRY_SECONDS = 120L
    }
}

internal data class InfinityConnectSessionConfig(
    val baseUrl: String,
    val clientKeyAlias: String,
    val serverPin: String,
    val expectedDeviceId: String,
    val sessionId: String,
    val sessionToken: String
)

internal object InfinityConnectSessionSupervisorRuntime {
    internal val current = AtomicReference<InfinityConnectSessionConfig?>(null)
    internal val lastActivityElapsed = AtomicLong(0L)

    fun start(context: Context, config: InfinityConnectSessionConfig) {
        require(config.sessionId.isNotBlank() && config.sessionToken.isNotBlank())
        current.set(config)
        lastActivityElapsed.set(0L)
        val intent = Intent(context, InfinityConnectSessionSupervisorService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    /** Call only after a real keyboard, pointer, touch, or text-input event was accepted. */
    fun recordUserActivity() {
        lastActivityElapsed.set(android.os.SystemClock.elapsedRealtime())
    }

    fun requestAuthenticatedRevocation(context: Context) {
        val intent = Intent(context, InfinityConnectSessionSupervisorService::class.java)
            .setAction(InfinityConnectSessionSupervisorService.ACTION_REVOKE)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    fun clear(sessionId: String) {
        if (current.get()?.sessionId == sessionId) {
            current.set(null)
            lastActivityElapsed.set(0L)
        }
    }
}
