package com.infinity.core

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
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
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val InfinityInk = Color(0xFF101827)
private val InfinityMuted = Color(0xFFB7C7D9)
private val InfinityPanel = Color(0xF0182A42)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 22, 37)
        window.navigationBarColor = android.graphics.Color.rgb(13, 22, 37)
        setContent { Infinity08App() }
    }
}

private data class DeskItem(val id: String, val label: String, val glyph: String)
private data class DeskWindow(
    val id: String,
    val title: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val width: Float = 370f,
    val height: Float = 330f,
    val minimized: Boolean = false,
    val workspace: Int = 1,
    val maximized: Boolean = false
)
private data class InstalledApp(val label: String, val packageName: String)
private data class ExplorerEntry(
    val documentId: String,
    val name: String,
    val mimeType: String?,
    val size: Long
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

@Composable
private fun Infinity08App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }

    val desktopItems = remember {
        listOf(
            DeskItem("files", "Archivos", "▣"),
            DeskItem("notes", "Notas", "✎"),
            DeskItem("apps", "Aplicaciones", "◈"),
            DeskItem("settings", "Personalizar", "⚙"),
            DeskItem("about", "INFINITY", "∞")
        )
    }

    var workspace by remember { mutableIntStateOf(1) }
    var startOpen by remember { mutableStateOf(false) }
    var orbitOpen by remember { mutableStateOf(false) }
    var controlOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedFolder by remember {
        mutableStateOf(prefs.getString("selected_folder_uri", null)?.let(Uri::parse))
    }
    var wallpaperUri by remember {
        mutableStateOf(prefs.getString("wallpaper_uri", null)?.let(Uri::parse))
    }
    var iconSize by remember {
        mutableFloatStateOf(prefs.getFloat("icon_size", 48f).coerceIn(40f, 72f))
    }
    var themeChoice by remember {
        mutableStateOf(prefs.getString("theme_choice", "midnight") ?: "midnight")
    }
    var appearance by remember {
        mutableStateOf(prefs.getString("appearance", "dark") ?: "dark")
    }
    val lightMode = appearance == "light"
    val accent = when (themeChoice) {
        "ocean" -> Color(0xFF45B7FF)
        "violet" -> Color(0xFFB69CFF)
        "emerald" -> Color(0xFF55D6A4)
        "sunset" -> Color(0xFFFFA66B)
        else -> Color(0xFF56D6C9)
    }
    val foreground = if (lightMode) Color(0xFF203149) else Color.White

    var clockText by remember { mutableStateOf("--:--") }
    var dateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            clockText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
            dateText = SimpleDateFormat("EEE, dd MMMM", Locale.getDefault()).format(now)
            delay(30_000)
        }
    }

    val wallpaperBitmap = remember(wallpaperUri) {
        wallpaperUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            wallpaperUri = uri
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            selectedFolder = uri
            prefs.edit().putString("selected_folder_uri", uri.toString()).apply()
        }
    }

    val windows = remember { mutableStateListOf<DeskWindow>() }
    val iconOffsets = remember {
        mutableStateMapOf<String, Offset>().apply {
            desktopItems.forEachIndexed { index, item ->
                put(
                    item.id,
                    Offset(
                        prefs.getFloat("${item.id}_x", 22f),
                        prefs.getFloat("${item.id}_y", 92f + index * 88f)
                    )
                )
            }
        }
    }

    val installedApps = remember {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcherIntent, 0)
            .map {
                InstalledApp(
                    it.loadLabel(context.packageManager).toString(),
                    it.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun launchApp(packageName: String) {
        try {
            context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                context.startActivity(it)
            }
        } catch (_: Exception) { }
    }

    MaterialTheme(
        colorScheme = if (lightMode) lightColorScheme(primary = accent)
        else darkColorScheme(primary = accent)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    if (lightMode) listOf(
                        Color(0xFFEAF1FA), Color(0xFFD4E2F2), Color(0xFFB9CDE2)
                    ) else listOf(Color(0xFF102238), Color(0xFF1C3B59), InfinityInk)
                )
            )
        ) {
            val maxW = maxWidth
            val maxH = maxHeight

            if (wallpaperBitmap != null) {
                Image(
                    wallpaperBitmap,
                    contentDescription = "Fondo de escritorio",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color(0x66081729)))
            }

            Box(
                Modifier.offset(x = maxW * .57f, y = maxH * .12f)
                    .size(260.dp)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(.22f), Color.Transparent)),
                        CircleShape
                    )
            )

            Column(Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp)) {
                Text(
                    "INFINITY",
                    color = foreground.copy(alpha = .94f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
                Text(
                    "A U R O R A  NEXT  •  WORKSPACE $workspace",
                    color = accent,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                Modifier.align(Alignment.TopEnd).padding(18.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(InfinityPanel.copy(alpha = .84f))
                    .border(1.dp, Color.White.copy(.12f), RoundedCornerShape(18.dp))
                    .padding(13.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(clockText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text(dateText, color = InfinityMuted, fontSize = 10.sp)
            }

            // Escritorio: accesos a herramientas, no se repiten dentro del Dock ni Orbit.
            desktopItems.forEach { item ->
                val offset = iconOffsets[item.id] ?: Offset(22f, 90f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .width(82.dp)
                        .pointerInput(item.id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val next = Offset(
                                    (iconOffsets[item.id]?.x ?: 0f) + drag.x,
                                    (iconOffsets[item.id]?.y ?: 0f) + drag.y
                                )
                                iconOffsets[item.id] = next
                                prefs.edit()
                                    .putFloat("${item.id}_x", next.x)
                                    .putFloat("${item.id}_y", next.y)
                                    .apply()
                            }
                        }
                        .clickable { openInfinityWindow(item.id, windows, workspace) }
                        .padding(5.dp)
                ) {
                    Box(
                        Modifier.size(iconSize.dp)
                            .shadow(12.dp, RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xDD294968), Color(0xCC142A43))
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .border(1.dp, Color.White.copy(.22f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.glyph, color = accent, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        item.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            windows.filter { !it.minimized && it.workspace == workspace }.forEach { win ->
                key(win.id) {
                    val shown = if (win.maximized) {
                        win.copy(
                            x = 8f,
                            y = 8f,
                            width = (maxW.value - 16f).coerceAtLeast(280f),
                            height = (maxH.value - 100f).coerceAtLeast(190f)
                        )
                    } else win

                    Infinity08Window(
                        win = shown,
                        installedApps = installedApps,
                        accent = accent,
                        lightMode = lightMode,
                        themeChoice = themeChoice,
                        onThemeChange = {
                            themeChoice = it
                            prefs.edit().putString("theme_choice", it).apply()
                        },
                        appearance = appearance,
                        onAppearanceChange = {
                            appearance = it
                            prefs.edit().putString("appearance", it).apply()
                        },
                        iconSize = iconSize,
                        onIconSizeChange = {
                            iconSize = it
                            prefs.edit().putFloat("icon_size", it).apply()
                        },
                        onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                        selectedFolder = selectedFolder,
                        onChooseFolder = { folderPicker.launch(null) },
                        onLaunchApp = ::launchApp,
                        onClose = { windows.removeAll { item -> item.id == win.id } },
                        onMinimize = {
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] = windows[index].copy(minimized = true)
                        },
                        onMaximize = {
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] =
                                windows[index].copy(maximized = !windows[index].maximized)
                        },
                        onMove = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) {
                                val current = windows[index]
                                windows[index] = current.copy(
                                    x = (current.x + dx).coerceIn(0f, (maxW.value - 280f).coerceAtLeast(0f)),
                                    y = (current.y + dy).coerceIn(0f, (maxH.value - 160f).coerceAtLeast(0f))
                                )
                            }
                        },
                        onResize = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) {
                                val current = windows[index]
                                windows[index] = current.copy(
                                    width = (current.width + dx).coerceIn(
                                        280f, (maxW.value - current.x).coerceAtLeast(280f)
                                    ),
                                    height = (current.height + dy).coerceIn(
                                        190f, (maxH.value - current.y - 80f).coerceAtLeast(190f)
                                    )
                                )
                            }
                        }
                    )
                }
            }

            // Dock: navegación y controles generales. No duplica herramientas del escritorio.
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .shadow(22.dp, RoundedCornerShape(26.dp))
                    .background(Color(0xE8172B43), RoundedCornerShape(26.dp))
                    .border(1.dp, Color.White.copy(.18f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DockAction("✦", "Start", accent) {
                    startOpen = !startOpen
                    orbitOpen = false
                    controlOpen = false
                }
                DockAction("◎", "Orbit", Color(0xFFB69CFF)) {
                    orbitOpen = !orbitOpen
                    startOpen = false
                    controlOpen = false
                }
                DockAction("☷", "Control Center", Color(0xFFFFC27A)) {
                    controlOpen = !controlOpen
                    startOpen = false
                    orbitOpen = false
                }
                Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(.2f)))
                Text(clockText, color = Color.White, fontSize = 10.sp)
            }

            // INFINITY START: búsqueda y catálogo de aplicaciones.
            if (startOpen) {
                Card(
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 78.dp)
                        .widthIn(min = 290.dp, max = 390.dp)
                        .heightIn(max = maxH - 110.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = InfinityPanel)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("INFINITY START", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("Tus herramientas y aplicaciones", color = InfinityMuted, fontSize = 10.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Buscar aplicaciones…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        Text(
                            "HERRAMIENTAS",
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 5.dp)
                        )
                        LazyColumn(Modifier.heightIn(max = 150.dp)) {
                            items(
                                desktopItems.filter { it.label.contains(search, ignoreCase = true) },
                                key = { it.id }
                            ) { item ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            openInfinityWindow(item.id, windows, workspace)
                                            startOpen = false
                                        }.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.glyph, color = accent, fontSize = 20.sp)
                                    Text(item.label, color = Color.White, modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                        Text(
                            "APLICACIONES ANDROID (${installedApps.count {
                                it.label.contains(search, true) || it.packageName.contains(search, true)
                            }})",
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 230.dp)) {
                            items(
                                installedApps.filter {
                                    it.label.contains(search, true) ||
                                        it.packageName.contains(search, true)
                                },
                                key = { it.packageName }
                            ) { app ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        launchApp(app.packageName)
                                        startOpen = false
                                    }.padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(30.dp)
                                            .background(accent.copy(.16f), RoundedCornerShape(9.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(app.label.take(1).uppercase(), color = accent, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        app.label,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 9.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(.07f))
                            }
                        }
                    }
                }
            }

            // ORBIT: acciones del espacio de trabajo; no vuelve a listar las apps.
            if (orbitOpen) {
                Card(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 78.dp)
                        .widthIn(min = 290.dp, max = 350.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF21B2945))
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "INFINITY ORBIT",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text("Centro de acciones del escritorio", color = InfinityMuted, fontSize = 10.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("ESPACIOS DE TRABAJO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..3).forEach { number ->
                                Button(
                                    onClick = {
                                        workspace = number
                                        orbitOpen = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (workspace == number) accent
                                        else Color.White.copy(.12f)
                                    )
                                ) { Text("$number") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                windows.indices.forEach { index ->
                                    windows[index] = windows[index].copy(minimized = true)
                                }
                                orbitOpen = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Minimizar todas las ventanas") }
                        OutlinedButton(
                            onClick = {
                                appearance = if (lightMode) "dark" else "light"
                                prefs.edit().putString("appearance", appearance).apply()
                                orbitOpen = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (lightMode) "Activar modo oscuro" else "Activar modo claro") }
                        TextButton(onClick = { orbitOpen = false }) {
                            Text("Cerrar Orbit", color = accent)
                        }
                    }
                }
            }

            // CONTROL CENTER: personalización, sin accesos de lanzamiento repetidos.
            if (controlOpen) {
                Card(
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 78.dp)
                        .widthIn(min = 270.dp, max = 320.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = InfinityPanel)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CONTROL CENTER", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("Personaliza tu espacio", color = InfinityMuted, fontSize = 10.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("TEMAS", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            listOf(
                                "midnight" to Color(0xFF56D6C9),
                                "ocean" to Color(0xFF45B7FF),
                                "violet" to Color(0xFFB69CFF),
                                "emerald" to Color(0xFF55D6A4),
                                "sunset" to Color(0xFFFFA66B)
                            ).forEach { (id, color) ->
                                Box(
                                    Modifier.size(30.dp).background(color, CircleShape)
                                        .border(
                                            if (themeChoice == id) 3.dp else 1.dp,
                                            Color.White,
                                            CircleShape
                                        )
                                        .clickable {
                                            themeChoice = id
                                            prefs.edit().putString("theme_choice", id).apply()
                                        }
                                )
                            }
                        }
                        Text("APARIENCIA", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !lightMode,
                                onClick = {
                                    appearance = "dark"
                                    prefs.edit().putString("appearance", appearance).apply()
                                },
                                label = { Text("Oscura") }
                            )
                            FilterChip(
                                selected = lightMode,
                                onClick = {
                                    appearance = "light"
                                    prefs.edit().putString("appearance", appearance).apply()
                                },
                                label = { Text("Clara") }
                            )
                        }
                        Text("Tamaño de iconos: ${iconSize.toInt()} dp", color = InfinityMuted, fontSize = 10.sp)
                        Slider(
                            value = iconSize,
                            onValueChange = {
                                iconSize = it
                                prefs.edit().putFloat("icon_size", it).apply()
                            },
                            valueRange = 40f..72f
                        )
                        OutlinedButton(
                            onClick = { wallpaperPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cambiar fondo") }
                        OutlinedButton(
                            onClick = {
                                workspace = if (workspace == 3) 1 else workspace + 1
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Siguiente espacio • $workspace / 3") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockAction(
    glyph: String,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        Modifier.clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Color.White.copy(.78f), fontSize = 8.sp, maxLines = 1)
    }
}

private fun openInfinityWindow(
    id: String,
    windows: MutableList<DeskWindow>,
    workspace: Int
) {
    val titles = mapOf(
        "files" to "Explorador de archivos",
        "notes" to "Notas rápidas",
        "apps" to "Aplicaciones instaladas",
        "settings" to "Preferencias",
        "about" to "Acerca de INFINITY"
    )
    val existing = windows.indexOfFirst { it.kind == id && it.workspace == workspace }
    if (existing >= 0) {
        windows[existing] = windows[existing].copy(minimized = false)
    } else {
        windows.add(
            DeskWindow(
                id = "${id}_$workspace",
                title = titles[id] ?: "INFINITY",
                kind = id,
                x = 90f + windows.size * 22f,
                y = 75f + windows.size * 20f,
                workspace = workspace
            )
        )
    }
}

@Composable
private fun FileExplorer08(
    rootUri: Uri?,
    accent: Color,
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
        val documentId = currentDocumentId
        if (tree != null && documentId != null) {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                try {
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE
                    )
                    val found = mutableListOf<ExplorerEntry>()
                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                        while (cursor.moveToNext()) {
                            found.add(
                                ExplorerEntry(
                                    cursor.getString(idColumn),
                                    cursor.getString(nameColumn) ?: "Sin nombre",
                                    if (cursor.isNull(mimeColumn)) null else cursor.getString(mimeColumn),
                                    if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn)
                                )
                            )
                        }
                    }
                    found.sortedWith(
                        compareByDescending<ExplorerEntry> { it.isDirectory }
                            .thenBy { it.name.lowercase(Locale.getDefault()) }
                    )
                } catch (exception: Exception) {
                    error = exception.message ?: "No se pudo leer esta carpeta."
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
        Text("INFINITY FILE EXPLORER", color = accent, fontWeight = FontWeight.Bold)
        Text("Navega por carpetas y abre archivos con aplicaciones Android.", color = InfinityMuted, fontSize = 10.sp)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onChooseFolder) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (rootUri == null) "Elegir carpeta" else "Cambiar carpeta")
            }
            if (rootUri != null && currentDocumentId != DocumentsContract.getTreeDocumentId(rootUri)) {
                OutlinedButton(
                    onClick = { currentDocumentId = DocumentsContract.getTreeDocumentId(rootUri) }
                ) { Text("Volver al inicio") }
            }
        }
        when {
            rootUri == null -> Text("Selecciona una carpeta para comenzar.", color = Color.White, fontSize = 12.sp)
            loading -> CircularProgressIndicator(color = accent, modifier = Modifier.size(26.dp))
            error != null -> Text("Error: $error", color = Color(0xFFFF9999), fontSize = 11.sp)
            entries.isEmpty() -> Text("Esta carpeta está vacía o no contiene elementos accesibles.", color = InfinityMuted, fontSize = 11.sp)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.documentId }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (entry.isDirectory) {
                                currentDocumentId = entry.documentId
                            } else {
                                onOpenFile(
                                    DocumentsContract.buildDocumentUriUsingTree(rootUri, entry.documentId),
                                    entry.mimeType
                                )
                            }
                        }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (entry.isDirectory) "📁" else "📄", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (entry.isDirectory) "Carpeta"
                                else if (entry.size > 0) "${(entry.size / 1024).coerceAtLeast(1)} KB"
                                else (entry.mimeType ?: "Archivo"),
                                color = InfinityMuted,
                                fontSize = 9.sp
                            )
                        }
                        if (entry.isDirectory) Text("›", color = accent, fontSize = 22.sp)
                    }
                    HorizontalDivider(color = Color.White.copy(.08f))
                }
            }
        }
    }
}

