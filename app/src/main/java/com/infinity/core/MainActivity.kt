package com.infinity.core

import android.content.ComponentName
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    private val resumeSignal = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        InfinityNotificationStore.refreshAccess(this)
        // Compose observes this signal so permission-dependent controls refresh
        // after the user returns from Android Settings.
        resumeSignal.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Infinity08App(resumeSignal = resumeSignal.intValue) }
    }
}

private data class DeskItem(
    val id: String,
    val label: String,
    val glyph: String,
    val packageName: String? = null
)

@Composable
private fun InfinityAppBadge(
    item: DeskItem,
    size: androidx.compose.ui.unit.Dp,
    accent: Color,
    iconPack: String = "infinity"
) {
    val identity = when (item.id) {
        "files" -> Color(0xFFFFC34D)
        "notes" -> Color(0xFFFFB45C)
        "apps" -> Color(0xFF8F7CFF)
        "connect" -> Color(0xFF37D8FF)
        "settings" -> Color(0xFF55D6A4)
        "orbit" -> Color(0xFFB78BFF)
        "about" -> Color(0xFF35DFFF)
        else -> Color(0xFF56D6C9)
    }
    val appIcon = when (item.id) {
        "files" -> Icons.Filled.FolderOpen
        "notes" -> Icons.Filled.EditNote
        "apps" -> Icons.Filled.Apps
        "connect" -> Icons.Filled.Computer
        "settings" -> Icons.Filled.Info
        "orbit" -> Icons.Filled.Apps
        else -> if (item.packageName != null) Icons.Filled.Apps else Icons.Filled.Info
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val shortcutIcon = remember(item.packageName, size, density) {
        item.packageName?.let { packageName ->
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val pixels = with(density) { size.toPx().roundToInt() }.coerceAtLeast(1)
                Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, pixels, pixels)
                    drawable.draw(canvas)
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    val radius = RoundedCornerShape(size * if (iconPack == "windows11") .22f else .27f)
    val tileBrush = if (iconPack == "windows11") {
        Brush.linearGradient(listOf(Color(0xFFB9E4FF), Color(0xFF1687D9), Color(0xFF07549B)))
    } else {
        Brush.linearGradient(
            listOf(
                identity.copy(alpha = .48f),
                Color(0xFF102B50),
                Color(0xFF080F25)
            )
        )
    }
    Box(
        Modifier
            .size(size)
            .shadow(if (iconPack == "windows11") 8.dp else 16.dp, radius)
            .clip(radius)
            .background(tileBrush)
            .border(
                1.4.dp,
                if (iconPack == "windows11") Color(0xFFBDEBFF) else identity.copy(alpha = .9f),
                radius
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glass highlight and inset edge give the INFINITY pack its own identity
        // without using a flattened poster image or changing app launch behavior.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.42f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (iconPack == "windows11") .22f else .16f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(size * .075f)
                .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(size * .21f))
        )
        if (item.id in setOf("about", "settings", "orbit", "connect")) {
            val symbol = when (item.id) {
                "about" -> "∞"
                "settings" -> "⚙"
                "orbit" -> "◎"
                else -> "◉"
            }
            Text(
                symbol,
                color = if (iconPack == "windows11") Color(0xFFF4FAFF) else Color.White,
                fontSize = (size.value * if (item.id == "about") .70f else .58f).sp,
                fontWeight = FontWeight.Black
            )
        } else if (shortcutIcon != null) {
            Image(
                bitmap = shortcutIcon,
                contentDescription = item.label,
                modifier = Modifier.size(size * .62f),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                imageVector = appIcon,
                contentDescription = item.label,
                tint = if (iconPack == "windows11") Color(0xFFF4FAFF) else Color.White,
                modifier = Modifier.size(size * .55f)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(size * .10f)
                .size(size * .105f)
                .shadow(4.dp, CircleShape)
                .background(if (iconPack == "windows11") Color(0xFFBDEBFF) else identity, CircleShape)
        )
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

/**
 * Gesture-local geometry transaction for the legacy DeskWindow surface.
 * Preview coordinates remain in Compose state; persistence is enabled only
 * after commit. Cancellation restores the exact pre-gesture geometry.
 */
private data class InfinityDeskWindowGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

private data class InfinityDeskWindowGeometryTransaction(
    val windowId: String,
    val original: InfinityDeskWindowGeometry,
    val current: InfinityDeskWindowGeometry
)

private fun infinityStartDeskWindowGeometryTransaction(
    windowId: String,
    original: InfinityDeskWindowGeometry
): InfinityDeskWindowGeometryTransaction =
    InfinityDeskWindowGeometryTransaction(windowId, original, original)

private fun infinityUpdateDeskWindowGeometryTransaction(
    transaction: InfinityDeskWindowGeometryTransaction,
    candidate: InfinityDeskWindowGeometry
): InfinityDeskWindowGeometryTransaction =
    transaction.copy(current = candidate)

private fun infinityCommitDeskWindowGeometryTransaction(
    transaction: InfinityDeskWindowGeometryTransaction,
    candidate: InfinityDeskWindowGeometry,
    viewportWidth: Float,
    viewportHeight: Float,
    safeBottom: Float,
    safeTop: Float = 0f
): InfinityDeskWindowGeometry {
    val widthLimit = viewportWidth.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val bottomLimit = minOf(
        viewportHeight.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f,
        safeBottom.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    )
    val topLimit = safeTop.takeIf { it.isFinite() }?.coerceIn(0f, bottomLimit) ?: 0f
    val heightLimit = (bottomLimit - topLimit).coerceAtLeast(1f)
    val minWidth = minOf(280f, widthLimit)
    val minHeight = minOf(190f, heightLimit)
    val width = candidate.width.takeIf { it.isFinite() }?.coerceIn(minWidth, widthLimit) ?: minWidth
    val height = candidate.height.takeIf { it.isFinite() }?.coerceIn(minHeight, heightLimit) ?: minHeight
    val x = candidate.x.takeIf { it.isFinite() }?.coerceIn(0f, (widthLimit - width).coerceAtLeast(0f)) ?: 0f
    val y = candidate.y.takeIf { it.isFinite() }?.coerceIn(topLimit, (bottomLimit - height).coerceAtLeast(topLimit)) ?: topLimit
    return InfinityDeskWindowGeometry(x, y, width, height)
}

private fun infinityCancelDeskWindowGeometryTransaction(
    transaction: InfinityDeskWindowGeometryTransaction
): InfinityDeskWindowGeometry = transaction.original

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
private fun Infinity08App(resumeSignal: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("infinity_desktop", 0) }

    // One-time upgrade: make BEYOND LIMITS the real first-run desktop, even when
    // an older installation persisted the previous classic/Windows presentation.
    // This changes presentation preferences only; user files and window data stay intact.
    remember {
        if (!prefs.getBoolean("beyond_limits_migration_v1", false)) {
            prefs.edit()
                .putString("desktop_wallpaper_preset", "futuristic")
                .putString("icon_pack", "infinity")
                .putString("background_preset", "interstellar")
                .putString("appearance", "dark")
                .putBoolean("beyond_limits_migration_v1", true)
                .apply()
        }
        true
    }

    val desktopBaseItems = remember {
        // INFINITY FUTURE reference desktop: stable IDs preserve existing window actions.
        listOf(
            DeskItem("files", "Este Equipo", "▣"),
            DeskItem("notes", "Notas rápidas", "▰"),
            DeskItem("apps", "Aplicaciones", "▤"),
            DeskItem("about", "INFINITY START", "∞"),
            DeskItem("settings", "System Guard", "⬡"),
            DeskItem("orbit", "ORBIT", "◎"),
            DeskItem("connect", "CONNECT", "◉")
        )
    }

    var workspace by remember { mutableIntStateOf(infinityNormalizeWorkspace(prefs.getInt("active_workspace", 1))) }
    var workspaceNames by remember {
        mutableStateOf((1..INFINITY_VIRTUAL_DESKTOP_COUNT).associateWith { id ->
            infinityNormalizeVirtualDesktopName(
                prefs.getString(infinityVirtualDesktopNameKey(id), null), id
            )
        })
    }
    var renameWorkspaceDialogOpen by remember { mutableStateOf(false) }
    var workspaceNameDraft by remember { mutableStateOf("") }
    var desktopShortcutPackages by remember(workspace) {
        mutableStateOf(
            infinityParseDesktopShortcutPackages(
                prefs.getString(infinityWorkspaceDesktopShortcutKey(workspace), null)
            )
        )
    }
    var dashboardOpen by remember { mutableStateOf(false) }
    var startOpen by remember { mutableStateOf(false) }
    var shortcutTransferMenuPackage by remember { mutableStateOf<String?>(null) }
    var orbitOpen by remember { mutableStateOf(false) }
    var windowTransferMenuId by remember { mutableStateOf<String?>(null) }
    var controlOpen by remember { mutableStateOf(false) }
    var controlMode by remember { mutableStateOf("personalization") }
    var controlSnapshot by remember { mutableStateOf(InfinityControlCenterSnapshot.unavailable()) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
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
    var wallpaperUri by remember(workspace) {
        val workspaceKey = infinityVirtualDesktopWallpaperKey(workspace)
        val savedWorkspaceWallpaper = prefs.getString(workspaceKey, null)
        val legacyWallpaper = if (workspace == 1) prefs.getString("wallpaper_uri", null) else null
        val resolvedWallpaper = infinityResolveLegacyWorkspaceOneWallpaper(
            workspace, savedWorkspaceWallpaper, legacyWallpaper
        )
        if (workspace == 1 && savedWorkspaceWallpaper == null && legacyWallpaper != null) {
            prefs.edit().putString(workspaceKey, legacyWallpaper).remove("wallpaper_uri").apply()
        }
        mutableStateOf(resolvedWallpaper?.let(Uri::parse))
    }
    var iconSize by remember {
        mutableFloatStateOf(infinityNormalizeIconSize(prefs.getFloat("icon_size", 48f)))
    }
    var themeChoice by remember {
        mutableStateOf(infinityNormalizeThemeChoice(prefs.getString("theme_choice", "midnight")))
    }
    // The taskbar presentation is independent from the desktop color theme and icon pack.
    var taskbarStyle by remember {
        mutableStateOf(
            if (prefs.getString("taskbar_style", "infinity") == "fluent_acrylic") {
                "fluent_acrylic"
            } else {
                "infinity"
            }
        )
    }

    // Dock preferences are independent from the five desktop color themes.
    var dockScale by remember { mutableFloatStateOf(infinityNormalizeDockPreferences(
        InfinityDockPreferences(dockScale = prefs.getFloat("dock_scale", 1f))
    ).dockScale) }
    var dockIconScale by remember { mutableFloatStateOf(infinityNormalizeDockPreferences(
        InfinityDockPreferences(iconScale = prefs.getFloat("dock_icon_scale", 1f))
    ).iconScale) }
    var dockTransparency by remember { mutableFloatStateOf(infinityNormalizeDockPreferences(
        InfinityDockPreferences(transparency = prefs.getFloat("dock_transparency", 0.12f))
    ).transparency) }
    var dockSpacing by remember { mutableFloatStateOf(infinityNormalizeDockPreferences(
        InfinityDockPreferences(itemSpacingDp = prefs.getFloat("dock_spacing", 8f))
    ).itemSpacingDp) }
    var dockScrollEnabled by remember { mutableStateOf(prefs.getBoolean("dock_scroll_enabled", true)) }
    var dockIndicatorsEnabled by remember { mutableStateOf(prefs.getBoolean("dock_indicators_enabled", true)) }
    var dockVisible by remember { mutableStateOf(prefs.getBoolean("dock_visible", true)) }
    var dockPosition by remember {
        mutableStateOf(
            runCatching { InfinityDockPosition.valueOf(prefs.getString("dock_position", "BOTTOM") ?: "BOTTOM") }
                .getOrDefault(InfinityDockPosition.BOTTOM)
        )
    }

    // Keep the desktop's live Compose theme synchronized with preference changes
    // made by ADVANCED and other modules, not only changes made inside this screen.
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
            if (key == "theme_choice") {
                themeChoice = infinityNormalizeThemeChoice(source.getString(key, "midnight"))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var iconPack by remember { mutableStateOf(infinityNormalizeDesktopTheme(prefs.getString("icon_pack", "infinity"))) }
    var backgroundPreset by remember(workspace) {
        val workspaceKey = infinityVirtualDesktopBackgroundPresetKey(workspace)
        val savedPreset = prefs.getString(workspaceKey, null)
        val legacyPreset = if (workspace == 1) prefs.getString("background_preset", null) else null
        if (workspace == 1 && savedPreset == null && legacyPreset != null) {
            prefs.edit().putString(workspaceKey, legacyPreset).remove("background_preset").apply()
        }
        mutableStateOf(savedPreset ?: legacyPreset ?: "interstellar")
    }
    var desktopPreset by remember { mutableStateOf(infinityNormalizeWallpaperPreset(prefs.getString("desktop_wallpaper_preset", "futuristic"))) }
    val futuristicDesktop = desktopPreset == InfinityDesktopWallpaperPreset.FUTURISTIC
    var appearance by remember {
        mutableStateOf(infinityNormalizeAppearance(prefs.getString("appearance", "dark")))
    }
    val lightMode = appearance == "light"
    val accent = infinityThemeAccentColor(themeChoice, lightMode)
    val foreground = if (lightMode) Color(0xFF203149) else Color.White
    val fluentTaskbar = taskbarStyle == "fluent_acrylic"

    var clock24h by remember { mutableStateOf(prefs.getBoolean("clock_24h", false)) }
    var clockText by remember { mutableStateOf("--:--") }
    var dateText by remember { mutableStateOf("") }
    LaunchedEffect(clock24h) {
        while (true) {
            val now = Date()
            clockText = SimpleDateFormat(if (clock24h) "HH:mm" else "hh:mm a", Locale("es", "DO")).format(now)
            dateText = SimpleDateFormat("EEE, dd MMM yyyy", Locale("es", "DO")).format(now)
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
            if (prefs.getString(infinityVirtualDesktopWallpaperKey(workspace), null) == uri.toString()) {
                prefs.edit().remove(infinityVirtualDesktopWallpaperKey(workspace)).apply()
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
                prefs.edit().putString(infinityVirtualDesktopWallpaperKey(workspace), uri.toString()).apply()
            } else {
                prefs.edit().remove(infinityVirtualDesktopWallpaperKey(workspace)).apply()
                Toast.makeText(
                    context,
                    "Fondo temporal: Android no permitió conservar el permiso. Selecciona la imagen de nuevo al reiniciar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val launchWallpaperPicker = {
        try {
            wallpaperPicker.launch(arrayOf("image/*"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No se encontró el selector de imágenes de Android.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Android bloqueó la apertura del selector de imágenes.", Toast.LENGTH_LONG).show()
        } catch (_: IllegalStateException) {
            Toast.makeText(context, "No se pudo abrir el selector en este momento. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
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

    val launchFolderPicker = {
        try {
            folderPicker.launch(null)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No se encontró el selector de carpetas de Android.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Android bloqueó la apertura del selector de carpetas.", Toast.LENGTH_LONG).show()
        } catch (_: IllegalStateException) {
            Toast.makeText(context, "No se pudo abrir el selector de carpetas en este momento.", Toast.LENGTH_LONG).show()
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

    // Geometry transactions keep preview frames out of SharedPreferences until commit.
    val geometryTransactions = remember { mutableStateMapOf<String, InfinityDeskWindowGeometryTransaction>() }
    val geometryTransactionRegistry = remember { InfinityWindowGeometryTransactionRegistry(geometryTransactions) }

    fun beginWindowGeometryTransaction(windowId: String) {
        val window = windows.firstOrNull { it.id == windowId } ?: return
        geometryTransactions[windowId] = infinityStartDeskWindowGeometryTransaction(
            windowId,
            InfinityDeskWindowGeometry(window.x, window.y, window.width, window.height)
        )
    }

    fun commitWindowGeometryTransaction(
        windowId: String, viewportWidth: Float, viewportHeight: Float, safeBottom: Float, safeTop: Float
    ) {
        val transaction = geometryTransactionRegistry.remove(windowId) ?: return
        val index = windows.indexOfFirst { it.id == windowId }
        if (index < 0) return
        val current = windows[index]
        val committed = infinityCommitDeskWindowGeometryTransaction(
            transaction,
            InfinityDeskWindowGeometry(current.x, current.y, current.width, current.height),
            viewportWidth, viewportHeight, safeBottom, safeTop
        )
        windows[index] = current.copy(
            x = committed.x, y = committed.y, width = committed.width, height = committed.height
        )
    }

    fun cancelWindowGeometryTransaction(windowId: String) {
        val transaction = geometryTransactionRegistry.remove(windowId) ?: return
        val index = windows.indexOfFirst { it.id == windowId }
        if (index < 0) return
        val restored = infinityCancelDeskWindowGeometryTransaction(transaction)
        windows[index] = windows[index].copy(
            x = restored.x, y = restored.y, width = restored.width, height = restored.height
        )
    }

    // Include every persisted field and the list order (AURORA z-order) in the key.
    // Debouncing avoids writing SharedPreferences for every pointer-move frame.
    val windowSnapshot = infinityWindowSnapshotKey(
        windows.map { win ->
            InfinityWindowSnapshotEntry(
                id = win.id,
                title = win.title,
                kind = win.kind,
                x = win.x,
                y = win.y,
                width = win.width,
                height = win.height,
                minimized = win.minimized,
                workspace = win.workspace,
                maximized = win.maximized
            )
        }
    )
    val activeGeometryTransactionCount = geometryTransactions.size
    LaunchedEffect(windowSnapshot, activeGeometryTransactionCount) {
        // Never serialize an in-flight preview. Starting a gesture cancels this effect;
        // only commit or rollback can make persistence eligible again.
        if (activeGeometryTransactionCount > 0 || geometryTransactions.isNotEmpty()) return@LaunchedEffect
        // Geometry previews are already isolated by the transaction registry.
        // Persist the committed snapshot immediately so a fast app/process restart
        // cannot discard a recent workspace move, minimize, maximize or close action.
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
                prefs.getString(infinityWorkspaceFocusedWindowKey(workspace), null)
                    ?: if (workspace == 1) prefs.getString("focused_window_id", null) else null,
                windows.map { it.id },
                windows.associate { it.id to it.workspace },
                windows.filter { it.minimized }.map { it.id }.toSet(),
                workspace
            )
        )
    }
    LaunchedEffect(focusedWindowId, workspace) {
        val key = infinityWorkspaceFocusedWindowKey(workspace)
        val id = focusedWindowId?.takeIf { windowId ->
            windows.any { it.id == windowId && it.workspace == workspace && !it.minimized }
        }
        val editor = prefs.edit()
        if (id == null) editor.remove(key) else editor.putString(key, id)
        // Migrate the legacy global focus key only into workspace 1, then retire it.
        if (workspace == 1 && prefs.contains("focused_window_id")) {
            editor.remove("focused_window_id")
        }
        editor.apply()
    }

    // Every activation path must update both logical focus and visual z-order.
    // Windows are drawn in list order, so changing only focusedWindowId can leave
    // an ORBIT/Dock-restored window visually underneath a later overlapping window.
    fun focusWindowToFront(windowId: String) {
        val index = windows.indexOfFirst { it.id == windowId }
        if (index < 0) return
        val ordered = infinityRaiseWindowAfterManipulation(windows.toList(), windows[index]) { it.id }
        if (ordered.map { it.id } != windows.map { it.id }) {
            windows.clear()
            windows.addAll(ordered)
        }
        focusedWindowId = windowId
    }

    fun switchWorkspace(target: Int) {
        // A workspace transition ends every in-flight gesture atomically.
        geometryTransactionRegistry.activeWindowIds().forEach(::cancelWindowGeometryTransaction)
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

val iconOffsets = remember { mutableStateMapOf<String, Offset>() }
    val latestWorkspace by rememberUpdatedState(workspace)

    val installedApps = remember(resumeSignal) {
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
    val installedPackageNames = remember(installedApps) { installedApps.map { it.packageName }.toSet() }
    LaunchedEffect(installedPackageNames, workspace) {
        val normalized = infinityNormalizeDesktopShortcutPackages(desktopShortcutPackages, installedPackageNames)
        if (normalized != desktopShortcutPackages) {
            desktopShortcutPackages = normalized
            prefs.edit().putString(
                infinityWorkspaceDesktopShortcutKey(workspace),
                infinitySerializeDesktopShortcutPackages(normalized)
            ).apply()
        }
    }
    val desktopItems = remember(desktopBaseItems, desktopShortcutPackages, installedApps) {
        desktopBaseItems + desktopShortcutPackages.mapNotNull { packageName ->
            installedApps.firstOrNull { it.packageName == packageName }?.let { app ->
                DeskItem(
                    id = infinityDesktopShortcutId(packageName),
                    label = app.label,
                    glyph = "◉",
                    packageName = packageName
                )
            }
        }
    }
    var dockFavorites by remember {
        mutableStateOf(
            infinityDockParseFavorites(prefs.getString("dock_favorites", ""))
        )
    }
    LaunchedEffect(installedPackageNames) {
        val normalized = infinityNormalizeDockPreferences(
            InfinityDockPreferences(favoritePackageNames = dockFavorites),
            installedPackageNames
        ).favoritePackageNames
        if (normalized != dockFavorites) {
            dockFavorites = normalized
            prefs.edit().putString("dock_favorites", normalized.joinToString("\n")).apply()
        }
    }
    fun persistDockFavorites(next: List<String>) {
        val normalized = infinityNormalizeDockPreferences(
            InfinityDockPreferences(favoritePackageNames = next),
            installedPackageNames
        ).favoritePackageNames
        dockFavorites = normalized
        prefs.edit().putString("dock_favorites", normalized.joinToString("\n")).apply()
    }

    fun persistDesktopShortcutPackages(next: List<String>) {
        val normalized = infinityNormalizeDesktopShortcutPackages(next, installedPackageNames)
        desktopShortcutPackages = normalized
        prefs.edit().putString(
            infinityWorkspaceDesktopShortcutKey(workspace),
            infinitySerializeDesktopShortcutPackages(normalized)
        ).apply()
    }

    fun persistWorkspaceShortcutPackages(targetWorkspace: Int, next: List<String>) {
        val normalized = infinityNormalizeDesktopShortcutPackages(next, installedPackageNames)
        prefs.edit().putString(
            infinityWorkspaceDesktopShortcutKey(targetWorkspace),
            infinitySerializeDesktopShortcutPackages(normalized)
        ).apply()
        if (targetWorkspace == workspace) desktopShortcutPackages = normalized
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
                .pointerInput(workspace) {
                    var horizontalTravel = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalTravel = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            horizontalTravel += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            val target = infinityResolveWorkspaceSwipe(
                                workspace, horizontalTravel, 0f, size.width.toFloat()
                            )
                            if (target != workspace) switchWorkspace(target)
                        },
                        onDragCancel = { horizontalTravel = 0f }
                    )
                }
                // Paint the desktop across the complete edge-to-edge window first.
                // Apply safe insets only to interactive content; otherwise landscape
                // devices expose unpainted system-bar strips around the desktop.
                .background(
                Brush.linearGradient(
                    when {
                        iconPack == "windows11" -> if (lightMode)
                            infinityThemeBackgroundStops(themeChoice, lightMode = true)
                            else listOf(Color(0xFF020B1A), accent.copy(alpha = .42f), Color(0xFF06152B))
                        backgroundPreset == "punk" -> listOf(Color(0xFF24051F), Color(0xFF631B43), Color(0xFF102338))
                        backgroundPreset == "interstellar" -> listOf(Color(0xFF030713), Color(0xFF17265D), Color(0xFF46246A))
                        backgroundPreset == "aurora" -> listOf(Color(0xFF062E36), Color(0xFF174E60), Color(0xFF102238))
                        else -> infinityThemeBackgroundStops(themeChoice, lightMode)
                    }
                )
            )
                // Personal wallpapers are painted on the full window BEFORE safe insets
                // are applied to the interactive desktop content. This prevents landscape
                // letterbox strips at the top/bottom and keeps the selected image edge-to-edge.
                .then(
                    wallpaperBitmap?.let {
                        Modifier.paint(BitmapPainter(it), contentScale = ContentScale.Crop)
                            .background(Color(0x66081729))
                    } ?: Modifier
                )
                // Keep controls clear of camera cutouts and Android system bars.
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            val density = LocalDensity.current
            // The measured Dock top is authoritative; use a conservative estimate for frame one.
            var dockSafeBottom by remember(maxW, maxH, futuristicDesktop, iconPack, taskbarStyle, dockVisible, dockPosition) {
                val reservedHeight = if (fluentTaskbar) 88f
                else infinityDockReservedHeightDp(maxW.value, futuristicDesktop, iconPack)
                mutableFloatStateOf(
                    if (dockVisible && dockPosition == InfinityDockPosition.BOTTOM) (maxH.value - reservedHeight).coerceAtLeast(0f)
                    else maxH.value
                )
            }
            var dockSafeTop by remember(maxW, maxH, dockVisible, dockPosition, futuristicDesktop, iconPack, taskbarStyle) {
                val reservedHeight = if (fluentTaskbar) 88f
                else infinityDockReservedHeightDp(maxW.value, futuristicDesktop, iconPack)
                mutableFloatStateOf(if (dockVisible && dockPosition == InfinityDockPosition.TOP) reservedHeight else 0f)
            }
            LaunchedEffect(dockVisible, dockPosition, maxH, maxW, futuristicDesktop, iconPack, taskbarStyle) {
                val reservedHeight = if (fluentTaskbar) 88f
                else infinityDockReservedHeightDp(maxW.value, futuristicDesktop, iconPack)
                dockSafeTop = if (dockVisible && dockPosition == InfinityDockPosition.TOP) reservedHeight else 0f
                dockSafeBottom = if (dockVisible && dockPosition == InfinityDockPosition.BOTTOM) {
                    (maxH.value - reservedHeight).coerceAtLeast(dockSafeTop)
                } else {
                    maxH.value
                }
            }
            val desktopIconMetrics = remember(maxW, maxH, density, desktopItems.size, dockPosition) {
                infinityDesktopIconLayoutMetrics(
                    with(density) { maxW.toPx() },
                    with(density) { maxH.toPx() },
                    desktopItems.size,
                    density.density,
                    dockAtBottom = dockPosition == InfinityDockPosition.BOTTOM
                )
            }
            val desktopIconTopInsetPx = desktopIconMetrics.topInsetPx + with(density) { dockSafeTop.dp.toPx() }

            // Load, normalize, de-collide and persist one workspace atomically.
            // Keep the icon layer hidden until this workspace snapshot is ready.
            var iconLayoutReady by remember(workspace) { mutableStateOf(false) }
            LaunchedEffect(workspace, maxW, maxH, dockSafeTop, dockPosition, desktopItems) {
                iconLayoutReady = false
                val viewportWidth = with(density) { maxW.toPx() }
                val safeDesktopHeight = desktopIconMetrics.safeDesktopHeightPx
                val itemWidth = desktopIconMetrics.itemWidthPx
                val itemHeight = desktopIconMetrics.itemHeightPx
                val topInset = desktopIconTopInsetPx
                val loaded = desktopItems.mapIndexed { index, item ->
                    val fallback = infinityDesktopIconGridOffset(
                        index, viewportWidth, itemWidth, itemHeight,
                        horizontalGap = desktopIconMetrics.horizontalGapPx,
                        verticalGap = desktopIconMetrics.verticalGapPx,
                        topInset = topInset
                    )
                    val legacyX = if (workspace == 1) {
                        prefs.getFloat(infinityLegacyWorkspaceIconPositionKey(item.id, "x"), fallback.x)
                    } else fallback.x
                    val legacyY = if (workspace == 1) {
                        prefs.getFloat(infinityLegacyWorkspaceIconPositionKey(item.id, "y"), fallback.y)
                    } else fallback.y
                    item.id to Offset(
                        prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "x"), legacyX),
                        prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, item.id, "y"), legacyY)
                    )
                }.toMap()
                val normalized = infinityResolveIconOverlaps(
                    desktopItems.map { loaded[it.id] ?: Offset.Zero },
                    viewportWidth,
                    safeDesktopHeight,
                    itemWidth = itemWidth,
                    itemHeight = itemHeight,
                    horizontalGap = desktopIconMetrics.horizontalGapPx,
                    verticalGap = desktopIconMetrics.verticalGapPx,
                    topInset = topInset
                )
                iconOffsets.clear()
                desktopItems.forEachIndexed { index, item -> iconOffsets[item.id] = normalized[index] }
                val editor = prefs.edit()
                infinityWorkspaceIconPositionPreferences(
                    workspace,
                    desktopItems.associate { it.id to (iconOffsets[it.id] ?: Offset.Zero) }
                ).forEach { (key, value) -> editor.putFloat(key, value) }
                editor.apply()
                iconLayoutReady = true
            }

            // Normalize persisted windows after screen size, orientation, or density changes.
            LaunchedEffect(maxW, maxH, dockSafeBottom, dockSafeTop) {
                // A viewport/orientation change invalidates active pointer geometry.
                // Roll back before normalizing the saved desktop geometry to the new area.
                geometryTransactions.keys.toList().forEach(::cancelWindowGeometryTransaction)
                // Phone rendering adapts windows visually; never persist phone-sized bounds
                // over the desktop geometry that must be restored on tablet/desktop.
                if (!infinityWindowGeometryNormalizationEnabled(maxW.value)) return@LaunchedEffect
                windows.indices.forEach { index ->
                    val current = windows[index]
                    val bounds = infinityNormalizeWindowBounds(
                        current.x, current.y, current.width, current.height,
                        maxW.value, maxH.value, safeBottom = dockSafeBottom, safeTop = dockSafeTop
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

            // The selected personal wallpaper is already painted edge-to-edge by the root window.
            val currentWallpaperBitmap = wallpaperBitmap

            // PACK13: render a true layered futuristic scene when no personal wallpaper is selected.
            // The scene is decorative only; all desktop controls remain live Compose elements above it.
            if (currentWallpaperBitmap == null && maxW >= 600.dp && !dashboardOpen && futuristicDesktop) {
                InfinityFuturisticScene(
                    backgroundPreset = backgroundPreset,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Cabecera de marca para tablet; no sustituye los accesos ni el fondo elegido.
            if (infinityReferenceMastheadVisible(maxW.value) && !dashboardOpen && futuristicDesktop) {
                InfinityReferenceMasthead(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = 88.dp)
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
                TextButton(
                    onClick = {
                        workspaceNameDraft = workspaceNames[workspace] ?: infinityDefaultVirtualDesktopName(workspace)
                        renameWorkspaceDialogOpen = true
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("infinity-workspace-name")
                ) {
                    Text(workspaceNames[workspace] ?: infinityDefaultVirtualDesktopName(workspace), color = accent, fontSize = 9.sp)
                    Text("  ✎", color = accent, fontSize = 10.sp)
                }
            }

            }
            if (maxW >= 600.dp && futuristicDesktop && !dashboardOpen) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            workspaceNameDraft = workspaceNames[workspace] ?: infinityDefaultVirtualDesktopName(workspace)
                            renameWorkspaceDialogOpen = true
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.testTag("infinity-workspace-name")
                    ) { Text(workspaceNames[workspace] ?: infinityDefaultVirtualDesktopName(workspace), color = accent, fontSize = 10.sp); Text("  ✎", color = accent, fontSize = 12.sp) }
                }
            }
            if (renameWorkspaceDialogOpen) {
                AlertDialog(
                    onDismissRequest = { renameWorkspaceDialogOpen = false },
                    title = { Text("Renombrar escritorio") },
                    text = {
                        Column {
                            Text("El nombre solo cambia en este espacio de trabajo.", fontSize = 12.sp)
                            OutlinedTextField(
                                value = workspaceNameDraft,
                                onValueChange = { workspaceNameDraft = it.take(24) },
                                modifier = Modifier.testTag("infinity-workspace-name-input"),
                                singleLine = true,
                                label = { Text("Nombre del escritorio") }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            modifier = Modifier.testTag("infinity-workspace-name-save"),
                            onClick = {
                            val normalizedName = infinityNormalizeVirtualDesktopName(workspaceNameDraft, workspace)
                            workspaceNames = workspaceNames + (workspace to normalizedName)
                            prefs.edit().putString(infinityVirtualDesktopNameKey(workspace), normalizedName).apply()
                            renameWorkspaceDialogOpen = false
                        }) { Text("Guardar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameWorkspaceDialogOpen = false }) { Text("Cancelar") }
                    }
                )
            }
            if (maxW >= 600.dp && !dashboardOpen && futuristicDesktop) {
                InfinityReferenceClockPanel(
                    clock = clockText,
                    date = dateText,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = dockSafeTop.dp + 18.dp, end = 28.dp)
                )
            } else {
                Column(
                    Modifier.align(Alignment.TopEnd).padding(top = dockSafeTop.dp + 18.dp, start = 18.dp, end = 18.dp, bottom = 18.dp)
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

            Box(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = dockSafeTop.dp + 18.dp)) {
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
                                type = when {
                                    item.packageName != null -> "app"
                                    item.id == "files" -> "folder"
                                    item.id == "apps" || item.id == "connect" -> "app"
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
                            itemWidth = desktopIconMetrics.itemWidthPx,
                            itemHeight = desktopIconMetrics.itemHeightPx,
                            leftInset = 16f * densityValue,
                            topInset = 118f * densityValue + with(density) { dockSafeTop.dp.toPx() }
                        )
                        desktopContextState = plan.nextState
                        prefs.edit()
                            .putString("desktop_icon_arrangement", plan.nextState.arrangement.name)
                            .putBoolean("desktop_icons_visible", plan.nextState.iconsVisible)
                            .putFloat("icon_size", plan.nextState.iconSizePx)
                            .apply()
                        desktopIconsVisible = plan.nextState.iconsVisible
                        iconSize = infinityNormalizeIconSize(plan.nextState.iconSizePx)
                        plan.placements?.let { placements ->
                            val safeHeight = with(density) {
                                infinityDesktopHeightAboveDock(
                                    maxH.toPx(),
                                    dockHeight = 88.dp.toPx(),
                                    dockBottomInset = 24.dp.toPx(),
                                    safetyGap = 56.dp.toPx()
                                )
                            }
                            val resolved = infinityResolveIconOverlaps(
                                desktopItems.map { placements[it.id] ?: iconOffsets[it.id] ?: Offset.Zero },
                                with(density) { maxW.toPx() },
                                safeHeight,
                                itemWidth = desktopIconMetrics.itemWidthPx,
                                itemHeight = desktopIconMetrics.itemHeightPx,
                                horizontalGap = desktopIconMetrics.horizontalGapPx,
                                verticalGap = desktopIconMetrics.verticalGapPx,
                                topInset = desktopIconTopInsetPx
                            )
                            desktopItems.forEachIndexed { index, desktopItem ->
                                iconOffsets[desktopItem.id] = resolved[index]
                            }
                            val editor = prefs.edit()
                            infinityWorkspaceIconPositionPreferences(
                                workspace,
                                desktopItems.associate { it.id to (iconOffsets[it.id] ?: Offset.Zero) }
                            ).forEach { (key, value) -> editor.putFloat(key, value) }
                            editor.apply()
                        }
                        if (plan.requestWallpaperPicker) wallpaperPicker.launch(arrayOf("image/*"))
                        if (plan.requestAppearancePanel) {
                            focusedWindowId = openInfinityWindow("settings", windows, workspace, maxW.value, maxH.value, dockSafeBottom, dockSafeTop)
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
                        focusedWindowId = openInfinityWindow(id, windows, workspace, maxW.value, maxH.value, dockSafeBottom, dockSafeTop)
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
            } else if (desktopIconsVisible && iconLayoutReady) {
                val referenceRail = maxW >= 600.dp && futuristicDesktop
                val compactIconCell = !referenceRail && desktopIconMetrics.itemHeightPx < with(density) { 112.dp.toPx() }
                val desktopItemWidth = with(density) { desktopIconMetrics.itemWidthPx.toDp() }
                val desktopItemHeight = with(density) { desktopIconMetrics.itemHeightPx.toDp() }
                val desktopBadgeSize = with(density) { desktopIconMetrics.iconSizePx.toDp() }
                val railLayout = if (referenceRail) infinityReferenceRailLayout(maxW.value, maxH.value, desktopItems.size) else null
                val railOrderedItems = if (referenceRail) {
                    val records = desktopItems.mapIndexed { index, item ->
                        InfinityDesktopIconRecord(
                            id = item.id,
                            label = item.label,
                            type = when {
                                item.packageName != null -> "app"
                                item.id == "files" -> "folder"
                                item.id == "apps" || item.id == "connect" -> "app"
                                else -> "system"
                            },
                            order = index
                        )
                    }
                    infinityReferenceRailOrderedRecords(records, desktopContextState.arrangement)
                        .mapNotNull { record -> desktopItems.firstOrNull { it.id == record.id } }
                } else desktopItems
                railOrderedItems.forEachIndexed { itemIndex, item ->
                    val shortcutInteraction = remember(item.id) { MutableInteractionSource() }
                    val shortcutHovered by shortcutInteraction.collectIsHoveredAsState()
                    val shortcutFocused by shortcutInteraction.collectIsFocusedAsState()
                    val shortcutHighlighted = shortcutHovered || shortcutFocused
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
                            .width((railLayout?.itemWidthDp ?: desktopItemWidth.value).dp)
                            .heightIn(max = if (referenceRail) Dp.Unspecified else desktopItemHeight)
                            .graphicsLayer {
                                val emphasis = if (shortcutHighlighted) 1.045f else 1f
                                scaleX = emphasis
                                scaleY = emphasis
                            }
                            .shadow(if (shortcutHighlighted) 18.dp else 0.dp, RoundedCornerShape(18.dp))
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
                            .padding(if (referenceRail) 2.dp else if (compactIconCell) 3.dp else 8.dp)
                            .hoverable(shortcutInteraction)
                            .focusable(interactionSource = shortcutInteraction)
                            .testTag("infinity-desktop-shortcut-${item.id}")
                            .pointerInput(item.id, referenceRail, workspace, desktopItems, maxW.value, maxH.value, density.density) {
                                if (!referenceRail) {
                                    var transaction: InfinityDesktopIconDragTransaction? = null
                                    fun persistSnapshot(targetWorkspace: Int, positions: Map<String, Offset>) {
                                        val editor = prefs.edit()
                                        infinityWorkspaceIconPositionPreferences(targetWorkspace, positions)
                                            .forEach { (key, value) -> editor.putFloat(key, value) }
                                        editor.apply()
                                    }
                                    detectDragGestures(
                                        onDragStart = {
                                            transaction = infinityBeginDesktopIconDrag(
                                                workspace,
                                                desktopItems.associate { it.id to (iconOffsets[it.id] ?: Offset.Zero) },
                                                item.id
                                            )
                                        },
                                        onDragEnd = {
                                            val active = transaction
                                            if (active != null) {
                                                val safeHeight = with(density) {
                                                    infinityDesktopHeightAboveDock(
                                                        maxH.toPx(),
                                                        dockHeight = 88.dp.toPx(),
                                                        dockBottomInset = 24.dp.toPx(),
                                                        safetyGap = 56.dp.toPx()
                                                    )
                                                }
                                                val committed = infinityCommitDesktopIconDrag(
                                                    active,
                                                    desktopItems.map { it.id },
                                                    with(density) { maxW.toPx() },
                                                    safeHeight,
                                                    itemWidth = desktopIconMetrics.itemWidthPx,
                                                    itemHeight = desktopIconMetrics.itemHeightPx,
                                                    horizontalGap = desktopIconMetrics.horizontalGapPx,
                                                    verticalGap = desktopIconMetrics.verticalGapPx,
                                                    topInset = desktopIconMetrics.topInsetPx
                                                )
                                                if (latestWorkspace == active.workspace) {
                                                    committed.forEach { (id, position) -> iconOffsets[id] = position }
                                                }
                                                persistSnapshot(active.workspace, committed)
                                            }
                                            transaction = null
                                        },
                                        onDragCancel = {
                                            val active = transaction
                                            if (active != null) {
                                                val restored = infinityCancelDesktopIconDrag(active)
                                                if (latestWorkspace == active.workspace) {
                                                    restored.forEach { (id, position) -> iconOffsets[id] = position }
                                                }
                                                persistSnapshot(active.workspace, restored)
                                            }
                                            transaction = null
                                        }
                                    ) { change, drag ->
                                        change.consume()
                                        val active = transaction ?: infinityBeginDesktopIconDrag(
                                            workspace,
                                            desktopItems.associate { it.id to (iconOffsets[it.id] ?: Offset.Zero) },
                                            item.id
                                        )
                                        if (active != null) {
                                            val next = infinityMoveDesktopIconDrag(
                                                active,
                                                Offset(drag.x, drag.y),
                                                with(density) { maxW.toPx() },
                                                with(density) {
                                                    infinityDesktopHeightAboveDock(
                                                        maxH.toPx(),
                                                        dockHeight = 88.dp.toPx(),
                                                        dockBottomInset = 24.dp.toPx(),
                                                        safetyGap = 56.dp.toPx()
                                                    )
                                                },
                                                itemWidth = desktopIconMetrics.itemWidthPx,
                                                itemHeight = desktopIconMetrics.itemHeightPx,
                                                topInset = desktopIconMetrics.topInsetPx
                                            )
                                            transaction = next
                                            next.currentPositions[item.id]?.let { iconOffsets[item.id] = it }
                                        }
                                    }
                                }
                            }
                            .clickable(interactionSource = shortcutInteraction, indication = null, role = Role.Button) {
                                when (item.id) {
                                    "orbit" -> {
                                        orbitOpen = !orbitOpen
                                        startOpen = false
                                        controlOpen = false
                                        advancedOpen = false
                                    }
                                    "about" -> {
                                        startOpen = true
                                        search = ""
                                        startCategory = "all"
                                        orbitOpen = false
                                        controlOpen = false
                                        advancedOpen = false
                                    }
                                    "settings" -> {
                                        controlOpen = true
                                        controlMode = "device"
                                        startOpen = false
                                        orbitOpen = false
                                        advancedOpen = false
                                    }
                                    else -> if (item.packageName != null) launchApp(item.packageName)
                                    else focusedWindowId = openInfinityWindow(item.id, windows, workspace, maxW.value, maxH.value, dockSafeBottom, dockSafeTop)
                                }
                            }
                            .padding(if (referenceRail) 2.dp else if (compactIconCell) 3.dp else 6.dp)
                    ) {
                        InfinityAppBadge(
                            item,
                            if (railLayout != null) {
                                infinityReferenceRailIconSize(
                                    railLayout.slotHeightDp,
                                    desktopContextState.iconSizePx
                                ).dp
                            } else desktopBadgeSize,
                            accent,
                            iconPack
                        )
                        Spacer(Modifier.height(if (referenceRail) 2.dp else if (compactIconCell) 3.dp else 7.dp))
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
                            fontSize = if (referenceRail) 7.sp else if (compactIconCell) 8.sp else 10.sp,
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
                        // Both maximized tablet windows and phone-adapted windows stop above
                        // the measured Dock, including its floating margin and safe inset.
                        val top = maxOf(if (maxW < 600.dp) 62f else 8f, dockSafeTop + 8f)
                        win.copy(
                            x = 8f,
                            y = top,
                            width = (maxW.value - 16f).coerceAtLeast(1f),
                            height = (dockSafeBottom - top - 8f).coerceAtLeast(1f)
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
                        onChooseWallpaper = launchWallpaperPicker,
                        selectedFolder = selectedFolder,
                        onChooseFolder = launchFolderPicker,
                        onLaunchApp = ::launchApp,
                        onClose = {
                            // Discard the gesture transaction before removing its window.
                            geometryTransactionRegistry.remove(win.id)
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
                            // Roll back any in-flight gesture before minimizing.
                            cancelWindowGeometryTransaction(win.id)
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
                            cancelWindowGeometryTransaction(win.id)
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0) {
                                windows[index] = windows[index].copy(maximized = !windows[index].maximized)
                                focusWindowToFront(win.id)
                            }
                        },
                        
                        // INFINITY 0.11 — MOVIMIENTO CON LÍMITES
                        onGeometryStart = { beginWindowGeometryTransaction(win.id) },
                        onGeometryCommit = {
                            commitWindowGeometryTransaction(win.id, maxW.value, maxH.value, dockSafeBottom, dockSafeTop)
                        },
                        onGeometryCancel = { cancelWindowGeometryTransaction(win.id) },
                        onMove = { dx, dy ->
                            val index = windows.indexOfFirst { it.id == win.id }
                            if (index >= 0 && infinityWindowCanManipulateGeometry(windows[index].maximized, maxW.value)) {
                                val current = windows[index]
                                // Compose drag deltas are pixels; persisted window geometry is dp.
                                val deltaXdp = infinityGestureDeltaToDp(dx, density.density)
                                val deltaYdp = infinityGestureDeltaToDp(dy, density.density)
                                val bounded = infinityNormalizeWindowBounds(
                                    current.x + deltaXdp,
                                    current.y + deltaYdp,
                                    current.width,
                                    current.height,
                                    maxW.value,
                                    maxH.value,
                                    safeBottom = dockSafeBottom,
                                    safeTop = dockSafeTop
                                )
                                windows[index] = current.copy(
                                    x = bounded.x,
                                    y = bounded.y,
                                    width = bounded.width,
                                    height = bounded.height
                                )
                                geometryTransactions[win.id]?.let { transaction ->
                                    geometryTransactions[win.id] = infinityUpdateDeskWindowGeometryTransaction(
                                        transaction, InfinityDeskWindowGeometry(bounded.x, bounded.y, bounded.width, bounded.height)
                                    )
                                }
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
                                    (dockSafeBottom - maxOf(current.y, dockSafeTop)).coerceAtLeast(1f)

                                val resized = current.copy(
                                    width = infinityResizeDimension(current.width, deltaWidthDp, availableWidth, 280f),
                                    height = infinityResizeDimension(current.height, deltaHeightDp, availableHeight, 190f)
                                )
                                windows[index] = resized
                                geometryTransactions[win.id]?.let { transaction ->
                                    geometryTransactions[win.id] = infinityUpdateDeskWindowGeometryTransaction(
                                        transaction, InfinityDeskWindowGeometry(resized.x, resized.y, resized.width, resized.height)
                                    )
                                }
                            }
                        },
                     onFocus = {
                         focusWindowToFront(win.id)
                     },
                        showGeometryControls = infinityWindowGeometryControlsVisible(maxW.value)
                    )
                }
            }

          
            // DOCK INFINITY — barra adaptable y desplazable en todos los tamaños.
            // El viewport ocupa el ancho disponible; el contenido interno puede desplazarse
            // sin recortar accesos primarios ni depender de un umbral de pantalla.
            val dockScrollState = rememberScrollState()
            if (dockVisible) {
            Row(
                modifier = Modifier
                    .align(if (dockPosition == InfinityDockPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                    .zIndex(20f)
                    .testTag("infinity-dock")
                    .onGloballyPositioned { coordinates ->
                        val measuredTop = with(density) { coordinates.positionInParent().y.toDp().value }
                        val measuredHeight = with(density) { coordinates.size.height.toDp().value }
                        if (dockPosition == InfinityDockPosition.BOTTOM) {
                            val nextSafeBottom = (measuredTop - 8f).coerceIn(0f, maxH.value)
                            if (kotlin.math.abs(dockSafeBottom - nextSafeBottom) > 0.5f) dockSafeBottom = nextSafeBottom
                            if (dockSafeTop != 0f) dockSafeTop = 0f
                        } else {
                            val nextSafeTop = (measuredTop + measuredHeight + 8f).coerceIn(0f, maxH.value)
                            if (kotlin.math.abs(dockSafeTop - nextSafeTop) > 0.5f) dockSafeTop = nextSafeTop
                            if (dockSafeBottom != maxH.value) dockSafeBottom = maxH.value
                        }
                    }
                    // A floating glass rail preserves the full action surface while leaving
                    // a visible spatial margin around the desktop on every side.
                    .padding(
                        horizontal = if (fluentTaskbar) 12.dp else if (maxW >= 600.dp && futuristicDesktop) 14.dp else 0.dp,
                        vertical = if (fluentTaskbar) 12.dp else if (maxW >= 600.dp && futuristicDesktop) 12.dp else 0.dp
                    )
                    // Todos los formatos conservan el mismo viewport desplazable.
                    // Esto evita que los accesos fijos, las ventanas y la bandeja se pisen
                    // cuando la tablet cambia de orientación o hay varias ventanas abiertas.
                    .fillMaxWidth()
                    .horizontalScroll(dockScrollState, enabled = dockScrollEnabled)
                    .graphicsLayer { scaleX = dockScale; scaleY = dockScale }
                    .then(
                        if (fluentTaskbar) Modifier.widthIn(max = 720.dp)
                        else if (maxW >= 600.dp && futuristicDesktop) Modifier.widthIn(max = 1500.dp)
                        else Modifier
                    )
                    .padding(
                        horizontal = if (fluentTaskbar || iconPack == "windows11" || (maxW >= 600.dp && futuristicDesktop)) 0.dp else 12.dp,
                        vertical = if (fluentTaskbar || iconPack == "windows11" || (maxW >= 600.dp && futuristicDesktop)) 0.dp else 12.dp
                    )
                    .shadow(
                        if (fluentTaskbar) 20.dp else if (iconPack == "windows11") 0.dp else 30.dp,
                        RoundedCornerShape(if (fluentTaskbar) 24.dp else if (iconPack == "windows11") 0.dp else 28.dp)
                    )
                    .background(
                        when {
                            fluentTaskbar -> Brush.verticalGradient(
                                listOf(Color(0xCC182236).copy(alpha = 1f - dockTransparency), Color(0xE60C1321).copy(alpha = 1f - dockTransparency))
                            )
                            maxW >= 600.dp && futuristicDesktop -> Brush.verticalGradient(
                                listOf(Color(0xF20C1B35).copy(alpha = 1f - dockTransparency), Color(0xE908142A).copy(alpha = 1f - dockTransparency), Color(0xF20A1730).copy(alpha = 1f - dockTransparency))
                            )
                            iconPack == "windows11" -> Brush.verticalGradient(listOf(Color(0xF20B1829).copy(alpha = 1f - dockTransparency), Color(0xF20B1829).copy(alpha = 1f - dockTransparency)))
                            else -> Brush.verticalGradient(listOf(Color(0xF016263D).copy(alpha = 1f - dockTransparency), Color(0xF016263D).copy(alpha = 1f - dockTransparency)))
                        },
                        RoundedCornerShape(if (fluentTaskbar) 24.dp else if (maxW >= 600.dp && futuristicDesktop) 28.dp else 20.dp)
                    )
                    .border(
                        if (iconPack == "windows11" && !fluentTaskbar) 0.dp else 1.dp,
                        if (fluentTaskbar) Brush.linearGradient(
                            listOf(Color(0x66FFFFFF), Color(0x22FFFFFF), Color(0x44FFFFFF))
                        ) else if (maxW >= 600.dp && futuristicDesktop) Brush.linearGradient(
                            listOf(Color(0xCC43D9FF), Color(0x665A7DFF), Color(0x998F68FF), Color(0x6643D9FF))
                        ) else Brush.linearGradient(listOf(Color(0xFF6686B5), Color(0xFF6686B5))),
                        RoundedCornerShape(if (fluentTaskbar) 24.dp else if (maxW >= 600.dp && futuristicDesktop) 28.dp else 20.dp)
                    )
                    .padding(
                        horizontal = if (fluentTaskbar) 14.dp else if (iconPack == "windows11") 12.dp else if (maxW < 600.dp) 6.dp else 18.dp,
                        vertical = if (fluentTaskbar) 6.dp else if (maxW >= 600.dp && futuristicDesktop) 8.dp else if (iconPack == "windows11") 7.dp else 10.dp
                    )
                    .heightIn(min = if (fluentTaskbar) 56.dp else if (maxW >= 600.dp && futuristicDesktop) 72.dp else 60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (fluentTaskbar) Arrangement.SpaceBetween
                else if (maxW >= 600.dp && futuristicDesktop) Arrangement.SpaceEvenly
                else if (iconPack == "windows11") Arrangement.Center
                else Arrangement.spacedBy(
                    dockSpacing.dp.coerceIn(2.dp, 24.dp)
                )
            ) {
                DockAction(if (futuristicDesktop && maxW >= 600.dp) "▦" else "⌂", "HOME", accent, iconPack, iconScale = dockIconScale, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp, fluentStyle = fluentTaskbar, active = dashboardOpen) {
                    dashboardOpen = true
                    startOpen = false
                    orbitOpen = false
                    controlOpen = false
                    advancedOpen = false
                }

                DockAction(if (futuristicDesktop && maxW >= 600.dp) "⌕" else "✦", "START", accent, iconPack, iconScale = dockIconScale, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp, fluentStyle = fluentTaskbar, active = startOpen) {
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
                            .background(Brush.linearGradient(listOf(Color(0xDD102B49), Color(0xCC101A35), Color(0xDD1A2450))))
                            .border(1.dp, Brush.horizontalGradient(listOf(Color(0xAA38DFFF), Color(0x887E79FF))), RoundedCornerShape(14.dp))
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

                DockAction(if (futuristicDesktop && maxW >= 600.dp) "∞" else "◎", "ORBIT", Color(0xFFB69CFF), iconPack, iconScale = dockIconScale, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp, fluentStyle = fluentTaskbar, active = orbitOpen) {
                    // ORBIT is an explicit launcher command: repeated activation must never
                    // cancel the opening transition while accessibility dispatch is settling.
                    orbitOpen = true
                    startOpen = false
                    controlOpen = false
                    advancedOpen = false
                }

                DockAction(if (futuristicDesktop && maxW >= 600.dp) "⬡" else "☷", "CONTROL", Color(0xFFFFC27A), iconPack, iconScale = dockIconScale, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp, fluentStyle = fluentTaskbar, active = controlOpen) {
                    controlOpen = !controlOpen
                    if (controlOpen) controlMode = "personalization"
                    startOpen = false
                    orbitOpen = false
                    advancedOpen = false
                }
DockAction(if (futuristicDesktop && maxW >= 600.dp) "⚙" else "▤", "ADVANCED", accent, iconPack, iconScale = dockIconScale, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp, fluentStyle = fluentTaskbar, active = advancedOpen) {
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

                dockFavorites.mapNotNull { packageName ->
                    installedApps.firstOrNull { it.packageName == packageName }
                }.forEach { app ->
                    DockInstalledAppAction(
                        app = app,
                        accent = accent,
                        iconScale = dockIconScale,
                        compact = maxW < 390.dp,
                        onClick = {
                            launchApp(app.packageName)
                            dashboardOpen = false
                            startOpen = false
                            orbitOpen = false
                            controlOpen = false
                            advancedOpen = false
                        }
                    )
                }

                infinityWorkspaceWindowIds(windows.map { it.id }, windows.associate { it.id to it.workspace }, workspace).mapNotNull { id -> windows.firstOrNull { it.id == id } }.let { workspaceWindows ->
                    val allTaskIds = infinityDockWindowIds(
                        workspaceWindows.map { it.id },
                        workspaceWindows.associate { it.id to it.minimized }
                    )
                    val visibleTaskLimit = infinityDockVisibleTaskLimit(maxW.value)
                    val visibleTaskIds = infinityDockVisibleTaskIds(allTaskIds, visibleTaskLimit)
                    visibleTaskIds.forEach { taskId ->
                        val task = workspaceWindows.firstOrNull { it.id == taskId } ?: return@forEach
                        DockAction(if (task.minimized) "□" else "▣", infinityDockLabel(task.title), accent, iconPack, iconScale = dockIconScale, showRunningIndicator = dockIndicatorsEnabled, running = !task.minimized, compact = maxW < 390.dp, fluentStyle = fluentTaskbar, active = !task.minimized && focusedWindowId == task.id) {
                            val idx = windows.indexOfFirst { it.id == task.id }
                            if (idx >= 0) {
                                val tappedTask = windows[idx]
                                val activation = infinityDockResolveActivation(
                                    tappedTask.id, focusedWindowId, tappedTask.minimized
                                )
                                val minimizeTask = activation == null
                                windows[idx] = tappedTask.copy(minimized = minimizeTask)
                                if (minimizeTask) {
                                    focusedWindowId = infinityResolveWorkspaceFocus(
                                        null, windows.map { it.id },
                                        windows.associate { it.id to it.workspace },
                                        windows.filter { it.minimized }.map { it.id }.toSet(), workspace
                                    )
                                } else {
                                    // Dock activation must update both logical focus and AURORA z-order.
                                    focusWindowToFront(tappedTask.id)
                                }
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
                        DockAction("⋯", "MÁS VENTANAS: $hiddenTaskCount", accent, iconPack, compact = maxW < 390.dp, referenceStyle = futuristicDesktop && maxW >= 600.dp, fluentStyle = fluentTaskbar) {
                            orbitOpen = true
                            dashboardOpen = false
                            startOpen = false
                            controlOpen = false
                            advancedOpen = false
                        }
                    }
                }

                val pendingNotificationCount = InfinityNotificationStore.notifications.size
                DockAction(
                    if (pendingNotificationCount > 0) pendingNotificationCount.coerceAtMost(99).toString() else "♢",
                    "NOTIFICACIONES",
                    Color(0xFF69D9FF),
                    iconPack,
                    iconScale = dockIconScale,
                    compact = maxW < 390.dp,
                    referenceStyle = futuristicDesktop && maxW >= 600.dp,
                    fluentStyle = fluentTaskbar,
                    active = advancedOpen
                ) {
                    advancedOpen = true
                    startOpen = false
                    orbitOpen = false
                    controlOpen = false
                    dashboardOpen = false
                }

                InfinityReferenceSystemTray(
                    modifier = Modifier.padding(horizontal = 6.dp).clickable {
                        controlOpen = true
                        controlMode = "personalization"
                        startOpen = false
                        orbitOpen = false
                        advancedOpen = false
                    }
                )

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
            }


            if (!dockVisible) {
                Button(
                    onClick = {
                        dockVisible = true
                        prefs.edit().putBoolean("dock_visible", true).apply()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).zIndex(30f).testTag("infinity-dock-show"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF173653))
                ) { Text("MOSTRAR DOCK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
            }

            // INFINITY START: búsqueda y catálogo de aplicaciones.
            if (startOpen) {
                Card(
                    Modifier.then(if (fluentTaskbar || iconPack == "windows11") Modifier.align(Alignment.BottomCenter) else Modifier.align(Alignment.BottomStart))
                        .zIndex(15f)
                        .fillMaxWidth(0.94f)
                        .widthIn(max = 420.dp)
                        .padding(bottom = infinityStartPanelBottomPaddingDp(maxH.value, dockSafeBottom).dp)
                        .heightIn(max = infinityStartPanelMaxHeightDp((dockSafeBottom - dockSafeTop).coerceAtLeast(1f)).dp)
                        .animateContentSize()
                        .shadow(28.dp, RoundedCornerShape(28.dp))
                        .testTag("infinity-start-panel"),
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
                    Column(
                        Modifier.padding(18.dp)
                            // The Start card is constrained above the Dock. Its full content
                            // must scroll inside that viewport instead of drawing underneath it.
                            .verticalScroll(rememberScrollState())
                            .testTag("infinity-start-content")
                    ) {
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
                                            when {
                                                item.id == "orbit" -> orbitOpen = true
                                                item.packageName != null -> launchApp(item.packageName)
                                                else -> focusedWindowId = openInfinityWindow(item.id, windows, workspace, maxW.value, maxH.value, dockSafeBottom, dockSafeTop)
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
                                    Modifier.fillMaxWidth().padding(vertical = 7.dp)
                                        .testTag("infinity-start-installed-app-${app.packageName}"),
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
                                            .background(Color(0xFF22364F))
                                            .clickable {
                                                launchApp(app.packageName)
                                                startOpen = false
                                            },
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
                                        modifier = Modifier.weight(1f).padding(start = 9.dp)
                                            .clickable {
                                                launchApp(app.packageName)
                                                startOpen = false
                                            },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    TextButton(
                                        onClick = {
                                            val next = if (app.packageName in dockFavorites) {
                                                infinityDockRemoveFavorite(dockFavorites, app.packageName)
                                            } else {
                                                infinityDockAddFavorite(dockFavorites, app.packageName, installedPackageNames)
                                            }
                                            persistDockFavorites(next)
                                        },
                                        modifier = Modifier.testTag("infinity-dock-favorite-toggle-${app.packageName}"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (app.packageName in dockFavorites) "QUITAR" else "ANCLAR",
                                            color = if (app.packageName in dockFavorites) Color(0xFFFFA78E) else accent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    val isDesktopShortcut = app.packageName in desktopShortcutPackages
                                    IconButton(
                                        onClick = {
                                            persistDesktopShortcutPackages(
                                                infinityToggleDesktopShortcut(desktopShortcutPackages, app.packageName, installedPackageNames)
                                            )
                                        },
                                        modifier = Modifier.size(34.dp).testTag("infinity-desktop-shortcut-toggle-${app.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = if (isDesktopShortcut) Icons.Filled.Close else Icons.Filled.Apps,
                                            contentDescription = if (isDesktopShortcut) "Quitar acceso directo del escritorio" else "Añadir acceso directo al escritorio",
                                            tint = accent,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Box {
                                        TextButton(
                                            onClick = { shortcutTransferMenuPackage = app.packageName },
                                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                                            modifier = Modifier.testTag("infinity-shortcut-transfer-${app.packageName}")
                                        ) { Text("ESPACIOS", color = accent, fontSize = 8.sp) }
                                        DropdownMenu(
                                            expanded = shortcutTransferMenuPackage == app.packageName,
                                            onDismissRequest = { shortcutTransferMenuPackage = null }
                                        ) {
                                            (1..INFINITY_VIRTUAL_DESKTOP_COUNT).filter { it != workspace }.forEach { targetWorkspace ->
                                                val targetName = workspaceNames[targetWorkspace] ?: infinityDefaultVirtualDesktopName(targetWorkspace)
                                                DropdownMenuItem(
                                                    text = { Text("COPIAR A $targetName") },
                                                    onClick = {
                                                        val targetPackages = infinityParseDesktopShortcutPackages(
                                                            prefs.getString(infinityWorkspaceDesktopShortcutKey(targetWorkspace), null)
                                                        )
                                                        val copied = infinityCopyWorkspaceShortcut(
                                                            desktopShortcutPackages, targetPackages, app.packageName
                                                        )
                                                        persistWorkspaceShortcutPackages(targetWorkspace, copied)
                                                        shortcutTransferMenuPackage = null
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("MOVER A $targetName") },
                                                    enabled = app.packageName in desktopShortcutPackages,
                                                    onClick = {
                                                        val targetPackages = infinityParseDesktopShortcutPackages(
                                                            prefs.getString(infinityWorkspaceDesktopShortcutKey(targetWorkspace), null)
                                                        )
                                                        val (sourceNext, targetNext) = infinityMoveWorkspaceShortcut(
                                                            desktopShortcutPackages, targetPackages, app.packageName
                                                        )
                                                        persistWorkspaceShortcutPackages(targetWorkspace, targetNext)
                                                        persistDesktopShortcutPackages(sourceNext)
                                                        shortcutTransferMenuPackage = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (app.packageName in dockFavorites) {
                                        val favoriteIndex = dockFavorites.indexOf(app.packageName)
                                        TextButton(
                                            onClick = {
                                                persistDockFavorites(
                                                    infinityDockMoveFavorite(dockFavorites, favoriteIndex, favoriteIndex - 1)
                                                )
                                            },
                                            enabled = favoriteIndex > 0,
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) { Text("↑", color = accent, fontSize = 14.sp) }
                                        TextButton(
                                            onClick = {
                                                persistDockFavorites(
                                                    infinityDockMoveFavorite(dockFavorites, favoriteIndex, favoriteIndex + 1)
                                                )
                                            },
                                            enabled = favoriteIndex < dockFavorites.lastIndex,
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) { Text("↓", color = accent, fontSize = 14.sp) }
                                    }
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
                        // Keep the close action at the top of the scrollable panel so it
                        // remains reachable on compact screens and does not require a long scroll.
                        TextButton(
                            onClick = {
                                Log.d("INFINITY_TEST_DIAGNOSTIC", "ORBIT close callback invoked; orbitOpen before close=$orbitOpen")
                                orbitOpen = false
                                startOpen = false
                                controlOpen = false
                                advancedOpen = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("infinity-orbit-close")
                        ) {
                            Text("CERRAR ORBIT", color = accent, fontSize = 10.sp)
                        }

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
                                    focusedWindowId = openInfinityWindow("connect", windows, workspace, maxW.value, maxH.value, dockSafeBottom, dockSafeTop)
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
                                    val minimizeTargets = infinityWorkspaceMinimizeTargets(
                                        windows.map { it.id },
                                        windows.associate { it.id to it.workspace },
                                        workspace
                                    )
                                    windows.indices
                                        .filter { windows[it].id in minimizeTargets }
                                        .forEach { index ->
                                            windows[index] = windows[index].copy(minimized = true)
                                        }
                                    focusedWindowId = infinityResolveWorkspaceFocus(
                                        focusedWindowId,
                                        windows.map { it.id },
                                        windows.associate { it.id to it.workspace },
                                        windows.filter { it.minimized }.map { it.id }.toSet(),
                                        workspace
                                    )
                                    orbitOpen = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("infinity-orbit-minimize-all"),
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
                                                        focusWindowToFront(win.id)
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
                                                        focusWindowToFront(win.id)
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

                                        Box {
                                            TextButton(onClick = { windowTransferMenuId = win.id }) {
                                                Text("MOVER", fontSize = 9.sp)
                                            }
                                            DropdownMenu(
                                                expanded = windowTransferMenuId == win.id,
                                                onDismissRequest = { windowTransferMenuId = null }
                                            ) {
                                                (1..INFINITY_VIRTUAL_DESKTOP_COUNT).filter { it != win.workspace }.forEach { targetWorkspace ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text("A ${workspaceNames[targetWorkspace] ?: infinityDefaultVirtualDesktopName(targetWorkspace)}")
                                                        },
                                                        onClick = {
                                                            val index = windows.indexOfFirst { it.id == win.id }
                                                            if (index >= 0) {
                                                                val allModels = windows.mapIndexed { order, item ->
                                                                    InfinityAuroraWindow(
                                                                        id = item.id, workspace = item.workspace,
                                                                        x = item.x, y = item.y, width = item.width, height = item.height,
                                                                        minimized = item.minimized, maximized = item.maximized,
                                                                        zIndex = order.toLong()
                                                                    )
                                                                }
                                                                if (infinityCanMoveAuroraWindow(allModels, win.id, win.kind, targetWorkspace)) {
                                                                    cancelWindowGeometryTransaction(win.id)
                                                                    val current = windows[index]
                                                                    val model = InfinityAuroraWindow(
                                                                        id = current.id,
                                                                        workspace = current.workspace,
                                                                        x = current.x,
                                                                        y = current.y,
                                                                        width = current.width,
                                                                        height = current.height,
                                                                        minimized = current.minimized,
                                                                        maximized = current.maximized,
                                                                        zIndex = index.toLong()
                                                                    )
                                                                    val moved = infinityMoveAuroraWindow(model, win.kind, targetWorkspace)
                                                                    val movedWindow = current.copy(id = moved.id, workspace = moved.workspace)
                                                                    prefs.getString("note_${win.id}", null)?.let { note ->
                                                                        prefs.edit()
                                                                            .putString("note_${moved.id}", note)
                                                                            .remove("note_${win.id}")
                                                                            .apply()
                                                                    }
                                                                    windows[index] = movedWindow
                                                                    focusedWindowId = infinityResolveFocusAfterWindowDeactivation(
                                                                        win.id, focusedWindowId,
                                                                        windows.map { it.id },
                                                                        windows.associate { it.id to it.workspace },
                                                                        windows.filter { it.minimized }.map { it.id }.toSet(),
                                                                        workspace
                                                                    )
                                                                } else {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Ya existe una ventana de este tipo en ese escritorio.",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                            windowTransferMenuId = null
                                                        }
                                                    )
                                                }
                                            }
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



                    }
                }
            }
 

            // INFINITY CONTROL CENTER: personalización nativa y controles Android reales.
            if (controlOpen && controlMode == "device") {
                LaunchedEffect(controlOpen, controlMode, resumeSignal) {
                    controlSnapshot = InfinityControlCenterSnapshot.read(context)
                }
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .zIndex(16f)
                        .padding(end = 14.dp, top = maxOf(76.dp, dockSafeTop.dp + 12.dp), bottom = 112.dp)
                        .widthIn(max = infinityControlDevicePanelMaxWidthDp(maxW.value).dp)
                        .fillMaxWidth()
                        .heightIn(
                            max = minOf(
                                infinityControlPanelMaxHeightDp((maxH.value - dockSafeTop).coerceAtLeast(1f)),
                                (maxH.value - dockSafeTop - 188f).coerceAtLeast(1f)
                            ).dp
                        )
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
                            if (!opened) Toast.makeText(context, "No se pudo abrir el permiso de brillo de Android.", Toast.LENGTH_LONG).show()
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
                        .heightIn(
                            max = minOf(
                                (maxH - dockSafeTop.dp - 200.dp).coerceAtLeast(220.dp),
                                (maxH - dockSafeTop.dp - 188.dp).coerceAtLeast(1.dp)
                            )
                        )
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
                        Text("BARRA DE TAREAS", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = taskbarStyle == "infinity",
                                onClick = {
                                    taskbarStyle = "infinity"
                                    prefs.edit().putString("taskbar_style", taskbarStyle).apply()
                                },
                                label = { Text("INFINITY") }
                            )
                            FilterChip(
                                selected = taskbarStyle == "fluent_acrylic",
                                onClick = {
                                    taskbarStyle = "fluent_acrylic"
                                    prefs.edit().putString("taskbar_style", taskbarStyle).apply()
                                },
                                label = { Text("Fluent Acrylic") }
                            )
                        }
                        Text("DOCK INFINITY", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Escala del Dock: ${(dockScale * 100).toInt()}%", color = InfinityMuted, fontSize = 10.sp)
                        Slider(
                            value = dockScale,
                            onValueChange = {
                                dockScale = it
                                prefs.edit().putFloat("dock_scale", it).apply()
                            },
                            valueRange = 0.75f..1.25f
                        )
                        Text("Tamaño de iconos: ${(dockIconScale * 100).toInt()}%", color = InfinityMuted, fontSize = 10.sp)
                        Slider(
                            value = dockIconScale,
                            onValueChange = {
                                dockIconScale = it
                                prefs.edit().putFloat("dock_icon_scale", it).apply()
                            },
                            valueRange = 0.75f..1.35f
                        )
                        Text("Transparencia: ${(dockTransparency * 100).toInt()}%", color = InfinityMuted, fontSize = 10.sp)
                        Slider(
                            value = dockTransparency,
                            onValueChange = {
                                dockTransparency = it
                                prefs.edit().putFloat("dock_transparency", it).apply()
                            },
                            valueRange = 0f..0.45f
                        )
                        Text("Separación: ${dockSpacing.toInt()} dp", color = InfinityMuted, fontSize = 10.sp)
                        Slider(
                            value = dockSpacing,
                            onValueChange = {
                                dockSpacing = it
                                prefs.edit().putFloat("dock_spacing", it).apply()
                            },
                            valueRange = 2f..18f
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Desplazamiento horizontal", color = InfinityMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = dockScrollEnabled,
                                onCheckedChange = {
                                    dockScrollEnabled = it
                                    prefs.edit().putBoolean("dock_scroll_enabled", it).apply()
                                }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Indicadores de ventanas", color = InfinityMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = dockIndicatorsEnabled,
                                onCheckedChange = {
                                    dockIndicatorsEnabled = it
                                    prefs.edit().putBoolean("dock_indicators_enabled", it).apply()
                                }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Mostrar Dock", color = InfinityMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = dockVisible,
                                onCheckedChange = { visible ->
                                    dockVisible = visible
                                    prefs.edit().putBoolean("dock_visible", visible).apply()
                                }
                            )
                        }
                        Text("Posición del Dock", color = InfinityMuted, fontSize = 10.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(InfinityDockPosition.BOTTOM to "Inferior", InfinityDockPosition.TOP to "Superior").forEach { option ->
                                FilterChip(
                                    selected = dockPosition == option.first,
                                    onClick = {
                                        dockPosition = option.first
                                        prefs.edit().putString("dock_position", option.first.name).apply()
                                    },
                                    label = { Text(option.second) }
                                )
                            }
                        }
                        Text("La ocultación automática se mantiene desactivada en pantallas táctiles para evitar accesos inaccesibles.", color = InfinityMuted, fontSize = 9.sp)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Formato de hora 24 h", color = InfinityMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = clock24h,
                                onCheckedChange = {
                                    clock24h = it
                                    prefs.edit().putBoolean("clock_24h", it).apply()
                                }
                            )
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
                                FilterChip(selected = backgroundPreset == preset, onClick = {
                                backgroundPreset = preset
                                prefs.edit()
                                    .putString(infinityVirtualDesktopBackgroundPresetKey(workspace), preset)
                                    .remove(infinityVirtualDesktopWallpaperKey(workspace))
                                    .apply()
                                wallpaperUri = null
                            }, label = { Text(label) })
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
                            onClick = launchWallpaperPicker,
                            modifier = Modifier.fillMaxWidth().testTag("infinity-wallpaper-picker")
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
                // In landscape, reclaim vertical space for the scrollable ADVANCED
                // panel instead of stacking portrait-only top/bottom reservations.
                val advancedTopInset = if (maxH < 420.dp) 44.dp else 68.dp
                val advancedBottomInset = if (maxH < 420.dp) 64.dp else 104.dp
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(15f)
                        .padding(top = advancedTopInset, end = 10.dp, bottom = advancedBottomInset)
                        .widthIn(max = 430.dp)
                        .fillMaxWidth()
                        .heightIn(max = (maxH - advancedTopInset - advancedBottomInset - 12.dp).coerceAtLeast(120.dp))
                ) {
                    InfinityAdvancedPanel(
                        onClose = { advancedOpen = false },
                        desktopTheme = iconPack,
                        themeId = themeChoice
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
    fluentStyle: Boolean = false,
    active: Boolean = false,
    running: Boolean = active,
    showRunningIndicator: Boolean = true,
    iconScale: Float = 1f,
    onClick: () -> Unit
) {
    val actionRadius = when {
        fluentStyle -> 12.dp
        desktopTheme == "windows11" -> 9.dp
        else -> 14.dp
    }
    val actionSurface = when {
        referenceStyle -> Color.Transparent
        fluentStyle -> Color.White.copy(alpha = .045f)
        else -> tint.copy(alpha = if (desktopTheme == "windows11") .18f else .09f)
    }
    Column(
        Modifier
            // Tablet reference dock keeps every primary action comfortably touchable.
             .widthIn(min = (if (fluentStyle) 44.dp else if (referenceStyle) 48.dp else if (compact) 40.dp else 48.dp) * iconScale)
            .clip(RoundedCornerShape(actionRadius))
            .background(actionSurface, RoundedCornerShape(actionRadius))
            .then(
                if (referenceStyle) Modifier
                else Modifier.border(
                    1.dp,
                    if (fluentStyle) Color.White.copy(alpha = .10f) else tint.copy(alpha = .22f),
                    RoundedCornerShape(actionRadius)
                )
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = (if (fluentStyle || referenceStyle) 4.dp else if (compact) 2.dp else 5.dp) * iconScale,
                vertical = (if (fluentStyle || referenceStyle) 4.dp else if (compact) 4.dp else 6.dp) * iconScale
            )
            // Keep the test/accessibility identity on the same node that owns the real click action.
            .testTag("infinity-dock-${description.lowercase()}")
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp * iconScale)
    ) {
        Box(
            Modifier.size((if (fluentStyle || referenceStyle) 40.dp else if (compact) 30.dp else 36.dp) * iconScale)
                .background(
                    if (fluentStyle) Brush.linearGradient(
                        listOf(Color.White.copy(alpha = .055f), Color.White.copy(alpha = .025f))
                    ) else Brush.linearGradient(listOf(tint.copy(alpha = .24f), tint.copy(alpha = .08f))),
                    RoundedCornerShape(if (desktopTheme == "windows11" || fluentStyle) 10.dp else 12.dp)
                )
                .border(
                    1.dp,
                    if (fluentStyle) Color.White.copy(alpha = .10f) else tint.copy(alpha = .58f),
                    RoundedCornerShape(if (desktopTheme == "windows11" || fluentStyle) 10.dp else 12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val dockIcon = if (referenceStyle || fluentStyle) when (description) {
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
                    modifier = Modifier.size(23.dp * iconScale)
                )
            } else {
                Text(glyph, color = tint, fontSize = (if (referenceStyle) 29f else 22f).times(iconScale).sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!compact && !referenceStyle && !fluentStyle) {
            Text(
                description,
                color = Color.White.copy(.94f),
                fontSize = (8f * iconScale).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .35.sp,
                maxLines = 1
            )
        }
        if (fluentStyle && active) {
            Box(
                Modifier.width(14.dp).height(3.dp)
                    .background(Color(0xFF38BDF8), RoundedCornerShape(999.dp))
            )
        } else if (showRunningIndicator && running && !fluentStyle) {
            Box(Modifier.width(if (compact) 12.dp else 18.dp).height(2.dp).background(tint.copy(alpha = .85f), RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun DockInstalledAppAction(
    app: InstalledApp,
    accent: Color,
    active: Boolean = false,
    compact: Boolean = false,
    iconScale: Float = 1f,
    onClick: () -> Unit
) {
    val context = LocalContext.current
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
    Column(
        Modifier
            .widthIn(min = 48.dp * iconScale)
            .clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp * iconScale, vertical = 3.dp * iconScale)
            .testTag("infinity-dock-app-${app.packageName}")
            .semantics(mergeDescendants = true) {
                contentDescription = "Abrir ${app.label}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier.size(38.dp * iconScale)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF22364F))
                .border(1.dp, accent.copy(alpha = .48f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(Icons.Filled.Apps, contentDescription = null, tint = accent)
            }
        }
        if (!compact) {
            Text(
                app.label,
                color = Color.White.copy(alpha = .94f),
                fontSize = (8f * iconScale).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (active) {
            Box(Modifier.width(14.dp).height(2.dp).background(accent, RoundedCornerShape(2.dp)))
        }
    }
}

private fun openInfinityWindow(
    id: String,
    windows: MutableList<DeskWindow>,
    workspace: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    safeBottom: Float = viewportHeight,
    safeTop: Float = 0f
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
        val availableHeight = (safeBottom - safeTop).coerceAtLeast(1f)
        val (windowWidth, windowHeight) = infinityInitialWindowSize(
            viewportWidth,
            availableHeight
        )
        val (windowX, windowY) = infinityInitialWindowPosition(
            infinityWorkspaceWindowCount(
                windows.map { it.id },
                windows.associate { it.id to it.workspace },
                workspace
            ),
            viewportWidth,
            availableHeight,
            windowWidth,
            windowHeight
        )
        windows.add(
            DeskWindow(
                id = windowId,
                title = titles[id] ?: "INFINITY",
                kind = id,
                x = windowX,
                y = safeTop + windowY,
                width = windowWidth,
                height = windowHeight,
                workspace = workspace
            )
        )
    }

    // Every entry point (desktop shortcut, START, dashboard and context menu) uses this
    // factory. Restore/open must also raise an existing window in the visual stack;
    // assigning focusedWindowId alone does not change the draw order of AURORA siblings.
    val activeIndex = windows.indexOfFirst { it.id == windowId }
    if (activeIndex >= 0) {
        val ordered = infinityRaiseWindowAfterManipulation(windows.toList(), windows[activeIndex]) { it.id }
        if (ordered.map { it.id } != windows.map { it.id }) {
            windows.clear()
            windows.addAll(ordered)
        }
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
            Button(onClick = onChooseFolder, modifier = Modifier.testTag("infinity-folder-picker")) {
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
    onGeometryStart: () -> Unit,
    onGeometryCommit: () -> Unit,
    onGeometryCancel: () -> Unit,
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
    val windowThemeStops = infinityThemeBackgroundStops(themeChoice, lightMode)
    val windowSurface = windowThemeStops.first().copy(alpha = .95f)
    val titleBarSurface = windowThemeStops[1]
    val windowBorder = accent.copy(if (desktopTheme == "windows11") .58f else .38f)
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
                    detectDragGestures(
                        onDragStart = {
                            onFocus()
                            onGeometryStart()
                        },
                        onDragEnd = onGeometryCommit,
                        onDragCancel = onGeometryCancel
                    ) { change, drag ->
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
                    Button(
                        onClick = onChooseWallpaper,
                        modifier = Modifier.testTag("infinity-wallpaper-picker")
                    ) { Text("Elegir fondo de pantalla") }
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
                    detectDragGestures(
                        onDragStart = {
                            onFocus()
                            onGeometryStart()
                        },
                        onDragEnd = onGeometryCommit,
                        onDragCancel = onGeometryCancel
                    ) { change, drag ->
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
