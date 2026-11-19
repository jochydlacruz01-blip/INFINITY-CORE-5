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
import kotlinx.coroutines.delay

@Composable
fun OrbitSystemMonitor(
    accent: Color,
    activeWindows: Int,
    totalWindows: Int
) {
    val context = LocalContext.current

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
                val batteryManager = context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as? BatteryManager

                batteryPercent = batteryManager
                    ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    ?.takeIf { it in 0..100 } ?: -1

                val batteryIntent = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )

                val temperatureTenths = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE, -1
                ) ?: -1

                batteryTemperature = infinityBatteryTemperature(temperatureTenths)

                val batteryStatus = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS, -1
                ) ?: -1

                chargingState = when (batteryStatus) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "CARGANDO"
                    BatteryManager.BATTERY_STATUS_FULL -> "COMPLETA"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "EN BATERÍA"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "SIN CARGA"
                    else -> "DESCONOCIDO"
                }

                val activityManager = context.getSystemService(
                    Context.ACTIVITY_SERVICE
                ) as? ActivityManager

                if (activityManager != null) {
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)

                    ramPercent = infinityRamUsagePercent(
                        memoryInfo.totalMem,
                        memoryInfo.availMem
                    ) 
                }

                val stat = StatFs(context.filesDir.absolutePath)
                val totalBytes = stat.totalBytes
                val availableBytes = stat.availableBytes

                storagePercent = infinityUsagePercent(totalBytes, availableBytes)
                storageDetail = infinityStorageSummary(totalBytes, availableBytes)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val powerManager = context.getSystemService(
                        Context.POWER_SERVICE
                    ) as? PowerManager

                    thermalState = infinityThermalState(powerManager?.currentThermalStatus)
                }
            } catch (_: Exception) {
                // Conserva los últimos valores válidos.
            }

            delay(15000)
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "BATERÍA",
                value = if (batteryPercent >= 0) "$batteryPercent%" else "--",
                accent = accent
            )
            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "RAM EN USO",
                value = ramPercent?.let { "$it%" } ?: "--",
                accent = accent
            )
            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "VENTANAS",
                value = "$activeWindows / $totalWindows",
                accent = accent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "ALMAC. DATOS APP",
                value = storagePercent?.let { "$it%" } ?: "--",
                detail = "Filesystem de datos · $storageDetail",
                accent = accent
            )
            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "TEMP. BATERÍA",
                value = batteryTemperature,
                detail = chargingState,
                accent = accent
            )
            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "ESTADO TÉRMICO",
                value = thermalState,
                accent = accent
            )
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
