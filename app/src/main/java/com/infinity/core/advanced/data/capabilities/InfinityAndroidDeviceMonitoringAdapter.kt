package com.infinity.core.advanced.data.capabilities

import android.app.ActivityManager
import android.content.Context
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

            return InfinityDeviceMetrics(
                capturedAtEpochMillis = System.currentTimeMillis(),
                cpuUsagePercent = null,
                memoryUsagePercent = memoryUsage,
                storageUsagePercent = storageUsage
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
