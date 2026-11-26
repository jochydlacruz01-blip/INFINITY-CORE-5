package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectTouchRulesTest {
    private val point = ConnectRemotePoint(2, 5)
    @Test fun tapMapsToClick() = assertEquals(listOf(ConnectMouseAction.DOWN, ConnectMouseAction.UP), InfinityConnectTouchRules.translate(ConnectTouchGesture.Tap(point)).events.map { it.action })
    @Test fun longPressMapsToRightClick() {
        val events = InfinityConnectTouchRules.translate(ConnectTouchGesture.LongPress(point)).events
        assertTrue(events.all { it.button == ConnectMouseButton.RIGHT })
    }
    @Test fun dragMapsToDragSequence() = assertEquals(listOf(ConnectMouseAction.DOWN, ConnectMouseAction.MOVE, ConnectMouseAction.UP), InfinityConnectTouchRules.translate(ConnectTouchGesture.Drag(point, ConnectRemotePoint(20,30))).events.map { it.action })
    @Test fun cancelProducesRelease() {
        val e = InfinityConnectTouchRules.translate(ConnectTouchGesture.CancelDrag(point)).events.single()
        assertEquals(ConnectMouseAction.UP,e.action); assertEquals(ConnectMouseButton.LEFT,e.button)
    }
    @Test fun scrollMapsDelta() = assertEquals(4, InfinityConnectTouchRules.translate(ConnectTouchGesture.Scroll(point,4)).events.single().scrollDelta)
}
