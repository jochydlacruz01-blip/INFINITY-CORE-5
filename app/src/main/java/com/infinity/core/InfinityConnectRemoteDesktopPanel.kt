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
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
                connected = true
                status = "Escritorio Windows conectado mediante NLA y certificado fijado."
            }

            override fun onDisconnected() {
                connected = false
                status = "La sesión RDP se desconectó."
            }

            override fun onError(message: String) {
                status = message
            }

            override fun onFrame(frameBitmap: Bitmap) {
                frame = frameBitmap
                frameRevision++
            }

            override fun onResolutionChanged(width: Int, height: Int) {
                resolution = "${width} × ${height}"
            }

            override fun onRdpCertificateRejected(host: String, observedFingerprint: String) {
                connected = false
                status = "Certificado RDP rechazado. Huella observada: $observedFingerprint"
            }
        }
    }

    fun disconnect() {
        val current = client ?: return
        client = null
        connected = false
        frame = null
        scope.launch {
            busy = true
            runCatching { current.closeSession() }
            busy = false
            status = "Sesión remota cerrada."
        }
    }

    DisposableEffect(client) {
        val activeClient = client
        onDispose { activeClient?.let { current -> scope.launch { runCatching { current.closeSession() } } } }
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
                    onValueChange = { rdpServerPin = it.filter { ch -> ch in "0123456789abcdefABCDEF:" }.take(80) },
                    label = { Text("SHA-256 del certificado RDP de Windows") },
                    supportingText = { Text("64 caracteres hexadecimales. No se acepta confiar en cualquier certificado.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rdpUsername,
                    onValueChange = { rdpUsername = it.take(128) },
                    label = { Text("Usuario de Windows") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rdpDomain,
                    onValueChange = { rdpDomain = it.take(128) },
                    label = { Text("Dominio (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rdpPassword,
                    onValueChange = { rdpPassword = it.take(256) },
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
                        val pin = rdpServerPin
                        val credentials = InfinityFreeRdpClient.RdpCredentials(
                            rdpUsername.trim(),
                            rdpDomain.trim(),
                            rdpPassword.toCharArray()
                        )
                        rdpPassword = ""
                        scope.launch {
                            busy = true
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
                                tunnel = withContext(Dispatchers.IO) {
                                    InfinityConnectRdpTunnel().open(config)
                                }
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
                                created?.let { runCatching { it.closeSession() } } ?: tunnel?.close()
                                credentials.clear()
                                client = null
                                status = "No se pudo abrir el escritorio: ${result.exceptionOrNull()?.message ?: "error"}"
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
                                event.nativeKeyEvent?.let { client?.processAndroidKeyEvent(it) } ?: false
                            }
                            .pointerInput(client, frameRevision) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val x = (offset.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        val y = (offset.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        client?.sendMouseMove(x, y)
                                        client?.sendMouseButton(x, y, 1, true)
                                        client?.sendMouseButton(x, y, 1, false)
                                    },
                                    onLongPress = { offset ->
                                        val x = (offset.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        val y = (offset.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        client?.sendMouseMove(x, y)
                                        client?.sendMouseButton(x, y, 3, true)
                                        client?.sendMouseButton(x, y, 3, false)
                                    }
                                )
                            }
                            .pointerInput(client, frameRevision) {
                                var lastX = 0
                                var lastY = 0
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        lastX = (offset.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        lastY = (offset.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        client?.sendMouseButton(lastX, lastY, 1, true)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        lastX = (change.position.x / size.width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        lastY = (change.position.y / size.height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        client?.sendMouseMove(lastX, lastY)
                                    },
                                    onDragEnd = { client?.sendMouseButton(lastX, lastY, 1, false) },
                                    onDragCancel = { client?.sendMouseButton(lastX, lastY, 1, false) }
                                )
                            }
                    )
                } ?: Text("Esperando el primer fotograma de Windows...")

                OutlinedTextField(
                    value = textToSend,
                    onValueChange = { textToSend = it.take(2048) },
                    label = { Text("Texto para enviar al PC") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    enabled = connected && textToSend.isNotEmpty(),
                    onClick = {
                        val active = client ?: return@OutlinedButton
                        var offset = 0
                        while (offset < textToSend.length) {
                            val codePoint = Character.codePointAt(textToSend, offset)
                            active.sendUnicodeCharacter(codePoint)
                            offset += Character.charCount(codePoint)
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
