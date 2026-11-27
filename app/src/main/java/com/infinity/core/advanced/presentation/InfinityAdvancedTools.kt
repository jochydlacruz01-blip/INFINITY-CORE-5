package com.infinity.core.advanced.presentation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.infinity.core.advanced.domain.automation.ExecuteInfinityAutomationAction
import com.infinity.core.advanced.domain.automation.InfinityAutomationAction
import com.infinity.core.advanced.domain.automation.InfinityAutomationDecision
import com.infinity.core.advanced.domain.automation.InfinityAutomationExecutionResult
import com.infinity.core.advanced.domain.automation.InfinityAutomationRequest
import com.infinity.core.advanced.domain.automation.InfinityAutomationExecutionLedger
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import com.infinity.core.advanced.domain.capabilities.InfinityThemeCapability
import com.infinity.core.advanced.domain.usecases.InfinityAdvancedOverviewSnapshot
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private const val AUTOMATION_PREFS = "infinity_advanced_automation"
private const val AUTOMATION_RULE_ID = "user_launch_application"

@Composable
fun InfinityAdvancedAutomationCard(
    registry: InfinityCapabilityRegistry,
    accent: Color,
    muted: Color,
    surface: Color
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(AUTOMATION_PREFS, Context.MODE_PRIVATE)
    }
    var targetPackage by remember {
        mutableStateOf(preferences.getString("target_package", "") ?: "")
    }
    var enabled by remember { mutableStateOf(preferences.getBoolean("enabled", false)) }
    var consent by remember { mutableStateOf(preferences.getBoolean("consent", false)) }
    var status by remember { mutableStateOf("La automatización solo se ejecuta cuando pulsas el botón.") }
    var executing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("AUTOMATIZACIÓN SEGURA", color = accent, fontWeight = FontWeight.Bold)
            Text(
                "Acción disponible: abrir una aplicación instalada. No hay tareas ocultas ni ejecución en segundo plano.",
                color = muted
            )
            OutlinedTextField(
                value = targetPackage,
                onValueChange = {
                    targetPackage = it.trim()
                    preferences.edit().putString("target_package", targetPackage).apply()
                },
                modifier = Modifier.fillMaxWidth().testTag("advanced-automation-package"),
                label = { Text("Nombre de paquete Android") },
                placeholder = { Text("com.ejemplo.aplicacion") },
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Regla habilitada", color = muted, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        preferences.edit().putBoolean("enabled", it).apply()
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = consent,
                    onCheckedChange = {
                        consent = it
                        preferences.edit().putBoolean("consent", it).apply()
                    }
                )
                Text(
                    "Autorizo abrir esta aplicación cuando pulse Ejecutar.",
                    color = muted
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth().testTag("advanced-automation-run"),
                enabled = enabled && consent && targetPackage.isNotBlank() && !executing,
                onClick = {
                    if (executing) return@Button
                    executing = true
                    scope.launch {
                        try {
                            val now = System.currentTimeMillis()
                            // Snapshot the user's current authorization before suspending.
                            val targetPackageSnapshot = targetPackage
                            val enabledSnapshot = enabled
                            val consentSnapshot = consent
                            val history = InfinityAutomationExecutionLedger.recentExecutions(
                                preferences.getString("execution_timestamps", "").orEmpty()
                                    .split(',')
                                    .takeLast(64)
                                    .mapNotNull { it.toLongOrNull() },
                                now
                            )
                            val request = InfinityAutomationRequest(
                                ruleId = AUTOMATION_RULE_ID,
                                action = InfinityAutomationAction.LAUNCH_APPLICATION,
                                enabled = enabledSnapshot,
                                userConsentGranted = consentSnapshot,
                                targetPackageName = targetPackageSnapshot,
                                nowEpochMillis = now,
                                lastExecutionEpochMillis = history.lastOrNull(),
                                executionsInLastHour = history.size
                            )
                            val result = try {
                                ExecuteInfinityAutomationAction(registry)(request)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                InfinityAutomationExecutionResult.ExecutionFailed
                            }
                            status = when (result) {
                                InfinityAutomationExecutionResult.Executed -> {
                                    val updatedHistory = InfinityAutomationExecutionLedger.recordExecution(history, now)
                                    preferences.edit()
                                        .putString("execution_timestamps", updatedHistory.joinToString(","))
                                        .apply()
                                    "Aplicación abierta correctamente."
                                }
                                InfinityAutomationExecutionResult.CapabilityUnavailable -> "La capacidad de apertura no está disponible."
                                InfinityAutomationExecutionResult.TargetNotFound -> "No se encontró una aplicación con ese paquete."
                                InfinityAutomationExecutionResult.ExecutionFailed -> "Android no permitió abrir la aplicación."
                                is InfinityAutomationExecutionResult.Rejected -> when (result.decision) {
                                    InfinityAutomationDecision.Allowed -> "Acción autorizada."
                                    is InfinityAutomationDecision.Rejected -> when (result.decision.reason) {
                                        InfinityAutomationDecision.Reason.RULE_DISABLED -> "Habilita la regla antes de ejecutarla."
                                        InfinityAutomationDecision.Reason.CONSENT_REQUIRED -> "Debes autorizar esta acción."
                                        InfinityAutomationDecision.Reason.RATE_LIMIT_EXCEEDED -> "Límite alcanzado: espera una hora."
                                        InfinityAutomationDecision.Reason.COOLDOWN_ACTIVE -> "Espera un minuto antes de repetir la acción."
                                        else -> "La solicitud no superó las validaciones de seguridad."
                                    }
                                }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            status = "No se pudo completar la acción. Revisa la aplicación y vuelve a intentarlo."
                        } finally {
                            executing = false
                        }
                    }
                }
            ) {
                Text(if (executing) "EJECUTANDO…" else "EJECUTAR AHORA")
            }
            Text(status, color = muted, modifier = Modifier.testTag("advanced-automation-status"))
        }
    }
}

