package com.infinity.core

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class InfinityFileEntry(
    val id: String,
    val name: String,
    val mime: String?,
    val size: Long
) {
    val isFolder: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
}

@Composable
internal fun InfinityFileManager(
    rootUri: Uri?,
    accent: Color,
    onChooseFolder: () -> Unit,
    onOpenFile: (Uri, String?) -> Unit
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var currentId by remember(rootUri) {
        mutableStateOf(rootUri?.let(DocumentsContract::getTreeDocumentId))
    }
    var entries by remember { mutableStateOf<List<InfinityFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Nombre") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var createDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rootUri, currentId, refreshKey) {
        val tree = rootUri
        val parentId = currentId
        if (tree == null || parentId == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            try {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                val found = mutableListOf<InfinityFileEntry>()
                resolver.query(children, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        found += InfinityFileEntry(
                            cursor.getString(idCol),
                            cursor.getString(nameCol) ?: "Sin nombre",
                            if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                            if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)
                        )
                    }
                }
                found
            } catch (e: Exception) {
                throw e
            }
        }
        entries = result.getOrElse {
            error = it.localizedMessage ?: "No se pudo leer esta ubicación."
            emptyList()
        }
        loading = false
        selected = emptySet()
    }

    val visibleEntries = entries
        .filter { it.name.contains(search, ignoreCase = true) }
        .let { list ->
            when (sortBy) {
                "Tipo" -> list.sortedWith(compareByDescending<InfinityFileEntry> { it.isFolder }.thenBy { it.name.lowercase(Locale.getDefault()) })
                "Tamaño" -> list.sortedWith(compareByDescending<InfinityFileEntry> { it.isFolder }.thenByDescending { it.size })
                else -> list.sortedWith(compareByDescending<InfinityFileEntry> { it.isFolder }.thenBy { it.name.lowercase(Locale.getDefault()) })
            }
        }

    fun documentUri(id: String): Uri? = rootUri?.let { DocumentsContract.buildDocumentUriUsingTree(it, id) }

    fun deleteSelected() {
        val targets = selected.toList()
        try {
            targets.forEach { id -> documentUri(id)?.let { DocumentsContract.deleteDocument(resolver, it) } }
            selected = emptySet()
            refreshKey++
            message = "Operación de eliminación completada."
        } catch (e: Exception) {
            message = e.localizedMessage ?: "El proveedor no permitió eliminar el elemento."
        }
    }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("INFINITY FILE MANAGER", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Administrador de archivos • acceso autorizado por Android", color = Color(0xFFB7C7D9), fontSize = 9.sp)
            }
            TextButton(onClick = onChooseFolder) { Text(if (rootUri == null) "Elegir carpeta" else "Cambiar") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Buscar archivos…", fontSize = 11.sp) }
            )
            Box {
                OutlinedButton(onClick = { sortMenu = true }) { Text("Ordenar") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf("Nombre", "Tipo", "Tamaño").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            sortBy = option
                            sortMenu = false
                        })
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rootUri != null) {
                OutlinedButton(onClick = {
                    currentId = DocumentsContract.getTreeDocumentId(rootUri)
                    selected = emptySet()
                }) { Text("Inicio") }
                OutlinedButton(onClick = { createDialog = true }) { Text("+ Carpeta") }
                if (selected.size == 1) {
                    OutlinedButton(onClick = {
                        nameInput = entries.firstOrNull { it.id == selected.first() }?.name.orEmpty()
                        renameDialog = true
                    }) { Text("Renombrar") }
                }
                if (selected.isNotEmpty()) {
                    OutlinedButton(onClick = { deleteSelected() }) { Text("Eliminar ${selected.size}") }
                    TextButton(onClick = { selected = emptySet() }) { Text("Cancelar") }
                }
            }
        }

        if (rootUri == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Selecciona una carpeta para comenzar", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onChooseFolder) { Text("Seleccionar ubicación") }
                }
            }
        } else if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
        } else if (error != null) {
            Text("Error: $error", color = Color(0xFFFF9999), fontSize = 11.sp)
        } else if (visibleEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (search.isBlank()) "Esta carpeta está vacía." else "No hay resultados.", color = Color(0xFFB7C7D9), fontSize = 12.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visibleEntries, key = { it.id }) { entry ->
                    val isSelected = entry.id in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(if (isSelected) accent.copy(alpha = .16f) else Color(0x331B2B40), RoundedCornerShape(10.dp))
                            .border(1.dp, if (isSelected) accent.copy(.65f) else Color.White.copy(.06f), RoundedCornerShape(10.dp))
                            .clickable {
                                if (selected.isNotEmpty()) {
                                    selected = if (isSelected) selected - entry.id else selected + entry.id
                                } else if (entry.isFolder) {
                                    currentId = entry.id
                                } else {
                                    documentUri(entry.id)?.let { onOpenFile(it, entry.mime) }
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (entry.isFolder) "📁" else "📄", fontSize = 20.sp)
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text(entry.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (entry.isFolder) "Carpeta" else "${if (entry.size > 0) "${(entry.size / 1024).coerceAtLeast(1)} KB" else "Tamaño no disponible"} • ${entry.mime ?: "Archivo"}",
                                color = Color(0xFFB7C7D9), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected.isNotEmpty()) {
                            Checkbox(checked = isSelected, onCheckedChange = {
                                selected = if (it) selected + entry.id else selected - entry.id
                            })
                        } else if (entry.isFolder) {
                            Text("›", color = accent, fontSize = 22.sp)
                        } else {
                            TextButton(onClick = { selected = setOf(entry.id) }) { Text("Seleccionar") }
                        }
                    }
                }
            }
        }
    }

    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Crear carpeta") },
            text = {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("Nombre") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val parent = currentId?.let(::documentUri)
                    try {
                        if (parent != null && nameInput.isNotBlank()) {
                            DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, nameInput.trim())
                            refreshKey++
                            message = "Carpeta creada."
                        }
                    } catch (e: Exception) {
                        message = e.localizedMessage ?: "No se pudo crear la carpeta."
                    }
                    nameInput = ""
                    createDialog = false
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancelar") } }
        )
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Renombrar elemento") },
            text = { OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("Nuevo nombre") }) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val target = selected.singleOrNull()?.let(::documentUri)
                        if (target != null && nameInput.isNotBlank()) {
                            DocumentsContract.renameDocument(resolver, target, nameInput.trim())
                            refreshKey++
                            message = "Elemento renombrado."
                        }
                    } catch (e: Exception) {
                        message = e.localizedMessage ?: "El proveedor no permitió renombrar."
                    }
                    renameDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancelar") } }
        )
    }

    message?.let { info ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("INFINITY FILE MANAGER") },
            text = { Text(info) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Aceptar") } }
        )
    }
}
