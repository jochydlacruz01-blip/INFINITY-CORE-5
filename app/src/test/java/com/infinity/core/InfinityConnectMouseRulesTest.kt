package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectMouseRulesTest {
    private val point = ConnectRemotePoint(12, 30)
    @Test fun clickReleasesButton() {
        val events = InfinityConnectMouseRules.click(point, ConnectMouseButton.RIGHT).events
        assertEquals(ConnectMouseAction.DOWN, events[0].action)
        assertEquals(ConnectMouseAction.UP, events[1].action)
        assertEquals(ConnectMouseButton.RIGHT, events[0].button)
    }
    @Test fun dragHasDownMoveUpOrder() {
        val events = InfinityConnectMouseRules.drag(point, ConnectRemotePoint(90, 100)).events
        assertEquals(listOf(ConnectMouseAction.DOWN, ConnectMouseAction.MOVE, ConnectMouseAction.UP), events.map { it.action })
    }
    @Test fun doubleClickContainsTwoClickCycles() {
        assertEquals(listOf(ConnectMouseAction.DOWN, ConnectMouseAction.UP, ConnectMouseAction.DOWN, ConnectMouseAction.UP), InfinityConnectMouseRules.doubleClick(point).events.map { it.action })
    }
    @Test fun scrollPreservesDeltaAndPosition() {
        val e = InfinityConnectMouseRules.scroll(point, -3).events.single()
        assertEquals(-3, e.scrollDelta); assertEquals(point, e.point)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroScrollRejected() { InfinityConnectMouseRules.scroll(point, 0) }
}