@Composable
fun InfinityAdvancedSystemReportCard(
    snapshot: InfinityAdvancedOverviewSnapshot?,
    accent: Color,
    muted: Color,
    surface: Color
) {
    val context = LocalContext.current
    val report = remember(snapshot) { snapshot?.let(::buildInfinityAdvancedShareReport).orEmpty() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("INFORMES Y DIAGNÓSTICO", color = accent, fontWeight = FontWeight.Bold)
            Text("Resumen del estado actual; no presenta históricos que el dispositivo no haya registrado.", color = muted)
            Text("Captura: ${metrics?.capturedAtEpochMillis?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Pendiente"}", color = muted)
            Text("Estado: ${if (snapshot == null) "Datos temporalmente no disponibles" else "Lectura actual disponible"}", color = muted)
            Button(
                modifier = Modifier.fillMaxWidth().testTag("advanced-share-report"),
                enabled = snapshot != null,
                onClick = {
                    runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "INFINITY ADVANCED — Informe del sistema")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Compartir informe INFINITY"))
                    }.onFailure {
                        Toast.makeText(context, "No se pudo compartir el informe.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("COMPARTIR INFORME")
            }
        }
    }
}

@Composable
fun InfinityAdvancedThemeCard(
    registry: InfinityCapabilityRegistry,
    selectedThemeId: String,
    accent: Color,
    muted: Color,
    surface: Color
) {
    val themeCapability = remember(registry) {
        registry.find(InfinityCapabilityId.THEME, InfinityThemeCapability::class.java)
    }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Elige un tema para actualizar el escritorio INFINITY.") }
    var selectingTheme by remember { mutableStateOf(false) }
    val themes = listOf(
        "midnight" to "Medianoche",
        "ocean" to "Océano",
        "violet" to "Violeta",
        "emerald" to "Esmeralda",
        "sunset" to "Atardecer"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PERSONALIZACIÓN DEL ESCRITORIO", color = accent, fontWeight = FontWeight.Bold)
            Text("Tema activo: $selectedThemeId", color = muted)
            themes.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { (id, label) ->
                        Button(
                            modifier = Modifier.weight(1f).testTag("advanced-theme-$id"),
                            enabled = themeCapability != null && id != selectedThemeId && !selectingTheme,
                            onClick = {
                                if (selectingTheme) return@Button
                                selectingTheme = true
                                scope.launch {
                                    try {
                                        themeCapability?.selectTheme(id)
                                        status = "Tema $label aplicado."
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        status = "No se pudo aplicar el tema. El escritorio conserva su configuración anterior."
                                    } finally {
                                        selectingTheme = false
                                    }
                                }
                            }
                        ) {
                            Text(if (id == selectedThemeId) "$label ✓" else label)
                        }
                    }
                    if (pair.size == 1) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
            Text(status, color = muted, modifier = Modifier.testTag("advanced-theme-status"))
        }
    }
}
