package com.infinity.core

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
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
    override fun onResume() {
        super.onResume()
        InfinityNotificationStore.refreshAccess(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(13, 22, 37)
        window.navigationBarColor = android.graphics.Color.rgb(13, 22, 37)
        setContent { Infinity08App() }
    }
}

private data class DeskItem(val id: String, val label: String, val glyph: String)

@Composable
private fun InfinityAppBadge(item: DeskItem, size: androidx.compose.ui.unit.Dp, accent: Color) {
    val identity = when (item.id) {
        "files" -> Color(0xFF48B5FF)
        "notes" -> Color(0xFFFFC66D)
        "apps" -> Color(0xFFB69CFF)
        "connect" -> Color(0xFF55D6A4)
        "settings" -> Color(0xFFFF8E78)
        else -> Color(0xFF56D6C9)
    }
    Box(
        Modifier.size(size).shadow(14.dp, RoundedCornerShape(size * .25f))
            .background(Brush.linearGradient(listOf(identity.copy(.36f), Color(0xFF142A43), Color(0xFF101827))), RoundedCornerShape(size * .25f))
            .border(1.4.dp, identity.copy(.82f), RoundedCornerShape(size * .25f)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().padding(size * .12f).border(1.dp, Color.White.copy(.08f), RoundedCornerShape(size * .2f)))
        Text(item.glyph, color = identity, fontSize = (size.value * .53f).sp, fontWeight = FontWeight.Black)
        Box(Modifier.align(Alignment.BottomEnd).padding(size * .10f).size(size * .10f).background(accent, CircleShape))
    }
}

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
            DeskItem("connect", "INFINITY CONNECT", "◉"),
            DeskItem("settings", "Personalizar", "⚙"),
            DeskItem("about", "INFINITY", "∞")
        )
    }

    var workspace by remember { mutableIntStateOf(1) }
    var startOpen by remember { mutableStateOf(false) }
    var orbitOpen by remember { mutableStateOf(false) }
    var controlOpen by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
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

    var wallpaperBitmap by remember {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(wallpaperUri) {
        val uri = wallpaperUri
        wallpaperBitmap = null
        val decoded = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
                    var sampleSize = 1
                    while (maxDimension > 0 && maxDimension / sampleSize > 2048) {
                        sampleSize *= 2
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
        wallpaperBitmap = decoded
        if (uri != null && decoded == null && wallpaperUri == uri) {
            if (prefs.getString("wallpaper_uri", null) == uri.toString()) {
                prefs.edit().remove("wallpaper_uri").apply()
            }
            wallpaperUri = null
            Toast.makeText(
                context,
                "No se pudo acceder al fondo guardado. Selecciona otra imagen.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (_: SecurityException) {
                false
            }
            // Use the selected image for this session, but only persist a URI
            // when Android confirms that its read grant survives process restarts.
            wallpaperUri = uri
            if (persisted) {
                prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
            } else {
                prefs.edit().remove("wallpaper_uri").apply()
                Toast.makeText(
                    context,
                    "Fondo temporal: Android no permitió conservar el permiso. Selecciona la imagen de nuevo al reiniciar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                true
            } catch (_: SecurityException) {
                false
            }
            // Keep the grant usable for this session, but never store a URI
            // that Android did not allow the app to persist across restarts.
            selectedFolder = uri
            if (persisted) {
                prefs.edit().putString("selected_folder_uri", uri.toString()).apply()
            } else {
                prefs.edit().remove("selected_folder_uri").apply()
                Toast.makeText(
                    context,
                    "Acceso temporal: Android no permitió guardar el permiso. Selecciona la carpeta de nuevo al reiniciar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val windows = remember { mutableStateListOf<DeskWindow>() }

var focusedWindowId by remember { mutableStateOf<String?>(null) }

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
            Modifier.fillMaxSize()
                // Respeta cámaras, barras del sistema y navegación por gestos.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .background(
                Brush.linearGradient(
                    if (lightMode) listOf(
                        Color(0xFFEAF1FA), Color(0xFFD4E2F2), Color(0xFFB9CDE2)
                    ) else listOf(Color(0xFF102238), Color(0xFF1C3B59), InfinityInk)
                )
            )
        ) {
            val maxW = maxWidth
            val maxH = maxHeight

            val currentWallpaperBitmap = wallpaperBitmap
            if (currentWallpaperBitmap != null) {
                Image(
                    currentWallpaperBitmap,
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

            // INFINITY DESKTOP 0.11 — ICONOS MEJORADOS
            desktopItems.forEach { item ->
                val offset = iconOffsets[item.id] ?: Offset(22f, 90f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                offset.x.roundToInt(),
                                offset.y.roundToInt()
                            )
                        }
                        .width(94.dp)
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
                        .clickable {
                            focusedWindowId = openInfinityWindow(item.id, windows, workspace)
                        }
                        .padding(6.dp)
                ) {
                    InfinityAppBadge(item, (iconSize + 8f).dp, accent)

                    Spacer(Modifier.height(7.dp))

                    Text(
                        item.label,
                        modifier = Modifier
                            .background(
                                Color(0xCC101827),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                0.7.dp,
                                Color.White.copy(alpha = 0.18f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(
                                horizontal = 7.dp,
                                vertical = 4.dp
                            ),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

 
            windows.filter { !it.minimized && it.workspace == workspace }.forEach { win ->
                key(win.id) {
                    val shown = if (win.maximized || maxW < 600.dp) {
                        // En teléfonos, las ventanas se ajustan automáticamente
                        // al espacio disponible para evitar recortes horizontales.
                        win.copy(
                            x = 8f,
                            y = if (maxW < 600.dp) 62f else 8f,
                            width = (maxW.value - 16f).coerceAtLeast(240f),
                            height = (maxH.value - if (maxW < 600.dp) 132f else 100f)
                                .coerceAtLeast(180f)
                        )
                    } else win

                    Infinity08Window(
                        win = shown,
                        isFocused = focusedWindowId == win.id,
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
                        
                        // INFINITY 0.11 — MOVIMIENTO CON LÍMITES
                        onMove = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) {
                                val current = windows[index]
                                windows[index] = current.copy(
                                    x = (current.x + dx).coerceIn(
                                        0f,
                                        (maxW.value - current.width).coerceAtLeast(0f)
                                    ),
                                    y = (current.y + dy).coerceIn(
                                        0f,
                                        (maxH.value - current.height).coerceAtLeast(0f)
                                    )
                                )
                            }
                        },
                        // INFINITY 0.11 — REDIMENSIONAMIENTO ADAPTABLE
                        onResize = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) {
                                val current = windows[index]

                                val availableWidth =
                                    (maxW.value - current.x).coerceAtLeast(1f)

                                val availableHeight =
                                    (maxH.value - current.y).coerceAtLeast(1f)

                                windows[index] = current.copy(
                                    width = (current.width + dx).coerceIn(
                                        minOf(280f, availableWidth),
                                        availableWidth
                                    ),
                                    height = (current.height + dy).coerceIn(
                                        minOf(190f, availableHeight),
                                        availableHeight
                                    )
                                )
                            }
                        },
                     onFocus = { focusedWindowId = win.id
                            
                        }
                    )
                }
            }

          
            // DOCK INFINITY 0.10 — DISEÑO MEJORADO
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .shadow(26.dp, RoundedCornerShape(24.dp))
                    .background(
                        Color(0xF016263D),
                        RoundedCornerShape(24.dp)
                    )
                    .border(
                        1.dp,
                        Color(0xFF6686B5),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(
                        horizontal = if (maxW < 600.dp) 6.dp else 18.dp,
                        vertical = 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    when {
                        maxW < 380.dp -> 5.dp
                        maxW < 600.dp -> 8.dp
                        else -> 22.dp
                    }
                )
            ) {
                DockAction("✦", "START", accent) {
                    startOpen = !startOpen
                    orbitOpen = false
                    controlOpen = false
                }

                DockAction("◎", "ORBIT", Color(0xFFB69CFF)) {
                    orbitOpen = !orbitOpen
                    startOpen = false
                    controlOpen = false
                }

                DockAction("☷", "CONTROL", Color(0xFFFFC27A)) {
                    controlOpen = !controlOpen
                    startOpen = false
                    orbitOpen = false
                }
DockAction("▤", "ADVANCED", accent) {
    advancedOpen = !advancedOpen
    startOpen = false
    orbitOpen = false
    controlOpen = false
}
                Box(
                    Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(Color.White.copy(alpha = 0.30f))
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        clockText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "INFINITY",
                        color = Color(0xFF9DB9E8),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                                            focusedWindowId = openInfinityWindow(item.id, windows, workspace)
                                            startOpen = false
                                        }.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    InfinityAppBadge(item, 34.dp, accent)
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

           
            // INFINITY ORBIT 0.9: centro de mando futurista.
            if (orbitOpen) {
                Card(
                 Modifier.align(Alignment.BottomCenter)
    .padding(horizontal = 12.dp, vertical = 40.dp)
    .widthIn(min = 300.dp, max = 410.dp)
    .heightIn(max = maxH - 80.dp),  
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xF20D1B30)
                    )
                ) {
                    Column(
    Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
                        // CABECERA Y NUCLEO ORBITAL
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(66.dp)
                                    .border(2.dp, accent, CircleShape)
                                    .padding(7.dp)
                                    .border(1.dp, Color.White.copy(.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "∞",
                                    color = accent,
                                    fontSize = 37.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Column(Modifier.padding(start = 12.dp)) {
                                Text(
                                    "INFINITY ORBIT",
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    "COMMAND CENTER • 0.10",
                                    color = accent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "Sistema de control del escritorio",
                                    color = InfinityMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))         
    
OrbitSystemMonitor(
    accent = accent,
    activeWindows = windows.count {
        it.workspace == workspace && !it.minimized
    },
    totalWindows = windows.count {
        it.workspace == workspace
    }
)
// INFINITY ORBIT 0.11 — CONTROL DE NOTIFICACIONES
Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
        containerColor = Color(0xFF192D47)
    )
) {
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CENTRO DE NOTIFICACIONES",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (InfinityNotificationStore.accessEnabled.value)
                        "● SERVICIO CONECTADO"
                    else
                        "● SERVICIO DESCONECTADO",
                    color = if (InfinityNotificationStore.accessEnabled.value)
                        Color(0xFF55D6A4)
                    else
                        Color(0xFFFFA66B),
                    fontSize = 9.sp
                )
            }

            Text(
                "${InfinityNotificationStore.notifications.size}",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black
            )
        }

        Text(
            "Notificaciones recibidas",
            color = InfinityMuted,
            fontSize = 10.sp
        )

        Button(
            onClick = { advancedOpen = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent
            )
        ) {
            Text(
                "ABRIR CENTRO DE NOTIFICACIONES",
                color = Color(0xFF101827),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!InfinityNotificationStore.accessGranted.value) {
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "No se pudo abrir la configuración de notificaciones",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CONFIGURAR ACCESO A NOTIFICACIONES")
            }
        }
    }
}
Spacer(Modifier.height(14.dp))

                        // INDICADORES DEL SISTEMA
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "WORKSPACE" to "$workspace / 3",
                                "VENTANAS" to "${windows.count { it.workspace == workspace && !it.minimized }}",
                                "ESTADO" to "ACTIVO"
                            ).forEach { (label, value) ->
                                Column(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(Color(0xFF192D47))
                                        .border(
                                            1.dp,
                                            accent.copy(.25f),
                                            RoundedCornerShape(13.dp)
                                        )
                                        .padding(9.dp)
                                ) {
                                    Text(
                                        label,
                                        color = InfinityMuted,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        value,
                                        color = accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        

// SELECTOR ORBIT 0.10 — ESPACIOS DE TRABAJO
Text(
    "◈  ESPACIOS DE TRABAJO",
    color = accent,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.align(Alignment.Start)
)

Spacer(Modifier.height(10.dp))

Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    (1..3).forEach { number ->
        val selected = workspace == number
        val buttonShape = RoundedCornerShape(14.dp)

        Button(
            onClick = {
                workspace = number
            },
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .border(
                    1.5.dp,
                    if (selected) accent else Color(0xFF52789C),
                    buttonShape
                ),
            shape = buttonShape,
            contentPadding = PaddingValues(3.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected)
                    accent.copy(alpha = 0.24f)
                else
                    Color(0xFF13263D)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (selected) 5.dp else 1.dp
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "ORBIT $number",
                    color = if (selected) accent else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (selected) "EN USO" else "DISPONIBLE",
                    color = if (selected) accent else InfinityMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


                        Spacer(Modifier.height(14.dp))

                       
                        // ORBIT 0.10 — ACCIONES RÁPIDAS
                        Text(
                            "◈  ACCIONES RÁPIDAS",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(Modifier.height(9.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Button(
                                onClick = {
                                    openInfinityWindow("connect", windows, workspace)
                                    orbitOpen = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color(0xFF071C2B)
                                )
                            ) {
                                Text(
                                    "◉ CONNECT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    windows.indices.forEach { index ->
                                        windows[index] =
                                            windows[index].copy(minimized = true)
                                    }
                                    orbitOpen = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(13.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color(0xFFFFB86B)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFFC27A)
                                )
                            ) {
                                Text(
                                    "▱ MINIMIZAR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))


                        
                        // ORBIT 0.10 — ADMINISTRADOR DE VENTANAS
                        Text(
                            "◈  ADMINISTRADOR DE VENTANAS",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(Modifier.height(8.dp))

                        val orbitWindows = windows.filter {
                            it.workspace == workspace
                        }

                        if (orbitWindows.isEmpty()) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF192D47))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("▱", color = accent, fontSize = 25.sp)
                                Text(
                                    "ESCRITORIO DESPEJADO",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "No hay ventanas en este espacio.",
                                    color = InfinityMuted,
                                    fontSize = 10.sp
                                )
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth()
                                    .heightIn(max = 190.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                orbitWindows.forEach { win ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF192D47))
                                            .border(
                                                1.dp,
                                                accent.copy(alpha = 0.20f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(
                                                horizontal = 9.dp,
                                                vertical = 5.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (win.minimized) "○" else "●",
                                            color = if (win.minimized)
                                                InfinityMuted else accent
                                        )

                                        Column(
                                            Modifier.weight(1f)
                                                .clickable {
                                                    val index = windows.indexOfFirst {
                                                        it.id == win.id
                                                    }
                                                    if (index >= 0) {
                                                        windows[index] =
                                                            windows[index].copy(
                                                                minimized = false
                                                            )
                                                    }
                                                    orbitOpen = false
                                                }
                                                .padding(start = 8.dp)
                                        ) {
                                            Text(
                                                win.title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                if (win.minimized) "MINIMIZADA" else "ACTIVA",
                                                color = if (win.minimized)
                                                    InfinityMuted else accent,
                                                fontSize = 8.sp
                                            )
                                        }

                                        TextButton(
                                            onClick = {
                                                val index = windows.indexOfFirst {
                                                    it.id == win.id
                                                }
                                                if (index >= 0) {
                                                    windows[index] =
                                                        windows[index].copy(
                                                            minimized = !win.minimized
                                                        )
                                                }
                                            }
                                        ) {
                                            Text(
                                                if (win.minimized) "ABRIR" else "MIN",
                                                fontSize = 9.sp
                                            )
                                        }

                                        TextButton(
                                            onClick = {
                                                windows.removeAll {
                                                    it.id == win.id
                                                }
                                            }
                                        ) {
                                            Text(
                                                "×",
                                                color = Color(0xFFFF8294),
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))


                        TextButton(onClick = { orbitOpen = false }) {
                            Text("CERRAR ORBIT", color = accent, fontSize = 10.sp)
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
              // INFINITY ADVANCED — CENTRO DE NOTIFICACIONES Y ACCIONES RÁPIDAS.
            if (advancedOpen) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 76.dp)
                        .widthIn(min = 300.dp, max = 430.dp)
                        .fillMaxWidth()
                ) {
                    InfinityAdvancedPanel(
                        onClose = { advancedOpen = false }
                    )
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
): String {
    val titles = mapOf(
        "files" to "Explorador de archivos",
        "notes" to "Notas rápidas",
        "apps" to "Aplicaciones instaladas",
        "connect" to "INFINITY CONNECT",
        "settings" to "Preferencias",
        "about" to "Acerca de INFINITY"
    )
    val existing = windows.indexOfFirst { it.kind == id && it.workspace == workspace }
    val windowId = "${id}_$workspace"
    if (existing >= 0) {
        windows[existing] = windows[existing].copy(minimized = false)
    } else {
        windows.add(
            DeskWindow(
                id = windowId,
                title = titles[id] ?: "INFINITY",
                kind = id,
                x = 90f + windows.size * 22f,
                y = 75f + windows.size * 20f,
                workspace = workspace
            )
        )
    }
    return windowId
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
    var refreshVersion by remember(rootUri) { mutableIntStateOf(0) }
    var entries by remember(rootUri, currentDocumentId) { mutableStateOf<List<ExplorerEntry>>(emptyList()) }
    var loading by remember(rootUri, currentDocumentId) { mutableStateOf(false) }
    var error by remember(rootUri, currentDocumentId) { mutableStateOf<String?>(null) }
    var fileSearch by remember(rootUri, currentDocumentId) { mutableStateOf("") }
    val filteredEntries = entries.filter { it.name.contains(fileSearch.trim(), ignoreCase = true) }

    LaunchedEffect(rootUri, currentDocumentId, refreshVersion) {
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
                    val cursor = context.contentResolver.query(
                        childrenUri, projection, null, null, null
                    ) ?: throw IllegalStateException(
                        "El proveedor de documentos no pudo consultar esta carpeta."
                    )
                    cursor.use {
                        val idColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (idColumn < 0 || nameColumn < 0) {
                            throw IllegalStateException(
                                "El proveedor no ofrece identificadores o nombres de archivos."
                            )
                        }
                        // MIME type and size are optional across SAF providers.
                        val mimeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        while (it.moveToNext()) {
                            found.add(
                                ExplorerEntry(
                                    it.getString(idColumn),
                                    it.getString(nameColumn) ?: "Sin nombre",
                                    if (mimeColumn < 0 || it.isNull(mimeColumn)) null
                                        else it.getString(mimeColumn),
                                    if (sizeColumn < 0 || it.isNull(sizeColumn)) -1L
                                        else it.getLong(sizeColumn)
                                )
                            )
                        }
                    }
                    Result.success(
                        found.sortedWith(
                            compareByDescending<ExplorerEntry> { it.isDirectory }
                                .thenBy { it.name.lowercase(Locale.getDefault()) }
                        )
                    )
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
            }
            entries = result.getOrDefault(emptyList())
            error = result.exceptionOrNull()?.let {
                it.message?.takeIf(String::isNotBlank) ?: "No se pudo leer esta carpeta."
            }
            loading = false
        } else {
            entries = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("INFINITY FILE EXPLORER", color = accent, fontWeight = FontWeight.Bold)
        Text("Navega por carpetas y abre archivos con aplicaciones Android.", color = InfinityMuted, fontSize = 10.sp)
        OutlinedTextField(
            value = fileSearch,
            onValueChange = { fileSearch = it },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
            placeholder = { Text("Buscar en esta carpeta…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
            trailingIcon = { if (fileSearch.isNotEmpty()) IconButton(onClick = { fileSearch = "" }) { Icon(Icons.Default.Close, contentDescription = "Limpiar") } }
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onChooseFolder) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (rootUri == null) "Elegir carpeta" else "Cambiar carpeta")
            }
            if (rootUri != null) {
                OutlinedButton(
                    onClick = { refreshVersion++ },
                    enabled = !loading
                ) { Text(if (loading) "Actualizando…" else "Actualizar") }
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
            else -> Column(Modifier.fillMaxSize()) {
                Text("${filteredEntries.size} elementos", color = accent, fontSize = 10.sp, modifier = Modifier.padding(vertical = 5.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filteredEntries, key = { it.documentId }) { entry ->
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
}

@Composable
private fun Infinity08Window(
    win: DeskWindow,
    isFocused: Boolean,
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
    onResize: (Float, Float) -> Unit,
    onFocus: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }
    var noteText by remember(win.id) {
        mutableStateOf(prefs.getString("note_${win.id}", "") ?: "")
    }
    val foreground = if (lightMode) Color(0xFF203149) else Color.White
 
    var windowVisible by remember(win.id) {
        mutableStateOf(false)
    }

    LaunchedEffect(win.id) {
        windowVisible = true
    }

    val windowAlpha by animateFloatAsState(
        targetValue = if (windowVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "windowAlpha"
    )

    val windowScale by animateFloatAsState(
        targetValue = if (windowVisible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 280),
        label = "windowScale"
    )
    Column(
     
        Modifier.offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                alpha = windowAlpha
                scaleX = windowScale
                scaleY = windowScale
            }
            .width(win.width.dp)
            .height(win.height.dp)
            .shadow(26.dp, RoundedCornerShape(20.dp))
            .background(
                if (lightMode) Color(0xF2F5F8FC) else Color(0xF21B2B40),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, accent.copy(.38f), RoundedCornerShape(20.dp)) 
            .clickable { onFocus() }
    ) {
        
        // INFINITY SMART WINDOWS 0.11 — BARRA DE CONTROLES
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    if (lightMode) Color(0xFFE0EAF5)
                    else Color(0xFF263D57),
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp
                    )
                ) 
                .clickable { onFocus() }
                .pointerInput(win.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onMove(drag.x, drag.y)
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfinityAppBadge(
                DeskItem(
                    win.kind,
                    win.title,
                    when (win.kind) {
                        "files" -> "▣"
                        "notes" -> "✎"
                        "apps" -> "◈"
                        "connect" -> "◉"
                        "settings" -> "⚙"
                        "about" -> "∞"
                        else -> "◇"
                    }
                ),
                34.dp,
                accent
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 9.dp)
            ) {
                Text(
                    win.title,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "VENTANA ACTIVA",
                    color = accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onMinimize,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Color(0xFF71849B).copy(alpha = 0.22f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Minimize,
                    contentDescription = "Minimizar",
                    tint = foreground
                )
            }

            Spacer(Modifier.width(5.dp))

            IconButton(
                onClick = onMaximize,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        accent.copy(alpha = 0.20f),
                        CircleShape
                    )
            ) {
                Icon(
                    if (win.maximized)
                        Icons.Default.FullscreenExit
                    else
                        Icons.Default.Fullscreen,
                    contentDescription = "Maximizar o restaurar",
                    tint = foreground
                )
            }

            Spacer(Modifier.width(5.dp))

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Color(0xFFFF6262).copy(alpha = 0.20f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color(0xFFFF8B8B)
                )
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

                "files" -> InfinityFileManager(
                    rootUri = selectedFolder,
                    accent = accent,
                    onChooseFolder = onChooseFolder
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
                "connect" -> InfinityConnectPanel()
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
                   Text("PROJECT INFINITY 0.10", color = accent, fontWeight = FontWeight.Bold)
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

         // INFINITY DESKTOP 0.11 — CONTROL DE REDIMENSIONAMIENTO
        Box(
            Modifier
                .align(Alignment.End)
                .padding(end = 8.dp, bottom = 8.dp)
                .size(44.dp)
                .background(
                    accent.copy(alpha = 0.18f),
                    RoundedCornerShape(14.dp)
                )
                .border(
                    1.dp,
                    accent.copy(alpha = 0.65f),
                    RoundedCornerShape(14.dp)
                )
                .pointerInput(win.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onResize(drag.x, drag.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "⤡",
                color = accent,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }      
    }
}
