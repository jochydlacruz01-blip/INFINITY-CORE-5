package com.infinity.core

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val appIcon = when (item.id) {
        "files" -> Icons.Filled.FolderOpen
        "notes" -> Icons.Filled.EditNote
        "apps" -> Icons.Filled.Apps
        "connect" -> Icons.Filled.Computer
        "settings" -> Icons.Filled.Palette
        else -> Icons.Filled.Info
    }
    Box(
        Modifier.size(size).shadow(14.dp, RoundedCornerShape(size * .25f))
            .background(Brush.linearGradient(listOf(identity.copy(.36f), Color(0xFF142A43), Color(0xFF101827))), RoundedCornerShape(size * .25f))
            .border(1.4.dp, identity.copy(.82f), RoundedCornerShape(size * .25f)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().padding(size * .12f).border(1.dp, Color.White.copy(.08f), RoundedCornerShape(size * .2f)))
        Icon(
            imageVector = appIcon,
            contentDescription = item.label,
            tint = identity,
            modifier = Modifier.size(size * .53f)
        )
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
private data class InstalledApp(val label: String, val packageName: String, val activityName: String)
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

    var workspace by remember { mutableIntStateOf(infinityNormalizeWorkspace(prefs.getInt("active_workspace", 1))) }
    var dashboardOpen by remember { mutableStateOf(false) }
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
        mutableFloatStateOf(infinityNormalizeIconSize(prefs.getFloat("icon_size", 48f)))
    }
    var themeChoice by remember {
        mutableStateOf(infinityNormalizeThemeChoice(prefs.getString("theme_choice", "midnight")))
    }
    var appearance by remember {
        mutableStateOf(infinityNormalizeAppearance(prefs.getString("appearance", "dark")))
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

    val windows = remember {
        mutableStateListOf<DeskWindow>().apply {
            val saved = prefs.getString("saved_windows", null)
            try {
                val array = JSONArray(saved ?: "[]")
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val kind = item.optString("kind")
                    if (kind !in setOf("files", "notes", "apps", "connect", "settings", "about")) continue
                    add(
                        DeskWindow(
                            id = item.optString("id", infinityWindowIdentity(kind, 1)),
                            title = item.optString("title", kind),
                            kind = kind,
                            x = item.optDouble("x", 24.0).toFloat(),
                            y = item.optDouble("y", 100.0).toFloat(),
                            width = item.optDouble("width", 370.0).toFloat().coerceIn(240f, 900f),
                            height = item.optDouble("height", 330.0).toFloat().coerceIn(180f, 900f),
                            minimized = item.optBoolean("minimized", false),
                            workspace = infinityNormalizeWorkspace(item.optInt("workspace", 1)),
                            maximized = item.optBoolean("maximized", false)
                        )
                    )
                }
            } catch (_: Exception) {
                clear()
                prefs.edit().remove("saved_windows").apply()
            }
        }
    }

    val windowSnapshot = windows.joinToString("|") {
        "${it.id},${it.x},${it.y},${it.width},${it.height},${it.minimized},${it.workspace},${it.maximized}"
    }
    LaunchedEffect(windowSnapshot) {
        val array = JSONArray()
        windows.forEach { win ->
            array.put(
                JSONObject().put("id", win.id).put("title", win.title).put("kind", win.kind)
                    .put("x", win.x).put("y", win.y).put("width", win.width).put("height", win.height)
                    .put("minimized", win.minimized).put("workspace", win.workspace)
                    .put("maximized", win.maximized)
            )
        }
        prefs.edit().putString("saved_windows", array.toString()).apply()
    }

    var focusedWindowId by remember {
        mutableStateOf(prefs.getString("focused_window_id", null)?.takeIf { savedId ->
            windows.any { it.id == savedId && !it.minimized && it.workspace == workspace }
        })
    }
    LaunchedEffect(focusedWindowId) {
        val id = focusedWindowId
        if (id == null) prefs.edit().remove("focused_window_id").apply()
        else prefs.edit().putString("focused_window_id", id).apply()
    }

    fun switchWorkspace(target: Int) {
        val normalizedTarget = infinityNormalizeWorkspace(target)
        workspace = normalizedTarget
        prefs.edit().putInt("active_workspace", normalizedTarget).apply()
        focusedWindowId = infinityResolveWorkspaceSwitchFocus(
            focusedWindowId, windows.map { it.id },
            windows.associate { it.id to it.workspace },
            windows.filter { it.minimized }.map { it.id }.toSet(), normalizedTarget
        )
        startOpen = false
        orbitOpen = false
        controlOpen = false
        advancedOpen = false
    }

