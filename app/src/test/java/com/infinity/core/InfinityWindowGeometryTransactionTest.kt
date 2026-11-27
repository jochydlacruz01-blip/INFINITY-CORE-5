package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityWindowGeometryTransactionTest {
    private val original = InfinityWindowGeometry(42f, 64f, 420f, 310f)

    @Test
    fun beginCapturesExactOriginalGeometry() {
        val transaction = infinityBeginWindowGeometryTransaction("notes-w1", original)
        assertEquals("notes-w1", transaction.windowId)
        assertEquals(original, transaction.original)
        assertEquals(original, transaction.current)
    }

    @Test
    fun updateChangesOnlyTransactionPreviewAndCancelRollsBackExactly() {
        val transaction = infinityBeginWindowGeometryTransaction("notes-w1", original)
        val preview = InfinityWindowGeometry(180f, 120f, 560f, 390f)
        val updated = infinityUpdateWindowGeometryTransaction(transaction, preview)

        assertEquals(original, transaction.current)
        assertEquals(preview, updated.current)
        assertEquals(original, infinityCancelWindowGeometryTransaction(updated))
    }

    @Test
    fun commitUsesFinalCandidateAndClampsItToVisibleArea() {
        val transaction = infinityBeginWindowGeometryTransaction("notes-w1", original)
        val candidate = InfinityWindowGeometry(900f, 700f, 700f, 600f)

        val committed = infinityCommitWindowGeometryTransaction(
            transaction, candidate,
            viewportWidth = 800f, viewportHeight = 600f, safeBottom = 500f
        )

        assertTrue(committed.x >= 0f)
        assertTrue(committed.y >= 0f)
        assertTrue(committed.x + committed.width <= 800f)
        assertTrue(committed.y + committed.height <= 500f)
        assertNotEquals(candidate, committed)
    }

    @Test
    fun restoredGeometryIsNormalizedWhenOrientationOrViewportShrinks() {
        val landscape = infinityNormalizeWindowBounds(
            640f, 400f, 520f, 420f,
            viewportWidth = 900f, viewportHeight = 600f, safeBottom = 510f
        )
        val portrait = infinityNormalizeWindowBounds(
            landscape.x, landscape.y, landscape.width, landscape.height,
            viewportWidth = 390f, viewportHeight = 780f, safeBottom = 690f
        )

        assertTrue(portrait.x >= 0f)
        assertTrue(portrait.y >= 0f)
        assertTrue(portrait.x + portrait.width <= 390f)
        assertTrue(portrait.y + portrait.height <= 690f)
    }

    @Test
    fun cancelledPreviewCannotBecomeCommittedGeometry() {
        val transaction = infinityBeginWindowGeometryTransaction("files-w1", original)
        val preview = infinityUpdateWindowGeometryTransaction(
            transaction, InfinityWindowGeometry(300f, 250f, 600f, 440f)
        )
        val rolledBack = infinityCancelWindowGeometryTransaction(preview)

        assertEquals(original, rolledBack)
        assertNotEquals(preview.current, rolledBack)
    }
}


class InfinityWindowGeometryTransactionRegistryTest {
    @Test fun removingClosedWindowDiscardsOnlyItsTransaction() {
        val first = infinityBeginWindowGeometryTransaction("notes-w1", InfinityWindowGeometry(10f, 20f, 300f, 220f))
        val second = infinityBeginWindowGeometryTransaction("files-w1", InfinityWindowGeometry(30f, 40f, 400f, 280f))
        val registry = InfinityWindowGeometryTransactionRegistry(linkedMapOf(first.windowId to first, second.windowId to second))
        assertEquals(first, registry.remove("notes-w1"))
        assertEquals(setOf("files-w1"), registry.activeWindowIds())
        assertEquals(null, registry.remove("missing"))
    }

    @Test fun workspaceExitCanEnumerateAndCancelEveryActiveTransaction() {
        val first = infinityBeginWindowGeometryTransaction("notes-w1", InfinityWindowGeometry(10f, 20f, 300f, 220f))
        val second = infinityBeginWindowGeometryTransaction("files-w1", InfinityWindowGeometry(30f, 40f, 400f, 280f))
        val registry = InfinityWindowGeometryTransactionRegistry(linkedMapOf(first.windowId to first, second.windowId to second))
        val idsToCancel = registry.activeWindowIds()
        val restored = idsToCancel.associateWith { id -> infinityCancelWindowGeometryTransaction(registry.remove(id)!!) }
        assertEquals(setOf("notes-w1", "files-w1"), idsToCancel)
        assertEquals(first.original, restored["notes-w1"])
        assertEquals(second.original, restored["files-w1"])
        assertTrue(registry.activeWindowIds().isEmpty())
    }

    @Test fun retainOnlyRemovesTransactionsForWindowsThatNoLongerExist() {
        val active = infinityBeginWindowGeometryTransaction("notes-w1", InfinityWindowGeometry(10f, 20f, 300f, 220f))
        val orphan = infinityBeginWindowGeometryTransaction("closed-w1", InfinityWindowGeometry(30f, 40f, 400f, 280f))
        val registry = InfinityWindowGeometryTransactionRegistry(linkedMapOf(active.windowId to active, orphan.windowId to orphan))
        assertEquals(mapOf("closed-w1" to orphan), registry.retainOnly(setOf("notes-w1")))
        assertEquals(setOf("notes-w1"), registry.activeWindowIds())
    }

    @Test fun removeAllReturnsSnapshotAndLeavesRegistryEmpty() {
        val transaction = infinityBeginWindowGeometryTransaction("notes-w1", InfinityWindowGeometry(10f, 20f, 300f, 220f))
        val registry = InfinityWindowGeometryTransactionRegistry(linkedMapOf(transaction.windowId to transaction))
        assertEquals(mapOf("notes-w1" to transaction), registry.removeAll())
        assertTrue(registry.activeWindowIds().isEmpty())
    }
}
