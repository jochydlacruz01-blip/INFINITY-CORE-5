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
    var confirmDeviceRevocation by remember { mutableStateOf(false) }
    var deviceStatus by remember { mutableStateOf<String?>(null) }
    var confirmSessionConsent by remember { mutableStateOf(false) }
    var identityVerified by remember { mutableStateOf(false) }
    var sessionActive by remember { mutableStateOf(false) }
    var activeSessionId by remember { mutableStateOf("") }
    var sessionExpiry by remember { mutableStateOf("") }
    var allowedCapabilities by remember { mutableStateOf(setOf("identity.read")) }
    var confirmRemoteConsent by remember { mutableStateOf(false) }
    var confirmFileConsent by remember { mutableStateOf(false) }
    var remoteSessionActive by remember { mutableStateOf(false) }
    var remoteSessionId by remember { mutableStateOf("") }
    var remoteSessionToken by remember { mutableStateOf("") }
    var remoteSessionExpiry by remember { mutableStateOf("") }
    var fileSessionActive by remember { mutableStateOf(false) }
    var fileSessionId by remember { mutableStateOf("") }
    var fileSessionExpiry by remember { mutableStateOf("") }
    var fileSessionToken by remember { mutableStateOf("") }
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
                            identityVerified = true
                            allowedCapabilities = identity.allowedCapabilities
                            status = "IDENTIDAD VERIFICADA: ${identity.product} · ${identity.deviceId} · ${identity.transportSecurity}"
                        } else {
                            identityVerified = false
                            allowedCapabilities = setOf("identity.read")
                            status = "Autenticación no completada: ${result.exceptionOrNull()?.message ?: "error TLS"}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("3 · Verificar identidad Windows con mTLS") }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            if (identityVerified) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    InfinityConnectMtlsTransport().fetchCurrentDeviceStatus(
                                        companionBaseUrl(companionHost, companionPort),
                                        CONNECT_CLIENT_KEY_ALIAS,
                                        serverPin,
                                        expectedServerId
                                    )
                                }
                            }
                            busy = false
                            deviceStatus = result.getOrNull()?.let { device ->
                                "Estado: ${device.status} · ID: ${device.deviceId} · Última conexión: ${device.lastSeenUtc ?: "sin registro"}"
                            }
                            if (result.isFailure) status = "No se pudo consultar el dispositivo: ${result.exceptionOrNull()?.message ?: "error mTLS"}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Consultar dispositivo autorizado") }
                deviceStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { confirmDeviceRevocation = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Revocar este dispositivo en Windows") }
            }
            if (identityVerified && !sessionActive) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { confirmSessionConsent = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("4 · Autorizar sesión protegida (solo identidad)") }
            }
            if (identityVerified) {
                Text(
                    "Capacidades habilitadas por Windows: ${allowedCapabilities.sorted().joinToString()}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (InfinityConnectFeatureGates.REMOTE_DESKTOP_VALIDATED && !remoteSessionActive &&
                    allowedCapabilities.containsAll(setOf("remote.display", "remote.input"))) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { confirmRemoteConsent = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Solicitar autorización de escritorio remoto") }
                }
                if (remoteSessionActive) {
                    Text("Permisos de escritorio concedidos · vence: $remoteSessionExpiry")
                    if (InfinityConnectFeatureGates.REMOTE_DESKTOP_VALIDATED && remoteSessionToken.isNotBlank()) {
                        InfinityConnectRemoteDesktopPanel(
                            companionHost = companionHost,
                            companionPort = companionPort.toInt(),
                            clientKeyAlias = CONNECT_CLIENT_KEY_ALIAS,
                            companionServerPin = serverPin,
                            expectedWindowsDeviceId = expectedServerId,
                            remoteSessionId = remoteSessionId,
                            remoteSessionToken = remoteSessionToken
                        )
                    } else {
                        Text(
                            "Conexión RDP bloqueada por seguridad hasta validar físicamente la tablet y el PC Windows.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val vault = InfinityConnectCredentialVault(context)
                                val token = vault.retrieve("remote-session:$expectedServerId")
                                val revoked = runCatching {
                                    withContext(Dispatchers.IO) {
                                        token != null && InfinityConnectMtlsTransport().revokeIdentitySession(
                                            companionBaseUrl(companionHost, companionPort),
                                            CONNECT_CLIENT_KEY_ALIAS, serverPin, remoteSessionId, token, expectedServerId
                                        )
                                    }
                                }.getOrDefault(false)
                                busy = false
                                if (revoked) {
                                    vault.delete("remote-session:$expectedServerId")
                                    remoteSessionActive = false
                                    remoteSessionId = ""
                                    remoteSessionToken = ""
                                    status = "Permisos de escritorio revocados en Windows."
                                } else status = "No se pudo confirmar la revocación de escritorio."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revocar permisos de escritorio") }
                }
                if (InfinityConnectFeatureGates.FILE_TRANSFER_VALIDATED && !fileSessionActive &&
                    allowedCapabilities.contains("file.transfer")) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { confirmFileConsent = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Solicitar transferencia segura de archivos") }
                }
                if (fileSessionActive) {
                    Text("Sesión de archivos autorizada · vence: $fileSessionExpiry")
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val vault = InfinityConnectCredentialVault(context)
                                val token = vault.retrieve("file-session:$expectedServerId")
                                val revoked = runCatching {
                                    withContext(Dispatchers.IO) {
                                        token != null && InfinityConnectMtlsTransport().revokeIdentitySession(
                                            companionBaseUrl(companionHost, companionPort),
                                            CONNECT_CLIENT_KEY_ALIAS, serverPin, fileSessionId, token, expectedServerId
                                        )
                                    }
                                }.getOrDefault(false)
                                busy = false
                                if (revoked) {
                                    vault.delete("file-session:$expectedServerId")
                                    fileSessionActive = false
                                    fileSessionId = ""
                                    fileSessionToken = ""
                                    status = "Sesión de transferencia revocada en Windows."
                                } else status = "No se pudo confirmar la revocación de archivos."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revocar sesión de archivos") }
                    if (InfinityConnectFeatureGates.FILE_TRANSFER_VALIDATED && fileSessionToken.isNotBlank()) {
                        InfinityConnectFileTransferPanel(
                            baseUrl = companionBaseUrl(companionHost, companionPort),
                            clientKeyAlias = CONNECT_CLIENT_KEY_ALIAS,
                            serverPin = serverPin,
                            expectedServerId = expectedServerId,
                            sessionId = fileSessionId,
                            sessionToken = fileSessionToken
                        )
                    }
                }
            }
            if (sessionActive) {
                Text("Sesión de identidad autorizada · vence: $sessionExpiry")
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val tokenVault = InfinityConnectCredentialVault(context)
                            val token = tokenVault.retrieve("identity-session:$expectedServerId")
                            val revoked = runCatching {
                                withContext(Dispatchers.IO) {
                                    token != null && InfinityConnectMtlsTransport().revokeIdentitySession(
                                        "https://${if (companionHost.contains(':') && !companionHost.startsWith("[")) "[$companionHost]" else companionHost}:${companionPort}/",
                                        CONNECT_CLIENT_KEY_ALIAS,
                                        serverPin,
                                        activeSessionId,
                                        token,
                                        expectedServerId
                                    )
                                }
                            }.getOrDefault(false)
                            busy = false
                            if (revoked) {
                                tokenVault.delete("identity-session:$expectedServerId")
                                prefs.edit().remove("session_id").remove("session_expiry").apply()
                                sessionActive = false
                                activeSessionId = ""
                                status = "Sesión revocada en Windows."
                            } else {
                                status = "No se pudo confirmar la revocación. La sesión expira automáticamente en Windows en 15 minutos."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Revocar sesión") }
            }
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
    if (confirmDeviceRevocation) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDeviceRevocation = false },
            title = { Text("Revocar este dispositivo") },
            text = { Text("Windows marcará este certificado como revocado, cancelará sus sesiones y rechazará conexiones futuras. Después se eliminará la clave privada de Android Keystore. Esta acción no se puede deshacer; para volver a conectar habrá que emitir y aprobar un certificado nuevo.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmDeviceRevocation = false
                        scope.launch {
                            busy = true
                            val revoked = runCatching {
                                withContext(Dispatchers.IO) {
                                    InfinityConnectMtlsTransport().revokeCurrentDevice(
                                        companionBaseUrl(companionHost, companionPort),
                                        CONNECT_CLIENT_KEY_ALIAS,
                                        serverPin,
                                        expectedServerId
                                    )
                                }
                            }
                            if (revoked.getOrDefault(false)) {
                                runCatching { InfinityConnectIdentityKeyStore().delete(CONNECT_CLIENT_KEY_ALIAS) }
                                val vault = InfinityConnectCredentialVault(context)
                                vault.delete("identity-session:$expectedServerId")
                                vault.delete("remote-session:$expectedServerId")
                                vault.delete("file-session:$expectedServerId")
                                prefs.edit().clear().apply()
                                identityVerified = false
                                sessionActive = false
                                activeSessionId = ""
                                remoteSessionActive = false
                                remoteSessionId = ""
                                remoteSessionToken = ""
                                fileSessionActive = false
                                fileSessionId = ""
                                fileSessionToken = ""
                                allowedCapabilities = setOf("identity.read")
                                deviceStatus = "Revocado"
                                status = "Dispositivo revocado en Windows. Sesiones canceladas y clave local eliminada."
                            } else {
                                status = "Windows no confirmó la revocación. La identidad local se conserva para permitir reintentar."
                            }
                            busy = false
                        }
                    }
                ) { Text("Revocar definitivamente") }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmDeviceRevocation = false }
                ) { Text("Cancelar") }
            }
        )
    }

    if (confirmSessionConsent) {
        AlertDialog(
            onDismissRequest = { confirmSessionConsent = false },
            title = { Text("Autorizar sesión INFINITY CONNECT") },
            text = { Text("¿Autorizas una sesión protegida de 15 minutos? En esta etapa solo se concede identity.read. No se habilitan pantalla, teclado, ratón ni archivos.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSessionConsent = false
                    scope.launch {
                        busy = true
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val host = companionHost.trim()
                                val formattedHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
                                InfinityConnectMtlsTransport().createIdentitySession(
                                    "https://$formattedHost:${companionPort}/",
                                    CONNECT_CLIENT_KEY_ALIAS,
                                    serverPin,
                                    expectedServerId
                                )
                            }
                        }
                        busy = false
                        if (result.isSuccess) {
                            val grant = result.getOrThrow()
                            val saved = InfinityConnectCredentialVault(context).save(
                                "identity-session:$expectedServerId",
                                grant.token
                            )
                            if (saved) {
                                activeSessionId = grant.sessionId
                                sessionExpiry = grant.expiresAtUtc
                                sessionActive = true
                                prefs.edit()
                                    .putString("session_id", grant.sessionId)
                                    .putString("session_expiry", grant.expiresAtUtc)
                                    .apply()
                                status = "Sesión autorizada por mTLS. Capacidad concedida: identity.read."
                            } else {
                                status = "Windows creó la sesión, pero no se pudo guardar su token cifrado. Revoca la sesión desde Windows."
                            }
                        } else {
                            status = "Windows no autorizó la sesión: ${result.exceptionOrNull()?.message ?: "error de autorización"}"
                        }
                    }
                }) { Text("Autorizar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSessionConsent = false }) { Text("Cancelar") }
            }
        )
    }

    if (confirmRemoteConsent) {
        AlertDialog(
            onDismissRequest = { confirmRemoteConsent = false },
            title = { Text("Autorizar escritorio remoto") },
            text = { Text("¿Autorizas que esta tablet solicite una sesión de 15 minutos para ver la pantalla y controlar teclado/ratón? Windows debe tener habilitadas ambas capacidades. No se activa el túnel hasta superar las validaciones de seguridad.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoteConsent = false
                    scope.launch {
                        busy = true
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                InfinityConnectMtlsTransport().createRemoteSession(
                                    companionBaseUrl(companionHost, companionPort),
                                    CONNECT_CLIENT_KEY_ALIAS, serverPin, expectedServerId
                                )
                            }
                        }
                        busy = false
                        if (result.isSuccess) {
                            val grant = result.getOrThrow()
                            val saved = InfinityConnectCredentialVault(context).save(
                                "remote-session:$expectedServerId", grant.token
                            )
                            if (saved) {
                                remoteSessionId = grant.sessionId
                                remoteSessionToken = grant.token
                                remoteSessionExpiry = grant.expiresAtUtc
                                remoteSessionActive = true
                                status = "Permisos de escritorio concedidos por Windows. El túnel sigue protegido por validación."
                            } else status = "No se pudo guardar el token cifrado. Revoca la sesión desde Windows."
                        } else status = "Windows no autorizó escritorio remoto: ${result.exceptionOrNull()?.message ?: "política denegada"}"
                    }
                }) { Text("Autorizar escritorio") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoteConsent = false }) { Text("Cancelar") } }
        )
    }

    if (confirmFileConsent) {
        AlertDialog(
            onDismissRequest = { confirmFileConsent = false },
            title = { Text("Autorizar transferencia de archivos") },
            text = { Text("¿Autorizas una sesión de 15 minutos para enviar y recibir archivos? Cada archivo tiene un límite de 50 MiB y se verifica mediante SHA-256. Windows debe habilitar file.transfer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFileConsent = false
                    scope.launch {
                        busy = true
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                InfinityConnectMtlsTransport().createFileTransferSession(
                                    companionBaseUrl(companionHost, companionPort),
                                    CONNECT_CLIENT_KEY_ALIAS, serverPin, expectedServerId
                                )
                            }
                        }
                        busy = false
                        if (result.isSuccess) {
                            val grant = result.getOrThrow()
                            val saved = InfinityConnectCredentialVault(context).save(
                                "file-session:$expectedServerId", grant.token
                            )
                            if (saved) {
                                fileSessionId = grant.sessionId
                                fileSessionExpiry = grant.expiresAtUtc
                                fileSessionToken = grant.token
                                fileSessionActive = true
                                status = "Sesión de transferencia autorizada por Windows."
                            } else status = "No se pudo guardar el token cifrado. Revoca la sesión desde Windows."
                        } else status = "Windows no autorizó archivos: ${result.exceptionOrNull()?.message ?: "política denegada"}"
                    }
                }) { Text("Autorizar archivos") }
            },
            dismissButton = { TextButton(onClick = { confirmFileConsent = false }) { Text("Cancelar") } }
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Eliminar identidad local") },
            text = { Text("Se borrará la clave privada de Android Keystore y los pins guardados. El PC deberá revocar también el certificado emitido.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { InfinityConnectIdentityKeyStore().delete(CONNECT_CLIENT_KEY_ALIAS) }
                    val vault = InfinityConnectCredentialVault(context)
                    vault.delete("identity-session:$expectedServerId")
                    vault.delete("remote-session:$expectedServerId")
                    vault.delete("file-session:$expectedServerId")
                    identityVerified = false
                    allowedCapabilities = setOf("identity.read")
                    sessionActive = false
                    activeSessionId = ""
                    remoteSessionActive = false
                    remoteSessionId = ""
                    remoteSessionToken = ""
                    fileSessionActive = false
                    fileSessionId = ""
                    fileSessionToken = ""
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


private fun companionBaseUrl(hostValue: String, port: String): String {
    val host = hostValue.trim()
    val formattedHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
    return "https://$formattedHost:$port/"
}
