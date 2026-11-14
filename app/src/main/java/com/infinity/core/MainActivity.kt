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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private val Muted = Color(0xFFB7C7D9)
private val Panel = Color(0xF0182A42)

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
    val id: String, val title: String, val kind: String,
    val x: Float, val y: Float, val width: Float = 350f,
    val height: Float = 300f, val minimized: Boolean = false,
    val workspace: Int = 1, val maximized: Boolean = false
)
private data class InstalledApp(val label: String, val packageName: String)
private data class ExplorerEntry(
    val documentId: String, val name: String, val mimeType: String?,
    val size: Long, val modified: Long
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

@Composable
private fun InfinityApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }
    val shortcuts = remember {
        listOf(
            DeskItem("files", "Archivos", "▣"), DeskItem("notes", "Notas", "✎"),
            DeskItem("apps", "Aplicaciones", "◈"), DeskItem("settings", "Ajustes", "⚙"),
            DeskItem("about", "INFINITY", "∞")
        )
    }
    var workspace by remember { mutableIntStateOf(1) }
    var startOpen by remember { mutableStateOf(false) }
    var orbitOpen by remember { mutableStateOf(false) }
    var controlOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf(prefs.getString("selected_folder_uri", null)?.let(Uri::parse)) }
    var wallpaperUri by remember { mutableStateOf(prefs.getString("wallpaper_uri", null)?.let(Uri::parse)) }
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
    val foreground = if (lightMode) Color(0xFF203149) else Color.White
    var clockText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = java.util.Date()
            clockText = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(now)
            dateText = java.text.SimpleDateFormat("EEE, dd MMMM", java.util.Locale.getDefault()).format(now)
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
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            catch (_: SecurityException) { }
            selectedFolder = uri
            prefs.edit().putString("selected_folder_uri", uri.toString()).apply()
        }
    }
    val windows = remember { mutableStateListOf<DeskWindow>() }
    val iconOffsets = remember {
        mutableStateMapOf<String, Offset>().apply {
            shortcuts.forEachIndexed { index, item ->
                put(item.id, Offset(prefs.getFloat("${item.id}_x", 22f),
                    prefs.getFloat("${item.id}_y", 92f + index * 88f)))
            }
        }
    }
    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    MaterialTheme(colorScheme = if (lightMode) lightColorScheme(primary = accentColor)
        else darkColorScheme(primary = accentColor)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(if (lightMode)
                    listOf(Color(0xFFEAF1FA), Color(0xFFD4E2F2), Color(0xFFB9CDE2))
                else listOf(Color(0xFF102238), Color(0xFF1C3B59), Ink))
            )
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            if (wallpaperBitmap != null) {
                Image(wallpaperBitmap, "Fondo de escritorio", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color(0x66081729)))
            }
            Box(Modifier.offset(x = maxW * .57f, y = maxH * .12f).size(260.dp)
                .background(Brush.radialGradient(listOf(accentColor.copy(.22f), Color.Transparent)), CircleShape))
            Box(Modifier.offset(x = maxW * .68f, y = maxH * .48f).size(150.dp)
                .background(accentColor.copy(.055f), CircleShape))

            Column(Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp)) {
                Text("INFINITY", color = foreground.copy(alpha = .92f), fontSize = 30.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                Text("A U R O R A   •   WORKSPACE $workspace", color = accentColor,
                    fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.align(Alignment.TopEnd).padding(18.dp)
                .clip(RoundedCornerShape(18.dp)).background(Panel.copy(alpha = .82f))
                .border(1.dp, Color.White.copy(.12f), RoundedCornerShape(18.dp)).padding(13.dp),
                horizontalAlignment = Alignment.End) {
                Text(clockText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text(dateText, color = Muted, fontSize = 10.sp)
            }

            shortcuts.forEach { item ->
                val offset = iconOffsets[item.id] ?: Offset(22f, 90f)
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .width(78.dp)
                        .pointerInput(item.id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val next = Offset((iconOffsets[item.id]?.x ?: 0f) + drag.x,
                                    (iconOffsets[item.id]?.y ?: 0f) + drag.y)
                                iconOffsets[item.id] = next
                                prefs.edit().putFloat("${item.id}_x", next.x).putFloat("${item.id}_y", next.y).apply()
                            }
                        }.clickable { openWindow(item.id, windows, workspace) }.padding(5.dp)
                ) {
                    Box(Modifier.size(iconSize.dp).shadow(12.dp, RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xDD294968), Color(0xCC142A43))), RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(.22f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(item.glyph, color = accentColor, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(item.label, color = Color.White, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            windows.filter { !it.minimized && it.workspace == workspace }.forEach { win ->
                key(win.id) {
                    val shown = if (win.maximized) win.copy(x = 8f, y = 8f,
                        width = (maxW.value - 16f).coerceAtLeast(280f),
                        height = (maxH.value - 100f).coerceAtLeast(190f)) else win
                    InfinityWindow(
                        win = shown, installedApps = installedApps, accentColor = accentColor,
                        lightMode = lightMode, themeChoice = themeChoice,
                        onThemeChange = { id -> themeChoice = id; prefs.edit().putString("theme_choice", id).apply() },
                        appearance = appearance,
                        onAppearanceChange = { mode -> appearance = mode; prefs.edit().putString("appearance", mode).apply() },
                        iconSize = iconSize,
                        onIconSizeChange = { size -> iconSize = size; prefs.edit().putFloat("icon_size", size).apply() },
                        onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                        selectedFolder = selectedFolder, onChooseFolder = { folderPicker.launch(null) },
                        onLaunchApp = { pkg -> context.packageManager.getLaunchIntentForPackage(pkg)?.let { launchIntent -> context.startActivity(launchIntent) } },
                        onClose = { windows.removeAll { it.id == win.id } },
                        onMaximize = { val i = windows.indexOfFirst { it.id == win.id }; if (i >= 0) windows[i] = windows[i].copy(maximized = !windows[i].maximized) },
                        onMinimize = { val i = windows.indexOfFirst { it.id == win.id }; if (i >= 0) windows[i] = windows[i].copy(minimized = true) },
                        onMove = { dx, dy ->
                            val i = windows.indexOfFirst { it.id == win.id }
                            if (i >= 0) windows[i] = windows[i].copy(
                                x = (windows[i].x + dx).coerceIn(0f, (maxW.value - 280f).coerceAtLeast(0f)),
                                y = (windows[i].y + dy).coerceIn(0f, (maxH.value - 160f).coerceAtLeast(0f))
                            )
                        },
                        onResize = { dx, dy ->
                            val i = windows.indexOfFirst { it.id == win.id }
                            if (i >= 0) windows[i] = windows[i].copy(
                                width = (windows[i].width + dx).coerceIn(280f, (maxW.value - windows[i].x).coerceAtLeast(280f)),
                                height = (windows[i].height + dy).coerceIn(190f, (maxH.value - windows[i].y - 80f).coerceAtLeast(190f))
                            )
                        }
                    )
                }
            }

            // Floating AURORA dock
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                .shadow(22.dp, RoundedCornerShape(26.dp))
                .background(Color(0xE8172B43), RoundedCornerShape(26.dp))
                .border(1.dp, Color.White.copy(.18f), RoundedCornerShape(26.dp))
                .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DockButton("✦", "INFINITY Start", accentColor) { startOpen = !startOpen; orbitOpen = false }
                DockButton("◉", "Orbit", Color(0xFFB69CFF)) { orbitOpen = !orbitOpen; startOpen = false }
                shortcuts.take(4).forEach { item ->
                    DockButton(item.glyph, item.label, accentColor) { openWindow(item.id, windows, workspace) }
                }
                Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(.18f)))
                DockButton("⚙", "Control Center", Color(0xFFFFC27A)) { controlOpen = !controlOpen; startOpen = false }
                Text(clockText, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp))
            }

            // INFINITY Start: built-in tools and installed Android apps
            if (startOpen) {
                Card(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 78.dp)
                    .widthIn(min = 290.dp, max = 390.dp).heightIn(max = maxH - 110.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("INFINITY START", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("Todo lo que necesitas, en un solo lugar", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = search, onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Buscar apps y herramientas") },
                            leadingIcon = { Icon(Icons.Default.Search, null) })
                        Text("HERRAMIENTAS", color = accentColor, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 5.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            shortcuts.filter { it.label.contains(search, true) }.forEach { item ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(.07f)).clickable {
                                        openWindow(item.id, windows, workspace); startOpen = false
                                    }.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(item.glyph, color = accentColor, fontSize = 21.sp)
                                    Text(item.label, color = Color.White, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                        }
                        Text("APLICACIONES", color = accentColor, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        Text("${installedApps.count { it.label.contains(search, true) || it.packageName.contains(search, true) }} aplicaciones disponibles",
                            color = Muted, fontSize = 9.sp)
                        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 230.dp)) {
                            items(installedApps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) },
                                key = { it.packageName }) { app ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launchIntent -> context.startActivity(launchIntent) }
                                    startOpen = false
                                }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(30.dp).background(accentColor.copy(.16f), RoundedCornerShape(9.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Text(app.label.take(1).uppercase(), color = accentColor, fontWeight = FontWeight.Bold)
                                    }
                                    Text(app.label, color = Color.White, fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider(color = Color.White.copy(.07f))
                            }
                        }
                    }
                }
            }

            // Orbit quick launcher
            if (orbitOpen) {
                Card(Modifier.align(Alignment.BottomCenter).padding(bottom = 78.dp)
                    .widthIn(max = 330.dp), shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF21B2945))) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("INFINITY ORBIT", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("Acceso rápido", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(10.dp))
                        shortcuts.chunked(3).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { item ->
                                    Column(Modifier.width(82.dp).clip(CircleShape)
                                        .background(accentColor.copy(.10f)).clickable {
                                            openWindow(item.id, windows, workspace); orbitOpen = false
                                        }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(item.glyph, color = accentColor, fontSize = 25.sp)
                                        Text(item.label, color = Color.White, fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Control Center
            if (controlOpen) {
                Card(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 78.dp)
                    .widthIn(min = 260.dp, max = 310.dp), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CONTROL CENTER", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("Personaliza tu espacio", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("TEMAS", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            listOf("midnight" to Color(0xFF56D6C9), "ocean" to Color(0xFF45B7FF),
                                "violet" to Color(0xFFB69CFF), "emerald" to Color(0xFF55D6A4),
                                "sunset" to Color(0xFFFFA66B)).forEach { (id, color) ->
                                Box(Modifier.size(30.dp).background(color, CircleShape)
                                    .border(if (themeChoice == id) 3.dp else 1.dp, Color.White, CircleShape)
                                    .clickable { themeChoice = id; prefs.edit().putString("theme_choice", id).apply() })
                            }
                        }
                        Text("APARIENCIA", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !lightMode, onClick = {
                                appearance = "dark"; prefs.edit().putString("appearance", appearance).apply()
                            }, label = { Text("Oscura") })
                            FilterChip(selected = lightMode, onClick = {
                                appearance = "light"; prefs.edit().putString("appearance", appearance).apply()
                            }, label = { Text("Clara") })
                        }
                        Text("Tamaño de iconos: ${iconSize.toInt()} dp", color = Muted, fontSize = 10.sp)
                        Slider(value = iconSize, onValueChange = {
                            iconSize = it; prefs.edit().putFloat("icon_size", it).apply()
                        }, valueRange = 40f..72f)
                        OutlinedButton(onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()) { Text("Cambiar fondo") }
                        OutlinedButton(onClick = { workspace = if (workspace == 3) 1 else workspace + 1 },
                            modifier = Modifier.fillMaxWidth()) { Text("Cambiar espacio • $workspace / 3") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockButton(glyph: String, description: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, color = tint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
    if (existing >= 0) windows[existing] = windows[existing].copy(minimized = false)
    else windows.add(DeskWindow("${id}_$workspace", title, id,
        90f + windows.size * 22f, 75f + windows.size * 20f, workspace = workspace))
}

@Composable
private fun FileExplorerContent(
    rootUri: Uri?, accentColor: Color, onChooseFolder: () -> Unit,
    onOpenFile: (Uri, String?) -> Unit
) {
    val context = LocalContext.current
    var currentDocumentId by remember(rootUri) { mutableStateOf(rootUri?.let { DocumentsContract.getTreeDocumentId(it) }) }
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
                    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val found = mutableListOf<ExplorerEntry>()
                    context.contentResolver.query(childrenUri, projection, null, null,
                        "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                        val modifiedCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        while (cursor.moveToNext()) found.add(ExplorerEntry(
                            cursor.getString(idCol), cursor.getString(nameCol) ?: "Sin nombre",
                            if (cursor.isNull(mimeCol)) null else cursor.getString(mimeCol),
                            if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol),
                            if (cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol)))
                    }
                    found.sortedWith(compareByDescending<ExplorerEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo leer esta carpeta."
                    emptyList()
                }
            }
            entries = result
            loading = false
        } else entries = emptyList()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("INFINITY FILE EXPLORER", color = accentColor, fontWeight = FontWeight.Bold)
        Text("Explora carpetas y abre archivos con aplicaciones Android.", color = Muted, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChooseFolder) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(5.dp))
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
                    Row(Modifier.fillMaxWidth().clickable {
                        if (entry.isDirectory) currentDocumentId = entry.documentId
                        else onOpenFile(DocumentsContract.buildDocumentUriUsingTree(rootUri, entry.documentId), entry.mimeType)
                    }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (entry.isDirectory) "📁" else "📄", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (entry.isDirectory) "Carpeta" else if (entry.size > 0) "${(entry.size / 1024).coerceAtLeast(1)} KB" else (entry.mimeType ?: "Archivo"),
                                color = Muted, fontSize = 9.sp)
                        }
                        if (entry.isDirectory) Text("›", color = accentColor, fontSize = 22.sp)
                    }
                    HorizontalDivider(color = Color.White.copy(.08f))
                }
            }
        }
    }
}

