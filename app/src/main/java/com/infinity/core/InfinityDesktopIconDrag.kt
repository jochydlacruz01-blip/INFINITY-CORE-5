package com.infinity.core

import androidx.compose.ui.geometry.Offset

/** Transactional drag state: cancel restores the original workspace snapshot. */
internal data class InfinityDesktopIconDragTransaction(
    val workspace: Int,
    val draggedIconId: String,
    val originalPositions: Map<String, Offset>,
    val currentPositions: Map<String, Offset>
)

internal fun infinityBeginDesktopIconDrag(
    workspace: Int,
    positions: Map<String, Offset>,
    iconId: String
): InfinityDesktopIconDragTransaction? {
    if (iconId !in positions) return null
    val snapshot = LinkedHashMap(positions)
    return InfinityDesktopIconDragTransaction(
        infinityNormalizeWorkspace(workspace), iconId, snapshot, LinkedHashMap(snapshot)
    )
}

internal fun infinityMoveDesktopIconDrag(
    transaction: InfinityDesktopIconDragTransaction,
    delta: Offset,
    viewportWidth: Float,
    safeDesktopHeight: Float,
    itemWidth: Float = 108f,
    itemHeight: Float = 150f,
    topInset: Float = 118f
): InfinityDesktopIconDragTransaction {
    val current = transaction.currentPositions[transaction.draggedIconId] ?: return transaction
    val moved = infinityBoundIconOffset(current, delta, viewportWidth, safeDesktopHeight, itemWidth, itemHeight, topInset)
    return transaction.copy(currentPositions = LinkedHashMap(transaction.currentPositions).apply {
        put(transaction.draggedIconId, moved)
    })
}

internal fun infinityCommitDesktopIconDrag(
    transaction: InfinityDesktopIconDragTransaction,
    orderedIds: List<String>,
    viewportWidth: Float,
    safeDesktopHeight: Float,
    itemWidth: Float = 108f,
    itemHeight: Float = 150f,
    horizontalGap: Float = 12f,
    verticalGap: Float = 14f,
    topInset: Float = 118f
): Map<String, Offset> {
    val ids = orderedIds.distinct()
    val resolved = infinityResolveIconOverlaps(
        ids.map { transaction.currentPositions[it] ?: transaction.originalPositions[it] ?: Offset.Zero },
        viewportWidth, safeDesktopHeight, itemWidth, itemHeight, horizontalGap, verticalGap, topInset
    )
    return ids.zip(resolved).toMap(LinkedHashMap())
}

internal fun infinityCancelDesktopIconDrag(
    transaction: InfinityDesktopIconDragTransaction
): Map<String, Offset> = LinkedHashMap(transaction.originalPositions)

/** Namespaced preference entries, suitable for one SharedPreferences.Editor.apply(). */
internal fun infinityWorkspaceIconPositionPreferences(
    workspace: Int,
    positions: Map<String, Offset>
): Map<String, Float> {
    val entries = LinkedHashMap<String, Float>()
    positions.forEach { (id, position) ->
        entries[infinityWorkspaceIconPositionKey(workspace, id, "x")] = position.x
        entries[infinityWorkspaceIconPositionKey(workspace, id, "y")] = position.y
    }
    return entries
}
