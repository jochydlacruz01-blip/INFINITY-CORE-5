package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Workspace-filtered host. Parent persists only values received by onGeometryCommit. */
@Composable
internal fun InfinityAuroraWindowContainer(
    state: InfinityAuroraWindowState,
    workspace: Int,
    bounds: InfinityAuroraSafeBounds,
    onFocus: (String) -> Unit,
    onGeometryPreview: (InfinityAuroraWindow) -> Unit,
    onGeometryCommit: (InfinityAuroraWindow) -> Unit,
    onGeometryCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
    windowContent: @Composable BoxScope.(InfinityAuroraWindow) -> Unit
) {
    val density = LocalDensity.current
    val orderedWindows = state.visibleWindows(workspace)
    Box(modifier.fillMaxSize()) {
        orderedWindows.forEachIndexed { stackOrder, modelWindow ->
            key(modelWindow.id) {
                var renderedWindow by remember(modelWindow.id, modelWindow.workspace) {
                    mutableStateOf(modelWindow)
                }
                LaunchedEffect(modelWindow) { renderedWindow = modelWindow }
                val active = state.activeWindowId == modelWindow.id

                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                with(density) { renderedWindow.x.dp.roundToPx() },
                                with(density) { renderedWindow.y.dp.roundToPx() }
                            )
                        }
                        .size(width = renderedWindow.width.dp, height = renderedWindow.height.dp)
                        .zIndex(stackOrder.toFloat())
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            1.dp,
                            if (active) Color(0xFF56D6C9) else Color.White.copy(alpha = .22f),
                            RoundedCornerShape(18.dp)
                        )
                        .pointerInput(modelWindow.id, modelWindow.workspace, bounds) {
                            var original: InfinityAuroraWindow? = null
                            var preview: InfinityAuroraWindow? = null
                            detectDragGestures(
                                onDragStart = {
                                    onFocus(modelWindow.id)
                                    original = renderedWindow
                                    preview = renderedWindow
                                },
                                onDragEnd = {
                                    preview?.let {
                                        renderedWindow = it
                                        onGeometryCommit(it)
                                    }
                                    original = null
                                    preview = null
                                },
                                onDragCancel = {
                                    original?.let { renderedWindow = it }
                                    onGeometryCancel(modelWindow.id)
                                    original = null
                                    preview = null
                                }
                            ) { change, drag ->
                                change.consume()
                                val base = preview ?: renderedWindow
                                val candidate = infinityAuroraBoundWindow(
                                    base.copy(
                                        x = base.x + with(density) { drag.x.toDp().value },
                                        y = base.y + with(density) { drag.y.toDp().value }
                                    ), bounds
                                )
                                preview = candidate
                                renderedWindow = candidate
                                onGeometryPreview(candidate)
                            }
                        },
                    color = Color(0xF0142036),
                    shadowElevation = if (active) 24.dp else 12.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        windowContent(renderedWindow)
                        if (!renderedWindow.maximized) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(30.dp)
                                    .background(
                                        Color(0xFF56D6C9).copy(alpha = .20f),
                                        RoundedCornerShape(topStart = 10.dp)
                                    )
                                    .pointerInput(modelWindow.id, modelWindow.workspace, bounds) {
                                        var original: InfinityAuroraWindow? = null
                                        var preview: InfinityAuroraWindow? = null
                                        detectDragGestures(
                                            onDragStart = {
                                                onFocus(modelWindow.id)
                                                original = renderedWindow
                                                preview = renderedWindow
                                            },
                                            onDragEnd = {
                                                preview?.let {
                                                    renderedWindow = it
                                                    onGeometryCommit(it)
                                                }
                                                original = null
                                                preview = null
                                            },
                                            onDragCancel = {
                                                original?.let { renderedWindow = it }
                                                onGeometryCancel(modelWindow.id)
                                                original = null
                                                preview = null
                                            }
                                        ) { change, drag ->
                                            change.consume()
                                            val base = preview ?: renderedWindow
                                            val candidate = infinityAuroraBoundWindow(
                                                base.copy(
                                                    width = base.width + with(density) { drag.x.toDp().value },
                                                    height = base.height + with(density) { drag.y.toDp().value }
                                                ), bounds
                                            )
                                            preview = candidate
                                            renderedWindow = candidate
                                            onGeometryPreview(candidate)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
