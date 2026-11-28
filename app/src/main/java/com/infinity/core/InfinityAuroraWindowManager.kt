package com.infinity.core

/** Canonical AURORA window record shared by the desktop and lifecycle manager. */
internal data class DeskWindow(
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
 * Coordinates AURORA lifecycle operations against the desktop's existing mutable list.
 * It intentionally does not allocate a second window collection or mirror window state.
 */
internal class InfinityAuroraWindowManager(
    private val windows: MutableList<DeskWindow>
) {
    fun focus(windowId: String): Boolean {
        val target = windows.firstOrNull { it.id == windowId && !it.minimized } ?: return false
        val ordered = infinityRaiseWindowAfterManipulation(windows.toList(), target) { it.id }
        if (ordered.map { it.id } != windows.map { it.id }) {
            windows.clear()
            windows.addAll(ordered)
        }
        return true
    }

    fun restore(windowId: String): Boolean {
        val index = windows.indexOfFirst { it.id == windowId }
        if (index < 0) return false
        windows[index] = windows[index].copy(minimized = false)
        focus(windowId)
        return true
    }

    fun minimize(windowId: String): Boolean {
        val index = windows.indexOfFirst { it.id == windowId }
        if (index < 0) return false
        windows[index] = windows[index].copy(minimized = true)
        return true
    }

    fun toggleMaximized(windowId: String): Boolean {
        val index = windows.indexOfFirst { it.id == windowId && !it.minimized }
        if (index < 0) return false
        windows[index] = windows[index].copy(maximized = !windows[index].maximized)
        focus(windowId)
        return true
    }

    fun close(windowId: String): Boolean = windows.removeAll { it.id == windowId }

    fun minimizeAll(windowIds: Set<String>): Int {
        var changed = 0
        windows.indices.forEach { index ->
            if (windows[index].id in windowIds && !windows[index].minimized) {
                windows[index] = windows[index].copy(minimized = true)
                changed++
            }
        }
        return changed
    }

    fun windowsInWorkspace(workspace: Int): List<DeskWindow> =
        windows.filter { it.workspace == infinityNormalizeWorkspace(workspace) }
}
