package com.infinity.core

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
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
    var ramPercent by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val batteryManager = context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as? BatteryManager

                batteryPercent = batteryManager
                    ?.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CAPACITY
                    )
                    ?.takeIf { it in 0..100 } ?: -1

                val activityManager = context.getSystemService(
                    Context.ACTIVITY_SERVICE
                ) as? ActivityManager

                if (activityManager != null) {
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)

                    if (memoryInfo.totalMem > 0L) {
                        ramPercent = (
                            (memoryInfo.totalMem - memoryInfo.availMem)
                                .toDouble() /
                                memoryInfo.totalMem * 100
                            ).toInt().coerceIn(0, 100)
                    }
                }
            } catch (_: Exception) {
                // Conserva el último valor disponible.
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
                value = if (batteryPercent >= 0)
                    "$batteryPercent%" else "--",
                accent = accent
            )

            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "RAM EN USO",
                value = "$ramPercent%",
                accent = accent
            )

            OrbitMetric(
                modifier = Modifier.weight(1f),
                label = "VENTANAS",
                value = "$activeWindows / $totalWindows",
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
            fontWeight = FontWeight.Bold
        )

        Text(
            value,
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
