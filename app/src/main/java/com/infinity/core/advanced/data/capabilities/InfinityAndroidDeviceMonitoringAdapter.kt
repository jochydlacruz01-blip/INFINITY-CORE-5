package com.infinity.core.advanced.data.capabilities

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMonitoringCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val DEVICE_METRICS_INTERVAL_MS = 15_000L

/**
 * Read-only bridge to Android's existing device telemetry.
 * CPU is intentionally null: Android exposes no stable, app-scoped CPU usage API.
 */
internal class InfinityAndroidDeviceMonitoringAdapter(
    context: Context,
    private val refreshIntervalMillis: Long = DEVICE_METRICS_INTERVAL_MS
) : InfinityDeviceMonitoringCapability {
    private val appContext = context.applicationContext

    init {
        require(refreshIntervalMillis > 0L)
    }

    override fun observeMetrics(): Flow<InfinityDeviceMetrics> = flow {
        while (true) {
            emit(readMetrics(appContext))
            delay(refreshIntervalMillis)
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        internal fun readMetrics(context: Context): InfinityDeviceMetrics {
            val memoryUsage = runCatching {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@runCatching null
                val info = ActivityManager.MemoryInfo()
                manager.getMemoryInfo(info)
                infinityDeviceUsagePercent(info.totalMem, info.availMem)
            }.getOrNull()

            val storageUsage = runCatching {
                val stats = StatFs(context.filesDir.absolutePath)
                infinityDeviceUsagePercent(stats.totalBytes, stats.availableBytes)
            }.getOrNull()

            val battery = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            val batteryLevel = battery?.let {
                infinityBatteryLevelPercent(
                    it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                    it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                )
            }
            val batteryTemperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }
                ?.div(10f)
                ?.takeIf { it in -30f..120f }
            val batteryStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = batteryStatus?.let {
                it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
            }
            val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    power?.let { infinityThermalStatusLabel(it.currentThermalStatus) }
                }.getOrNull()
            } else null

            return InfinityDeviceMetrics(
                capturedAtEpochMillis = System.currentTimeMillis(),
                cpuUsagePercent = null,
                memoryUsagePercent = memoryUsage,
                storageUsagePercent = storageUsage,
                batteryLevelPercent = batteryLevel,
                batteryTemperatureCelsius = batteryTemperature,
                isCharging = charging,
                thermalStatus = thermalStatus
            )
        }
    }
}

internal fun infinityDeviceUsagePercent(totalBytes: Long, availableBytes: Long): Float? {
    if (totalBytes <= 0L || availableBytes < 0L) return null
    val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
    return ((usedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
        .toFloat()
        .coerceIn(0f, 100f)
}

internal fun infinityBatteryLevelPercent(level: Int, scale: Int): Float? {
    if (level < 0 || scale <= 0 || level > scale) return null
    return (level.toDouble() / scale.toDouble() * 100.0).toFloat().coerceIn(0f, 100f)
}

internal fun infinityThermalStatusLabel(status: Int): String? = when (status) {
    0 -> "Normal"
    1 -> "Ligero"
    2 -> "Moderado"
    3 -> "Severo"
    4 -> "Crítico"
    5 -> "Emergencia"
    6 -> "Apagado"
    else -> null
}
