package com.infinity.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Conservative, read-only preview policy. Binary and potentially expensive formats
 * are deliberately left to an installed Android application.
 */
internal object InfinityFilesPreviewPolicy {
    const val MAX_PREVIEW_BYTES = 256 * 1024

    fun supportsTextPreview(fileName: String, mimeType: String?): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.startsWith("text/")) return true
        if (mime in setOf("application/json", "application/xml", "application/csv", "application/x-yaml")) return true
        // A provider's explicit binary/unknown MIME type overrides a misleading extension.
        if (mime.isNotEmpty()) return false

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in setOf("txt", "md", "csv", "json", "xml", "log", "yaml", "yml", "kt", "kts", "java", "html", "css")
    }

    fun supportsImagePreview(fileName: String, mimeType: String?): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val supportedMimeTypes = setOf("image/png", "image/jpeg", "image/jpg", "image/webp", "image/bmp")
        if (mime in supportedMimeTypes) return true
        if (mime.isNotEmpty()) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in setOf("png", "jpg", "jpeg", "webp", "bmp")
    }
}

@Composable
internal fun InfinityFilesPreviewDialog(
    uri: Uri,
    fileName: String,
    mimeType: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var previewText by remember(uri) { mutableStateOf<String?>(null) }
    var previewBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember(uri) { mutableStateOf<String?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }

    LaunchedEffect(uri, fileName, mimeType) {
        loading = true
        previewText = null
        previewBitmap = null
        errorMessage = null
        val imagePreview = InfinityFilesPreviewPolicy.supportsImagePreview(fileName, mimeType)
        val textPreview = InfinityFilesPreviewPolicy.supportsTextPreview(fileName, mimeType)
        if (!imagePreview && !textPreview) {
            errorMessage = "La vista previa interna todavía no admite este formato. Puedes abrirlo con una aplicación Android."
            loading = false
        } else {
            try {
                if (imagePreview) {
                    previewBitmap = withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        } ?: throw IllegalStateException("No se pudo abrir la imagen.")
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                            throw IllegalArgumentException("El archivo de imagen no es válido.")
                        }
                        var sampleSize = 1
                        while (bounds.outWidth / sampleSize > 2048 || bounds.outHeight / sampleSize > 2048) {
                            sampleSize *= 2
                        }
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        } ?: throw IllegalStateException("No se pudo decodificar la imagen.")
                    }
                } else {
                    previewText = withContext(Dispatchers.IO) {
                        val output = ByteArrayOutputStream()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (total <= InfinityFilesPreviewPolicy.MAX_PREVIEW_BYTES) {
                                val remaining = InfinityFilesPreviewPolicy.MAX_PREVIEW_BYTES + 1 - total
                                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                total += count
                            }
                        } ?: throw IllegalStateException("No se pudo abrir el archivo.")
                        val bytes = output.toByteArray()
                        if (bytes.any { it == 0.toByte() }) {
                            throw IllegalArgumentException("El archivo parece binario y no se mostrará como texto.")
                        }
                        val decoder = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
                        if (bytes.size > InfinityFilesPreviewPolicy.MAX_PREVIEW_BYTES) {
                            text + "\n\n[Vista limitada a 256 KB para proteger la memoria de la tablet.]"
                        } else text
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage = failure.message?.takeIf { it.isNotBlank() }
                    ?: "No se pudo generar la vista previa."
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName, maxLines = 2) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
                when {
                    loading -> CircularProgressIndicator()
                    previewText != null -> Text(previewText!!, style = MaterialTheme.typography.bodySmall)
                    previewBitmap != null -> Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Vista previa de $fileName",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentScale = ContentScale.Fit
                    )
                    else -> Text(errorMessage ?: "Vista previa no disponible.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
