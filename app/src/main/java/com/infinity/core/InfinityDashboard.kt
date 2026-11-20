package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DashboardInk = Color(0xFF0C1728)
private val DashboardPanel = Color(0xE8172B43)
private val DashboardMuted = Color(0xFFB6C7DA)
private val DashboardCyan = Color(0xFF56D6D0)
private val DashboardBlue = Color(0xFF54AFFF)
private val DashboardViolet = Color(0xFFB69CFF)
private val DashboardGold = Color(0xFFFFC477)

@Composable
internal fun InfinityRealDashboard(
    clockText: String,
    dateText: String,
    workspace: Int,
    onOpenWindow: (String) -> Unit,
    onOpenOrbit: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenControl: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
            start = 14.dp, end = 14.dp, top = 104.dp, bottom = 92.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("CENTRO DE CONTROL", color = DashboardCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Bienvenido a INFINITY", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("AURORA NEXT  •  ESPACIO DE TRABAJO $workspace", color = DashboardMuted, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(clockText, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
                Text(dateText, color = DashboardMuted, fontSize = 9.sp)
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardPanel)
        ) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF1D4564), Color(0xFF14253D), DashboardInk))
                ).padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("∞  PROJECT INFINITY", color = DashboardCyan, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Tu espacio digital.\nTu centro de operaciones.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
                    Text("Entorno Android nativo • Escritorio inteligente • Herramientas integradas", color = DashboardMuted, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpenWindow("apps") }) { Text("Abrir aplicaciones", fontSize = 10.sp) }
                        OutlinedButton(onClick = onOpenOrbit) { Text("Ver ORBIT", fontSize = 10.sp) }
                    }
                }
            }
        }

        Text("MÓDULOS PRINCIPALES", color = DashboardCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardModule("ESCRITORIO", "Ventanas y espacios", "▣", DashboardBlue, Modifier.weight(1f)) { onOpenWindow("about") }
            DashboardModule("ORBIT", "Estado del sistema", "◉", DashboardViolet, Modifier.weight(1f), onOpenOrbit)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardModule("CONNECT", "Perfiles y red TCP", "⌁", DashboardCyan, Modifier.weight(1f)) { onOpenWindow("connect") }
            DashboardModule("ADVANCED", "Privacidad y ajustes", "◇", DashboardGold, Modifier.weight(1f), onOpenAdvanced)
        }

        Text("ACCESO RÁPIDO", color = DashboardCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { DashboardQuickAction("Archivos", "▤") { onOpenWindow("files") } }
            item { DashboardQuickAction("Notas", "✎") { onOpenWindow("notes") } }
            item { DashboardQuickAction("Aplicaciones", "◈") { onOpenWindow("apps") } }
            item { DashboardQuickAction("Personalizar", "⚙") { onOpenWindow("settings") } }
            item { DashboardQuickAction("Control Center", "☷", onOpenControl) }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DashboardPanel)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("ESTADO DE LA PLATAFORMA", color = DashboardCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                DashboardStatusRow("Entorno Android", "Activo", DashboardCyan)
                DashboardStatusRow("Escritorio y ventanas", "Disponible", DashboardBlue)
                DashboardStatusRow("INFINITY CONNECT", "Diagnóstico TCP disponible · RDP pendiente", DashboardGold)
                DashboardStatusRow("Seguridad y privacidad", "Controles integrados", DashboardViolet)
                Text("Los indicadores de hardware y conectividad se muestran en sus módulos reales; no se inventan métricas en el panel de inicio.", color = DashboardMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun DashboardModule(
    title: String,
    subtitle: String,
    glyph: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DashboardPanel)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier.size(34.dp).background(tint.copy(alpha = .16f), RoundedCornerShape(11.dp))
                    .border(1.dp, tint.copy(alpha = .5f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Text(glyph, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = DashboardMuted, fontSize = 9.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DashboardQuickAction(label: String, glyph: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Text("$glyph  $label", fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun DashboardStatusRow(label: String, value: String, tint: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(tint, CircleShape))
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Text(value, color = DashboardMuted, fontSize = 9.sp, modifier = Modifier.widthIn(max = 190.dp))
    }
}
