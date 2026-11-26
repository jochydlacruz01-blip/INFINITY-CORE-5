package com.infinity.core

import kotlin.math.min

/** Pure geometry helpers for mapping a remote framebuffer into a local viewport. */
data class ConnectSize(val width: Int, val height: Int) {
    init { require(width > 0 && height > 0) { "Dimensions must be positive." } }
}

data class ConnectViewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    init { require(width > 0f && height > 0f) }
}

data class ConnectRemotePoint(val x: Int, val y: Int)

object InfinityConnectViewportRules {
    /** FIT mode: preserve aspect ratio and center the framebuffer within available space. */
    fun fit(local: ConnectSize, remote: ConnectSize): ConnectViewport {
        val scale = min(local.width.toFloat() / remote.width, local.height.toFloat() / remote.height)
        val w = remote.width * scale
        val h = remote.height * scale
        return ConnectViewport((local.width - w) / 2f, (local.height - h) / 2f, w, h)
    }

    /** Convert local viewport coordinates to remote pixels, clamping to framebuffer edges. */
    fun mapPointer(localX: Float, localY: Float, viewport: ConnectViewport, remote: ConnectSize): ConnectRemotePoint {
        val nx = ((localX - viewport.left) / viewport.width).coerceIn(0f, 1f)
        val ny = ((localY - viewport.top) / viewport.height).coerceIn(0f, 1f)
        return ConnectRemotePoint(
            (nx * (remote.width - 1)).toInt().coerceIn(0, remote.width - 1),
            (ny * (remote.height - 1)).toInt().coerceIn(0, remote.height - 1)
        )
    }

    /** Normalized pointer coordinates are safe for forwarding even when touch starts in letterbox area. */
    fun normalizePointer(localX: Float, localY: Float, viewport: ConnectViewport): Pair<Float, Float> =
        ((localX - viewport.left) / viewport.width).coerceIn(0f, 1f) to
            ((localY - viewport.top) / viewport.height).coerceIn(0f, 1f)
}