@Composable
private fun Infinity08Window(
    win: DeskWindow,
    installedApps: List<InstalledApp>,
    accent: Color,
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
    var noteText by remember(win.id) {
        mutableStateOf(prefs.getString("note_${win.id}", "") ?: "")
    }
    val foreground = if (lightMode) Color(0xFF203149) else Color.White

    Column(
        Modifier.offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
            .width(win.width.dp)
            .height(win.height.dp)
            .shadow(26.dp, RoundedCornerShape(20.dp))
            .background(
                if (lightMode) Color(0xF2F5F8FC) else Color(0xF21B2B40),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, accent.copy(.38f), RoundedCornerShape(20.dp))
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(
                    if (lightMode) Color(0xFFE0EAF5) else Color(0xFF263D57),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .pointerInput(win.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onMove(drag.x, drag.y)
                    }
                }
                .padding(start = 13.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                win.title,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = onMinimize, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Minimize, contentDescription = "Minimizar", tint = foreground)
            }
            IconButton(onClick = onMaximize, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (win.maximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Maximizar o restaurar",
                    tint = foreground
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFFFF8B8B))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (win.kind) {
                "notes" -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Bloc de ideas", color = accent, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            prefs.edit().putString("note_${win.id}", it).apply()
                        },
                        modifier = Modifier.fillMaxSize().padding(top = 7.dp),
                        placeholder = { Text("Escribe tus ideas aquí…") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = foreground)
                    )
                }

                "files" -> FileExplorer08(
                    selectedFolder,
                    accent,
                    onChooseFolder
                ) { uri, mime ->
                    try {
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(viewIntent, "Abrir archivo con"))
                    } catch (_: Exception) { }
                }

                "apps" -> {
                    var appSearch by remember(win.id) { mutableStateOf("") }
                    val filteredApps = installedApps.filter {
                        it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true)
                    }
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Text(
                            "${filteredApps.size} de ${installedApps.size} aplicaciones",
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = appSearch,
                            onValueChange = { appSearch = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            singleLine = true,
                            placeholder = { Text("Buscar aplicación…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        onLaunchApp(app.packageName)
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("◈", color = accent, fontSize = 19.sp)
                                    Text(app.label, color = foreground, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                                }
                                HorizontalDivider(color = Color.Gray.copy(.18f))
                            }
                        }
                    }
                }

                "settings" -> Column(
                    Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Personalización del escritorio", color = accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("TEMAS Y COLORES", color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "midnight" to "Aqua",
                            "ocean" to "Azul",
                            "violet" to "Violeta",
                            "emerald" to "Verde",
                            "sunset" to "Naranja"
                        ).forEach { (id, label) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onThemeChange(id) }
                            ) {
                                val color = when (id) {
                                    "ocean" -> Color(0xFF45B7FF)
                                    "violet" -> Color(0xFFB69CFF)
                                    "emerald" -> Color(0xFF55D6A4)
                                    "sunset" -> Color(0xFFFFA66B)
                                    else -> Color(0xFF56D6C9)
                                }
                                Box(
                                    Modifier.size(28.dp).background(color, CircleShape)
                                        .border(
                                            if (themeChoice == id) 3.dp else 1.dp,
                                            Color.White,
                                            CircleShape
                                        )
                                )
                                Text(label, color = foreground, fontSize = 8.sp)
                            }
                        }
                    }
                    Text("APARIENCIA", color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = appearance == "dark",
                            onClick = { onAppearanceChange("dark") },
                            label = { Text("Oscura") }
                        )
                        FilterChip(
                            selected = appearance == "light",
                            onClick = { onAppearanceChange("light") },
                            label = { Text("Clara") }
                        )
                    }
                    Text("Tamaño de iconos: ${iconSize.toInt()} dp", color = foreground, fontSize = 11.sp)
                    Slider(value = iconSize, onValueChange = onIconSizeChange, valueRange = 40f..72f)
                    Button(onClick = onChooseWallpaper) { Text("Elegir fondo de pantalla") }
                }

                else -> Column(Modifier.padding(14.dp)) {
                    Text("PROJECT INFINITY 0.8", color = accent, fontWeight = FontWeight.Bold)
                    Text(
                        "AURORA NEXT\nEscritorio Android nativo • Kotlin • Jetpack Compose\n" +
                            "Dock sin accesos duplicados • Orbit de acciones • Control Center\n" +
                            "Explorador SAF • Notas • Lanzador Android • Espacios de trabajo",
                        color = foreground,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Box(
            Modifier.align(Alignment.End).size(30.dp)
                .pointerInput(win.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onResize(drag.x, drag.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("⤡", color = accent, fontSize = 19.sp)
        }
    }
}
