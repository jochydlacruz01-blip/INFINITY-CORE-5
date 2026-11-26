package com.infinity.core

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.provider.DocumentsContract
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
        enableEdgeToEdge()
        setContent { Infinity08App() }
    }
}

private data class DeskItem(val id: String, val label: String, val glyph: String)

@Composable
private fun InfinityAppBadge(item: DeskItem, size: androidx.compose.ui.unit.Dp, accent: Color, iconPack: String = "infinity") {
    val identity = when (item.id) {
        "files" -> Color(0xFF48B5FF)
        "notes" -> Color(0xFFFFC66D)
        "apps" -> Color(0xFFB69CFF)
        "connect" -> Color(0xFF55D6A4)
        "settings" -> Color(0xFFFF8E78)
        "orbit" -> Color(0xFFB69CFF)
        else -> Color(0xFF56D6C9)
    }
    val appIcon = when (item.id) {
        "files" -> Icons.Filled.FolderOpen
        "notes" -> Icons.Filled.EditNote
        "apps" -> Icons.Filled.Apps
        "connect" -> Icons.Filled.Computer
        "settings" -> Icons.Filled.Palette
        "orbit" -> Icons.Filled.Apps
        else -> Icons.Filled.Info
    }
    Box(
        Modifier.size(size).shadow(14.dp, RoundedCornerShape(size * if (iconPack == "windows11") .22f else .25f))
            .background(
                Brush.linearGradient(
                    when (iconPack) {
                        "windows11" -> listOf(Color(0xFFB9E4FF), Color(0xFF1687D9), Color(0xFF07549B))
                        else -> listOf(identity.copy(.36f), Color(0xFF142A43), Color(0xFF101827))
                    }
                ), RoundedCornerShape(size * if (iconPack == "windows11") .22f else .25f)
            )
            .border(1.4.dp, if (iconPack == "windows11") Color(0xFFBDEBFF) else identity.copy(.82f), RoundedCornerShape(size * .25f)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize().padding(size * .12f).border(1.dp, Color.White.copy(.08f), RoundedCornerShape(size * .2f)))
        Icon(
            imageVector = appIcon,
            contentDescription = item.label,
            tint = if (iconPack == "windows11") Color(0xFFF4FAFF) else identity,
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
        // INFINITY FUTURE reference desktop: stable IDs preserve existing window actions.
        listOf(
            DeskItem("files", "Este Equipo", "▣"),
            DeskItem("notes", "Mis Archivos", "✎"),
            DeskItem("apps", "Papelera de Reciclaje", "◈"),
            DeskItem("about", "INFINITY START", "∞"),
            DeskItem("settings", "System Guard", "⚙"),
            DeskItem("orbit", "ORBIT", "∞"),
            DeskItem("connect", "CONNECT", "◉")
        )
    }

    var workspace by remember { mutableIntStateOf(infinityNormalizeWorkspace(prefs.getInt("active_workspace", 1))) }
    var dashboardOpen by remember { mutableStateOf(false) }
    var startOpen by remember { mutableStateOf(false) }
    var orbitOpen by remember { mutableStateOf(false) }
    var controlOpen by remember { mutableStateOf(false) }
    var controlMode by remember { mutableStateOf("personalization") }
    var controlSnapshot by remember { mutableStateOf(InfinityControlCenterSnapshot.unavailable()) }
    var advancedOpen by remember { mutableStateOf(false) }
    var contextMenuExpanded by remember { mutableStateOf(false) }
    var desktopIconsVisible by remember { mutableStateOf(prefs.getBoolean("desktop_icons_visible", true)) }
    var desktopContextState by remember {
        mutableStateOf(
            InfinityDesktopContextState(
                arrangement = runCatching {
                    InfinityIconArrangement.valueOf(prefs.getString("desktop_icon_arrangement", "MANUAL") ?: "MANUAL")
                }.getOrDefault(InfinityIconArrangement.MANUAL),
                iconsVisible = prefs.getBoolean("desktop_icons_visible", true),
                iconSizePx = prefs.getFloat("icon_size", 48f)
            )
        )
    }
    var search by remember { mutableStateOf("") }
    var startCategory by remember { mutableStateOf("all") }
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
    var iconPack by remember { mutableStateOf(infinityNormalizeDesktopTheme(prefs.getString("icon_pack", "infinity"))) }
    var backgroundPreset by remember { mutableStateOf(prefs.getString("background_preset", "nebula") ?: "nebula") }
    var desktopPreset by remember { mutableStateOf(infinityNormalizeWallpaperPreset(prefs.getString("desktop_wallpaper_preset", "futuristic"))) }
    val futuristicDesktop = desktopPreset == InfinityDesktopWallpaperPreset.FUTURISTIC
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
            dateText = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(now)
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
                    val restoredWorkspace = infinityNormalizeWorkspace(item.optInt("workspace", 1))
                    // Canonical IDs keep restored windows addressable by openInfinityWindow().
                    // Legacy or mismatched IDs previously made focus point to a nonexistent task ID.
                    val restoredId = infinityNormalizeRestoredWindowId(
                        item.optString("id"), kind, restoredWorkspace
                    )
                    // Ignore duplicate kind/workspace records instead of restoring duplicate tasks.
                    if (any { it.id == restoredId }) continue
                    add(
                        DeskWindow(
                            id = restoredId,
                            title = item.optString("title", kind).trim().take(80).ifBlank { kind },
                            kind = kind,
                            x = infinityNormalizeWindowCoordinate(item.optDouble("x", 24.0).toFloat(), 24f),
                            y = infinityNormalizeWindowCoordinate(item.optDouble("y", 100.0).toFloat(), 100f),
                            width = infinityNormalizeWindowDimension(item.optDouble("width", 370.0).toFloat(), 370f, 240f, 900f),
                            height = infinityNormalizeWindowDimension(item.optDouble("height", 330.0).toFloat(), 330f, 180f, 900f),
                            minimized = item.optBoolean("minimized", false),
                            workspace = restoredWorkspace,
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
        mutableStateOf(
            infinityResolveRestoredWindowFocus(
                prefs.getString("focused_window_id", null),
                windows.map { it.id },
                windows.associate { it.id to it.workspace },
                windows.filter { it.minimized }.map { it.id }.toSet(),
                workspace
            )
        )
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
                        prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "x"), if (workspace == 1) prefs.getFloat(infinityLegacyWorkspaceIconPositionKey(item.id, "x"), infinityDesktopIconGridOffset(
                            index, context.resources.displayMetrics.widthPixels.toFloat(),
                            116f * context.resources.displayMetrics.density, 150f * context.resources.displayMetrics.density,
                            horizontalGap = 12f * context.resources.displayMetrics.density,
                            verticalGap = 14f * context.resources.displayMetrics.density,
                            topInset = 118f * context.resources.displayMetrics.density
                        ).x) else infinityDesktopIconGridOffset(
                            index, context.resources.displayMetrics.widthPixels.toFloat(),
                            116f * context.resources.displayMetrics.density, 150f * context.resources.displayMetrics.density,
                            horizontalGap = 12f * context.resources.displayMetrics.density,
                            verticalGap = 14f * context.resources.displayMetrics.density,
                            topInset = 118f * context.resources.displayMetrics.density
                        ).x),
                        prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "y"), if (workspace == 1) prefs.getFloat(infinityLegacyWorkspaceIconPositionKey(item.id, "y"), infinityDesktopIconGridOffset(
                            index, context.resources.displayMetrics.widthPixels.toFloat(),
                            116f * context.resources.displayMetrics.density, 150f * context.resources.displayMetrics.density,
                            horizontalGap = 12f * context.resources.displayMetrics.density,
                            verticalGap = 14f * context.resources.displayMetrics.density,
                            topInset = 118f * context.resources.displayMetrics.density
                        ).y) else infinityDesktopIconGridOffset(
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

    // Reload the selected ORBIT workspace's own desktop icon arrangement.
    // Workspace 1 imports legacy coordinates once; spaces 2 and 3 start independently.
    LaunchedEffect(workspace) {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        desktopItems.forEachIndexed { index, item ->
            val fallback = infinityDesktopIconGridOffset(
                index, metrics.widthPixels.toFloat(),
                116f * density, 150f * density,
                horizontalGap = 12f * density,
                verticalGap = 14f * density,
                topInset = 118f * density
            )
            val legacyX = if (workspace == 1) {
                prefs.getFloat(infinityLegacyWorkspaceIconPositionKey(item.id, "x"), fallback.x)
            } else fallback.x
            val legacyY = if (workspace == 1) {
                prefs.getFloat(infinityLegacyWorkspaceIconPositionKey(item.id, "y"), fallback.y)
            } else fallback.y
            iconOffsets[item.id] = Offset(
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "x"), legacyX),
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "y"), legacyY)
            )
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
                    when {
                        iconPack == "windows11" -> if (lightMode)
                            listOf(Color(0xFFEAF4FF), Color(0xFFB7D8F7), Color(0xFF6FA9DE))
                            else listOf(Color(0xFF020B1A), Color(0xFF073A70), Color(0xFF06152B))
                        backgroundPreset == "punk" -> listOf(Color(0xFF24051F), Color(0xFF631B43), Color(0xFF102338))
                        backgroundPreset == "interstellar" -> listOf(Color(0xFF030713), Color(0xFF17265D), Color(0xFF46246A))
                        backgroundPreset == "aurora" -> listOf(Color(0xFF062E36), Color(0xFF174E60), Color(0xFF102238))
                        else -> if (lightMode) listOf(Color(0xFFEAF1FA), Color(0xFFD4E2F2), Color(0xFFB9CDE2)) else listOf(Color(0xFF030A17), Color(0xFF102A49), Color(0xFF08111F))
                    }
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
                        current, Offset.Zero, viewportWidth, infinityDesktopHeightAboveDock(viewportHeight, dockHeight = with(density) { 88.dp.toPx() }, dockBottomInset = with(density) { 24.dp.toPx() }, safetyGap = with(density) { 56.dp.toPx() }),
                        itemWidth = with(density) { 116.dp.toPx() },
                        itemHeight = with(density) { 150.dp.toPx() }
                    )
                    if (bounded != current) {
                        iconOffsets[item.id] = bounded
                        prefs.edit()
                            .putFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "x"), bounded.x)
                            .putFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "y"), bounded.y)
                            .apply()
                    }
                }
            }

            // Automatically displace overlapping saved icons while preserving valid placements.
            LaunchedEffect(maxW, maxH, iconOffsets.keys.toList()) {
                val viewportWidth = with(density) { maxW.toPx() }
                val viewportHeight = with(density) { infinityDesktopHeightAboveDock(maxH.toPx(), 88f * density.density, 24f * density.density, 56f * density.density) }
                val ordered = desktopItems.map { iconOffsets[it.id] ?: Offset.Zero }
                val resolved = infinityResolveIconOverlaps(
                    ordered, viewportWidth, viewportHeight,
                    itemWidth = with(density) { 116.dp.toPx() },
                    itemHeight = with(density) { 150.dp.toPx() },
                    horizontalGap = with(density) { 12.dp.toPx() },
                    verticalGap = with(density) { 14.dp.toPx() },
                    topInset = with(density) { 118.dp.toPx() }
                )
                desktopItems.forEachIndexed { index, item ->
                    val next = resolved[index]
                    if (iconOffsets[item.id] != next) {
                        iconOffsets[item.id] = next
                        prefs.edit().putFloat("${item.id}_x", next.x).putFloat("${item.id}_y", next.y).apply()
                    }
                }
            }

            // Normalize persisted windows after screen size, orientation, or density changes.
            LaunchedEffect(maxW, maxH) {
                windows.indices.forEach { index ->
                    val current = windows[index]
                    val bounds = infinityNormalizeWindowBounds(
                        current.x, current.y, current.width, current.height,
                        maxW.value, maxH.value
                    )
                    if (bounds.x != current.x || bounds.y != current.y ||
                        bounds.width != current.width || bounds.height != current.height
                    ) {
                        windows[index] = current.copy(
                            x = bounds.x, y = bounds.y,
                            width = bounds.width, height = bounds.height
                        )
                    }
                }
            }

            // The selected desktop theme is rendered by the root background above.
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

            // PACK13: render a true layered futuristic scene when no personal wallpaper is selected.
            // The scene is decorative only; all desktop controls remain live Compose elements above it.
            if (currentWallpaperBitmap == null && maxW >= 600.dp && !dashboardOpen && futuristicDesktop) {
                InfinityFuturisticScene(modifier = Modifier.fillMaxSize())
            }

            // Cabecera de marca para tablet; no sustituye los accesos ni el fondo elegido.
            if (maxW >= 600.dp && !dashboardOpen && futuristicDesktop) {
                InfinityReferenceMasthead(
                    modifier = Modifier.align(Alignment.Center)
                        .offset(y = maxH * 0.035f)
                )
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

            // Compact workspace branding is suppressed on the reference desktop to preserve its clean composition.
            if (!(maxW >= 600.dp && futuristicDesktop && !dashboardOpen)) {
            Column(Modifier.align(Alignment.TopStart).padding(
                start = 22.dp,
                top = 18.dp
            )) {
                Text(
                    when (iconPack) {
                        "windows11" -> "Windows 11"
                        else -> "INFINITY"
                    },
                    color = foreground.copy(alpha = .94f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
                Text(
                    when (iconPack) {
                        "windows11" -> "DESKTOP  •  ESPACIO $workspace"
                        else -> "A U R O R A  NEXT  •  WORKSPACE $workspace"
                    },
                    color = accent,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (iconPack) {
                        "windows11" -> "PERSONAL COMPUTER ENVIRONMENT"
                        else -> "DESKTOP ENVIRONMENT  /  ANDROID"
                    },
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
                                .semantics { contentDescription = infinityWorkspaceAccessibilityLabel(targetWorkspace) }
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

            }
            if (maxW >= 600.dp && !dashboardOpen && futuristicDesktop) {
                InfinityReferenceClockPanel(
                    clock = clockText,
                    date = dateText,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 28.dp)
                )
            } else {
                Column(
                    Modifier.align(Alignment.TopEnd).padding(18.dp)
                        .clip(RoundedCornerShape(if (maxW >= 600.dp) 24.dp else 20.dp))
                        .background(if (iconPack == "windows11") Color(0xCC173653) else Color(0xAA101D30))
                        .border(1.dp, if (iconPack == "windows11") Color(0xFF8BCBFA).copy(.55f) else accent.copy(alpha = .24f), RoundedCornerShape(if (maxW >= 600.dp) 24.dp else 20.dp))
                        .padding(horizontal = if (maxW >= 600.dp) 22.dp else 16.dp, vertical = if (maxW >= 600.dp) 16.dp else 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(clockText, color = Color.White, fontSize = if (maxW >= 600.dp) 42.sp else 24.sp, fontWeight = FontWeight.Light)
                    Text(dateText, color = InfinityMuted, fontSize = if (maxW >= 600.dp) 14.sp else 10.sp)
                }
            }

            // Marca del escritorio en tablet, situada sobre el Dock y sin tapar accesos.
            if (maxW >= 600.dp && futuristicDesktop) {
                InfinityDesktopBrandBadge(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 104.dp),
                    onClick = {
                        startOpen = true
                        search = ""
                        startCategory = "all"
                        dashboardOpen = false
                        orbitOpen = false
                        controlOpen = false
                        advancedOpen = false
                    }
                )
            }

            Box(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 18.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xCC173653))
                        .border(1.dp, accent.copy(alpha = .55f), RoundedCornerShape(14.dp))
                        .clickable { contextMenuExpanded = true }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋮", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                InfinityDesktopContextMenu(
                    expanded = contextMenuExpanded,
                    iconsVisible = desktopIconsVisible,
                    onDismiss = { contextMenuExpanded = false },
                    onAction = { action ->
                        val densityValue = density.density
                        val records = desktopItems.mapIndexed { index, item ->
                            InfinityDesktopIconRecord(
                                id = item.id,
                                label = item.label,
                                type = when (item.id) {
                                    "files" -> "folder"
                                    "apps", "connect" -> "app"
                                    else -> "system"
                                },
                                order = index
                            )
                        }
                        val plan = infinityPlanDesktopContextCommand(
                            state = desktopContextState,
                            action = action,
                            icons = records,
                            viewportWidth = with(density) { maxW.toPx() },
                            itemWidth = 116f * densityValue,
                            itemHeight = 150f * densityValue,
                            leftInset = 16f * densityValue,
                            topInset = 118f * densityValue
                        )
                        desktopContextState = plan.nextState
                        prefs.edit()
                            .putString("desktop_icon_arrangement", plan.nextState.arrangement.name)
                            .putBoolean("desktop_icons_visible", plan.nextState.iconsVisible)
                            .putFloat("icon_size", plan.nextState.iconSizePx)
                            .apply()
                        desktopIconsVisible = plan.nextState.iconsVisible
                        iconSize = infinityNormalizeIconSize(plan.nextState.iconSizePx)
                        plan.placements?.forEach { (id, position) ->
                            iconOffsets[id] = position
                            prefs.edit().putFloat(infinityWorkspaceIconPositionKey(workspace, id, "x"), position.x).putFloat(infinityWorkspaceIconPositionKey(workspace, id, "y"), position.y).apply()
                        }
                        if (plan.requestWallpaperPicker) wallpaperPicker.launch(arrayOf("image/*"))
                        if (plan.requestAppearancePanel) {
                            focusedWindowId = openInfinityWindow("settings", windows, workspace, maxW.value, maxH.value)
                        }
                        if (plan.requestWorkspacePanel) dashboardOpen = true
                    }
                )
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
            } else if (desktopIconsVisible) {
                val referenceRail = maxW >= 600.dp && futuristicDesktop
                val railLayout = if (referenceRail) infinityReferenceRailLayout(maxW.value, maxH.value, desktopItems.size) else null
                desktopItems.forEach { item ->
                    val itemIndex = desktopItems.indexOf(item)
                    val offset = if (railLayout != null) {
                        with(density) {
                            Offset(
                                railLayout.leftDp.dp.toPx(),
                                (railLayout.topDp + itemIndex * railLayout.slotHeightDp).dp.toPx()
                            )
                        }
                    } else iconOffsets[item.id] ?: Offset(22f, 90f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                            .width((railLayout?.itemWidthDp ?: 108f).dp)
                            .clip(RoundedCornerShape(if (iconPack == "windows11") 12.dp else 18.dp))
                            .background(
                                if (maxW >= 600.dp && futuristicDesktop) Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                else if (iconPack == "windows11") Brush.verticalGradient(
                                    listOf(Color(0x552D5A82), Color(0x552D5A82))
                                ) else Brush.verticalGradient(
                                    listOf(Color(0x88366A91), Color(0x66304770), Color(0x88202A49))
                                )
                            )
                            .border(
                                if (maxW >= 600.dp && futuristicDesktop) 0.dp else 1.dp,
                                if (maxW >= 600.dp && futuristicDesktop) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                else if (iconPack == "windows11") Brush.linearGradient(
                                    listOf(Color(0x668BCBFA), Color(0x668BCBFA))
                                ) else Brush.linearGradient(
                                    listOf(Color(0xAA78D9FF), accent.copy(alpha = 0.42f), Color(0x667D78FF))
                                ),
                                RoundedCornerShape(if (iconPack == "windows11") 12.dp else 18.dp)
                            )
                            .padding(if (referenceRail) 2.dp else 8.dp)
                            .pointerInput(item.id, referenceRail) {
                                if (!referenceRail) detectDragGestures { change, drag ->
                                    change.consume()
                                    val viewportWidth = with(density) { maxW.toPx() }
                                    val safeDesktopHeight = with(density) {
                                        infinityDesktopHeightAboveDock(
                                            maxH.toPx(),
                                            dockHeight = 88.dp.toPx(),
                                            dockBottomInset = 24.dp.toPx(),
                                            safetyGap = 56.dp.toPx()
                                        )
                                    }
                                    val dragged = infinityBoundIconOffset(
                                        iconOffsets[item.id] ?: Offset(22f, 90f),
                                        Offset(drag.x, drag.y),
                                        viewportWidth,
                                        safeDesktopHeight,
                                        itemWidth = with(density) { 116.dp.toPx() },
                                        itemHeight = with(density) { 150.dp.toPx() }
                                    )
                                    iconOffsets[item.id] = dragged
                                    // Resolve collisions immediately after a drop, not only on screen rotation.
                                    val orderedOffsets = desktopItems.map { iconOffsets[it.id] ?: Offset.Zero }
                                    val resolvedOffsets = infinityResolveIconOverlaps(
                                        orderedOffsets,
                                        viewportWidth,
                                        safeDesktopHeight,
                                        itemWidth = with(density) { 116.dp.toPx() },
                                        itemHeight = with(density) { 150.dp.toPx() },
                                        horizontalGap = with(density) { 12.dp.toPx() },
                                        verticalGap = with(density) { 14.dp.toPx() },
                                        topInset = with(density) { 118.dp.toPx() }
                                    )
                                    desktopItems.forEachIndexed { index, desktopItem ->
                                        val position = resolvedOffsets[index]
                                        iconOffsets[desktopItem.id] = position
                                        prefs.edit()
                                            .putFloat(infinityWorkspaceIconPositionKey(workspace, desktopItem.id, "x"), position.x)
                                            .putFloat(infinityWorkspaceIconPositionKey(workspace, desktopItem.id, "y"), position.y)
                                            .apply()
                                    }
                                }
                            }
                            .clickable {
                                if (item.id == "orbit") {
                                    orbitOpen = !orbitOpen
                                    startOpen = false
                                    controlOpen = false
                                    advancedOpen = false
                                } else {
                                    focusedWindowId = openInfinityWindow(item.id, windows, workspace, maxW.value, maxH.value)
                                }
                            }
                            .padding(if (referenceRail) 2.dp else 6.dp)
                    ) {
                        InfinityAppBadge(
                            item,
                            (railLayout?.iconSizeDp ?: (iconSize + 14f)).dp,
                            accent,
                            iconPack
                        )
                        Spacer(Modifier.height(if (referenceRail) 2.dp else 7.dp))
                        if (railLayout?.showLabels != false) Text(
                            item.label,
                            modifier = Modifier
                                .shadow(10.dp, RoundedCornerShape(12.dp))
                                .background(if (iconPack == "windows11") Color(0xCC173653) else Color(0xDD101D30), RoundedCornerShape(if (iconPack == "windows11") 8.dp else 12.dp))
                                .border(1.dp, if (iconPack == "windows11") Color(0xFF8BCBFA).copy(alpha = 0.38f) else accent.copy(alpha = 0.38f), RoundedCornerShape(if (iconPack == "windows11") 8.dp else 12.dp))
                                .padding(
                                    horizontal = if (referenceRail) 3.dp else 9.dp,
                                    vertical = if (referenceRail) 1.dp else 6.dp
                                ),
                            color = Color.White,
                            fontSize = if (referenceRail) 7.sp else 10.sp,
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
                        desktopTheme = iconPack,
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
                            focusedWindowId = infinityResolveFocusAfterWindowDeactivation(
                                win.id,
                                focusedWindowId,
                                windows.map { it.id },
                                windows.associate { it.id to it.workspace },
                                windows.filter { it.minimized }.map { it.id }.toSet(),
                                workspace
                            )
                        },
                        onMinimize = {
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) windows[index] = windows[index].copy(minimized = true)
                            focusedWindowId = infinityResolveFocusAfterWindowDeactivation(
                                win.id,
                                focusedWindowId,
                                windows.map { it.id },
                                windows.associate { it.id to it.workspace },
                                windows.filter { it.minimized }.map { it.id }.toSet(),
                                workspace
                            )
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
                            if (index >= 0 && infinityWindowCanManipulateGeometry(windows[index].maximized, maxW.value)) {
                                val current = windows[index]
                                // Compose drag deltas are pixels; persisted window geometry is dp.
                                val deltaXdp = infinityGestureDeltaToDp(dx, density.density)
                                val deltaYdp = infinityGestureDeltaToDp(dy, density.density)
                                val boundedPosition = infinityClampWindowPosition(
                                    current.x + deltaXdp,
                                    current.y + deltaYdp,
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
                            if (index >= 0 && infinityWindowCanManipulateGeometry(windows[index].maximized, maxW.value)) {
                                val current = windows[index]
                                // Resize gestures also report pixels; dimensions and viewport are dp.
                                val deltaWidthDp = infinityGestureDeltaToDp(dx, density.density)
                                val deltaHeightDp = infinityGestureDeltaToDp(dy, density.density)

                                val availableWidth =
                                    (maxW.value - current.x).coerceAtLeast(1f)

                                val availableHeight =
                                    (maxH.value - current.y).coerceAtLeast(1f)

                                windows[index] = current.copy(
                                    width = infinityResizeDimension(current.width, deltaWidthDp, availableWidth, 280f),
                                    height = infinityResizeDimension(current.height, deltaHeightDp, availableHeight, 190f)
                                )
                            }
                        },
                     onFocus = {
                         val activeIndex = windows.indexOfFirst { it.id == win.id }
                         if (activeIndex >= 0) {
                             val orderedWindows = infinityBringItemToFront(
                                 windows.toList(),
                                 windows[activeIndex]
                             ) { it.id }
                             if (orderedWindows.map { it.id } != windows.map { it.id }) {
                                 windows.clear()
                                 windows.addAll(orderedWindows)
                             }
                         }
                         focusedWindowId = win.id
                     },
                        showGeometryControls = infinityWindowGeometryControlsVisible(maxW.value)
                    )
                }
            }

          
            // DOCK INFINITY 0.10 — DISEÑO MEJORADO
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(20f)
                    // On narrow screens, keep every Dock action reachable instead of clipping
                    // the trailing controls and task buttons beyond the viewport.
                    .horizontalScroll(rememberScrollState())
                    .then(if (iconPack == "windows11" || (maxW >= 600.dp && futuristicDesktop)) Modifier.fillMaxWidth() else Modifier)
                    .padding(
                        horizontal = if (iconPack == "windows11" || (maxW >= 600.dp && futuristicDesktop)) 0.dp else 12.dp,
                        vertical = if (iconPack == "windows11" || (maxW >= 600.dp && futuristicDesktop)) 0.dp else 12.dp
                    )
                    .shadow(
                        if (iconPack == "windows11") 0.dp else 26.dp,
                        RoundedCornerShape(if (iconPack == "windows11") 0.dp else 20.dp)
                    )
                    .background(
                        when {
                            maxW >= 600.dp && futuristicDesktop -> Color(0xF208142A)
                            iconPack == "windows11" -> Color(0xF20B1829)
                            else -> Color(0xF016263D)
                        },
                        if (maxW >= 600.dp && futuristicDesktop) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else RoundedCornerShape(20.dp)
                    )
                    .border(
                        if (maxW >= 600.dp && futuristicDesktop) 1.dp else if (iconPack == "windows11") 0.dp else 1.dp,
                        if (maxW >= 600.dp && futuristicDesktop) Color(0xAA29CFFF)
                        else if (iconPack == "windows11") Color.Transparent else Color(0xFF6686B5),
                        if (maxW >= 600.dp && futuristicDesktop) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else RoundedCornerShape(20.dp)
                    )
                    .padding(
                        horizontal = if (iconPack == "windows11") 12.dp else if (maxW < 600.dp) 6.dp else 18.dp,
                        vertical = if (maxW >= 600.dp && futuristicDesktop) 8.dp else if (iconPack == "windows11") 7.dp else 10.dp
                    )
                    .heightIn(min = if (maxW >= 600.dp && futuristicDesktop) 72.dp else 60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (maxW >= 600.dp && futuristicDesktop) Arrangement.SpaceEvenly
                else if (iconPack == "windows11") Arrangement.Center
                else Arrangement.spacedBy(
                    when {
                        maxW < 380.dp -> 5.dp
                        maxW < 600.dp -> 8.dp
                        else -> 10.dp
                    }
                )
            ) {
                DockAction(if (futuristicDesktop && maxW >= 600.dp) "▦" else "⌂", "HOME", accent, iconPack, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp) {
                    dashboardOpen = true
                    startOpen = false
                    orbitOpen = false
                    controlOpen = false
                    advancedOpen = false
                }

                DockAction(if (futuristicDesktop && maxW >= 600.dp) "⌕" else "✦", "START", accent, iconPack, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp) {
                    startOpen = !startOpen
                    if (startOpen) {
                        search = ""
                        startCategory = "all"
                    }
                    orbitOpen = false
                    controlOpen = false
                    advancedOpen = false
                }
                if (maxW >= 600.dp && futuristicDesktop) {
                    Row(
                        Modifier.widthIn(min = 150.dp, max = 260.dp).height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF122640))
                            .border(1.dp, Color(0xFF38C8FF).copy(.55f), RoundedCornerShape(14.dp))
                            .clickable {
                                startOpen = true
                                search = ""
                                startCategory = "all"
                                orbitOpen = false
                                controlOpen = false
                                advancedOpen = false
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color(0xFF62D8FF), modifier = Modifier.size(19.dp))
                        Text("Buscar en INFINITY...", color = Color(0xFFB8D5EF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                DockAction(if (futuristicDesktop && maxW >= 600.dp) "∞" else "◎", "ORBIT", Color(0xFFB69CFF), iconPack, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp) {
                    // ORBIT is an explicit launcher command: repeated activation must never
                    // cancel the opening transition while accessibility dispatch is settling.
                    orbitOpen = true
                    startOpen = false
                    controlOpen = false
                    advancedOpen = false
                }

                DockAction(if (futuristicDesktop && maxW >= 600.dp) "⬡" else "☷", "CONTROL", Color(0xFFFFC27A), iconPack, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp) {
                    controlOpen = !controlOpen
                    if (controlOpen) controlMode = "personalization"
                    startOpen = false
                    orbitOpen = false
                    advancedOpen = false
                }
DockAction(if (futuristicDesktop && maxW >= 600.dp) "⚙" else "▤", "ADVANCED", accent, iconPack, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp) {
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
                    val allTaskIds = infinityDockWindowIds(
                        workspaceWindows.map { it.id },
                        workspaceWindows.associate { it.id to it.minimized }
                    )
                    val visibleTaskLimit = infinityDockVisibleTaskLimit(maxW.value)
                    val visibleTaskIds = allTaskIds.takeLast(visibleTaskLimit)
                    visibleTaskIds.forEach { taskId ->
                        val task = workspaceWindows.firstOrNull { it.id == taskId } ?: return@forEach
                        DockAction(if (task.minimized) "□" else "▣", infinityDockLabel(task.title), accent, iconPack, compact = maxW < 390.dp) {
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
                    val hiddenTaskCount = infinityDockHiddenTaskCount(allTaskIds.size, visibleTaskIds.size)
                    if (hiddenTaskCount > 0) {
                        DockAction("⋯", "MÁS VENTANAS: $hiddenTaskCount", accent, iconPack, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp) {
                            orbitOpen = true
                            dashboardOpen = false
                            startOpen = false
                            controlOpen = false
                            advancedOpen = false
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) {
                            controlOpen = !controlOpen
                            if (controlOpen) controlMode = "personalization"
                            startOpen = false
                            orbitOpen = false
                            advancedOpen = false
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Hora y fecha. Abrir centro de control"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        clockText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        dateText,
                        color = Color(0xFFB7CBE7),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }


            // INFINITY START: búsqueda y catálogo de aplicaciones.
            if (startOpen) {
                Card(
                    Modifier.then(if (iconPack == "windows11") Modifier.align(Alignment.BottomCenter) else Modifier.align(Alignment.BottomStart))
                        .zIndex(15f)
                        .fillMaxWidth(0.94f)
                        .widthIn(max = 420.dp)
                        .padding(bottom = 84.dp)
                        .heightIn(max = (maxH - 116.dp).coerceAtLeast(220.dp))
                        .animateContentSize()
                        .shadow(28.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(if (iconPack == "windows11") 16.dp else 28.dp),
                    colors = CardDefaults.cardColors(containerColor = when (iconPack) {
                        "windows11" -> Color(0xF0254563)
                        else -> Color(0xF0142036)
                    }),
                    border = androidx.compose.foundation.BorderStroke(1.dp, when (iconPack) {
                        "windows11" -> Color(0xFF8BCBFA).copy(alpha = .58f)
                        else -> accent.copy(alpha = .28f)
                    })
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(if (iconPack == "windows11") "INICIO" else "INFINITY START", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(if (iconPack == "windows11") "Aplicaciones ancladas y recomendadas" else "Tus herramientas y aplicaciones", color = InfinityMuted, fontSize = 10.sp)
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
                                    Modifier.fillMaxWidth()
                                        .testTag("infinity-start-app-${item.id}")
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (item.id == "orbit") {
                                                orbitOpen = true
                                            } else {
                                                focusedWindowId = openInfinityWindow(item.id, windows, workspace, maxW.value, maxH.value)
                                            }
                                            startOpen = false
                                        }.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    InfinityAppBadge(item, 34.dp, accent, iconPack)
                                    Text(item.label, color = Color.White, modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "all" to "Todas",
                                "productivity" to "Productividad",
                                "communication" to "Comunicación",
                                "media" to "Multimedia",
                                "games" to "Juegos",
                                "system" to "Sistema"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = startCategory == key,
                                    onClick = { startCategory = key },
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                        Text(
                            "APLICACIONES ANDROID (${installedApps.count {
                                InfinityStartCatalogPolicy.matches(it.label, it.packageName, search, startCategory)
                            }})",
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 230.dp)) {
                            items(
                                installedApps.filter {
                                    InfinityStartCatalogPolicy.matches(
                                        it.label, it.packageName, search, startCategory
                                    )
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
                val orbitRadius = if (iconPack == "windows11") 16.dp else 24.dp
                val orbitSurface = when (iconPack) {
                    "windows11" -> Color(0xF0273D58)
                    else -> Color(0xF2101D30)
                }
                val orbitBorder = when (iconPack) {
                    "windows11" -> Color(0xFF8BCBFA).copy(alpha = .62f)
                    else -> accent.copy(alpha = .30f)
                }
                val orbitTile = when (iconPack) {
                    "windows11" -> Color(0xFF345574)
                    else -> Color(0xFF192D47)
                }
                Card(
                 Modifier.align(Alignment.TopCenter)
    .zIndex(15f)
    .padding(start = 12.dp, end = 12.dp, top = 72.dp)
    .widthIn(max = 430.dp)
    // Reserve a fixed safe zone for the Dock; constrain the card itself,
    // not only its padding, so the scrollable panel cannot extend over it.
    .heightIn(max = (maxH - 260.dp).coerceAtLeast(160.dp))
    .shadow(30.dp, RoundedCornerShape(orbitRadius)),
                    shape = RoundedCornerShape(orbitRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = orbitSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, orbitBorder)
                ) {
                    Column(
    Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .testTag("infinity-orbit-panel")
        .semantics { contentDescription = "Panel ORBIT" }
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
                .testTag("infinity-orbit-workspace-$number")
                .semantics(mergeDescendants = true) {
                    contentDescription = "Espacio $number, " + when (number) {
                        1 -> "Principal"
                        2 -> "Trabajo"
                        else -> "Personal"
                    }
                }
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
                                    .clip(RoundedCornerShape(if (iconPack == "windows11") 9.dp else 14.dp))
                                    .background(orbitTile)
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
                                            .clip(RoundedCornerShape(if (iconPack == "windows11") 8.dp else 12.dp))
                                            .background(orbitTile)
                                            .border(
                                                1.dp,
                                                accent.copy(alpha = 0.20f),
                                                RoundedCornerShape(if (iconPack == "infinity") 18.dp else if (iconPack == "windows11") 8.dp else 12.dp)
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
                                                        focusedWindowId = infinityResolveFocusAfterWindowDeactivation(
                                                            win.id,
                                                            focusedWindowId,
                                                            windows.map { it.id },
                                                            windows.associate { it.id to it.workspace },
                                                            windows.filter { it.minimized }.map { it.id }.toSet(),
                                                            workspace
                                                        )
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
                                                focusedWindowId = infinityResolveFocusAfterWindowDeactivation(
                                                    win.id,
                                                    focusedWindowId,
                                                    windows.map { it.id },
                                                    windows.associate { it.id to it.workspace },
                                                    windows.filter { it.minimized }.map { it.id }.toSet(),
                                                    workspace
                                                )
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


                        TextButton(
                            onClick = {
                                Log.d("INFINITY_TEST_DIAGNOSTIC", "ORBIT close callback invoked; orbitOpen before close=$orbitOpen")
                                orbitOpen = false
                                startOpen = false
                                controlOpen = false
                                advancedOpen = false
                            },
                            modifier = Modifier.testTag("infinity-orbit-close")
                        ) {
                            Text("CERRAR ORBIT", color = accent, fontSize = 10.sp)
                        }
                    }
                }
            }
 

            // INFINITY CONTROL CENTER: personalización nativa y controles Android reales.
            if (controlOpen && controlMode == "device") {
                LaunchedEffect(controlOpen, controlMode) {
                    controlSnapshot = InfinityControlCenterSnapshot.read(context)
                }
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .zIndex(16f)
                        .padding(end = 14.dp, top = 76.dp, bottom = 112.dp)
                        .widthIn(max = infinityControlDevicePanelMaxWidthDp(maxW.value).dp)
                        .fillMaxWidth()
                        .heightIn(max = infinityControlPanelMaxHeightDp(maxH.value).dp)
                ) {
                    InfinityControlCenterPanel(
                        snapshot = controlSnapshot,
                        onDestinationClick = { destination ->
                            if (destination == InfinityControlDestination.PERSONALIZATION) {
                                controlMode = "personalization"
                            } else {
                                val opened = InfinityControlCenterActions.openSettings(context, destination)
                                if (opened) {
                                    controlOpen = false
                                } else {
                                    Toast.makeText(
                                        context,
                                        "No se pudieron abrir los ajustes de Android.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onBrightnessChange = { value ->
                            val updated = InfinityControlCenterActions.setBrightness(context, value)
                            if (updated) {
                                controlSnapshot = InfinityControlCenterSnapshot.read(context)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Android no permitió cambiar el brillo.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onRequestBrightnessAccess = {
                            val opened = InfinityControlCenterActions.requestBrightnessAccess(context)
                            if (opened) controlOpen = false
                            else Toast.makeText(context, "No se pudo abrir el permiso de brillo de Android.", Toast.LENGTH_LONG).show()
                        },
                        onVolumeChange = { value ->
                            val updated = InfinityControlCenterActions.setMediaVolumePercent(context, value)
                            if (updated) {
                                controlSnapshot = InfinityControlCenterSnapshot.read(context)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Android no permitió cambiar el volumen multimedia.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onClose = { controlOpen = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (controlOpen) {
                val controlRadius = if (iconPack == "windows11") 16.dp else 24.dp
                val controlSurface = when (iconPack) {
                    "windows11" -> Color(0xF0273D58)
                    else -> Color(0xF0142036)
                }
                val controlBorder = when (iconPack) {
                    "windows11" -> Color(0xFF8BCBFA).copy(alpha = .65f)
                    else -> Color(0xFFFFC27A).copy(alpha = .30f)
                }
                Card(
                    Modifier.align(Alignment.TopEnd)
                        .zIndex(15f)
                        .padding(end = 14.dp, top = 76.dp, bottom = 112.dp)
                        .widthIn(max = infinityControlPanelMaxWidthDp(maxW.value).dp)
                        .heightIn(max = (maxH - 200.dp).coerceAtLeast(220.dp))
                        .shadow(24.dp, RoundedCornerShape(controlRadius)),
                    shape = RoundedCornerShape(controlRadius),
                    colors = CardDefaults.cardColors(containerColor = controlSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, controlBorder)
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text("CONTROL CENTER", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text("Personaliza tu espacio", color = InfinityMuted, fontSize = 10.sp)
                        OutlinedButton(
                            onClick = {
                                controlMode = "device"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Controles del dispositivo") }
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
                        Text("ESTILO DE ESCRITORIO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("infinity" to "INFINITY", "windows11" to "WINDOWS 11").forEach { (pack, label) ->
                                FilterChip(selected = iconPack == pack, onClick = { val normalizedPack = infinityNormalizeDesktopTheme(pack); iconPack = normalizedPack; prefs.edit().putString("icon_pack", normalizedPack).apply() }, label = { Text(label) })
                            }
                        }
                        Text("ESTILO DEL ESCRITORIO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                InfinityDesktopWallpaperPreset.LEGACY,
                                InfinityDesktopWallpaperPreset.FUTURISTIC
                            ).forEach { preset ->
                                FilterChip(
                                    selected = desktopPreset == preset,
                                    onClick = {
                                        desktopPreset = preset
                                        prefs.edit().putString("desktop_wallpaper_preset", preset.id).apply()
                                    },
                                    label = { Text(if (preset == InfinityDesktopWallpaperPreset.FUTURISTIC) "INFINITY Futurista" else "INFINITY clásico") }
                                )
                            }
                        }
                        Text("FONDOS", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("nebula" to "Nebula", "interstellar" to "Interestelar", "punk" to "Punk", "aurora" to "Aurora").forEach { (preset, label) ->
                                FilterChip(selected = backgroundPreset == preset, onClick = { backgroundPreset = preset; prefs.edit().putString("background_preset", preset).apply(); wallpaperUri = null; prefs.edit().remove("wallpaper_uri").apply() }, label = { Text(label) })
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
                        .align(Alignment.TopEnd)
                        .zIndex(15f)
                        .padding(top = 68.dp, end = 10.dp, bottom = 104.dp)
                        .widthIn(max = 430.dp)
                        .fillMaxWidth()
                        .heightIn(max = (maxH - 200.dp).coerceAtLeast(220.dp))
                ) {
                    InfinityAdvancedPanel(
                        onClose = { advancedOpen = false },
                        desktopTheme = iconPack
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
    desktopTheme: String = "infinity",
    compact: Boolean = false,
    referenceStyle: Boolean = false,
    onClick: () -> Unit
) {
    val actionRadius = if (desktopTheme == "windows11") 9.dp else 14.dp
    val actionSurface = if (referenceStyle) Color.Transparent else if (desktopTheme == "windows11") Color(0xFF345574).copy(alpha = .48f) else Color.White.copy(alpha = .045f)
    Column(
        Modifier
            // Tablet reference dock keeps every primary action comfortably touchable.
            .widthIn(min = if (referenceStyle) 48.dp else if (compact) 40.dp else 48.dp)
            .clip(RoundedCornerShape(actionRadius))
            .background(actionSurface, RoundedCornerShape(actionRadius))
            .then(if (referenceStyle) Modifier else Modifier.border(1.dp, tint.copy(alpha = .22f), RoundedCornerShape(actionRadius)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (referenceStyle) 4.dp else if (compact) 2.dp else 5.dp, vertical = if (referenceStyle) 4.dp else if (compact) 4.dp else 6.dp)
            // Keep the test/accessibility identity on the same node that owns the real click action.
            .testTag("infinity-dock-${description.lowercase()}")
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(if (referenceStyle) 40.dp else if (compact) 30.dp else 36.dp)
                .background(
                    Brush.linearGradient(listOf(tint.copy(alpha = .24f), tint.copy(alpha = .08f))),
                    RoundedCornerShape(if (desktopTheme == "windows11") 8.dp else 12.dp)
                )
                .border(1.dp, tint.copy(alpha = .58f), RoundedCornerShape(if (desktopTheme == "windows11") 8.dp else 12.dp)),
            contentAlignment = Alignment.Center
        ) {
            val dockIcon = if (referenceStyle) when (description) {
                "HOME" -> Icons.Filled.Apps
                "START" -> Icons.Filled.Search
                "CONTROL" -> Icons.Filled.Computer
                "ADVANCED" -> Icons.Filled.Palette
                else -> null
            } else null
            if (dockIcon != null) {
                Icon(
                    imageVector = dockIcon,
                    contentDescription = description,
                    tint = tint,
                    modifier = Modifier.size(23.dp)
                )
            } else {
                Text(glyph, color = tint, fontSize = if (referenceStyle) 29.sp else 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!compact && !referenceStyle) {
            Text(
                description,
                color = Color.White.copy(.94f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .35.sp,
                maxLines = 1
            )
        }
        if (!referenceStyle) Box(Modifier.width(if (compact) 12.dp else 18.dp).height(2.dp).background(tint.copy(alpha = .65f), RoundedCornerShape(2.dp)))
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
    var folderPath by remember(rootUri) {
        mutableStateOf(
            rootUri?.let {
                listOf(DocumentsContract.getTreeDocumentId(it) to "Inicio")
            } ?: emptyList()
        )
    }
    var refreshVersion by remember(rootUri) { mutableIntStateOf(0) }
    var entries by remember(rootUri, currentDocumentId) { mutableStateOf<List<ExplorerEntry>>(emptyList()) }
    var loading by remember(rootUri, currentDocumentId) { mutableStateOf(false) }
    var error by remember(rootUri, currentDocumentId) { mutableStateOf<String?>(null) }
    var fileSearch by remember(rootUri, currentDocumentId) { mutableStateOf("") }
    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var createFolderRequest by remember { mutableStateOf<Pair<String, String>?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var actionEntry by remember { mutableStateOf<ExplorerEntry?>(null) }
    var previewTarget by remember { mutableStateOf<Triple<Uri, String, String?>?>(null) }
    var pendingTransfer by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var transferRequest by remember { mutableStateOf<Pair<Triple<String, String, Boolean>, String>?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }
    var renameDocumentId by remember { mutableStateOf<String?>(null) }
    var renameRequest by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteRequest by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<ExplorerEntry?>(null) }
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

    LaunchedEffect(createFolderRequest, rootUri) {
        val request = createFolderRequest
        val tree = rootUri
        if (request != null && tree != null) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, request.second)
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        request.first
                    ) ?: throw IllegalStateException("El proveedor no pudo crear la carpeta.")
                }
            }
            createFolderRequest = null
            if (result.isSuccess) {
                operationMessage = "Carpeta creada correctamente."
                refreshVersion++
            } else {
                operationMessage = result.exceptionOrNull()?.message ?: "No se pudo crear la carpeta."
            }
        }
    }

    LaunchedEffect(renameRequest, rootUri) {
        val request = renameRequest
        val tree = rootUri
        if (request != null && tree != null) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, request.second)
                    DocumentsContract.renameDocument(context.contentResolver, uri, request.first)
                        ?: throw IllegalStateException("El proveedor no pudo renombrar el elemento.")
                }
            }
            renameRequest = null
            operationMessage = if (result.isSuccess) "Elemento renombrado correctamente."
                else result.exceptionOrNull()?.message ?: "No se pudo renombrar el elemento."
            if (result.isSuccess) refreshVersion++
        }
    }

    LaunchedEffect(deleteRequest, rootUri) {
        val documentId = deleteRequest
        val tree = rootUri
        if (documentId != null && tree != null) {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
                    if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                        throw IllegalStateException("El proveedor no pudo eliminar el elemento.")
                    }
                }
            }
            deleteRequest = null
            operationMessage = if (result.isSuccess) "Elemento eliminado correctamente."
                else result.exceptionOrNull()?.message ?: "No se pudo eliminar el elemento."
            if (result.isSuccess) refreshVersion++
        }
    }

    LaunchedEffect(transferRequest, rootUri) {
        val request = transferRequest
        val tree = rootUri
        if (request != null && tree != null) {
            val (source, destinationDocumentId) = request
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val validationError = InfinityFilesTransferPolicy.validate(
                        sourceDocumentId = source.first,
                        sourceParentDocumentId = source.second,
                        destinationDocumentId = destinationDocumentId
                    )
                    if (validationError != null) {
                        throw IllegalArgumentException(validationError)
                    }
                    val sourceUri = DocumentsContract.buildDocumentUriUsingTree(tree, source.first)
                    val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(tree, source.second)
                    val destinationUri = DocumentsContract.buildDocumentUriUsingTree(tree, destinationDocumentId)
                    val transferred = if (source.third) {
                        DocumentsContract.moveDocument(
                            context.contentResolver, sourceUri, sourceParentUri, destinationUri
                        )
                    } else {
                        DocumentsContract.copyDocument(
                            context.contentResolver, sourceUri, destinationUri
                        )
                    }
                    transferred ?: throw IllegalStateException(
                        "El proveedor de almacenamiento no admite esta operación o no pudo completarla."
                    )
                }
            }
            transferRequest = null
            pendingTransfer = null
            operationMessage = if (result.isSuccess) {
                if (source.third) "Archivo movido correctamente." else "Copia creada correctamente."
            } else {
                result.exceptionOrNull()?.message
                    ?: "No se pudo completar la operación. Este proveedor puede no admitir copiar o mover."
            }
            if (result.isSuccess) refreshVersion++
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
                    onClick = { showCreateFolder = true },
                    enabled = !loading
                ) { Text("+ Carpeta") }
                OutlinedButton(
                    onClick = { refreshVersion++ },
                    enabled = !loading
                ) { Text(if (loading) "Actualizando…" else "Actualizar") }
            }
            if (rootUri != null && folderPath.size > 1) {
                OutlinedButton(
                    onClick = {
                        folderPath = folderPath.dropLast(1)
                        currentDocumentId = folderPath.last().first
                    }
                ) { Text("‹ Atrás") }
            }
        }
        if (rootUri != null && folderPath.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                folderPath.forEachIndexed { index, level ->
                    Text(
                        level.second,
                        color = if (index == folderPath.lastIndex) accent else InfinityMuted,
                        fontSize = 10.sp,
                        fontWeight = if (index == folderPath.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable {
                            folderPath = folderPath.take(index + 1)
                            currentDocumentId = level.first
                        }
                    )
                    if (index < folderPath.lastIndex) Text("›", color = InfinityMuted, fontSize = 10.sp)
                }
            }
        }
        operationMessage?.let { message ->
            Text(message, color = accent, fontSize = 10.sp)
        }

        pendingTransfer?.let { transfer ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (transfer.third) "Mover archivo: navega al destino y confirma aquí."
                    else "Copiar archivo: navega al destino y confirma aquí.",
                    color = accent,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    enabled = !loading && currentDocumentId != null,
                    onClick = {
                        val destination = currentDocumentId
                        if (destination != null) {
                            val validationError = InfinityFilesTransferPolicy.validate(
                                sourceDocumentId = transfer.first,
                                sourceParentDocumentId = transfer.second,
                                destinationDocumentId = destination
                            )
                            if (validationError != null) {
                                operationMessage = validationError
                            } else {
                                transferRequest = transfer to destination
                                operationMessage = if (transfer.third) "Moviendo archivo…" else "Copiando archivo…"
                            }
                        }
                    }
                ) { Text("Confirmar aquí") }
                TextButton(onClick = { pendingTransfer = null }) { Text("Cancelar") }
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
                                    folderPath = folderPath + (entry.documentId to entry.name)
                                    fileSearch = ""
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
                            Box {
                                Text(
                                    "⋮",
                                    color = accent,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clickable { actionEntry = entry }
                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                        .semantics { contentDescription = "Más opciones para ${entry.name}" }
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

    actionEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { actionEntry = null },
            title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text(if (entry.isDirectory) "Administrar carpeta" else "Administrar archivo") },
            confirmButton = {
                TextButton(onClick = {
                    renameName = entry.name
                    renameDocumentId = entry.documentId
                    showRenameDialog = true
                    actionEntry = null
                }) { Text("Renombrar") }
            },
            dismissButton = {
                Row {
                    if (!entry.isDirectory && rootUri != null) {
                        TextButton(onClick = {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, entry.documentId)
                            previewTarget = Triple(uri, entry.name, entry.mimeType)
                            actionEntry = null
                        }) { Text("Vista previa") }
                    }

                    if (!entry.isDirectory && currentDocumentId != null) {
                        TextButton(onClick = {
                            pendingTransfer = Triple(entry.documentId, currentDocumentId!!, false)
                            operationMessage = "Elige la carpeta de destino y confirma la copia."
                            actionEntry = null
                        }) { Text("Copiar") }
                        TextButton(onClick = {
                            pendingTransfer = Triple(entry.documentId, currentDocumentId!!, true)
                            operationMessage = "Elige la carpeta de destino y confirma el movimiento."
                            actionEntry = null
                        }) { Text("Mover") }
                    }
                    TextButton(onClick = {
                        deleteCandidate = entry
                        actionEntry = null
                    }) { Text("Eliminar") }
                    TextButton(onClick = { actionEntry = null }) { Text("Cerrar") }
                }
            }
        )
    }

    deleteCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("¿Eliminar elemento?") },
            text = {
                Text("Se eliminará “${entry.name}”. Esta acción depende del proveedor de archivos y puede no tener deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteRequest = entry.documentId
                    operationMessage = "Eliminando elemento…"
                    deleteCandidate = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar elemento") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    label = { Text("Nuevo nombre") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameName.isNotBlank(),
                    onClick = {
                        val name = renameName.trim()
                        val documentId = renameDocumentId
                        if (name.isNotEmpty() && documentId != null) {
                            renameRequest = name to documentId
                            operationMessage = "Renombrando elemento…"
                        }
                        showRenameDialog = false
                        renameDocumentId = null
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("Crear carpeta") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Proyectos") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newFolderName.isNotBlank() && currentDocumentId != null,
                    onClick = {
                        val name = newFolderName.trim()
                        val parent = currentDocumentId
                        if (name.isNotEmpty() && parent != null) {
                            createFolderRequest = name to parent
                            newFolderName = ""
                            showCreateFolder = false
                            operationMessage = "Creando carpeta…"
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false }) { Text("Cancelar") }
            }
        )
    }

    previewTarget?.let { target ->
        InfinityFilesPreviewDialog(
            uri = target.first,
            fileName = target.second,
            mimeType = target.third,
            onDismiss = { previewTarget = null }
        )
    }
}

@Composable
private fun Infinity08Window(
    win: DeskWindow,
    isFocused: Boolean,
    installedApps: List<InstalledApp>,
    accent: Color,
    lightMode: Boolean,
    desktopTheme: String,
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
    onFocus: () -> Unit,
    showGeometryControls: Boolean
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }
    var noteText by remember(win.id) {
        mutableStateOf(prefs.getString("note_${win.id}", "") ?: "")
    }
    val foreground = if (lightMode) Color(0xFF203149) else Color.White

    val windowRadius = if (desktopTheme == "windows11") 12.dp else 20.dp
    val windowSurface = if (desktopTheme == "windows11") {
        if (lightMode) Color(0xF2F3F8FC) else Color(0xF21A3652)
    } else if (lightMode) Color(0xF2F5F8FC) else Color(0xF21B2B40)
    val titleBarSurface = if (desktopTheme == "windows11") {
        if (lightMode) Color(0xFFE2EDF8) else Color(0xFF24496B)
    } else if (lightMode) Color(0xFFE0EAF5) else Color(0xFF263D57)
    val windowBorder = if (desktopTheme == "windows11") Color(0xFF8BCBFA).copy(.58f) else accent.copy(.38f)
    val controlRadius = if (desktopTheme == "windows11") 8.dp else 14.dp

 
    Column(
     
        Modifier.offset { IntOffset(win.x.roundToInt(), win.y.roundToInt()) }
            .zIndex(if (isFocused) 10f else 0f)
            .width(win.width.dp)
            .height(win.height.dp)
            .testTag("infinity-window-${win.kind}")
            .animateContentSize()
             .shadow(26.dp, RoundedCornerShape(windowRadius))
            .background(windowSurface, RoundedCornerShape(windowRadius))
            .border(1.dp, windowBorder, RoundedCornerShape(windowRadius))
            .clickable { onFocus() }
            // Keep the window name as an explicit accessible node outside the
            // clickable's descendant-merging semantics.
            .semantics(mergeDescendants = false) {
                contentDescription = "Ventana ${win.title}"
            }
    ) {
        
        // INFINITY SMART WINDOWS 0.11 — BARRA DE CONTROLES
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    titleBarSurface,
                    RoundedCornerShape(
                        topStart = windowRadius,
                        topEnd = windowRadius
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
                .testTag("infinity-window-title-${win.kind}")
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
                accent,
                desktopTheme
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 9.dp)
            ) {
                Text(
                    win.title,
                    modifier = Modifier.semantics {
                        contentDescription = "Ventana ${win.title}"
                    },
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
                        RoundedCornerShape(controlRadius)
                    )
            ) {
                Icon(
                    Icons.Default.Minimize,
                    contentDescription = "Minimizar",
                    tint = foreground
                )
            }

            Spacer(Modifier.width(3.dp))

            if (showGeometryControls) IconButton(
                onClick = onMaximize,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        accent.copy(alpha = 0.20f),
                        RoundedCornerShape(controlRadius)
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
                        RoundedCornerShape(controlRadius)
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

                "files" -> FileExplorer08(
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

         // En teléfono se usa ventana adaptada a pantalla completa; se ocultan controles inoperantes.
        if (showGeometryControls) {
        // INFINITY DESKTOP 0.11 — CONTROL DE REDIMENSIONAMIENTO
        Box(
            Modifier
                .align(Alignment.End)
                .padding(end = 8.dp, bottom = 8.dp)
                .size(38.dp)
                .testTag("infinity-window-resize-${win.kind}")
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
}
