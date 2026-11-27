package com.infinity.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal data class InfinityReferenceSystemStatus(
    val networkLabel: String,
    val batteryPercent: Int?,
    val vpnActive: Boolean
)

internal fun infinitySystemTrayNetworkLabel(
    connected: Boolean,
    wifi: Boolean,
    cellular: Boolean,
    ethernet: Boolean
): String = when {
    !connected -> "SIN RED"
    wifi -> "WIFI"
    cellular -> "LTE"
    ethernet -> "LAN"
    else -> "RED"
}

internal fun infinitySystemTrayBatteryPercent(rawPercent: Int): Int? =
    rawPercent.takeIf { it in 0..100 }

private fun readInfinityReferenceSystemStatus(context: Context): InfinityReferenceSystemStatus {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val capabilities = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities)
    val connected = capabilities != null &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val networkLabel = infinitySystemTrayNetworkLabel(
        connected = connected,
        wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
        ethernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
    )
    val battery = (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.let(::infinitySystemTrayBatteryPercent)
    return InfinityReferenceSystemStatus(
        networkLabel = networkLabel,
        batteryPercent = battery,
        vpnActive = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    )
}

/** Live, privacy-safe system tray for the tablet's BEYOND LIMITS Dock. */
@Composable
internal fun InfinityReferenceSystemTray(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var status by remember(context) {
        mutableStateOf(readInfinityReferenceSystemStatus(context))
    }
    LaunchedEffect(context) {
        while (true) {
            delay(30_000)
            status = readInfinityReferenceSystemStatus(context)
        }
    }
    val description = buildString {
        append("Conexión ${status.networkLabel}. ")
        append(status.batteryPercent?.let { "Batería $it por ciento." } ?: "Batería no disponible.")
        if (status.vpnActive) append(" VPN activa.")
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66142640), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x3349CFFF), RoundedCornerShape(12.dp))
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = "● ${status.networkLabel}",
            color = if (status.networkLabel == "SIN RED") Color(0xFFFFB36B) else Color(0xFF71E6C1),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = "▰ ${status.batteryPercent?.let { "$it%" } ?: "—"}",
            color = Color(0xFFE6F2FF),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (status.vpnActive) {
            Text("VPN", color = Color(0xFFBDA7FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
