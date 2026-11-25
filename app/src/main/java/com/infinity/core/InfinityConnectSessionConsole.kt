package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Session viewport boundary for the future FreeRDP framebuffer.
 * This is intentionally a placeholder viewport, not a remote desktop renderer.
 * Replace its content with engine frame callbacks when native RDP is integrated.
 */
@Composable
fun InfinityConnectSessionConsole(
    targetLabel: String,
    stateLabel: String,
    onDisconnect: () -> Unit,
    onKeyboard: () -> Unit,
    onPointerMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("INFINITY CONNECT", style = MaterialTheme.typography.titleLarge)
                    Text(targetLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(stateLabel, color = MaterialTheme.colorScheme.secondary)
                }
                OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).aspectRatio(16f / 9f)
                    .background(Color(0xFF101827)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RDP FRAMEBUFFER", color = Color(0xFF90CAF9), style = MaterialTheme.typography.titleMedium)
                    Text("Viewport reservado para pantalla remota", color = Color.White)
                    Text("Motor nativo pendiente de integración", color = Color(0xFFB0BEC5))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onKeyboard, modifier = Modifier.weight(1f)) { Text("Teclado") }
                OutlinedButton(onClick = onPointerMode, modifier = Modifier.weight(1f)) { Text("Modo táctil") }
            }
            Text(
                "La consola define la interfaz de sesión. Todavía no transmite pantalla ni entrada remota hasta conectar un motor RDP real.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
