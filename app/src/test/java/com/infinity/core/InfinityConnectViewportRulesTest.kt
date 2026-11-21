package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectViewportRulesTest {
    @Test fun fitPreservesAspectRatioAndCentersLetterbox() {
        val viewport = InfinityConnectViewportRules.fit(ConnectSize(1000, 600), ConnectSize(1920, 1080))
        assertEquals(1000f, viewport.width, 0.01f)
        assertEquals(562.5f, viewport.height, 0.01f)
        assertEquals(18.75f, viewport.top, 0.01f)
        assertEquals(0f, viewport.left, 0.01f)
    }

    @Test fun mapPointerMapsCornersToRemotePixels() {
        val viewport = ConnectViewport(10f, 20f, 400f, 200f)
        val remote = ConnectSize(1280, 720)
        assertEquals(ConnectRemotePoint(0, 0), InfinityConnectViewportRules.mapPointer(10f, 20f, viewport, remote))
        assertEquals(ConnectRemotePoint(1279, 719), InfinityConnectViewportRules.mapPointer(410f, 220f, viewport, remote))
    }

    @Test fun pointerOutsideViewportIsClamped() {
        val viewport = ConnectViewport(50f, 50f, 100f, 100f)
        val remote = ConnectSize(800, 600)
        assertEquals(ConnectRemotePoint(0, 0), InfinityConnectViewportRules.mapPointer(-100f, -100f, viewport, remote))
        assertEquals(ConnectRemotePoint(799, 599), InfinityConnectViewportRules.mapPointer(900f, 900f, viewport, remote))
    }

    @Test fun normalizePointerClampsAndMapsInterior() {
        val viewport = ConnectViewport(10f, 10f, 80f, 40f)
        assertEquals(0f to 0f, InfinityConnectViewportRules.normalizePointer(0f, 0f, viewport))
        val middle = InfinityConnectViewportRules.normalizePointer(50f, 30f, viewport)
        assertTrue(kotlin.math.abs(middle.first - 0.5f) < 0.001f)
        assertTrue(kotlin.math.abs(middle.second - 0.5f) < 0.001f)
    }
}
