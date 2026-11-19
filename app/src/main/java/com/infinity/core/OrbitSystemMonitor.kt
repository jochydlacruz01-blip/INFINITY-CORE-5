package com.infinity.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class OrbitSystemSnapshot(
    val batteryPercent: Int,
    val ramPercent: Int?,
    val storagePercent: Int?,
    val batteryTemperature: String,
    val chargingState: String,
    val thermalState: String,
    val storageDetail: String
)

/**
 * Reads system telemetry away from the main thread so periodic polling
 * cannot stall Compose rendering or user interaction.
 */
private fun readOrbitSystemSnapshot(context: Context): OrbitSystemSnapshot {
    val batteryManager = context.getSystemService(
        Context.BATTERY_SERVICE
    ) as? BatteryManager

    val batteryPercent = infinityBatteryPercent(
        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    ) ?: -1

    val batteryIntent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )

    val temperatureTenths = batteryIntent?.getIntExtra(
        BatteryManager.EXTRA_TEMPERATURE, -1
    ) ?: -1
    val batteryTemperature = infinityBatteryTemperature(temperatureTenths)

    val batteryStatus = batteryIntent?.getIntExtra(
        BatteryManager.EXTRA_STATUS, -1
    ) ?: -1
    val chargingState = infinityChargingState(batteryStatus.takeIf { it >= 0 })

    val activityManager = context.getSystemService(
        Context.ACTIVITY_SERVICE
    ) as? ActivityManager
    val ramPercent = activityManager?.let {
        val memoryInfo = ActivityManager.MemoryInfo()
        it.getMemoryInfo(memoryInfo)
        infinityRamUsagePercent(memoryInfo.totalMem, memoryInfo.availMem)
    }

    val stat = StatFs(context.filesDir.absolutePath)
    val totalBytes = stat.totalBytes
    val availableBytes = stat.availableBytes
    val storagePercent = infinityUsagePercent(totalBytes, availableBytes)
    val storageDetail = infinityStorageSummary(totalBytes, availableBytes)

    val thermalState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val powerManager = context.getSystemService(
            Context.POWER_SERVICE
        ) as? PowerManager
        infinityThermalState(powerManager?.currentThermalStatus)
    } else {
        "NO DISPONIBLE"
    }

    return OrbitSystemSnapshot(
        batteryPercent = batteryPercent,
        ramPercent = ramPercent,
        storagePercent = storagePercent,
        batteryTemperature = batteryTemperature,
        chargingState = chargingState,
        thermalState = thermalState,
        storageDetail = storageDetail
    )
}

@Composable
fun OrbitSystemMonitor(
    accent: Color,
    activeWindows: Int,
    totalWindows: Int
) {
    val context = LocalContext.current.applicationContext

    var batteryPercent by remember { mutableIntStateOf(-1) }
    var ramPercent by remember { mutableStateOf<Int?>(null) }
    var storagePercent by remember { mutableStateOf<Int?>(null) }
    var batteryTemperature by remember { mutableStateOf("--") }
    var chargingState by remember { mutableStateOf("CONSULTANDO") }
    var thermalState by remember { mutableStateOf("NO DISPONIBLE") }
    var storageDetail by remember { mutableStateOf("--") }

    LaunchedEffect(context) {
        while (true) {
            try {
                // Keep framework and filesystem reads off Compose's main thread.
                val snapshot = withContext(Dispatchers.IO) {
                    readOrbitSystemSnapshot(context)
                }

                batteryPercent = snapshot.batteryPercent
                ramPercent = snapshot.ramPercent
                storagePercent = snapshot.storagePercent
                batteryTemperature = snapshot.batteryTemperature
                chargingState = snapshot.chargingState
                thermalState = snapshot.thermalState
                storageDetail = snapshot.storageDetail
            } catch (cancelled: CancellationException) {
                // Respeta la cancelación de LaunchedEffect al salir de la pantalla.
                throw cancelled
            } catch (_: Exception) {
                // Evita mostrar métricas antiguas como si fueran datos actuales.
                batteryPercent = -1
                batteryTemperature = "--"
                chargingState = "NO DISPONIBLE"
                ramPercent = null
                storagePercent = null
                storageDetail = "--"
                thermalState = "NO DISPONIBLE"
            }

            delay(15000)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactLayout = maxWidth < 420.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "◈  SYSTEM MONITOR • LIVE",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            if (compactLayout) {
                // Dos columnas en teléfonos estrechos: evita comprimir seis
                // indicadores en dos filas de tres tarjetas.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitMetric(Modifier.weight(1f), "BATERÍA", if (batteryPercent >= 0) "$batteryPercent%" else "--", accent = accent)
                    OrbitMetric(Modifier.weight(1f), "RAM EN USO", ramPercent?.let { "$it%" } ?: "--", accent = accent)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitMetric(Modifier.weight(1f), "VENTANAS", "$activeWindows / $totalWindows", accent = accent)
                    OrbitMetric(Modifier.weight(1f), "ALMAC. DATOS APP", storagePercent?.let { "$it%" } ?: "--", "Filesystem de datos · $storageDetail", accent)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitMetric(Modifier.weight(1f), "TEMP. BATERÍA", batteryTemperature, chargingState, accent)
                    OrbitMetric(Modifier.weight(1f), "ESTADO TÉRMICO", thermalState, accent = accent)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitMetric(Modifier.weight(1f), "BATERÍA", if (batteryPercent >= 0) "$batteryPercent%" else "--", accent = accent)
                    OrbitMetric(Modifier.weight(1f), "RAM EN USO", ramPercent?.let { "$it%" } ?: "--", accent = accent)
                    OrbitMetric(Modifier.weight(1f), "VENTANAS", "$activeWindows / $totalWindows", accent = accent)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitMetric(Modifier.weight(1f), "ALMAC. DATOS APP", storagePercent?.let { "$it%" } ?: "--", "Filesystem de datos · $storageDetail", accent)
                    OrbitMetric(Modifier.weight(1f), "TEMP. BATERÍA", batteryTemperature, chargingState, accent)
                    OrbitMetric(Modifier.weight(1f), "ESTADO TÉRMICO", thermalState, accent = accent)
                }
            }
        }
    }
}

@Composable
private fun OrbitMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    detail: String? = null,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF192D47))
            .border(
                1.dp,
                accent.copy(alpha = 0.25f),
                RoundedCornerShape(13.dp)
            )
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            label,
            color = Color(0xFFB7C7D9),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Text(
            value,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        if (detail != null) {
            Text(
                detail,
                color = Color(0xFFB7C7D9),
                fontSize = 7.sp,
                maxLines = 1
            )
        }
    }
}
