package com.infinity.core

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Ink = Color(0xFF101827)
private val Accent = Color(0xFF56D6C9)
private val Muted = Color(0xFFB7C7D9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 22, 37)
        window.navigationBarColor = android.graphics.Color.rgb(13, 22, 37)
        setContent { InfinityApp() }
    }
}

private data class DeskItem(val id: String, val label: String, val glyph: String)
private data class DeskWindow(
    val id: String,
    val title: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val width: Float = 350f,
    val height: Float = 300f,
    val minimized: Boolean = false,
    val workspace: Int = 1,
    val maximized: Boolean = false
)
private data class InstalledApp(val label: String, val packageName: String)
private data class ExplorerEntry(
    val documentId: String,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val modified: Long
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

@Composable
private fun InfinityApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }
    val shortcuts = remember {
        listOf(
            DeskItem("files", "Archivos", "▣"),
            DeskItem("notes", "Notas", "✎"),
            DeskItem("apps", "Aplicaciones", "◈"),
            DeskItem("settings", "Ajustes", "⚙"),
            DeskItem("about", "INFINITY", "∞")
        )
    }
    var workspace by remember { mutableIntStateOf(1) }
    var startOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf(prefs.getString("selected_folder_uri", null)?.let { Uri.parse(it) }) }
    var wallpaperUri by remember { mutableStateOf(prefs.getString("wallpaper_uri", null)?.let { Uri.parse(it) }) }
    var iconSize by remember { mutableFloatStateOf(prefs.getFloat("icon_size", 48f).coerceIn(40f, 72f)) }
    var themeChoice by remember { mutableStateOf(prefs.getString("theme_choice", "midnight") ?: "midnight") }
    var appearance by remember { mutableStateOf(prefs.getString("appearance", "dark") ?: "dark") }
    val accentColor = when (themeChoice) {
        "ocean" -> Color(0xFF45B7FF)
        "violet" -> Color(0xFFB69CFF)
        "emerald" -> Color(0xFF55D6A4)
        "sunset" -> Color(0xFFFFA66B)
        else -> Color(0xFF56D6C9)
    }
    val lightMode = appearance == "light"
    var clockText by remember { mutableStateOf(java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            delay(30_000)
        }
    }
    val wallpaperBitmap = remember(wallpaperUri) {
        wallpaperUri?.let { uri ->
            try { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
            catch (_: Exception) { null }
        }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { }
            wallpaperUri = uri
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
        }
    }
    val windows = remember { mutableStateListOf<DeskWindow>() }
    val iconOffsets = remember {
        mutableStateMapOf<String, Offset>().apply {
            shortcuts.forEachIndexed { index, item ->
                put(item.id, Offset(
                    prefs.getFloat("${item.id}_x", 22f),
                    prefs.getFloat("${item.id}_y", 28f + index * 94f)
                ))
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            selectedFolder = uri
            prefs.edit().putString("selected_folder_uri", uri.toString()).apply()
        }
    }

    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .map {
                InstalledApp(
                    it.loadLabel(context.packageManager).toString(),
                    it.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    MaterialTheme(colorScheme = if (lightMode) lightColorScheme(primary = accentColor, secondary = Color(0xFF5277B8)) else darkColorScheme(primary = accentColor, secondary = Color(0xFF8CA8FF))) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(if (lightMode) listOf(Color(0xFFEAF1FA), Color(0xFFD8E5F4), Color(0xFFC7D8EA)) else listOf(Color(0xFF13243A), Color(0xFF1B3550), Ink))
            )
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
Column(
    modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = 20.dp, top = 18.dp)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(12.dp, RoundedCornerShape(17.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1677FF),
                            Color(0xFF14C78A),
                            Color(0xFFFFC928),
                            Color(0xFFFF4545)
                        )
                    ),
                    RoundedCornerShape(17.dp)
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.35f),
                    RoundedCornerShape(17.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "∞",
                color = Color.White,
                fontSize = 43.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column {
            Text(
                "INFINITY",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
            Text(
                "A U R O R A",
                color = Color(0xFF8EDFD9),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Text(
                "WORKSPACE 0$workspace  •  CORE 0.6",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
            Box(Modifier.offset(x = maxW * .58f, y = maxH * .12f).size(240.dp)
                .background(accentColor.copy(alpha = .08f), CircleShape))
           
            }

            shortcuts.forEach { item ->
                val offset = iconOffsets[item.id] ?: Offset(22f, 28f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .width(76.dp)
                        .pointerInput(item.id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val next = Offset(
                                    (iconOffsets[item.id]?.x ?: 0f) + drag.x,
                                    (iconOffsets[item.id]?.y ?: 0f) + drag.y
                                )
                                iconOffsets[item.id] = next
                                prefs.edit().putFloat("${item.id}_x", next.x)
                                    .putFloat("${item.id}_y", next.y).apply()
                            }
                        }
                        .clickable { openWindow(item.id, windows, workspace) }
                        .padding(5.dp)
                ) {
                    Box(
                        Modifier.size(iconSize.dp).shadow(12.dp, RoundedCornerShape(15.dp))
                            .background(Color(0xCC233B55), RoundedCornerShape(15.dp))
                            .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.glyph, color = accentColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(item.label, color = Color.White, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            windows.filter { !it.minimized && it.workspace == workspace }.forEach { win ->
                key(win.id) {
                    val shownWindow = if (win.maximized) win.copy(
                        x = 8f, y = 8f,
                        width = (maxW.value - 16f).coerceAtLeast(280f),
                        height = (maxH.value - 92f).coerceAtLeast(190f)
                    ) else win
                    InfinityWindow(
                        win = shownWindow,
                        installedApps = installedApps,
                        accentColor = accentColor,
                        lightMode = lightMode,
                        themeChoice = themeChoice,
                        onThemeChange = { id -> themeChoice = id; prefs.edit().putString("theme_choice", id).apply() },
                        appearance = appearance,
                        onAppearanceChange = { mode -> appearance = mode; prefs.edit().putString("appearance", mode).apply() },
                        iconSize = iconSize,
                        onIconSizeChange = { size -> iconSize = size; prefs.edit().putFloat("icon_size", size).apply() },
                        onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                        selectedFolder = selectedFolder,
                        onChooseFolder = { folderPicker.launch(null) },
                        onLaunchApp = { packageName ->
                            context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                                context.startActivity(it)
                            }
                        },
                        onClose = { windows.removeAll { it.id == win.id } },
                        onMaximize = {
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] = windows[index].copy(maximized = !windows[index].maximized)
                        },
                        onMinimize = {
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] = windows[index].copy(minimized = true)
                        },
                        onMove = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] = windows[index].copy(
                                x = (windows[index].x + dx).coerceIn(0f, (maxW.value - 280f).coerceAtLeast(0f)),
                                y = (windows[index].y + dy).coerceIn(0f, (maxH.value - 160f).coerceAtLeast(0f))
                            )
                        },
                        onResize = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] = windows[index].copy(
                                width = (windows[index].width + dx).coerceIn(280f, (maxW.value - windows[index].x).coerceAtLeast(280f)),
                                height = (windows[index].height + dy).coerceIn(190f, (maxH.value - windows[index].y - 70f).coerceAtLeast(190f))
                            )
                        }
                    )
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                    .shadow(18.dp, RoundedCornerShape(22.dp))
                    .background(Color(0xE916263B), RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { startOpen = !startOpen }) {
                    Icon(Icons.Default.GridView, contentDescription = "Inicio", tint = accentColor)
                }
                Column(Modifier.weight(1f)) {
                    Text("INFINITY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Sistema de escritorio", color = Muted, fontSize = 10.sp)
                }
                windows.filter { it.workspace == workspace }.forEach { win ->
                    TextButton(onClick = {
                        val i = windows.indexOfFirst { it.id == win.id }
                        if (i >= 0) windows[i] = windows[i].copy(minimized = false)
                    }) { Text(if (win.minimized) "· ${win.title.take(7)}" else win.title.take(8), color = if (win.minimized) Muted else accentColor, fontSize = 10.sp) }
                }
                Text("●", color = accentColor, fontSize = 11.sp)
                Text("$workspace / 3", color = Muted, fontSize = 11.sp)
                Column(horizontalAlignment = Alignment.End) {
                    Text(clockText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()), color = Muted, fontSize = 8.sp)
                }
                IconButton(onClick = { workspace = if (workspace == 3) 1 else workspace + 1 }) {
                    Icon(Icons.Default.Widgets, contentDescription = "Espacio", tint = Color.White)
                }
            }

            if (startOpen) {
                Card(
                    Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 78.dp)
                        .widthIn(min = 260.dp, max = 320.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF21A2B42)),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Centro INFINITY", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Herramientas y aplicaciones instaladas.", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Buscar herramienta…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        Spacer(Modifier.height(8.dp))
                        shortcuts.filter { it.label.contains(search, true) }.forEach { item ->
                            TextButton(onClick = { openWindow(item.id, windows, workspace); startOpen = false }) {
                                Text("${item.glyph}   ${item.label}", color = Color.White)
                            }
                        }
                        Text("${installedApps.size} aplicaciones detectadas", color = accentColor, fontSize = 11.sp)
                        Text("Personalización", color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        Text("Tema de color", color = if (lightMode) Color(0xFF24364A) else Muted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("midnight" to "Aqua", "ocean" to "Azul", "violet" to "Violeta", "emerald" to "Verde", "sunset" to "Naranja").forEach { (id, label) ->
                                Box(Modifier.size(25.dp).background(when(id) { "ocean" -> Color(0xFF45B7FF); "violet" -> Color(0xFFB69CFF); "emerald" -> Color(0xFF55D6A4); "sunset" -> Color(0xFFFFA66B); else -> Color(0xFF56D6C9) }, CircleShape).border(if (themeChoice == id) 3.dp else 1.dp, if (themeChoice == id) Color.White else Color.Gray, CircleShape).clickable { themeChoice = id; prefs.edit().putString("theme_choice", id).apply() })
                            }
                        }
                        Text("Apariencia", color = if (lightMode) Color(0xFF24364A) else Muted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("dark" to "Oscuro", "light" to "Claro").forEach { (id, label) ->
                                FilterChip(selected = appearance == id, onClick = { appearance = id; prefs.edit().putString("appearance", id).apply() }, label = { Text(label) })
                            }
                        }
                        Text("Tamaño de iconos: ${iconSize.toInt()} dp", color = Muted, fontSize = 10.sp)
                        Slider(value = iconSize, onValueChange = { iconSize = it; prefs.edit().putFloat("icon_size", it).apply() }, valueRange = 40f..72f)
                        OutlinedButton(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cambiar fondo de pantalla")
                        }
                        Text("Tema, apariencia, fondo y tamaño de iconos se guardan automáticamente.", color = if (lightMode) Color(0xFF52657A) else Muted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun openWindow(id: String, windows: MutableList<DeskWindow>, workspace: Int) {
    val title = when (id) {
        "files" -> "Explorador de archivos"
        "notes" -> "Notas rápidas"
        "apps" -> "Aplicaciones instaladas"
        "settings" -> "Preferencias"
        else -> "Acerca de INFINITY"
    }
    val existing = windows.indexOfFirst { it.kind == id && it.workspace == workspace }
    if (existing >= 0) {
        windows[existing] = windows[existing].copy(minimized = false)
    } else {
        windows.add(DeskWindow("${id}_$workspace", title, id, 100f + windows.size * 24f, 80f + windows.size * 22f, workspace = workspace))
    }
}

@Composable
private fun FileExplorerContent(
    rootUri: Uri?,
    accentColor: Color,
    onChooseFolder: () -> Unit,
    onOpenFile: (Uri, String?) -> Unit
) {
    val context = LocalContext.current
    var currentDocumentId by remember(rootUri) {
        mutableStateOf(rootUri?.let { DocumentsContract.getTreeDocumentId(it) })
    }
    var entries by remember(rootUri, currentDocumentId) { mutableStateOf<List<ExplorerEntry>>(emptyList()) }
    var loading by remember(rootUri, currentDocumentId) { mutableStateOf(false) }
    var error by remember(rootUri, currentDocumentId) { mutableStateOf<String?>(null) }

    LaunchedEffect(rootUri, currentDocumentId) {
        val tree = rootUri
        val docId = currentDocumentId
        if (tree != null && docId != null) {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                try {
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )
                    val found = mutableListOf<ExplorerEntry>()
                    context.contentResolver.query(childrenUri, projection, null, null,
                        "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                        val modifiedCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        while (cursor.moveToNext()) {
                            found.add(ExplorerEntry(
                                cursor.getString(idCol), cursor.getString(nameCol) ?: "Sin nombre",
                                if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                                if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol),
                                if (cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol)
                            ))
                        }
                    }
                    found.sortedWith(compareByDescending<ExplorerEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo leer esta carpeta."
                    emptyList()
                }
            }
            entries = result
            loading = false
        } else {
            entries = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("INFINITY FILE EXPLORER", color = accentColor, fontWeight = FontWeight.Bold)
        Text("Explora carpetas y abre archivos usando los permisos oficiales de Android.", color = Muted, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChooseFolder) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(5.dp))
                Text(if (rootUri == null) "Elegir carpeta" else "Cambiar carpeta")
            }
            if (rootUri != null && currentDocumentId != DocumentsContract.getTreeDocumentId(rootUri)) {
                OutlinedButton(onClick = { currentDocumentId = DocumentsContract.getTreeDocumentId(rootUri) }) {
                    Text("Volver al inicio")
                }
            }
        }
        when {
            rootUri == null -> Text("Selecciona una carpeta para comenzar.", color = Color.White, fontSize = 12.sp)
            loading -> CircularProgressIndicator(color = accentColor, modifier = Modifier.size(26.dp))
            error != null -> Text("Error: $error", color = Color(0xFFFF9999), fontSize = 11.sp)
            entries.isEmpty() -> Text("Esta carpeta está vacía o no contiene elementos accesibles.", color = Muted, fontSize = 11.sp)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.documentId }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (entry.isDirectory) currentDocumentId = entry.documentId
                            else {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, entry.documentId)
                                onOpenFile(fileUri, entry.mimeType)
                            }
                        }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (entry.isDirectory) "📁" else "📄", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val details = if (entry.isDirectory) "Carpeta" else if (entry.size > 0) "${(entry.size / 1024).coerceAtLeast(1)} KB" else (entry.mimeType ?: "Archivo")
                            Text(details, color = Muted, fontSize = 9.sp)
                        }
                        if (entry.isDirectory) Text("›", color = accentColor, fontSize = 22.sp)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = .08f))
                }
            }
        }
    }
}