@Composable
private fun InfinityWindow(
    win: DeskWindow, installedApps: List<InstalledApp>, accentColor: Color,
    lightMode: Boolean, themeChoice: String, onThemeChange: (String) -> Unit,
    appearance: String, onAppearanceChange: (String) -> Unit,
    iconSize: Float, onIconSizeChange: (Float) -> Unit,
    onChooseWallpaper: () -> Unit, selectedFolder: Uri?, onChooseFolder: () -> Unit,
    onLaunchApp: (String) -> Unit, onClose: () -> Unit, onMinimize: () -> Unit,
    onMaximize: () -> Unit, onMove: (Float, Float) -> Unit, onResize: (Float, Float) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }
    var noteText by remember(win.id) { mutableStateOf(prefs.getString("note_${win.id}", "") ?: "") }
    val fg = if (lightMode) Color(0xFF203149) else Color.White
    Column(Modifier.offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
        .width(win.width.dp).height(win.height.dp).shadow(26.dp, RoundedCornerShape(20.dp))
        .background(if (lightMode) Color(0xF2F5F8FC) else Color(0xF21B2B40), RoundedCornerShape(20.dp))
        .border(1.dp, accentColor.copy(.38f), RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().background(if (lightMode) Color(0xFFE0EAF5) else Color(0xFF263D57),
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .pointerInput(win.id) { detectDragGestures { change, drag -> change.consume(); onMove(drag.x, drag.y) } }
            .padding(start = 13.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(win.title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onMinimize, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Minimize, "Minimizar", tint = fg)
            }
            IconButton(onClick = onMaximize, modifier = Modifier.size(30.dp)) {
                Icon(if (win.maximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    "Maximizar o restaurar", tint = fg)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, "Cerrar", tint = Color(0xFFFF8B8B))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (win.kind) {
                "notes" -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Bloc de ideas", color = accentColor, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = noteText, onValueChange = {
                        noteText = it; prefs.edit().putString("note_${win.id}", it).apply()
                    }, modifier = Modifier.fillMaxSize().padding(top = 7.dp),
                        placeholder = { Text("Escribe tus ideas aquí…") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = fg))
                }
                "files" -> FileExplorerContent(selectedFolder, accentColor, onChooseFolder) { uri, mime ->
                    try {
                        val view = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime ?: "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(view, "Abrir archivo con"))
                    } catch (_: Exception) { }
                }
                "apps" -> {
                    var appSearch by remember(win.id) { mutableStateOf("") }
                    val filtered = remember(installedApps, appSearch) {
                        installedApps.filter { it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true) }
                    }
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Text("${filtered.size} de ${installedApps.size} aplicaciones", color = accentColor, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = appSearch, onValueChange = { appSearch = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
                            placeholder = { Text("Buscar aplicación…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) })
                        LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                            items(filtered, key = { it.packageName }) { app ->
                                Row(Modifier.fillMaxWidth().clickable { onLaunchApp(app.packageName) }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("◈", color = accentColor, fontSize = 19.sp)
                                    Text(app.label, color = fg, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                                }
                                HorizontalDivider(color = Color.Gray.copy(.18f))
                            }
                        }
                    }
                }
                "settings" -> Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ajustes del escritorio", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("TEMAS Y COLORES", color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("midnight" to "Aqua", "ocean" to "Azul", "violet" to "Violeta",
                            "emerald" to "Verde", "sunset" to "Naranja").forEach { (id, label) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onThemeChange(id) }) {
                                val c = when (id) { "ocean" -> Color(0xFF45B7FF); "violet" -> Color(0xFFB69CFF); "emerald" -> Color(0xFF55D6A4); "sunset" -> Color(0xFFFFA66B); else -> Color(0xFF56D6C9) }
                                Box(Modifier.size(28.dp).background(c, CircleShape).border(if (themeChoice == id) 3.dp else 1.dp, Color.White, CircleShape))
                                Text(label, color = fg, fontSize = 8.sp)
                            }
                        }
                    }
                    Text("APARIENCIA", color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = appearance == "dark", onClick = { onAppearanceChange("dark") }, label = { Text("Oscura") })
                        FilterChip(selected = appearance == "light", onClick = { onAppearanceChange("light") }, label = { Text("Clara") })
                    }
                    Text("Tamaño de iconos: ${iconSize.toInt()} dp", color = fg, fontSize = 11.sp)
                    Slider(value = iconSize, onValueChange = onIconSizeChange, valueRange = 40f..72f)
                    Button(onClick = onChooseWallpaper) { Text("Elegir fondo de pantalla") }
                }
                else -> Column(Modifier.padding(14.dp)) {
                    Text("PROJECT INFINITY 0.7", color = accentColor, fontWeight = FontWeight.Bold)
                    Text("AURORA EVOLUTION\nEscritorio Android nativo • Kotlin • Jetpack Compose\nVentanas móviles • Explorador SAF • Lanzador de aplicaciones",
                        color = fg, fontSize = 12.sp)
                }
            }
        }
        Box(Modifier.align(Alignment.End).size(30.dp).pointerInput(win.id) {
            detectDragGestures { change, drag -> change.consume(); onResize(drag.x, drag.y) }
        }, contentAlignment = Alignment.Center) {
            Text("⤡", color = accentColor, fontSize = 19.sp)
        }
    }
}
