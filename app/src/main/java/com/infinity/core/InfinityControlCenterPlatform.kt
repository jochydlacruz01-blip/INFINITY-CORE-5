package com.infinity.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.net.Uri

private fun readMediaVolumePercent(context: Context): Int? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return null
    return runCatching {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) null
        else (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max)
            .toInt()
            .coerceIn(0, 100)
    }.getOrNull()
}

/**
 * Read-only snapshot of device information used by the INFINITY Control Center.
 *
 * Values are sampled on demand. The caller should refresh this snapshot from an
 * appropriate lifecycle point (for example, when the panel becomes visible).
 */
internal data class InfinityControlCenterSnapshot(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val storageTotalBytes: Long?,
    val storageAvailableBytes: Long?,
    val deviceName: String,
    val androidVersion: String,
    val brightness: Int?,
    val brightnessWritable: Boolean,
    val mediaVolumePercent: Int?
) {
    companion object {
        fun unavailable() = InfinityControlCenterSnapshot(
            batteryPercent = null,
            isCharging = null,
            storageTotalBytes = null,
            storageAvailableBytes = null,
            deviceName = "Dispositivo Android",
            androidVersion = "—",
            brightness = null,
            brightnessWritable = false,
            mediaVolumePercent = null
        )

        fun read(context: Context): InfinityControlCenterSnapshot {
            val appContext = context.applicationContext
            val batteryIntent = runCatching {
                appContext.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
            }.getOrNull()

            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPercent = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                null
            }

            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            }

            val storage = runCatching {
                StatFs(Environment.getDataDirectory().absolutePath)
            }.getOrNull()

            val brightness = runCatching {
                Settings.System.getInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ).coerceIn(0, 255)
            }.getOrNull()

            val volume = readMediaVolumePercent(appContext)

            return InfinityControlCenterSnapshot(
                batteryPercent = batteryPercent,
                isCharging = charging,
                storageTotalBytes = storage?.totalBytes,
                storageAvailableBytes = storage?.availableBytes,
                deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
                    .ifBlank { "Dispositivo Android" },
                androidVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { "Desconocida" },
                brightness = brightness,
                brightnessWritable = runCatching {
                    Settings.System.canWrite(appContext)
                }.getOrDefault(false),
                mediaVolumePercent = volume
            )
        }
    }
}

/** Destinations are opened through Android-owned settings screens; no restricted
 * connectivity toggles are simulated or written by INFINITY.
 */
internal enum class InfinityControlDestination {
    WIFI,
    BLUETOOTH,
    AIRPLANE_MODE,
    MOBILE_NETWORK,
    DISPLAY,
    SOUND,
    PERSONALIZATION,
    SECURITY,
    PRIVACY,
    SYSTEM
}

internal object InfinityControlCenterActions {

    fun openSettings(context: Context, destination: InfinityControlDestination): Boolean {
        val action = when (destination) {
            InfinityControlDestination.WIFI -> Settings.ACTION_WIFI_SETTINGS
            InfinityControlDestination.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            InfinityControlDestination.AIRPLANE_MODE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            InfinityControlDestination.MOBILE_NETWORK -> Settings.ACTION_WIRELESS_SETTINGS
            InfinityControlDestination.DISPLAY,
            InfinityControlDestination.PERSONALIZATION -> Settings.ACTION_DISPLAY_SETTINGS
            InfinityControlDestination.SOUND -> Settings.ACTION_SOUND_SETTINGS
            InfinityControlDestination.SECURITY -> Settings.ACTION_SECURITY_SETTINGS
            InfinityControlDestination.PRIVACY -> Settings.ACTION_PRIVACY_SETTINGS
            InfinityControlDestination.SYSTEM -> Settings.ACTION_SETTINGS
        }

        return launch(context, Intent(action))
    }

    /** Opens Android's special-access screen when WRITE_SETTINGS is not granted. */
    fun requestBrightnessAccess(context: Context): Boolean {
        if (Settings.System.canWrite(context)) return true
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        return launch(context, intent)
    }

    /** Writes brightness only after Android has granted the special WRITE_SETTINGS access. */
    fun setBrightness(context: Context, value: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(1, 255)
            )
        }.getOrDefault(false)
    }

    fun setMediaVolumePercent(context: Context, percent: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * percent.coerceIn(0, 100) / 100f).toInt()
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                target.coerceIn(0, max),
                0
            )
            true
        }.getOrDefault(false)
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
