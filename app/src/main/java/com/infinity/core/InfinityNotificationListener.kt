package com.infinity.core

import android.app.Notification
import android.app.PendingIntent
import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

data class InfinityNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val ongoing: Boolean,
    val clearable: Boolean,
    val contentIntent: PendingIntent?
)

private const val MAX_NOTIFICATIONS = 100

object InfinityNotificationStore {

    val notifications = mutableStateListOf<InfinityNotification>()
    val accessEnabled = mutableStateOf(false)
    val accessGranted = mutableStateOf(false)

    internal fun refreshAccess(context: Context) {
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

        accessGranted.value = enabledListeners.split(':').any { flattened ->
            android.content.ComponentName.unflattenFromString(flattened)
                ?.let { component ->
                    component.packageName == context.packageName &&
                        component.className == InfinityNotificationListener::class.java.name
                } == true
        }
        // Permission alone does not mean the listener is currently connected.
        accessEnabled.value = infinityNotificationAccessEnabled(
            granted = accessGranted.value,
            connected = service != null
        )
    }

    internal var service: InfinityNotificationListener? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var hideContent = true

    internal fun setHideContent(enabled: Boolean) {
        hideContent = enabled
        if (enabled) {
            mainHandler.post {
                val redacted = infinityNotificationApplyPrivacy(notifications.toList(), hideContent = true)
                notifications.clear()
                notifications.addAll(redacted)
            }
        }
    }

    internal fun update(notification: InfinityNotification) {
        val safeNotification = if (hideContent) {
            notification.copy(title = "", message = "")
        } else {
            notification
        }

        mainHandler.post {
            val bounded = infinityNotificationFeedUpdate(notifications.toList(), safeNotification, MAX_NOTIFICATIONS)
            notifications.clear()
            notifications.addAll(bounded)
        }
    }

    internal fun remove(key: String) {
        mainHandler.post {
            notifications.removeAll { it.key == key }
        }
    }

    internal fun connected(listener: InfinityNotificationListener) {
        service = listener
        accessEnabled.value = true
    }

    internal fun disconnected() {
        service = null
        accessEnabled.value = false

        mainHandler.post {
            notifications.clear()
        }
    }

    fun dismiss(key: String) {
        service?.dismissNotification(key)
    }

    fun clearAll() {
        // Limpiar únicamente la lista local de INFINITY.
        // Si se invoca desde Compose (hilo principal), aplicar el cambio de inmediato;
        // desde callbacks del servicio, despachar de forma segura al hilo principal.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifications.clear()
        } else {
            mainHandler.post {
                notifications.clear()
            }
        }
    }

    fun open(notification: InfinityNotification) {
        service?.openNotification(notification)
    }
}

class InfinityNotificationListener : NotificationListenerService() {

    internal fun dismissNotification(key: String) {
        try {
            if (key.isBlank()) return
            cancelNotification(key)
        } catch (error: Exception) {
            val report = infinityGuardReport("Descartar notificación", error)
            android.widget.Toast.makeText(
                this,
                report.userMessage,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    internal fun openNotification(notification: InfinityNotification) {
        try {
            val pendingIntent = notification.contentIntent

            if (pendingIntent != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )

                    pendingIntent.send(
                        this,
                        0,
                        null,
                        null,
                        null,
                        null,
                        options.toBundle()
                    )
                } else {
                    pendingIntent.send()
                }

                return
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            val report = infinityGuardReport("Abrir notificación", error)
            android.widget.Toast.makeText(
                this,
                report.userMessage,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        try {
            val launchIntent = packageManager
                .getLaunchIntentForPackage(notification.packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                startActivity(launchIntent)
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            val report = infinityGuardReport("Inicio de aplicación", error)
            android.widget.Toast.makeText(
                this,
                report.userMessage,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        InfinityNotificationStore.connected(this)

        activeNotifications?.forEach {
            publishNotification(it)
        }
    }

    override fun onListenerDisconnected() {
        InfinityNotificationStore.disconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        InfinityNotificationStore.remove(sbn.key)
    }

    private fun publishNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras

            val rawTitle = extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()

            val rawMessage = (
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()

            val hideContent = getSharedPreferences(
                "infinity_privacy",
                Context.MODE_PRIVATE
            ).getBoolean("hide_notification_content", true)

            InfinityNotificationStore.setHideContent(hideContent)
            val content = infinityNotificationContent(rawTitle, rawMessage, hideContent)

            val appName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(sbn.packageName, 0)
                ).toString().let { label ->
                    infinityNotificationAppNameOrFallback(label, sbn.packageName)
                }
            } catch (_: Exception) {
                infinityNotificationAppNameOrFallback(null, sbn.packageName)
            }

            InfinityNotificationStore.update(
                InfinityNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appName = appName,
                    title = content.first,
                    message = content.second,
                    timestamp = sbn.postTime,
                    ongoing = notification.flags and
                        Notification.FLAG_ONGOING_EVENT != 0,
                    clearable = sbn.isClearable,
                    contentIntent = notification.contentIntent
                )
            )
        } catch (_: Exception) {
            // Ignorar notificaciones defectuosas sin cerrar INFINITY.
        }
    }
}
