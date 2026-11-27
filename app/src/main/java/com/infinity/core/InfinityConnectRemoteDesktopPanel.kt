package com.infinity.core

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun InfinityConnectRemoteDesktopPanel(
    companionHost: String,
    companionPort: Int,
    clientKeyAlias: String,
    companionServerPin: String,
    expectedWindowsDeviceId: String,
    remoteSessionId: String,
    remoteSessionToken: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val lifecycle = remember { InfinityConnectSessionLifecycle() }
    var rdpServerPin by remember { mutableStateOf("") }
    var rdpUsername by remember { mutableStateOf("") }
    var rdpDomain by remember { mutableStateOf("") }
    var rdpPassword by remember { mutableStateOf("") }
    var textToSend by remember { mutableStateOf("") }
    var client by remember { mutableStateOf<InfinityFreeRdpClient?>(null) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var frameRevision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("La sesión remota requiere autorización explícita y pin SHA-256 del certificado RDP.") }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf("") }

    val listener = remember {
        object : InfinityFreeRdpClient.Listener {
            override fun onConnected() {
                if (client == null) return
                lifecycle.transition(InfinityConnectSessionState.CONNECTED)
                connected = true
                InfinityConnectSessionSupervisorRuntime.start(
                    context.applicationContext,
                    InfinityConnectSessionConfig(
                        baseUrl = buildCompanionSessionBaseUrl(companionHost, companionPort),
                        clientKeyAlias = clientKeyAlias,
                        serverPin = companionServerPin,
                        expectedDeviceId = expectedWindowsDeviceId,
                        sessionId = remoteSessionId,
                        sessionToken = remoteSessionToken
                    )
                )
                status = "Escritorio Windows conectado mediante NLA y certificado fijado."
            }

            override fun onAuthenticating() {
                if (client == null) return
                lifecycle.transition(InfinityConnectSessionState.AUTHENTICATING)
                status = "Autenticando la sesión RDP con Network Level Authentication..."
            }

            override fun onDisconnected() {
                connected = false
                InfinityConnectSessionSupervisorRuntime.requestAuthenticatedRevocation(context.applicationContext)
                val disconnectedClient = client
                if (disconnectedClient != null) {
                    client = null
                    frame = null
                    lifecycle.transition(InfinityConnectSessionState.DISCONNECTING)
                    lifecycle.transition(InfinityConnectSessionState.IDLE)
                    status = "RDP desconectado · esperando confirmación de revocación de Windows."
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        runCatching { disconnectedClient.closeSession() }
                    }
                }
            }

            override fun onReconnecting() {
                if (client == null) return
                lifecycle.transition(InfinityConnectSessionState.RECONNECTING)
                connected = false
                status = "Windows está intentando restablecer la sesión RDP."
            }

            override fun onError(message: String) {
                val failedClient = client
                if (failedClient == null) return
                lifecycle.transition(InfinityConnectSessionState.FAILED, message)
                InfinityConnectSessionSupervisorRuntime.requestAuthenticatedRevocation(context.applicationContext)
                client = null
                connected = false
                frame = null
                status = message
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching { failedClient.closeSession() }
                }
            }

            override fun onFrame(frameBitmap: Bitmap) {
                if (client == null) {
                    frameBitmap.recycle()
                    return
                }
                frame = frameBitmap
                frameRevision++
            }

            override fun onResolutionChanged(width: Int, height: Int) {
                if (client == null) return
                resolution = "${width} × ${height}"
            }

            override fun onRdpCertificateRejected(host: String, observedFingerprint: String) {
                val rejectedClient = client
                if (rejectedClient == null) return
                connected = false
                frame = null
                client = null
                val message = "Certificado RDP rechazado. Huella observada: $observedFingerprint"
                lifecycle.transition(InfinityConnectSessionState.FAILED, message)
                InfinityConnectSessionSupervisorRuntime.requestAuthenticatedRevocation(context.applicationContext)
                status = message
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching { rejectedClient.closeSession() }
                }
            }
        }
    }

    fun disconnect() {
        val current = client ?: return
        lifecycle.transition(InfinityConnectSessionState.DISCONNECTING)
        InfinityConnectSessionSupervisorRuntime.requestAuthenticatedRevocation(context.applicationContext)
        client = null
        connected = false
        frame = null
        scope.launch {
            busy = true
            runCatching { current.closeSession() }
            lifecycle.transition(InfinityConnectSessionState.IDLE)
            busy = false
            status = "RDP cerrado localmente · esperando confirmación de revocación de Windows."
        }
    }

    DisposableEffect(client) {
        val activeClient = client
        onDispose {
            activeClient?.let { current ->
                // The composition scope is cancelled on disposal; cleanup must outlive it.
                InfinityConnectSessionSupervisorRuntime.requestAuthenticatedRevocation(context.applicationContext)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching { current.closeSession() }
                }
            }
        }
    }

    // Each delivered frame is an immutable snapshot owned by this composition.
    // Dispose the previous snapshot only after Compose has switched to the new one.
    DisposableEffect(frame) {
        val displayedFrame = frame
        onDispose {
            if (displayedFrame != null && !displayedFrame.isRecycled) displayedFrame.recycle()
        }
    }

    LaunchedEffect(remoteSessionId) {
        runCatching {
            InfinityConnectSessionSupervisorRuntime.start(context.applicationContext, InfinityConnectSessionConfig(
        baseUrl = buildCompanionSessionBaseUrl(companionHost, companionPort),
        clientKeyAlias = clientKeyAlias,
        serverPin = companionServerPin,
        expectedDeviceId = expectedWindowsDeviceId,
        sessionId = remoteSessionId,
        sessionToken = remoteSessionToken
    ))
        }.onFailure {
            status = "No se pudo iniciar el supervisor seguro de sesión. Verifica los permisos y vuelve a intentarlo."
        }
    }

    LaunchedEffect(frameRevision) {
        if (frameRevision > 0) runCatching { focusRequester.requestFocus() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ESCRITORIO WINDOWS · INFINITY CONNECT", style = MaterialTheme.typography.titleSmall)
            Text("Canal RDP sobre túnel mTLS · NLA · certificado SHA-256 fijado · portapapeles deshabilitado.")

            if (client == null) {
                OutlinedTextField(
                    value = rdpServerPin,
                    onValueChange = {
                        rdpServerPin = it.filter { ch -> ch in "0123456789abcdefABCDEF:" }.take(80)
                        InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                    },
                    label = { Text("SHA-256 del certificado RDP de Windows") },
                    supportingText = { Text("64 caracteres hexadecimales. No se acepta confiar en cualquier certificado.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rdpUsername,
                    onValueChange = {
                        rdpUsername = it.take(128)
                        InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                    },
                    label = { Text("Usuario de Windows") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rdpDomain,
                    onValueChange = {
                        rdpDomain = it.take(128)
                        InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                    },
                    label = { Text("Dominio (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rdpPassword,
                    onValueChange = {
                        rdpPassword = it.take(256)
                        InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                    },
                    label = { Text("Contraseña de Windows") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    enabled = !busy && rdpServerPin.filter { it != ':' }.matches(Regex("[0-9A-Fa-f]{64}")) &&
                        rdpUsername.isNotBlank() && rdpPassword.isNotEmpty(),
                    onClick = {
                        InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                        val pin = rdpServerPin
                        val credentials = InfinityFreeRdpClient.RdpCredentials(
                            rdpUsername.trim(),
                            rdpDomain.trim(),
                            rdpPassword.toCharArray()
                        )
                        rdpPassword = ""
                        scope.launch {
                            busy = true
                            lifecycle.transition(InfinityConnectSessionState.PREPARING)
                            var tunnel: InfinityConnectRdpTunnel.Handle? = null
                            var created: InfinityFreeRdpClient? = null
                            val result = runCatching {
                                val config = InfinityConnectRdpTunnel.Config(
                                    companionHost = companionHost,
                                    companionPort = companionPort,
                                    clientKeyAlias = clientKeyAlias,
                                    trustedServerSha256 = companionServerPin,
                                    expectedWindowsDeviceId = expectedWindowsDeviceId,
                                    sessionId = remoteSessionId,
                                    sessionToken = remoteSessionToken,
                                    grantedCapabilities = setOf("identity.read", "remote.display", "remote.input")
                                )
                                withContext(Dispatchers.IO) {
                                    // Assign inside the IO block so a cancelled dispatcher hand-off
                                    // cannot lose the newly opened handle before failure cleanup.
                                    tunnel = InfinityConnectRdpTunnel().open(config)
                                }
                                lifecycle.transition(InfinityConnectSessionState.CONNECTING)
                                val activeTunnel = tunnel ?: error("No se pudo establecer el túnel autenticado.")
                                created = InfinityFreeRdpClient(
                                    context,
                                    activeTunnel,
                                    pin,
                                    credentials,
                                    setOf("remote.display", "remote.input"),
                                    listener
                                )
                                client = created
                                created!!.connect()
                            }
                            if (result.isFailure) {
                                created?.let { failedClient ->
                                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                        runCatching { failedClient.closeSession() }
                                    }
                                } ?: tunnel?.close()
                                credentials.clear()
                                client = null
                                val message = "No se pudo abrir el escritorio. Verifica la autorización y la conectividad."
                                lifecycle.transition(InfinityConnectSessionState.FAILED, message)
                                status = message
                            } else {
                                status = "Negociando conexión segura con Windows..."
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Conectar al escritorio Windows") }
            } else {
                Text(if (connected) "Conectado · $resolution" else status)
                OutlinedButton(
                    enabled = !busy,
                    onClick = { disconnect() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Desconectar escritorio") }

                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Pantalla remota de Windows",
                        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            .clip(RoundedCornerShape(8.dp))
                            .focusRequester(focusRequester)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                event.nativeKeyEvent?.let {
                                    val accepted = client?.processAndroidKeyEvent(it) ?: false
                                    if (accepted && it.action == android.view.KeyEvent.ACTION_DOWN) {
                                        InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                                    }
                                    accepted
                                } ?: false
                            }
                            .pointerInput(client, bitmap.width, bitmap.height) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val x = (offset.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        val y = (offset.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        val active = client ?: return@detectTapGestures
                                        val moved = active.sendMouseMove(x, y)
                                        val pressed = active.sendMouseButton(x, y, 1, true)
                                        val released = active.sendMouseButton(x, y, 1, false)
                                        if (moved && pressed && released) {
                                            InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                                        }
                                    },
                                    onLongPress = { offset ->
                                        val x = (offset.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        val y = (offset.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        val active = client ?: return@detectTapGestures
                                        val moved = active.sendMouseMove(x, y)
                                        val pressed = active.sendMouseButton(x, y, 3, true)
                                        val released = active.sendMouseButton(x, y, 3, false)
                                        if (moved && pressed && released) {
                                            InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                                        }
                                    }
                                )
                            }
                            .pointerInput(client, bitmap.width, bitmap.height) {
                                var lastX = 0
                                var lastY = 0
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        lastX = (offset.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        lastY = (offset.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        if (client?.sendMouseButton(lastX, lastY, 1, true) == true) {
                                            InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        lastX = (change.position.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        lastY = (change.position.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        if (client?.sendMouseMove(lastX, lastY) == true) {
                                            InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                                        }
                                    },
                                    onDragEnd = { client?.sendMouseButton(lastX, lastY, 1, false) },
                                    onDragCancel = { client?.sendMouseButton(lastX, lastY, 1, false) }
                                )
                            }
                    )
                } ?: Text("Esperando el primer fotograma de Windows...")

                OutlinedTextField(
                    value = textToSend,
                    onValueChange = {
                        textToSend = it.take(2048)
                        if (connected) InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                    },
                    label = { Text("Texto para enviar al PC") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    enabled = connected && textToSend.isNotEmpty(),
                    onClick = {
                        val active = client ?: return@OutlinedButton
                        var offset = 0
                        var anyCharacterAccepted = false
                        while (offset < textToSend.length) {
                            val codePoint = Character.codePointAt(textToSend, offset)
                            anyCharacterAccepted = active.sendUnicodeCharacter(codePoint) || anyCharacterAccepted
                            offset += Character.charCount(codePoint)
                        }
                        if (anyCharacterAccepted) {
                            InfinityConnectSessionSupervisorRuntime.recordUserActivity()
                        }
                        textToSend = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enviar texto al PC") }
            }

            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}


/** Builds the HTTPS base URL while preserving IPv6 literal authority syntax. */
private fun buildCompanionSessionBaseUrl(hostValue: String, port: Int): String {
    val host = hostValue.trim().removePrefix("[").removeSuffix("]")
    require(host.isNotBlank() && host.none { it.isWhitespace() || it == '/' || it == '@' }) {
        "La dirección del Companion no es válida."
    }
    require(port in 1..65535) { "El puerto del Companion no es válido." }
    val authorityHost = if (host.contains(':')) "[$host]" else host
    return "https://$authorityHost:$port"
}
