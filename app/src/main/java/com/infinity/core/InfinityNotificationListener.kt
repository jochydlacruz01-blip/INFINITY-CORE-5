package com.infinity.core

import android.app.Notification
import android.app.PendingIntent
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

object InfinityNotificationStore {

    val notifications = mutableStateListOf<InfinityNotification>()
    val accessEnabled = mutableStateOf(false)

    internal var service: InfinityNotificationListener? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun update(notification: InfinityNotification) {
        mainHandler.post {
            val index = notifications.indexOfFirst {
                it.key == notification.key
            }

            if (index >= 0) {
                notifications[index] = notification
            } else {
                notifications.add(0, notification)
            }
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
    }

    fun dismiss(key: String) {
    service?.cancelNotification(key)
}

    fun clearAll() {
        service?.cancelAllNotifications()
    }

    ffun open(notification: InfinityNotification) {
    service?.openNotification(notification)
}
        try {
            notification.contentIntent?.send()
        } catch (_: Exception) {
        }
    }
}

class InfinityNotificationListener : NotificationListenerService() {
    internal fun dismissNotification(key: String) {
        try {
            cancelNotification(key)
        } catch (_: Exception) {
            // Android puede impedir retirar determinadas notificaciones del sistema.
        }
    }

    internal fun openNotification(notification: InfinityNotification) {
        try {
            val pendingIntent = notification.contentIntent
            if (pendingIntent != null) {
                pendingIntent.send()
                return
            }
        } catch (_: Exception) {
            // Si el destino directo ya no existe, intentamos abrir la aplicación.
        }

        try {
            val launchIntent = packageManager
                .getLaunchIntentForPackage(notification.packageName)
            launchIntent?.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        } catch (_: Exception) {
            // Algunas aplicaciones no ofrecen una pantalla de inicio accesible.
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

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()

        val message = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        )?.toString().orEmpty()

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        InfinityNotificationStore.update(
            InfinityNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                message = message,
                timestamp = sbn.postTime,
                ongoing = notification.flags and
                    Notification.FLAG_ONGOING_EVENT != 0,
                clearable = sbn.isClearable,
                contentIntent = notification.contentIntent
            )
        )
    }
}
