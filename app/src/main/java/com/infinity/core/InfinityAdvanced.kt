package com.infinity.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AdvancedBackground = Color(0xF20D1B30)
private val AdvancedAccent = Color(0xFF56D6C9)
private val AdvancedMuted = Color(0xFFB7C7D9)

@Composable
fun InfinityAdvancedPanel(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val privacyPrefs = remember {
        context.getSharedPreferences("infinity_privacy", Context.MODE_PRIVATE)
    }

    var hideNotificationContent by remember {
        mutableStateOf(
            privacyPrefs.getBoolean("hide_notification_content", true)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = AdvancedBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "INFINITY ADVANCED",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Centro de notificaciones y acciones rápidas",
                        color = AdvancedMuted,
                        fontSize = 10.sp
                    )
                }

                TextButton(onClick = onClose) {
                    Text("CERRAR", color = AdvancedAccent)
                }
            }

            Text(
                "ACCIONES RÁPIDAS",
                color = AdvancedAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                AdvancedActionTile(
                    modifier = Modifier.weight(1f),
                    glyph = "◉",
                    title = "Wi-Fi",
                    subtitle = "Redes e Internet"
                ) {
                    openAdvancedSettings(context, Settings.ACTION_WIFI_SETTINGS)
                }

                AdvancedActionTile(
                    modifier = Modifier.weight(1f),
                    glyph = "ᛒ",
                    title = "Bluetooth",
                    subtitle = "Dispositivos"
                ) {
                    openAdvancedSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                AdvancedActionTile(
                    modifier = Modifier.weight(1f),
                    glyph = "☼",
                    title = "Pantalla",
                    subtitle = "Brillo y visualización"
                ) {
                    openAdvancedSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
                }

                AdvancedActionTile(
                    modifier = Modifier.weight(1f),
                    glyph = "♫",
                    title = "Sonido",
                    subtitle = "Volumen y audio"
                ) {
                    openAdvancedSettings(context, Settings.ACTION_SOUND_SETTINGS)
                }
            }

            Text(
                "NOTIFICACIONES",
                color = AdvancedAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1B304A)
                )
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Centro de notificaciones",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    !InfinityNotificationStore.accessGranted.value ->
                                        "Acceso pendiente de autorización"
                                    InfinityNotificationStore.accessEnabled.value ->
                                        "Acceso autorizado · servicio conectado"
                                    else ->
                                        "Acceso autorizado · esperando conexión"
                                },
                                color = if (InfinityNotificationStore.accessGranted.value)
                                    AdvancedAccent else AdvancedMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            openAdvancedSettings(
                                context,
                                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                            )
                        }
                    ) {
                        Text(
                            "ABRIR AJUSTES DE NOTIFICACIONES",
                            color = AdvancedAccent,
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "OCULTAR CONTENIDO PRIVADO",
                                color = AdvancedMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Evita guardar títulos y mensajes en el feed mientras esté activado",
                                color = AdvancedMuted,
                                fontSize = 9.sp
                            )
                        }

                        Switch(
                            checked = hideNotificationContent,
                            onCheckedChange = { enabled ->
                                hideNotificationContent = enabled
                                privacyPrefs.edit()
                                    .putBoolean("hide_notification_content", enabled)
                                    .apply()
                                InfinityNotificationStore.setHideContent(enabled)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "NOTIFICACIONES RECIBIDAS",
                            color = AdvancedAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        TextButton(
                            onClick = {
                                InfinityNotificationStore.clearAll()
                            },
                            enabled = InfinityNotificationStore.notifications.isNotEmpty()
                        ) {
                            Text("LIMPIAR", color = AdvancedAccent, fontSize = 9.sp)
                        }
                    }

                    if (InfinityNotificationStore.notifications.isEmpty()) {
                        Text(
                            "Todavía no hay notificaciones disponibles.",
                            color = AdvancedMuted,
                            fontSize = 11.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            InfinityNotificationStore.notifications.take(30).forEach { notification ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(13.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF263D57)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(11.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                notification.appName,
                                                color = AdvancedAccent,
                                                fontSize = 10.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                SimpleDateFormat(
                                                    "HH:mm",
                                                    Locale.getDefault()
                                                ).format(Date(notification.timestamp)),
                                                color = AdvancedMuted,
                                                fontSize = 9.sp
                                            )
                                        }

                                        if (hideNotificationContent) {
                                            Text(
                                                "Contenido oculto por privacidad",
                                                color = AdvancedMuted,
                                                fontSize = 11.sp
                                            )
                                        } else {
                                            Text(
                                                notification.title.ifBlank { "Notificación" },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                notification.message,
                                                color = AdvancedMuted,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row {
                                            TextButton(
                                                onClick = {
                                                    InfinityNotificationStore.open(notification)
                                                }
                                            ) {
                                                Text("ABRIR", color = AdvancedAccent)
                                            }

                                            TextButton(
                                                onClick = {
                                                    InfinityNotificationStore.dismiss(
                                                        notification.key
                                                    )
                                                }
                                            ) {
                                                Text("DESCARTAR", color = AdvancedMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "INFINITY ADVANCED · MÓDULO 1",
                color = AdvancedMuted,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun AdvancedActionTile(
    modifier: Modifier = Modifier,
    glyph: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF203A57)
        )
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                glyph,
                color = AdvancedAccent,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                subtitle,
                color = AdvancedMuted,
                fontSize = 9.sp
            )
        }
    }
}

private fun openAdvancedSettings(
    context: Context,
    action: String,
    extra: Pair<String, String>? = null
) {
    try {
        val intent = Intent(action)
        extra?.let {
            intent.putExtra(it.first, it.second)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (fallbackException: Exception) {
            val report = infinityGuardReport("Apertura de ajustes", fallbackException)
            android.widget.Toast.makeText(
                context,
                report.userMessage,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
