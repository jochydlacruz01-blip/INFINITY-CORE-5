package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDockResponsivePolicyTest {
    @Test fun phoneViewportUsesScrollableDock() {
        assertTrue(infinityDockRequiresHorizontalScroll(360f))
        assertTrue(infinityDockRequiresHorizontalScroll(599.9f))
    }

    @Test fun tabletViewportKeepsDockInItsFullWidthLayout() {
        assertFalse(infinityDockRequiresHorizontalScroll(600f))
        assertFalse(infinityDockRequiresHorizontalScroll(840f))
    }

    @Test fun invalidViewportFailsSafeToScrollableDock() {
        assertTrue(infinityDockRequiresHorizontalScroll(Float.NaN))
        assertTrue(infinityDockRequiresHorizontalScroll(Float.POSITIVE_INFINITY))
    }
}


class InfinityDockCompactActionPolicyTest {
    @Test fun allPhoneWidthsUseCompactActions() {
        assertTrue(infinityDockUsesCompactActions(320f))
        assertTrue(infinityDockUsesCompactActions(360f))
        assertTrue(infinityDockUsesCompactActions(412f))
        assertTrue(infinityDockUsesCompactActions(599.9f))
    }

    @Test fun tabletWidthsKeepReferenceActionSizing() {
        assertFalse(infinityDockUsesCompactActions(600f))
        assertFalse(infinityDockUsesCompactActions(840f))
    }

    @Test fun invalidWidthsFailSafeToCompactActions() {
        assertTrue(infinityDockUsesCompactActions(Float.NaN))
        assertTrue(infinityDockUsesCompactActions(Float.POSITIVE_INFINITY))
    }
}