@Composable
private fun InfinityWindow(
    win: DeskWindow,
    installedApps: List<InstalledApp>,
    accentColor: Color,
    lightMode: Boolean,
    themeChoice: String,
    onThemeChange: (String) -> Unit,
    appearance: String,
    onAppearanceChange: (String) -> Unit,
    iconSize: Float,
    onIconSizeChange: (Float) -> Unit,
    onChooseWallpaper: () -> Unit,
    selectedFolder: Uri?,
    onChooseFolder: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }
    var noteText by remember(win.id) { mutableStateOf(prefs.getString("note_${win.id}", "") ?: "") }
    Column(
        Modifier.offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
            .width(win.width.dp).height(win.height.dp)
            .shadow(24.dp, RoundedCornerShape(17.dp))
            .background(Color(0xF21B2B40), RoundedCornerShape(17.dp))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(17.dp))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(Color(0xFF263D57), RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp))
                .pointerInput(win.id) {
                    detectDragGestures { change, drag -> change.consume(); onMove(drag.x, drag.y) }
                }
                .padding(start = 13.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(win.title, color = Color.White, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onMinimize, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Minimize, contentDescription = "Minimizar", tint = Muted)
            }
            IconButton(onClick = onMaximize, modifier = Modifier.size(30.dp)) {
                Icon(if (win.maximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (win.maximized) "Restaurar" else "Maximizar", tint = Muted)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFFF8B8B))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (win.kind) {
                "notes" -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Bloc de ideas", color = accentColor, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = noteText, onValueChange = {
                            noteText = it
                            prefs.edit().putString("note_${win.id}", it).apply()
                        },
                        Modifier.fillMaxSize().padding(top = 7.dp),
                        placeholder = { Text("Escribe tus ideas aquí…") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                    )
                }
                "files" -> FileExplorerContent(
                    rootUri = selectedFolder,
                    accentColor = accentColor,
                    onChooseFolder = onChooseFolder,
                    onOpenFile = { fileUri, mimeType ->
                        try {
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(fileUri, mimeType ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(viewIntent, "Abrir archivo con"))
                        } catch (_: Exception) { }
                    }
                )
                "apps" -> {
                    var appSearch by remember(win.id) { mutableStateOf("") }
                    val filteredApps = remember(installedApps, appSearch) {
                        installedApps.filter { it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true) }
                    }
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("${filteredApps.size} de ${installedApps.size} aplicaciones", color = accentColor, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = appSearch, onValueChange = { appSearch = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
                        placeholder = { Text("Buscar aplicación…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Text("Pulsa una aplicación para abrirla.", color = Muted, fontSize = 10.sp)
                    LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onLaunchApp(app.packageName) }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("◈", color = accentColor, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(app.label, color = Color.White, fontSize = 12.sp)
                                    Text(app.packageName, color = Muted, fontSize = 9.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = .08f))
                        }
                    }
                    }
                }
                "settings" -> Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Ajustes del escritorio", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("TEMAS Y COLORES", color = if (lightMode) Color(0xFF52657A) else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf("midnight" to "Aqua", "ocean" to "Azul", "violet" to "Violeta", "emerald" to "Verde", "sunset" to "Naranja").forEach { (id, label) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onThemeChange(id) }) {
                                Box(Modifier.size(30.dp).background(when(id) { "ocean" -> Color(0xFF45B7FF); "violet" -> Color(0xFFB69CFF); "emerald" -> Color(0xFF55D6A4); "sunset" -> Color(0xFFFFA66B); else -> Color(0xFF56D6C9) }, CircleShape).border(if (themeChoice == id) 3.dp else 1.dp, Color.White, CircleShape))
                                Text(label, color = if (lightMode) Color(0xFF24364A) else Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                    Text("APARIENCIA", color = if (lightMode) Color(0xFF52657A) else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = appearance == "dark", onClick = { onAppearanceChange("dark") }, label = { Text("Oscura") })
                        FilterChip(selected = appearance == "light", onClick = { onAppearanceChange("light") }, label = { Text("Clara") })
                    }
                    Text("ESCRITORIO", color = if (lightMode) Color(0xFF52657A) else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Tamaño de iconos: ${iconSize.toInt()} dp", color = if (lightMode) Color(0xFF24364A) else Color.White, fontSize = 11.sp)
                    Slider(value = iconSize, onValueChange = onIconSizeChange, valueRange = 40f..72f)
                    Button(onClick = onChooseWallpaper) { Text("Elegir fondo de pantalla") }
                    Text("Los cambios se guardan automáticamente.", color = if (lightMode) Color(0xFF52657A) else Muted, fontSize = 10.sp)
                }
                else -> Column(Modifier.padding(14.dp)) {
                    Text("INFINITY AURORA 0.6", color = accentColor, fontWeight = FontWeight.Bold))
                    Text("Escritorio nativo Android • Kotlin • Jetpack Compose\nVentanas móviles y redimensionables • Acceso SAF • Lanzador de apps",
                        color = Color.White, fontSize = 12.sp)
                }
            }
        }
        // Resize grip: drag this corner to resize the current internal window.
        Box(
            Modifier.align(Alignment.End).size(30.dp)
                .pointerInput(win.id) {
                    detectDragGestures { change, drag -> change.consume(); onResize(drag.x, drag.y) }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("⤡", color = accentColor, fontSize = 19.sp)
        }
    }
}
