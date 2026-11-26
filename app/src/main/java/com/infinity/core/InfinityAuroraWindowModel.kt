package com.infinity.core

/** Normal (restorable) geometry is expressed in dp. */
internal data class InfinityAuroraWindowGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/** Persistable geometry is expressed in dp and is owned by exactly one workspace. */
internal data class InfinityAuroraWindow(
    val id: String,
    val workspace: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val minimized: Boolean = false,
    val maximized: Boolean = false,
    val zIndex: Long = 0L,
    /** Captured exactly once when maximizing; null for normal or legacy non-maximized windows. */
    val restoreGeometry: InfinityAuroraWindowGeometry? = null
)

internal data class InfinityAuroraWindowTransaction(
    val windowId: String,
    val workspace: Int,
    val original: InfinityAuroraWindow,
    val current: InfinityAuroraWindow
)

internal data class InfinityAuroraWindowState(
    val windows: List<InfinityAuroraWindow> = emptyList(),
    val activeWindowId: String? = null,
    val activeWindowWorkspace: Int? = null,
    val nextZIndex: Long = 1L
) {
    fun maximize(windowId: String, workspace: Int, bounds: InfinityAuroraSafeBounds): InfinityAuroraWindowState {
        val targetWorkspace = infinityNormalizeWorkspace(workspace)
        val target = windows.firstOrNull { it.id == windowId && it.workspace == targetWorkspace && !it.minimized }
            ?: return this
        if (target.maximized) return this
        val normal = InfinityAuroraWindowGeometry(target.x, target.y, target.width, target.height)
        val maximized = infinityAuroraMaximizedWindow(target, bounds).copy(restoreGeometry = normal)
        return copy(windows = windows.map { if (it.id == windowId && it.workspace == targetWorkspace) maximized else it })
    }

    fun restore(windowId: String, workspace: Int): InfinityAuroraWindowState {
        val targetWorkspace = infinityNormalizeWorkspace(workspace)
        val target = windows.firstOrNull { it.id == windowId && it.workspace == targetWorkspace }
            ?: return this
        val normal = target.restoreGeometry ?: return this
        val restored = target.copy(
            x = normal.x, y = normal.y, width = normal.width, height = normal.height,
            maximized = false, restoreGeometry = null
        )
        return copy(windows = windows.map { if (it.id == windowId && it.workspace == targetWorkspace) restored else it })
    }

    fun visibleWindows(workspace: Int): List<InfinityAuroraWindow> =
        windows.filter { it.workspace == infinityNormalizeWorkspace(workspace) && !it.minimized }
            .sortedBy { it.zIndex }

    fun focus(windowId: String, workspace: Int): InfinityAuroraWindowState {
        val targetWorkspace = infinityNormalizeWorkspace(workspace)
        val target = windows.firstOrNull {
            it.id == windowId && it.workspace == targetWorkspace && !it.minimized
        } ?: return this
        val raised = target.copy(zIndex = nextZIndex)
        return copy(
            windows = windows.map { if (it.id == windowId && it.workspace == targetWorkspace) raised else it },
            activeWindowId = windowId,
            activeWindowWorkspace = targetWorkspace,
            nextZIndex = if (nextZIndex == Long.MAX_VALUE) 1L else nextZIndex + 1L
        )
    }

    fun beginGeometryChange(windowId: String, workspace: Int): InfinityAuroraWindowTransaction? {
        val target = windows.firstOrNull {
            it.id == windowId && it.workspace == infinityNormalizeWorkspace(workspace) &&
                !it.minimized && !it.maximized
        } ?: return null
        return InfinityAuroraWindowTransaction(windowId, target.workspace, target, target)
    }

    fun updateGeometry(transaction: InfinityAuroraWindowTransaction, candidate: InfinityAuroraWindow): InfinityAuroraWindowTransaction {
        require(candidate.id == transaction.windowId && candidate.workspace == transaction.workspace)
        return transaction.copy(current = candidate)
    }

    /** Commit once at gesture end. Callers persist only this returned geometry. */
    fun commitGeometry(transaction: InfinityAuroraWindowTransaction, bounds: InfinityAuroraSafeBounds): InfinityAuroraWindowState {
        if (transaction.workspace != transaction.original.workspace ||
            transaction.current.id != transaction.windowId || transaction.current.workspace != transaction.workspace
        ) return this
        val target = windows.firstOrNull { it.id == transaction.windowId && it.workspace == transaction.workspace } ?: return this
        if (target.minimized || target.maximized) return this
        val bounded = infinityAuroraBoundWindow(transaction.current, bounds)
        return copy(windows = windows.map {
            if (it.id == transaction.windowId && it.workspace == transaction.workspace) bounded else it
        })
    }

    /** Cancellation is a true rollback: no state mutation and no persistence required. */
    fun cancelGeometry(transaction: InfinityAuroraWindowTransaction): InfinityAuroraWindowState = this

    /** Minimize a window and select the top remaining visible window in that workspace. */
    fun minimize(windowId: String, workspace: Int): InfinityAuroraWindowState {
        val targetWorkspace = infinityNormalizeWorkspace(workspace)
        val target = windows.firstOrNull { it.id == windowId && it.workspace == targetWorkspace }
            ?: return this
        val updated = windows.map { if (it.id == windowId && it.workspace == targetWorkspace) it.copy(minimized = true) else it }
        // Legacy states may contain activeWindowId without its workspace. Infer only
        // when the ID is unique; never guess when the same ID exists in multiple workspaces.
        val currentActiveWorkspace = resolvedActiveWindowWorkspace()
        val minimizingActiveWindow = activeWindowId == windowId && currentActiveWorkspace == targetWorkspace
        val nextActive = if (minimizingActiveWindow) {
            updated.filter { it.workspace == targetWorkspace && !it.minimized }
                .maxByOrNull { it.zIndex }?.id
        } else activeWindowId
        return copy(
            windows = updated,
            activeWindowId = nextActive,
            activeWindowWorkspace = if (nextActive == null) null
                else if (minimizingActiveWindow) targetWorkspace else currentActiveWorkspace
        )
    }

    /** Restore and focus a task, assigning a fresh stacking order. */
    fun activate(windowId: String, workspace: Int): InfinityAuroraWindowState {
        val targetWorkspace = infinityNormalizeWorkspace(workspace)
        val target = windows.firstOrNull { it.id == windowId && it.workspace == targetWorkspace }
            ?: return this
        val raised = target.copy(minimized = false, zIndex = nextZIndex)
        return copy(
            windows = windows.map { if (it.id == windowId && it.workspace == targetWorkspace) raised else it },
            activeWindowId = windowId,
            activeWindowWorkspace = targetWorkspace,
            nextZIndex = if (nextZIndex == Long.MAX_VALUE) 1L else nextZIndex + 1L
        )
    }

    /** Close removes the task and deterministically selects the top remaining visible task. */
    fun close(windowId: String, workspace: Int): InfinityAuroraWindowState {
        val targetWorkspace = infinityNormalizeWorkspace(workspace)
        if (windows.none { it.id == windowId && it.workspace == targetWorkspace }) return this
        val remaining = windows.filterNot { it.id == windowId && it.workspace == targetWorkspace }
        val currentActiveWorkspace = resolvedActiveWindowWorkspace()
        val closingActiveWindow = activeWindowId == windowId && currentActiveWorkspace == targetWorkspace
        val nextActive = if (closingActiveWindow) {
            remaining.filter { it.workspace == targetWorkspace && !it.minimized }
                .maxByOrNull { it.zIndex }?.id
        } else {
            activeWindowId?.takeIf { id ->
                remaining.any { it.id == id && it.workspace == currentActiveWorkspace && !it.minimized }
            }
        }
        return copy(
            windows = remaining,
            activeWindowId = nextActive,
            activeWindowWorkspace = if (nextActive == null) null
                else if (closingActiveWindow) targetWorkspace else currentActiveWorkspace
        )
    }

    /** Resolves legacy focus state without weakening composite identity when IDs collide. */
    private fun resolvedActiveWindowWorkspace(): Int? {
        activeWindowWorkspace?.let { return infinityNormalizeWorkspace(it) }
        val activeId = activeWindowId ?: return null
        return windows.filter { it.id == activeId }.singleOrNull()?.workspace
    }

    fun switchWorkspace(workspace: Int): InfinityAuroraWindowState {
        val target = infinityNormalizeWorkspace(workspace)
        val nextActive = windows.filter { it.workspace == target && !it.minimized }
            .maxByOrNull { it.zIndex }?.id
        return copy(activeWindowId = nextActive, activeWindowWorkspace = target.takeIf { nextActive != null })
    }
}

internal fun infinityAuroraWindowPositionKey(workspace: Int, windowId: String, property: String): String {
    val id = windowId.trim().lowercase(java.util.Locale.ROOT)
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64)
    require(id.isNotBlank())
    require(property in setOf("x", "y", "width", "height", "maximized", "minimized"))
    return "aurora_workspace_${infinityNormalizeWorkspace(workspace)}_${id}_$property"
}


/** Includes restore bounds so maximize/restore-only changes invalidate persistence. */
internal fun infinityAuroraWindowSnapshotToken(window: InfinityAuroraWindow): String {
    val restore = window.restoreGeometry
    return listOf(
        window.id, window.workspace, window.x, window.y, window.width, window.height,
        window.minimized, window.maximized, window.zIndex,
        restore?.x, restore?.y, restore?.width, restore?.height
    ).joinToString("|")
}
