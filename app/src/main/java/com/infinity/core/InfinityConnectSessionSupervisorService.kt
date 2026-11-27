package com.infinity.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import java.util.concurrent.atomic.AtomicBoolean
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
    @Volatile private var revokeRequested = false
    @Volatile private var retrySeconds = INITIAL_RETRY_SECONDS
    @Volatile private var networkCallbackRegistered = false
    private var connectivityManager: ConnectivityManager? = null
    private val networkAvailable = AtomicBoolean(true)
    private val networkGeneration = AtomicLong(0L)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkAvailability()
        override fun onLost(network: Network) = refreshNetworkAvailability()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshNetworkAvailability()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = connectivityManager != null
        }
        refreshNetworkAvailability()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = InfinityConnectSessionSupervisorRuntime.current.get()
        if (config == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REVOKE) revokeRequested = true
        startAsForeground("Sesión remota autorizada · supervisando conexión")
        if (worker?.isActive != true) worker = scope.launch { supervise(config) }
        return START_STICKY
    }

    private suspend fun supervise(config: InfinityConnectSessionConfig) {
        while (scope.isActive) {
            if (revokeRequested) {
                if (!networkAvailable.get()) {
                    updateNotification("Sin red · revocación pendiente hasta recuperar Wi-Fi/LTE/VPN")
                    waitForNetworkRecovery()
                    continue
                }
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
                    stopSelf()
                    return
                }
                updateNotification("Desconexión pendiente · esperando confirmación de Windows")
                waitForRetry(interruptOnRevoke = false)
                retrySeconds = (retrySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
                continue
            }

            val activityAt = InfinityConnectSessionSupervisorRuntime.lastActivityElapsed.get()
            val now = android.os.SystemClock.elapsedRealtime()
            if (InfinityConnectSessionActivityPolicy.isHumanActivityRecent(activityAt, now, MAX_IDLE_MILLIS)) {
                if (!networkAvailable.get()) {
                    updateNotification("Red perdida · esperando recuperación de Wi-Fi/LTE/VPN")
                    waitForNetworkRecovery()
                    continue
                }
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        InfinityConnectMtlsTransport().heartbeatSession(
                            config.baseUrl, config.clientKeyAlias, config.serverPin,
                            config.sessionId, config.sessionToken, config.expectedDeviceId,
                            InfinityConnectSessionSupervisorRuntime.heartbeatSequence.incrementAndGet(),
                            userActivity = true
                        )
                    }
                }
                if (result.isSuccess) {
                    retrySeconds = INITIAL_RETRY_SECONDS
                    updateNotification("Conectado · autorización renovada por Windows")
                } else {
                    updateNotification("Conexión interrumpida · reintentando autorización")
                    waitForRetry(interruptOnRevoke = true)
                    retrySeconds = (retrySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
                }
            } else {
                val sessionStartedAt = InfinityConnectSessionSupervisorRuntime.startedElapsed.get()
                if (InfinityConnectSessionActivityPolicy.hasIdleTimeoutExpired(
                        activityAt, sessionStartedAt, now, MAX_IDLE_MILLIS
                    )) {
                    revokeRequested = true
                    updateNotification("Sesión inactiva · revocando autorización")
                    continue
                }
                updateNotification("Esperando actividad humana · no se renueva la autorización")
            }
            waitForNextHeartbeat()
        }
    }

    private fun refreshNetworkAvailability() {
        val manager = connectivityManager ?: return
        val activeNetwork = manager.activeNetwork
        val capabilities = activeNetwork?.let(manager::getNetworkCapabilities)
        val available = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val previous = networkAvailable.getAndSet(available)
        if (previous != available) {
            networkGeneration.incrementAndGet()
            if (available) retrySeconds = INITIAL_RETRY_SECONDS
        }
    }

    private suspend fun waitForNetworkRecovery() {
        if (!networkCallbackRegistered) {
            delay(NETWORK_POLL_FALLBACK_MILLIS)
            return
        }
        while (scope.isActive && !networkAvailable.get()) delay(NETWORK_POLL_FALLBACK_MILLIS)
    }

    private suspend fun waitForRetry(interruptOnRevoke: Boolean) {
        val generation = networkGeneration.get()
        val deadline = android.os.SystemClock.elapsedRealtime() + retrySeconds * 1_000L
        while (scope.isActive &&
            android.os.SystemClock.elapsedRealtime() < deadline &&
            generation == networkGeneration.get() &&
            (!interruptOnRevoke || !revokeRequested)) {
            val remaining = deadline - android.os.SystemClock.elapsedRealtime()
            delay(remaining.coerceAtMost(NETWORK_POLL_FALLBACK_MILLIS))
        }
    }

    private suspend fun waitForNextHeartbeat() {
        val deadline = android.os.SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MILLIS
        while (scope.isActive && !revokeRequested && networkAvailable.get()) {
            val remaining = deadline - android.os.SystemClock.elapsedRealtime()
            if (remaining <= 0L) return
            delay(remaining.coerceAtMost(500L))
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
        if (networkCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        }
        networkCallbackRegistered = false
        connectivityManager = null
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
        private const val NETWORK_POLL_FALLBACK_MILLIS = 500L
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
    internal val startedElapsed = AtomicLong(0L)
    internal val heartbeatSequence = AtomicLong(0L)
    private val gate = Any()

    fun start(context: Context, config: InfinityConnectSessionConfig) {
        require(config.sessionId.isNotBlank() && config.sessionToken.isNotBlank())
        synchronized(gate) {
            val active = current.get()
            check(active == null || active == config) {
                "INFINITY CONNECT already supervises a different authorized session."
            }
            if (active == null) {
                current.set(config)
                lastActivityElapsed.set(0L)
                startedElapsed.set(android.os.SystemClock.elapsedRealtime())
                heartbeatSequence.set(0L)
            }
            val intent = Intent(context, InfinityConnectSessionSupervisorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (failure: RuntimeException) {
                if (active == null) {
                    current.compareAndSet(config, null)
                    lastActivityElapsed.set(0L)
                    startedElapsed.set(0L)
                }
                throw failure
            }
        }
    }

    /** Call only after a real keyboard, pointer, touch, or text-input event was accepted. */
    fun recordUserActivity() {
        lastActivityElapsed.set(android.os.SystemClock.elapsedRealtime())
    }

    fun requestAuthenticatedRevocation(context: Context) {
        synchronized(gate) {
            if (current.get() == null) return
            val intent = Intent(context, InfinityConnectSessionSupervisorService::class.java)
                .setAction(InfinityConnectSessionSupervisorService.ACTION_REVOKE)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    fun clear(sessionId: String) {
        synchronized(gate) {
            if (current.get()?.sessionId == sessionId) {
                current.set(null)
                lastActivityElapsed.set(0L)
                startedElapsed.set(0L)
                heartbeatSequence.set(0L)
            }
        }
    }
}
