package com.infinity.core

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
    val zIndex: Long = 0L
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
    val nextZIndex: Long = 1L
) {
    fun visibleWindows(workspace: Int): List<InfinityAuroraWindow> =
        windows.filter { it.workspace == infinityNormalizeWorkspace(workspace) && !it.minimized }
            .sortedBy { it.zIndex }

    fun focus(windowId: String, workspace: Int): InfinityAuroraWindowState {
        val target = windows.firstOrNull {
            it.id == windowId && it.workspace == infinityNormalizeWorkspace(workspace) && !it.minimized
        } ?: return this
        val raised = target.copy(zIndex = nextZIndex)
        return copy(
            windows = windows.map { if (it.id == windowId) raised else it },
            activeWindowId = windowId,
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
        if (transaction.workspace != transaction.original.workspace) return this
        val bounded = infinityAuroraBoundWindow(transaction.current, bounds)
        return copy(windows = windows.map {
            if (it.id == transaction.windowId && it.workspace == transaction.workspace) bounded else it
        })
    }

    /** Cancellation is a true rollback: no state mutation and no persistence required. */
    fun cancelGeometry(transaction: InfinityAuroraWindowTransaction): InfinityAuroraWindowState = this

    fun switchWorkspace(workspace: Int): InfinityAuroraWindowState {
        val target = infinityNormalizeWorkspace(workspace)
        val nextActive = windows.filter { it.workspace == target && !it.minimized }
            .maxByOrNull { it.zIndex }?.id
        return copy(activeWindowId = nextActive)
    }
}

internal fun infinityAuroraWindowPositionKey(workspace: Int, windowId: String, property: String): String {
    val id = windowId.trim().lowercase(java.util.Locale.ROOT)
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64)
    require(id.isNotBlank())
    require(property in setOf("x", "y", "width", "height", "maximized", "minimized"))
    return "aurora_workspace_${infinityNormalizeWorkspace(workspace)}_${id}_$property"
}
