package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityReferenceMastheadRulesTest {
    @Test fun mastheadAppearsAtTabletBreakpoint() {
        assertFalse(infinityReferenceMastheadVisible(599.9f))
        assertTrue(infinityReferenceMastheadVisible(600f))
        assertTrue(infinityReferenceMastheadVisible(720f))
    }

    @Test fun invalidViewportWidthsKeepMastheadHidden() {
        assertFalse(infinityReferenceMastheadVisible(Float.NaN))
        assertFalse(infinityReferenceMastheadVisible(Float.POSITIVE_INFINITY))
        assertFalse(infinityReferenceMastheadVisible(Float.NEGATIVE_INFINITY))
    }
}
