package com.infinity.core

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale

private data class InfinityFileEntry(
    val id: String,
    val name: String,
    val mime: String?,
    val size: Long,
    val modified: Long,
    val flags: Long?
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
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val rootDocumentId = remember(rootUri) { rootUri?.let { DocumentsContract.getTreeDocumentId(it) } }
    var currentId by remember(rootUri) { mutableStateOf(rootDocumentId) }
    var folderIds by remember(rootUri) { mutableStateOf(listOfNotNull(rootDocumentId)) }
    var folderPath by remember(rootUri) { mutableStateOf(listOf("Inicio")) }
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
    var clipboardIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var clipboardParent by remember { mutableStateOf<Uri?>(null) }
    var clipboardIsMove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var detailsEntry by remember { mutableStateOf<InfinityFileEntry?>(null) }
    var gridView by remember { mutableStateOf(false) }

    LaunchedEffect(rootUri, currentId, refreshKey) {
        val tree = rootUri
        val parentId = currentId
        if (tree == null || parentId == null) {
            entries = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val found = withContext(Dispatchers.IO) {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_FLAGS
                )
                val result = mutableListOf<InfinityFileEntry>()
                val cursor = resolver.query(children, projection, null, null, null)
                    ?: error("Android no pudo consultar esta ubicación. El proveedor de archivos no devolvió resultados.")
                cursor.use {
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val flagsCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                    check(idCol >= 0 && nameCol >= 0 && mimeCol >= 0) { "El proveedor no devolvió las columnas básicas de archivos." }
                    while (cursor.moveToNext()) {
                        result += InfinityFileEntry(
                            cursor.getString(idCol),
                            cursor.getString(nameCol) ?: "Sin nombre",
                            if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                            if (sizeCol < 0 || cursor.isNull(sizeCol)) -1L else cursor.getLong(sizeCol),
                            if (modifiedCol < 0 || cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol),
                            if (flagsCol < 0 || cursor.isNull(flagsCol)) null else cursor.getLong(flagsCol)
                        )
                    }
                }
                result
            }
            entries = found
            // Keep retry targets during refresh, but discard selections no longer present.
            selected = infinityRetainedSelection(selected, found.map { it.id }.toSet())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            entries = emptyList()
            error = exception.localizedMessage ?: "No se pudo leer esta ubicación."
        } finally {
            loading = false
        }
    }

    val selectedEntries = entries.filter { it.id in selected }
    fun supportsFlag(entry: InfinityFileEntry, flag: Long): Boolean =
        infinitySupportsDocumentFlag(entry.flags, flag)

    val canRenameSelected = selectedEntries.size == 1 &&
        supportsFlag(selectedEntries.first(), DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong())
    val canDeleteSelected = selectedEntries.isNotEmpty() &&
        selectedEntries.all { supportsFlag(it, DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong()) }
    val canCopySelected = selectedEntries.isNotEmpty() &&
        selectedEntries.all { supportsFlag(it, DocumentsContract.Document.FLAG_SUPPORTS_COPY.toLong()) }
    val canMoveSelected = selectedEntries.isNotEmpty() &&
        selectedEntries.all { supportsFlag(it, DocumentsContract.Document.FLAG_SUPPORTS_MOVE.toLong()) }

    val visibleEntries = entries
        .filter { it.name.contains(search, ignoreCase = true) }
        .let { list ->
            when (sortBy) {
                "Tipo" -> list.sortedWith(compareByDescending<InfinityFileEntry> { it.isFolder }.thenBy { it.mime.orEmpty() }.thenBy { it.name.lowercase(Locale.getDefault()) })
                "Tamaño" -> list.sortedWith(
                    compareByDescending<InfinityFileEntry> { it.isFolder }
                        .thenBy { infinityFileSizeSortRank(it.size) }
                        .thenByDescending { it.size }
                )
                "Fecha" -> list.sortedWith(compareByDescending<InfinityFileEntry> { it.isFolder }.thenByDescending { it.modified })
                else -> list.sortedWith(compareByDescending<InfinityFileEntry> { it.isFolder }.thenBy { it.name.lowercase(Locale.getDefault()) })
            }
        }

    fun documentUri(id: String): Uri? = rootUri?.let { DocumentsContract.buildDocumentUriUsingTree(it, id) }

    fun stageClipboard(move: Boolean) {
        if (selected.isEmpty() || currentId == null) return
        clipboardIds = selected.toList()
        clipboardParent = documentUri(currentId!!)
        clipboardIsMove = move
        message = if (move) "Elementos preparados para mover. Entra en la carpeta destino y pulsa Pegar." else "Elementos copiados. Entra en la carpeta destino y pulsa Pegar."
    }

    fun pasteClipboard() {
        val tree = rootUri ?: return
        val sourceParent = clipboardParent
        val targetParent = currentId?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
        val ids = clipboardIds
        val isMove = clipboardIsMove
        if (sourceParent == null || targetParent == null || ids.isEmpty() || loading) return
        val sourceParentId = runCatching { DocumentsContract.getDocumentId(sourceParent) }.getOrNull()
        val targetParentId = runCatching { DocumentsContract.getDocumentId(targetParent) }.getOrNull()
        if (isSameInfinityFolder(sourceParentId, targetParentId)) {
            message = "El destino es la misma carpeta de origen. Elige otra carpeta para pegar."
            return
        }
        loading = true
        scope.launch {
            val completedIds = mutableSetOf<String>()
            try {
                val (completed, failedIds) = withContext(Dispatchers.IO) {
                    var done = 0
                    val failures = mutableSetOf<String>()
                    ids.forEach { id ->
                        try {
                            val source = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            val result = if (isMove) {
                                DocumentsContract.moveDocument(resolver, source, sourceParent, targetParent)
                            } else {
                                DocumentsContract.copyDocument(resolver, source, targetParent)
                            }
                            checkNotNull(result) { "El proveedor no confirmó la operación." }
                            completedIds += id
                            done++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failures += id
                        }
                    }
                    done to failures
                }
                val failed = failedIds.size
                if (isMove) {
                    clipboardIds = infinityRemainingMoveIds(ids, failedIds)
                    if (clipboardIds.isEmpty()) clipboardParent = null
                }
                selected = emptySet()
                refreshKey++
                message = if (failed == 0) {
                    "$completed de ${ids.size} elemento(s) procesado(s)."
                } else {
                    "$completed completado(s) y $failed pendiente(s). El portapapeles conserva solo los elementos pendientes para reintentar."
                }
            } catch (cancelled: CancellationException) {
                val unfinished = infinityUnfinishedOperationIds(ids, completedIds)
                if (isMove) {
                    clipboardIds = unfinished
                    if (unfinished.isEmpty()) clipboardParent = null
                }
                selected = unfinished.toSet()
                refreshKey++
                message = "Operación interrumpida: ${completedIds.size} completado(s); ${unfinished.size} pendiente(s). Se conservaron los elementos pendientes para reintentar."
                throw cancelled
            } catch (exception: Exception) {
                message = exception.localizedMessage ?: "No se pudo completar la operación de archivos."
            } finally {
                loading = false
            }
        }
    }
    fun deleteSelected() {
        val tree = rootUri ?: return
        val targets = selected.toList()
        if (targets.isEmpty() || loading) return
        loading = true
        scope.launch {
            val completedIds = mutableSetOf<String>()
            try {
                val (completed, failed) = withContext(Dispatchers.IO) {
                    var done = 0
                    val failures = mutableSetOf<String>()
                    targets.forEach { id ->
                        try {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            check(DocumentsContract.deleteDocument(resolver, uri)) {
                                "El proveedor no confirmó la eliminación."
                            }
                            completedIds += id
                            done++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failures += id
                        }
                    }
                    done to failures
                }
                selected = infinityRemainingDeleteIds(targets, failed).toSet()
                refreshKey++
                message = if (failed.isEmpty()) {
                    "$completed elemento(s) eliminado(s)."
                } else {
                    "$completed eliminado(s) y ${failed.size} no eliminado(s). Los elementos pendientes siguen seleccionados para reintentar."
                }
            } catch (cancelled: CancellationException) {
                val unfinished = infinityUnfinishedOperationIds(targets, completedIds)
                selected = unfinished.toSet()
                refreshKey++
                message = "Eliminación interrumpida: ${completedIds.size} eliminado(s); ${unfinished.size} pendiente(s). Los elementos pendientes siguen seleccionados para reintentar."
                throw cancelled
            } catch (exception: Exception) {
                message = exception.localizedMessage ?: "No se pudo completar la eliminación."
            } finally {
                loading = false
            }
        }
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF0B1220))) {
        Column(Modifier.width(when {
            screenWidth < 400 -> 80.dp
            screenWidth < 600 -> 104.dp
            else -> 150.dp
        }).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 6.dp, top = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("INFINITY", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text("NAVEGACIÓN", color = Color(0xFF8FA6BF), fontSize = 9.sp)
            listOf("Inicio", "Escritorio", "Documentos", "Descargas", "Imágenes", "Música", "Vídeos", "Almacenamiento interno", "Unidades y dispositivos").forEach { place ->
                TextButton(onClick = {
                    if (place == "Inicio" && rootUri != null) {
                        currentId = rootDocumentId
                        folderIds = listOfNotNull(rootDocumentId)
                        folderPath = listOf("Inicio")
                        selected = emptySet()
                    } else {
                        message = "Selecciona la ubicación de " + place + " en el selector seguro de Android."
                        onChooseFolder()
                    }
                }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)) {
                    Text(place, color = if (place == "Inicio" && folderIds.size == 1) accent else Color(0xFFD7E2EF), fontSize = if (screenWidth < 400) 8.sp else if (screenWidth < 600) 9.sp else 10.sp, maxLines = 2)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("INFINITY FILE MANAGER", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Administrador de archivos • acceso autorizado por Android", color = Color(0xFFB7C7D9), fontSize = 9.sp)
            }
            TextButton(onClick = onChooseFolder) { Text(if (rootUri == null) "Elegir carpeta" else "Cambiar") }
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Buscar archivos…", fontSize = 11.sp) }
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { gridView = !gridView }) {
                    Text(if (gridView) "Lista" else "Cuadrícula")
                }
                Box {
                    OutlinedButton(onClick = { sortMenu = true }) { Text("Ordenar") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("Nombre", "Tipo", "Tamaño", "Fecha").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                sortBy = option
                                sortMenu = false
                            })
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rootUri != null) {
                OutlinedButton(onClick = {
                    currentId = rootDocumentId
                    folderIds = listOfNotNull(rootDocumentId)
                    folderPath = listOf("Inicio")
                    selected = emptySet()
                }) { Text("Inicio") }
                if (folderIds.size > 1) {
                    OutlinedButton(onClick = {
                        folderIds = folderIds.dropLast(1)
                        folderPath = folderPath.dropLast(1)
                        currentId = folderIds.lastOrNull()
                        selected = emptySet()
                    }) { Text("Atrás") }
                }
                OutlinedButton(onClick = { createDialog = true }) { Text("+ Carpeta") }
                if (selected.size == 1) {
                    OutlinedButton(enabled = canRenameSelected, onClick = {
                        nameInput = entries.firstOrNull { it.id == selected.first() }?.name.orEmpty()
                        renameDialog = true
                    }) { Text("Renombrar") }
                }
                Text(folderPath.joinToString(" > "), color = Color(0xFFB7C7D9), fontSize = 10.sp, maxLines = 1)
                val allVisibleSelected = visibleEntries.isNotEmpty() && visibleEntries.all { it.id in selected }
                OutlinedButton(onClick = {
                    selected = if (allVisibleSelected) {
                        selected - visibleEntries.map { it.id }.toSet()
                    } else {
                        selected + visibleEntries.map { it.id }
                    }
                }) { Text(if (allVisibleSelected) "Quitar selección" else "Seleccionar todo") }
                if (selected.isNotEmpty()) {
                    OutlinedButton(enabled = canCopySelected, onClick = { stageClipboard(false) }) { Text("Copiar") }
                    OutlinedButton(enabled = canMoveSelected, onClick = { stageClipboard(true) }) { Text("Cortar") }
                    OutlinedButton(enabled = canDeleteSelected, onClick = { confirmDelete = true }) { Text("Eliminar ${selected.size}") }
                    TextButton(onClick = { selected = emptySet() }) { Text("Cancelar") }
                }
                if (clipboardIds.isNotEmpty()) {
                    Button(onClick = { pasteClipboard() }) { Text(if (clipboardIsMove) "Pegar / mover" else "Pegar") }
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
            LazyVerticalGrid(
                columns = if (gridView) GridCells.Adaptive(minSize = 150.dp) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleEntries, key = { it.id }) { entry ->
                    val isSelected = entry.id in selected
                    val itemModifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .background(if (isSelected) accent.copy(alpha = .16f) else Color(0x331B2B40), RoundedCornerShape(12.dp))
                        .border(1.dp, if (isSelected) accent.copy(.7f) else Color.White.copy(.07f), RoundedCornerShape(12.dp))
                    val activateEntry: () -> Unit = {
                        if (selected.isNotEmpty()) {
                            selected = if (isSelected) selected - entry.id else selected + entry.id
                        } else if (entry.isFolder) {
                            currentId = entry.id
                            folderIds = folderIds + entry.id
                            folderPath = folderPath + entry.name
                        } else {
                            documentUri(entry.id)?.let { onOpenFile(it, entry.mime) }
                        }
                    }
                    val fileSummary = if (entry.isFolder) "Carpeta" else "${formatInfinityFileSize(entry.size)} • ${formatInfinityMimeType(entry.mime)}"
                    if (gridView) {
                        Column(
                            itemModifier.clickable(onClick = activateEntry).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(if (entry.isFolder) "📁" else "📄", fontSize = 32.sp)
                            Text(entry.name, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                            Text(fileSummary, color = Color(0xFFB7C7D9), fontSize = 9.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { detailsEntry = entry }) { Text("Detalles") }
                                if (selected.isNotEmpty()) {
                                    Checkbox(checked = isSelected, onCheckedChange = { checked -> selected = if (checked) selected + entry.id else selected - entry.id })
                                } else if (!entry.isFolder) {
                                    TextButton(onClick = { selected = setOf(entry.id) }) { Text("Elegir") }
                                }
                            }
                        }
                    } else {
                        Row(
                            itemModifier.clickable(onClick = activateEntry).padding(horizontal = 9.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (entry.isFolder) "📁" else "📄", fontSize = 20.sp)
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                Text(entry.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fileSummary, color = Color(0xFFB7C7D9), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { detailsEntry = entry }) { Text("Detalles") }
                            if (selected.isNotEmpty()) {
                                Checkbox(checked = isSelected, onCheckedChange = { checked -> selected = if (checked) selected + entry.id else selected - entry.id })
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
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Deseas eliminar ${selected.size} elemento(s)? Esta acción puede no ser reversible.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; deleteSelected() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
    detailsEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { detailsEntry = null },
            title = { Text("Detalles del elemento") },
            text = { Text("Nombre: ${entry.name}\nTipo: ${entry.mime ?: "No disponible"}\nTamaño: ${if (entry.isFolder) "Carpeta" else if (entry.size >= 0) "${entry.size} bytes" else "No disponible"}\nÚltima modificación: ${if (entry.modified > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.modified)) else "No disponible"}") },
            confirmButton = { TextButton(onClick = { detailsEntry = null }) { Text("Cerrar") } }
        )
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
                    val requestedName = nameInput.trim()
                    createDialog = false
                    if (parent != null && isValidInfinityDocumentName(requestedName) && !loading) {
                        loading = true
                        scope.launch {
                            try {
                                val created = withContext(Dispatchers.IO) {
                                    val result = DocumentsContract.createDocument(
                                        resolver, parent,
                                        DocumentsContract.Document.MIME_TYPE_DIR,
                                        requestedName
                                    )
                                    checkNotNull(result) { "El proveedor no confirmó la creación de la carpeta." }
                                }
                                refreshKey++
                                message = "Carpeta creada."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (exception: Exception) {
                                message = exception.localizedMessage ?: "No se pudo crear la carpeta."
                            } finally {
                                loading = false
                            }
                        }
                    } else if (parent != null && !isValidInfinityDocumentName(requestedName)) {
                        message = "Nombre inválido: usa hasta 255 caracteres y evita barras o caracteres de control."
                    }
                    nameInput = ""
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
                    val target = selected.singleOrNull()?.let(::documentUri)
                    val requestedName = nameInput.trim()
                    renameDialog = false
                    if (target != null && isValidInfinityDocumentName(requestedName) && !loading) {
                        loading = true
                        scope.launch {
                            try {
                                val renamed = withContext(Dispatchers.IO) {
                                    val result = DocumentsContract.renameDocument(resolver, target, requestedName)
                                    checkNotNull(result) { "El proveedor no confirmó el cambio de nombre." }
                                }
                                selected = emptySet()
                                refreshKey++
                                message = "Elemento renombrado."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (exception: Exception) {
                                message = exception.localizedMessage ?: "No se pudo renombrar el elemento."
                            } finally {
                                loading = false
                            }
                        }
                    } else if (target != null && !isValidInfinityDocumentName(requestedName)) {
                        message = "Nombre inválido: usa hasta 255 caracteres y evita barras o caracteres de control."
                    }
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
