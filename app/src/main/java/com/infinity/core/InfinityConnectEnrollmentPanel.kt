package com.infinity.core

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONNECT_ENROLLMENT_PREFS = "infinity_connect_enrollment"
private const val CONNECT_CLIENT_KEY_ALIAS = "infinity-connect-client-v1"

/** User-mediated CSR export, certificate-chain installation and real mTLS identity verification. */
@Composable
fun InfinityConnectEnrollmentPanel() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(CONNECT_ENROLLMENT_PREFS, Context.MODE_PRIVATE)
    }
    val savedClientId = remember {
        prefs.getString("client_device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("client_device_id", it).apply()
        }
    }
    var clientDeviceId by remember { mutableStateOf(savedClientId) }
    var companionHost by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    var companionPort by remember { mutableStateOf(prefs.getString("port", "7443") ?: "7443") }
    var caPin by remember { mutableStateOf(prefs.getString("ca_pin", "") ?: "") }
    var serverPin by remember { mutableStateOf(prefs.getString("server_pin", "") ?: "") }
    var expectedServerId by remember { mutableStateOf(prefs.getString("server_id", "") ?: "") }
    var pendingCsr by remember { mutableStateOf<ByteArray?>(null) }
    var status by remember { mutableStateOf("Enrolamiento pendiente. Todavía no hay una identidad Windows verificada.") }
    var busy by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val createCsrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val csr = pendingCsr
        pendingCsr = null
        if (uri == null || csr == null) {
            status = "Exportación del CSR cancelada."
        } else {
            scope.launch {
                val saved = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(csr) }
                            ?: error("No se pudo abrir el destino del CSR.")
                    }
                }
                status = if (saved.isSuccess) {
                    "CSR exportado. Transfiere ese archivo al PC y solicita la emisión local del certificado."
                } else {
                    "No se pudo guardar el CSR: ${saved.exceptionOrNull()?.message ?: "error de archivo"}"
                }
                csr.fill(0)
            }
        }
    }

    val installChainLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            status = "Importación del certificado cancelada."
        } else {
            scope.launch {
                busy = true
                status = "Validando certificado, clave y autoridad..."
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(2048)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 64 * 1024) {
                                    "La cadena de certificados supera 64 KiB."
                                }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: error("No se pudo abrir la cadena de certificados.")
                        try {
                            InfinityConnectClientCertificateInstaller().install(
                                CONNECT_CLIENT_KEY_ALIAS,
                                bytes,
                                caPin
                            )
                        } finally {
                            bytes.fill(0)
                        }
                    }
                }
                busy = false
                if (result.isSuccess) {
                    prefs.edit().putString("ca_pin", caPin).apply()
                    status = "Certificado cliente instalado y vinculado a la clave protegida de Android Keystore."
                } else {
                    status = "Certificado rechazado: ${result.exceptionOrNull()?.message ?: "validación fallida"}"
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("EMPAREJAMIENTO SEGURO ANDROID–WINDOWS", style = MaterialTheme.typography.titleMedium)
            Text(
                "Este proceso crea una clave no exportable, genera un CSR firmado, instala el certificado emitido por el PC y verifica la identidad del Companion mediante TLS 1.3 con mTLS."
            )
            OutlinedTextField(
                value = clientDeviceId,
                onValueChange = { clientDeviceId = it.take(64) },
                label = { Text("ID de esta tablet (CN del certificado)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                enabled = !busy && clientDeviceId.matches(Regex("[A-Za-z0-9._-]{1,64}")),
                onClick = {
                    runCatching {
                        val identity = InfinityConnectIdentityKeyStore()
                        identity.getOrCreate(CONNECT_CLIENT_KEY_ALIAS)
                        pendingCsr = identity.createCertificateSigningRequest(
                            CONNECT_CLIENT_KEY_ALIAS,
                            clientDeviceId
                        )
                        createCsrLauncher.launch("infinity-${clientDeviceId}.csr")
                    }.onFailure {
                        status = "No se pudo generar el CSR: ${it.message ?: "error de clave"}"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("1 · Generar y exportar CSR firmado") }

            OutlinedTextField(
                value = caPin,
                onValueChange = { caPin = it.take(95) },
                label = { Text("SHA-256 de la CA de enrolamiento (Windows)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedButton(
                enabled = !busy && caPin.replace(":", "").replace(" ", "").matches(Regex("[A-Fa-f0-9]{64}")),
                onClick = { installChainLauncher.launch(arrayOf("application/octet-stream", "application/pkix-cert")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("2 · Instalar cadena de certificado emitida") }

            OutlinedTextField(
                value = companionHost,
                onValueChange = { companionHost = it.take(253) },
                label = { Text("IP o nombre privado/VPN del PC") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = companionPort,
                onValueChange = { companionPort = it.filter(Char::isDigit).take(5) },
                label = { Text("Puerto INFINITY Companion (predeterminado 7443)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = serverPin,
                onValueChange = { serverPin = it.take(95) },
                label = { Text("SHA-256 del certificado TLS del PC") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = expectedServerId,
                onValueChange = { expectedServerId = it.take(36) },
                label = { Text("Device ID del Companion (GUID)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                enabled = !busy &&
                    companionHost.isNotBlank() &&
                    companionPort.toIntOrNull()?.let { it in 1024..65535 } == true &&
                    serverPin.replace(":", "").replace(" ", "").matches(Regex("[A-Fa-f0-9]{64}")) &&
                    runCatching { UUID.fromString(expectedServerId) }.isSuccess,
                onClick = {
                    scope.launch {
                        busy = true
                        status = "Estableciendo TLS 1.3 con autenticación mutua..."
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val host = companionHost.trim()
                                require(host.none { it.isWhitespace() || it == '/' || it == '@' }) {
                                    "La dirección del PC contiene caracteres no permitidos."
                                }
                                val formattedHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
                                InfinityConnectMtlsTransport().fetchWindowsIdentity(
                                    "https://$formattedHost:${companionPort}/",
                                    CONNECT_CLIENT_KEY_ALIAS,
                                    serverPin,
                                    expectedServerId
                                )
                            }
                        }
                        busy = false
                        if (result.isSuccess) {
                            val identity = result.getOrThrow()
                            prefs.edit()
                                .putString("host", companionHost.trim())
                                .putString("port", companionPort)
                                .putString("ca_pin", caPin)
                                .putString("server_pin", serverPin)
                                .putString("server_id", expectedServerId)
                                .apply()
                            status = "IDENTIDAD VERIFICADA: ${identity.product} · ${identity.deviceId} · ${identity.transportSecurity}"
                        } else {
                            status = "Autenticación no completada: ${result.exceptionOrNull()?.message ?: "error TLS"}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("3 · Verificar identidad Windows con mTLS") }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Text(
                "El CSR y la cadena son archivos públicos, pero la clave privada nunca se exporta. La emisión y aprobación siguen siendo operaciones locales del administrador de Windows. Esta verificación no habilita escritorio remoto.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                enabled = !busy,
                onClick = { confirmForget = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Eliminar identidad y confianza de esta tablet") }
        }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Eliminar identidad local") },
            text = { Text("Se borrará la clave privada de Android Keystore y los pins guardados. El PC deberá revocar también el certificado emitido.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { InfinityConnectIdentityKeyStore().delete(CONNECT_CLIENT_KEY_ALIAS) }
                    prefs.edit().clear().apply()
                    confirmForget = false
                    status = "Identidad local eliminada. Revoca el certificado en Windows para cerrar su autorización."
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancelar") }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        )
    }
}