val iconOffsets = remember {
    mutableStateMapOf<String, Offset>().apply {
        desktopItems.forEachIndexed { index, item ->
            put(
                    item.id,
                    Offset(
                        prefs.getFloat("${item.id}_x", infinityDesktopIconGridOffset(
                            index, context.resources.displayMetrics.widthPixels.toFloat(),
                            116f * context.resources.displayMetrics.density, 150f * context.resources.displayMetrics.density,
                            horizontalGap = 12f * context.resources.displayMetrics.density,
                            verticalGap = 14f * context.resources.displayMetrics.density,
                            topInset = 118f * context.resources.displayMetrics.density
                        ).x),
                        prefs.getFloat("${item.id}_y", infinityDesktopIconGridOffset(
                            index, context.resources.displayMetrics.widthPixels.toFloat(),
                            116f * context.resources.displayMetrics.density, 150f * context.resources.displayMetrics.density,
                            horizontalGap = 12f * context.resources.displayMetrics.density,
                            verticalGap = 14f * context.resources.displayMetrics.density,
                            topInset = 118f * context.resources.displayMetrics.density
                        ).y)
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
                    label = it.loadLabel(context.packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name
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
            val density = LocalDensity.current

            // Reubica iconos guardados cuando cambia el tamaño/orientación del dispositivo.
            LaunchedEffect(maxW, maxH) {
                val viewportWidth = with(density) { maxW.toPx() }
                val viewportHeight = with(density) { maxH.toPx() }
                desktopItems.forEach { item ->
                    val current = iconOffsets[item.id] ?: Offset(22f, 90f)
                    val bounded = infinityBoundIconOffset(
                        current, Offset.Zero, viewportWidth, viewportHeight - with(density) { 168.dp.toPx() },
                        itemWidth = with(density) { 116.dp.toPx() },
                        itemHeight = with(density) { 150.dp.toPx() }
                    )
                    if (bounded != current) {
                        iconOffsets[item.id] = bounded
                        prefs.edit()
                            .putFloat("${item.id}_x", bounded.x)
                            .putFloat("${item.id}_y", bounded.y)
                            .apply()
                    }
                }
            }

            // Repara geometría persistida tras cambios de pantalla, orientación o tamaño.
            LaunchedEffect(maxW, maxH) {
                val widthLimit = (maxW.value - 16f).coerceAtLeast(240f)
                val heightLimit = (maxH.value - 96f).coerceAtLeast(180f)
                windows.indices.forEach { index ->
                    val current = windows[index]
                    val safeWidth = current.width.coerceIn(240f, widthLimit)
                    val safeHeight = current.height.coerceIn(180f, heightLimit)
                    val safeX = current.x.coerceIn(0f, (maxW.value - safeWidth).coerceAtLeast(0f))
                    val safeY = current.y.coerceIn(0f, (maxH.value - safeHeight).coerceAtLeast(0f))
                    if (safeWidth != current.width || safeHeight != current.height ||
                        safeX != current.x || safeY != current.y
                    ) {
                        windows[index] = current.copy(
                            x = safeX, y = safeY, width = safeWidth, height = safeHeight
                        )
                    }
                }
            }

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

            // INFINITY AURORA DESKTOP — profundidad ambiental y anillos orbitales.
            Box(
                Modifier.offset(x = maxW * .57f, y = maxH * .12f)
                    .size(280.dp)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(.24f), Color(0x222B6B91), Color.Transparent)),
                        CircleShape
                    )
            )
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .offset(x = 104.dp, y = (-34).dp)
                    .size(260.dp)
                    .border(1.dp, accent.copy(alpha = .13f), CircleShape)
            )
            Box(
                Modifier.align(Alignment.CenterEnd)
                    .offset(x = 70.dp, y = (-34).dp)
                    .size(190.dp)
                    .border(1.dp, Color.White.copy(alpha = .07f), CircleShape)
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
                Text(
                    "DESKTOP ENVIRONMENT  /  ANDROID",
                    color = InfinityMuted.copy(alpha = .78f),
                    fontSize = 8.sp,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(9.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..3).forEach { targetWorkspace ->
                        val selected = workspace == targetWorkspace
                        Box(
                            Modifier
                                .widthIn(min = 48.dp)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) accent.copy(alpha = .20f)
                                    else Color(0x55101D30)
                                )
                                .border(
                                    1.dp,
                                    if (selected) accent.copy(alpha = .72f)
                                    else Color.White.copy(alpha = .14f),
                                    RoundedCornerShape(12.dp)
                                )
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    switchWorkspace(targetWorkspace)
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "0$targetWorkspace",
                                color = if (selected) accent else Color.White.copy(alpha = .76f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        "ESPACIOS",
                        color = InfinityMuted,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Column(
                Modifier.align(Alignment.TopEnd).padding(18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xAA101D30))
                    .border(1.dp, accent.copy(alpha = .24f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(clockText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text(dateText, color = InfinityMuted, fontSize = 10.sp)
            }

            if (dashboardOpen) {
                InfinityRealDashboard(
                    workspace = workspace,
                    onWorkspaceChange = { target -> switchWorkspace(target); dashboardOpen = true },
                    onOpenWindow = { id ->
                        focusedWindowId = openInfinityWindow(id, windows, workspace, maxW.value, maxH.value)
                        dashboardOpen = false
                        startOpen = false
                        orbitOpen = false
                        controlOpen = false
                        advancedOpen = false
                    },
                    onOpenOrbit = {
                        orbitOpen = true
                        startOpen = false
                        controlOpen = false
                        advancedOpen = false
                    },
                    onOpenAdvanced = {
                        advancedOpen = true
                        startOpen = false
                        orbitOpen = false
                        controlOpen = false
                    },
                    onOpenControl = {
                        controlOpen = true
                        startOpen = false
                        orbitOpen = false
                        advancedOpen = false
                    },
                    onShowDesktop = { dashboardOpen = false }
                )
            } else {
                desktopItems.forEach { item ->
                    val offset = iconOffsets[item.id] ?: Offset(22f, 90f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                            .width(108.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x66101D30))
                            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                            .padding(8.dp)
                            .pointerInput(item.id) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val next = infinityBoundIconOffset(
                                        iconOffsets[item.id] ?: Offset(22f, 90f),
                                        Offset(drag.x, drag.y),
                                        with(density) { maxW.toPx() },
                                        with(density) { maxH.toPx() - 168.dp.toPx() },
                                        itemWidth = with(density) { 116.dp.toPx() },
                                        itemHeight = with(density) { 150.dp.toPx() }
                                    )
                                    iconOffsets[item.id] = next
                                    prefs.edit()
                                        .putFloat("${item.id}_x", next.x)
                                        .putFloat("${item.id}_y", next.y)
                                        .apply()
                                }
                            }
                            .clickable {
                                focusedWindowId = openInfinityWindow(item.id, windows, workspace, maxW.value, maxH.value)
                            }
                            .padding(6.dp)
                    ) {
                        InfinityAppBadge(item, (iconSize + 14f).dp, accent)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            item.label,
                            modifier = Modifier
                                .shadow(10.dp, RoundedCornerShape(12.dp))
                                .background(Color(0xDD101D30), RoundedCornerShape(12.dp))
                                .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                            width = (maxW.value - 16f).coerceAtLeast(1f),
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
                            val normalized = infinityNormalizeThemeChoice(it)
                            themeChoice = normalized
                            prefs.edit().putString("theme_choice", normalized).apply()
                        },
                        appearance = appearance,
                        onAppearanceChange = {
                            val normalized = infinityNormalizeAppearance(it)
                            appearance = normalized
                            prefs.edit().putString("appearance", normalized).apply()
                        },
                        iconSize = iconSize,
                        onIconSizeChange = {
                            val normalized = infinityNormalizeIconSize(it)
                            iconSize = normalized
                            prefs.edit().putFloat("icon_size", normalized).apply()
                        },
                        onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                        selectedFolder = selectedFolder,
                        onChooseFolder = { folderPicker.launch(null) },
                        onLaunchApp = ::launchApp,
                        onClose = {
                            windows.removeAll { item -> item.id == win.id }
                            if (focusedWindowId == win.id) {
                                focusedWindowId = infinityResolveWorkspaceFocus(
                                    focusedWindowId,
                                    windows.map { it.id },
                                    windows.associate { it.id to it.workspace },
                                    windows.filter { it.minimized }.map { it.id }.toSet(),
                                    workspace
                                )
                            }
                        },
                        onMinimize = {
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] = windows[index].copy(minimized = true)
                            if (focusedWindowId == win.id) {
                                focusedWindowId = infinityResolveWorkspaceFocus(
                                    null,
                                    windows.map { it.id }.filterNot { it == win.id },
                                    windows.filterNot { it.id == win.id }.associate { it.id to it.workspace },
                                    windows.filter { it.minimized || it.id == win.id }.map { it.id }.toSet(),
                                    workspace
                                )
                            }
                        },
                        onMaximize = {
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) {
                                windows[index] = windows[index].copy(maximized = !windows[index].maximized)
                                focusedWindowId = win.id
                            }
                        },
                        
                        // INFINITY 0.11 — MOVIMIENTO CON LÍMITES
                        onMove = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) {
                                val current = windows[index]
                                val boundedPosition = infinityClampWindowPosition(
                                    current.x + dx,
                                    current.y + dy,
                                    current.width,
                                    current.height,
                                    maxW.value,
                                    maxH.value
                                )
                                windows[index] = current.copy(
                                    x = boundedPosition.first,
                                    y = boundedPosition.second
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
                                    width = infinityResizeDimension(current.width, dx, availableWidth, 280f),
                                    height = infinityResizeDimension(current.height, dy, availableHeight, 190f)
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
                DockAction("⌂", "HOME", accent) {
                    dashboardOpen = true
                    startOpen = false
                    orbitOpen = false
                    controlOpen = false
                    advancedOpen = false
                }

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

                infinityWorkspaceWindowIds(windows.map { it.id }, windows.associate { it.id to it.workspace }, workspace).mapNotNull { id -> windows.firstOrNull { it.id == id } }.let { workspaceWindows ->
                    infinityDockWindowIds(workspaceWindows.map { it.id }, workspaceWindows.associate { it.id to it.minimized }).forEach { taskId ->
                        val task = workspaceWindows.firstOrNull { it.id == taskId } ?: return@forEach
                        DockAction(if (task.minimized) "□" else "▣", infinityDockLabel(task.title), accent) {
                            val idx = windows.indexOfFirst { it.id == task.id }
                            if (idx >= 0) {
                                val tappedTask = windows[idx]
                                val activation = infinityDockResolveActivation(
                                    tappedTask.id, focusedWindowId, tappedTask.minimized
                                )
                                val minimizeTask = activation == null
                                windows[idx] = tappedTask.copy(minimized = minimizeTask)
                                focusedWindowId = if (minimizeTask) {
                                    infinityResolveWorkspaceFocus(
                                        null, windows.map { it.id },
                                        windows.associate { it.id to it.workspace },
                                        windows.filter { it.minimized }.map { it.id }.toSet(), workspace
                                    )
                                } else activation
                            }
                            dashboardOpen = false
                            startOpen = false
                            orbitOpen = false
                            controlOpen = false
                            advancedOpen = false
                        }
                    }
                }

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
                        .padding(start = 14.dp, bottom = 84.dp)
                        .widthIn(min = 290.dp, max = 420.dp)
                        .heightIn(max = maxH - 116.dp)
                        .shadow(28.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF0142036)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .28f))
                ) {
                    Column(Modifier.padding(18.dp)) {
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
                                            focusedWindowId = openInfinityWindow(item.id, windows, workspace, maxW.value, maxH.value)
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
                                    val appIcon = remember(app.packageName, app.activityName) {
                                        runCatching {
                                            val component = ComponentName(app.packageName, app.activityName)
                                            val drawable = runCatching {
                                                context.packageManager.getActivityIcon(component)
                                            }.getOrElse {
                                                context.packageManager.getApplicationIcon(app.packageName)
                                            }
                                            val iconSize = 128
                                            val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
                                            val canvas = Canvas(bitmap)
                                            drawable.setBounds(0, 0, iconSize, iconSize)
                                            drawable.draw(canvas)
                                            bitmap.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    Box(
                                        Modifier.size(42.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(Color(0xFF22364F)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon,
                                                contentDescription = "Icono de ${app.label}",
                                                modifier = Modifier.fillMaxSize().padding(2.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Apps,
                                                contentDescription = "Icono genérico de aplicación",
                                                tint = accent,
                                                modifier = Modifier.size(25.dp)
                                            )
                                        }
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
                 Modifier.align(Alignment.TopCenter)
    .padding(start = 12.dp, end = 12.dp, top = 72.dp)
    .widthIn(min = 300.dp, max = 430.dp)
    // Reserve a fixed safe zone for the Dock; constrain the card itself,
    // not only its padding, so the scrollable panel cannot extend over it.
    .heightIn(max = (maxH - 260.dp).coerceAtLeast(160.dp))
    .shadow(30.dp, RoundedCornerShape(30.dp)),
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xF2101D30)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .30f))
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
                switchWorkspace(number)
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
                                    focusedWindowId = openInfinityWindow("connect", windows, workspace, maxW.value, maxH.value)
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
                                                            windows[index].copy(minimized = false)
                                                        focusedWindowId = win.id
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
                                                    if (win.minimized) {
                                                        windows[index] = windows[index].copy(minimized = false)
                                                        focusedWindowId = win.id
                                                        orbitOpen = false
                                                    } else {
                                                        windows[index] = windows[index].copy(minimized = true)
                                                        if (focusedWindowId == win.id) focusedWindowId = null
                                                    }
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
                        .padding(end = 14.dp, bottom = 84.dp)
                        .widthIn(min = 270.dp, max = 350.dp)
                        .shadow(24.dp, RoundedCornerShape(26.dp)),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF0142036)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC27A).copy(alpha = .30f))
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
                                    Modifier.size(48.dp)
                                        .selectable(
                                            selected = themeChoice == id,
                                            role = Role.RadioButton,
                                            onClick = {
                                                themeChoice = id
                                                prefs.edit().putString("theme_choice", id).apply()
                                            }
                                        )
                                        .semantics { contentDescription = "Tema $id" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.size(30.dp)
                                            .background(color, CircleShape)
                                            .border(
                                                if (themeChoice == id) 3.dp else 1.dp,
                                                Color.White,
                                                CircleShape
                                            )
                                    )
                                }
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
                                switchWorkspace(if (workspace == 3) 1 else workspace + 1)
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
        Modifier
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(15.dp))
            .border(1.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(36.dp)
                .background(
                    Brush.linearGradient(listOf(tint.copy(alpha = .24f), tint.copy(alpha = .08f))),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, tint.copy(alpha = .58f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            description,
            color = Color.White.copy(.94f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .35.sp,
            maxLines = 1
        )
        Box(Modifier.width(18.dp).height(2.dp).background(tint.copy(alpha = .65f), RoundedCornerShape(2.dp)))
    }
}

private fun openInfinityWindow(
    id: String,
    windows: MutableList<DeskWindow>,
    workspace: Int,
    viewportWidth: Float,
    viewportHeight: Float
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
    val windowId = infinityWindowIdentity(id, workspace)
    if (existing >= 0) {
        windows[existing] = windows[existing].copy(minimized = false)
    } else {
        val (windowWidth, windowHeight) = infinityInitialWindowSize(
            viewportWidth,
            viewportHeight
        )
        val (windowX, windowY) = infinityInitialWindowPosition(
            windows.size,
            viewportWidth,
            viewportHeight,
            windowWidth,
            windowHeight
        )
        windows.add(
            DeskWindow(
                id = windowId,
                title = titles[id] ?: "INFINITY",
                kind = id,
                x = windowX,
                y = windowY,
                width = windowWidth,
                height = windowHeight,
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
            .zIndex(if (isFocused) 10f else 0f)
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
                .heightIn(min = 48.dp)
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
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
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

            Spacer(Modifier.width(3.dp))

            IconButton(
                onClick = onMaximize,
                modifier = Modifier
                    .size(36.dp)
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

            Spacer(Modifier.width(3.dp))

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
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
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable(role = Role.Button) {
                                            onLaunchApp(app.packageName)
                                        }
                                        .semantics { contentDescription = "Abrir aplicación ${app.label}" }
                                        .padding(vertical = 8.dp),
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
                                modifier = Modifier
                                    .widthIn(min = 48.dp)
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = themeChoice == id,
                                        role = Role.RadioButton,
                                        onClick = { onThemeChange(id) }
                                    )
                                    .semantics { contentDescription = "Tema $label" }
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
                .size(38.dp)
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
