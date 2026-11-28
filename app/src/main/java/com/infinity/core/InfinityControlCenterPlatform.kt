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
import android.net.wifi.WifiManager

private fun readMediaVolumeWritable(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return runCatching {
        !audioManager.isVolumeFixed && audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) > 0
    }.getOrDefault(false)
}

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
    val mediaVolumePercent: Int?,
    val mediaVolumeWritable: Boolean = false,
    val wifiEnabled: Boolean? = null,
    val bluetoothEnabled: Boolean? = null,
    val airplaneModeEnabled: Boolean? = null
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
            mediaVolumePercent = null,
            mediaVolumeWritable = false,
            wifiEnabled = null,
            bluetoothEnabled = null,
            airplaneModeEnabled = null
        )

        fun read(context: Context): InfinityControlCenterSnapshot {
            val appContext = context.applicationContext
            val batteryIntent = appContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

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
            val volumeWritable = readMediaVolumeWritable(appContext)
            val wifiEnabled = runCatching {
                (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
            }.getOrNull()
            // Android 12+ protects Bluetooth adapter state behind BLUETOOTH_CONNECT.
            // Do not request a sensitive runtime permission just to render a status tile.
            val bluetoothEnabled: Boolean? = null
            val airplaneModeEnabled = runCatching {
                Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            }.getOrNull()

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
                brightnessWritable = Settings.System.canWrite(appContext),
                mediaVolumePercent = volume,
                mediaVolumeWritable = volumeWritable,
                wifiEnabled = wifiEnabled,
                bluetoothEnabled = bluetoothEnabled,
                airplaneModeEnabled = airplaneModeEnabled
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
            // Automatic brightness can immediately override a manually written
            // SCREEN_BRIGHTNESS value. Switch to manual mode first so the slider
            // has deterministic, visible effect on supported Android devices.
            val manualModeApplied = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            if (!manualModeApplied) return@runCatching false
            val target = value.coerceIn(1, 255)
            val written = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                target
            )
            written && Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                -1
            ) == target
        }.getOrDefault(false)
    }

    fun setMediaVolumePercent(context: Context, percent: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return runCatching {
            if (audioManager.isVolumeFixed) return@runCatching false
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = infinityMediaVolumeTargetLevel(max, percent) ?: return@runCatching false
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            // Android's setter is void; verify the device's actual stream level
            // before telling Compose that the slider change was applied.
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == target
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
