package com.infinity.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Standalone INFINITY Control Center content.
 *
 * The host desktop owns presentation visibility and lifecycle refresh. All device
 * actions are injected as callbacks so this composable does not reach into the
 * activity, preferences, or Android services.
 */
@Composable
internal fun InfinityControlCenterPanel(
    snapshot: InfinityControlCenterSnapshot,
    onDestinationClick: (InfinityControlDestination) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onRequestBrightnessAccess: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = infinityControlPanelColumns(maxWidth.value)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color(0xF0182032),
            contentColor = Color(0xFFF4F7FF),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "INFINITY",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF80B9FF),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Centro de control",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cerrar")
                    }
                }

                SectionLabel("CONECTIVIDAD")
                val connectivity = listOf(
                    ControlCenterTileData("Wi-Fi", infinityControlConnectivityStatus(snapshot.wifiEnabled), Icons.Outlined.Wifi, InfinityControlDestination.WIFI),
                    ControlCenterTileData("Bluetooth", infinityControlConnectivityStatus(snapshot.bluetoothEnabled), Icons.Outlined.Bluetooth, InfinityControlDestination.BLUETOOTH),
                    ControlCenterTileData("Modo avión", infinityControlConnectivityStatus(snapshot.airplaneModeEnabled), Icons.Outlined.AirplanemodeActive, InfinityControlDestination.AIRPLANE_MODE),
                    ControlCenterTileData("Red móvil", "SIM y datos · Ajustes Android", Icons.Outlined.SignalCellularAlt, InfinityControlDestination.MOBILE_NETWORK)
                )
                TileGrid(items = connectivity, columns = columns, onClick = onDestinationClick)

                SectionLabel("PANTALLA Y SONIDO")
                ControlSlider(
                    title = "Brillo",
                    value = snapshot.brightness?.toFloat(),
                    valueRange = 1f..255f,
                    icon = Icons.Outlined.Brightness6,
                    enabled = snapshot.brightnessWritable,
                    onValueChange = { onBrightnessChange(it.roundToInt()) }
                )
                if (!snapshot.brightnessWritable) {
                    TextButton(onClick = onRequestBrightnessAccess) {
                        Text("Permitir control de brillo en Android")
                    }
                }
                ControlSlider(
                    title = "Volumen multimedia",
                    value = snapshot.mediaVolumePercent?.toFloat(),
                    valueRange = 0f..100f,
                    icon = Icons.Outlined.VolumeUp,
                    enabled = snapshot.mediaVolumeWritable,
                    onValueChange = { onVolumeChange(it.roundToInt()) }
                )
                TileGrid(
                    items = listOf(
                        ControlCenterTileData("Pantalla", "Visualización", Icons.Outlined.Brightness6, InfinityControlDestination.DISPLAY),
                        ControlCenterTileData("Sonido", "Audio del dispositivo", Icons.Outlined.VolumeUp, InfinityControlDestination.SOUND)
                    ),
                    columns = columns,
                    onClick = onDestinationClick
                )

                SectionLabel("PERSONALIZACIÓN Y SEGURIDAD")
                TileGrid(
                    items = listOf(
                        ControlCenterTileData("Apariencia", "Temas y visualización", Icons.Outlined.Palette, InfinityControlDestination.PERSONALIZATION),
                        ControlCenterTileData("Seguridad", "Protección del dispositivo", Icons.Outlined.Security, InfinityControlDestination.SECURITY),
                        ControlCenterTileData("Privacidad", "Permisos y controles", Icons.Outlined.PrivacyTip, InfinityControlDestination.PRIVACY),
                        ControlCenterTileData("Ajustes", "Configuración Android", Icons.Outlined.Settings, InfinityControlDestination.SYSTEM)
                    ),
                    columns = columns,
                    onClick = onDestinationClick
                )

                SectionLabel("ESTADO DEL DISPOSITIVO")
                StatusCard(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF9FB2CC),
        fontWeight = FontWeight.Bold
    )
}

private data class ControlCenterTileData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: InfinityControlDestination
)

@Composable
private fun TileGrid(
    items: List<ControlCenterTileData>,
    columns: Int,
    onClick: (InfinityControlDestination) -> Unit
) {
    items.chunked(columns.coerceAtLeast(1)).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rowItems.forEach { item ->
                ControlTile(
                    item = item,
                    modifier = Modifier.weight(1f),
                    onClick = { onClick(item.destination) }
                )
            }
            repeat((columns - rowItems.size).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ControlTile(
    item: ControlCenterTileData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF25344B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color(0xFF8FC4FF)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB5C2D5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ControlSlider(
    title: String,
    value: Float?,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202D42))) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color(0xFF8FC4FF))
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = value?.roundToInt()?.let {
                        if (valueRange.endInclusive == 255f) "$it" else "$it%"
                    } ?: "No disponible",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB5C2D5)
                )
            }
            var sliderValue by remember(value) {
                mutableFloatStateOf((value ?: valueRange.start).coerceIn(valueRange))
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(sliderValue) },
                valueRange = valueRange,
                enabled = value != null && enabled
            )
        }
    }
}

@Composable
private fun StatusCard(snapshot: InfinityControlCenterSnapshot) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202D42))) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusLine(
                icon = Icons.Outlined.BatteryChargingFull,
                label = "Batería",
                value = snapshot.batteryPercent?.let { percent ->
                    val charging = if (snapshot.isCharging == true) " · Cargando" else ""
                    "$percent%$charging"
                } ?: "No disponible"
            )
            StatusLine(
                icon = Icons.Outlined.Storage,
                label = "Almacenamiento",
                value = "${formatBytes(snapshot.storageAvailableBytes)} / ${formatBytes(snapshot.storageTotalBytes)} libres/total"
            )
            StatusLine(
                icon = Icons.Outlined.Settings,
                label = snapshot.deviceName,
                value = "Android ${snapshot.androidVersion}"
            )
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF8FC4FF))
        Text(
            text = label,
            modifier = Modifier.padding(start = 9.dp).weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFDCE6F5)
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return "No disponible"
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(java.util.Locale.ROOT, "%.1f GB", gib)
}
