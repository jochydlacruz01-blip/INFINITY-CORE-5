package com.infinity.core

/** Immutable geometry snapshot used to make a window gesture atomic. */
internal data class InfinityWindowGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

internal data class InfinityWindowGeometryTransaction(
    val windowId: String,
    val original: InfinityWindowGeometry,
    val current: InfinityWindowGeometry = original
)

internal fun infinityBeginWindowGeometryTransaction(
    windowId: String,
    original: InfinityWindowGeometry
): InfinityWindowGeometryTransaction {
    require(windowId.isNotBlank())
    return InfinityWindowGeometryTransaction(windowId, original)
}

internal fun infinityUpdateWindowGeometryTransaction(
    transaction: InfinityWindowGeometryTransaction,
    candidate: InfinityWindowGeometry
): InfinityWindowGeometryTransaction = transaction.copy(current = candidate)

/** Commit only the final candidate, normalized against the currently visible safe area. */
internal fun infinityCommitWindowGeometryTransaction(
    transaction: InfinityWindowGeometryTransaction,
    candidate: InfinityWindowGeometry,
    viewportWidth: Float,
    viewportHeight: Float,
    safeBottom: Float
): InfinityWindowGeometry {
    val bounded = infinityNormalizeWindowBounds(
        candidate.x, candidate.y, candidate.width, candidate.height,
        viewportWidth, viewportHeight, safeBottom = safeBottom
    )
    return InfinityWindowGeometry(bounded.x, bounded.y, bounded.width, bounded.height)
}

/** A cancelled gesture restores the exact geometry captured at pointer-down. */
internal fun infinityCancelWindowGeometryTransaction(
    transaction: InfinityWindowGeometryTransaction
): InfinityWindowGeometry = transaction.original


/** Compose-independent owner for in-flight geometry transaction lifecycle. */
internal class InfinityWindowGeometryTransactionRegistry<T>(
    private val transactions: MutableMap<String, T>
) {
    fun remove(windowId: String): T? = transactions.remove(windowId)
    fun activeWindowIds(): Set<String> = transactions.keys.toSet()
    fun removeAll(): Map<String, T> {
        val removed = transactions.toMap()
        transactions.clear()
        return removed
    }
    fun retainOnly(activeWindowIds: Set<String>): Map<String, T> {
        val orphaned = transactions.filterKeys { it !in activeWindowIds }
        orphaned.keys.forEach(transactions::remove)
        return orphaned
    }
}
