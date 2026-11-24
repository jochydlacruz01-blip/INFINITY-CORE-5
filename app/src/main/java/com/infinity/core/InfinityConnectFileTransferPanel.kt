package com.infinity.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_CONNECT_TRANSFER_BYTES = 50L * 1024 * 1024

@Composable
internal fun InfinityConnectFileTransferPanel(
    baseUrl: String,
    clientKeyAlias: String,
    serverPin: String,
    expectedServerId: String,
    sessionId: String,
    sessionToken: String
) {
    val context = LocalContext.current
    val transport = remember { InfinityConnectMtlsTransport() }
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<InfinityConnectMtlsTransport.FileTransferReceipt>>(emptyList()) }
    var pendingDownload by remember { mutableStateOf<InfinityConnectMtlsTransport.FileTransferReceipt?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("La transferencia está protegida por mTLS y SHA-256.") }

    fun refreshFiles() {
        scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    transport.listRemoteFiles(
                        baseUrl, clientKeyAlias, serverPin, expectedServerId,
                        sessionId, sessionToken
                    )
                }
            }
            busy = false
            if (result.isSuccess) {
                files = result.getOrThrow()
                status = "Archivos disponibles: ${files.size}"
            } else {
                status = "No se pudo consultar el PC: ${result.exceptionOrNull()?.message ?: "error"}"
            }
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            status = "Selección de archivo cancelada."
        } else {
            scope.launch {
                busy = true
                status = "Verificando tamaño y SHA-256 del archivo..."
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val metadata = queryTransferMetadata(context, uri)
                        require(metadata.second in 0..MAX_CONNECT_TRANSFER_BYTES) {
                            "El archivo supera el límite de 50 MiB o no declara su tamaño."
                        }
                        val digest = calculateTransferSha256(context, uri)
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("No se pudo volver a abrir el archivo.")
                        input.use {
                            transport.uploadFile(
                                baseUrl,
                                clientKeyAlias,
                                serverPin,
                                expectedServerId,
                                sessionId,
                                sessionToken,
                                metadata.first,
                                metadata.second,
                                digest,
                                it
                            )
                        }
                    }
                }
                busy = false
                if (result.isSuccess) {
                    val receipt = result.getOrThrow()
                    status = "Archivo enviado y verificado: ${receipt.fileName} · ID ${receipt.fileId}"
                    refreshFiles()
                } else {
                    status = "Transferencia rechazada: ${result.exceptionOrNull()?.message ?: "error"}"
                }
            }
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val selected = pendingDownload
        pendingDownload = null
        if (uri == null || selected == null) {
            status = "Descarga cancelada."
        } else {
            scope.launch {
                busy = true
                status = "Descargando y verificando integridad SHA-256..."
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("No se pudo abrir el destino de descarga.")
                        output.use {
                            transport.downloadFileTo(
                                baseUrl,
                                clientKeyAlias,
                                serverPin,
                                expectedServerId,
                                sessionId,
                                sessionToken,
                                selected.fileId,
                                it
                            )
                        }
                    }
                }
                busy = false
                if (result.isSuccess) {
                    status = "Archivo descargado y verificado: ${selected.fileName}"
                } else {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    status = "Descarga incompleta eliminada: ${result.exceptionOrNull()?.message ?: "error"}"
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("TRANSFERENCIA SEGURA DE ARCHIVOS", style = MaterialTheme.typography.titleSmall)
            Text("Límite 50 MiB por archivo · almacenamiento privado por dispositivo · verificación SHA-256.")
            Button(
                enabled = !busy,
                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Seleccionar archivo para enviar al PC") }
            OutlinedButton(
                enabled = !busy,
                onClick = { refreshFiles() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Actualizar archivos del PC") }

            files.forEach { file ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(file.fileName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${file.length} bytes · SHA-256 ${file.sha256.take(16)}…",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("ID: ${file.fileId}", style = MaterialTheme.typography.labelSmall)
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            pendingDownload = file
                            downloadLauncher.launch(file.fileName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Descargar a esta tablet") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val deleted = runCatching {
                                    withContext(Dispatchers.IO) {
                                        transport.deleteRemoteFile(
                                            baseUrl, clientKeyAlias, serverPin, expectedServerId,
                                            sessionId, sessionToken, file.fileId
                                        )
                                    }
                                }.getOrDefault(false)
                                busy = false
                                status = if (deleted) "Archivo eliminado del PC." else "No se pudo eliminar el archivo."
                                if (deleted) refreshFiles()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Eliminar del PC") }
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }

    androidx.compose.runtime.LaunchedEffect(sessionId) {
        refreshFiles()
    }
}

private fun queryTransferMetadata(context: Context, uri: Uri): Pair<String, Long> {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
            require(!name.isNullOrBlank() && size >= 0) {
                "El proveedor de documentos no entregó nombre y tamaño verificables."
            }
            return name to size
        }
    }
    error("No se pudo leer la información del documento.")
}

private fun calculateTransferSha256(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: error("No se pudo abrir el archivo para calcular SHA-256.")
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    input.use {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_CONNECT_TRANSFER_BYTES) { "El archivo supera el límite de 50 MiB." }
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
}
