package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityBatch10RegressionTest {
    @Test
    fun telemetryPercentagesRemainBoundedForMalformedCapacityValues() {
        assertEquals(null, infinityUsagePercent(0L, 0L))
        assertEquals(0, infinityUsagePercent(100L, 100L))
        assertEquals(100, infinityUsagePercent(100L, 0L))
        assertEquals(0, infinityUsagePercent(100L, 200L))
        assertEquals(100, infinityUsagePercent(100L, -10L))
    }

    @Test
    fun fileNavigationRejectsLoopsAndMalformedBreadcrumbs() {
        assertTrue(infinityCanNavigateFolder(listOf("root", "a"), "b"))
        assertFalse(infinityCanNavigateFolder(listOf("root", "a"), "a"))
        assertFalse(infinityCanNavigateFolder(emptyList(), ""))
        assertEquals(null, infinityFolderNavigationAt(listOf("root", "a"), listOf("Root", "A"), 2))
        assertEquals(null, infinityFolderNavigationAt(listOf("root", "root"), listOf("Root", "Again"), 1))
        assertEquals(null, infinityFolderNavigationAt(listOf("root"), listOf(""), 0))
        assertEquals(InfinityFolderNavigationTarget(listOf("root", "a"), listOf("Root", "A")), infinityFolderNavigationAt(listOf("root", "a", "b"), listOf("Root", "A", "B"), 1))
    }

    @Test
    fun failedOperationsKeepOnlyUnfinishedItemsInOriginalOrder() {
        val ids = listOf("one", "two", "three", "four")
        assertEquals(listOf("two", "four"), infinityUnfinishedOperationIds(ids, setOf("one", "three")))
        assertEquals(listOf("one", "three"), infinityRemainingOperationIds(ids, setOf("one", "three")))
        assertEquals(listOf("two", "four"), infinityRemainingDeleteIds(ids, setOf("two", "four")))
        assertEquals(emptyList<String>(), infinityUnfinishedOperationIds(ids, ids.toSet()))
    }
}
