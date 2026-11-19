package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityFileManagerRulesTest {
    @Test fun unknownFlagsPermitAttempt() {
        assertTrue(infinitySupportsDocumentFlag(null, 4L))
    }

    @Test fun presentFlagRequiresBit() {
        assertTrue(infinitySupportsDocumentFlag(4L, 4L))
        assertFalse(infinitySupportsDocumentFlag(2L, 4L))
        assertFalse(infinitySupportsDocumentFlag(0L, 4L))
    }

    @Test fun sameFolderDetectionRequiresMatchingNonblankIds() {
        assertTrue(isSameInfinityFolder("folder", "folder"))
        assertFalse(isSameInfinityFolder("folder", "other"))
        assertFalse(isSameInfinityFolder(null, "folder"))
        assertFalse(isSameInfinityFolder("", ""))
        assertFalse(isSameInfinityFolder("folder", null))
    }

    @Test fun fileSizeFormattingHandlesUnknownAndBoundaryValues() {
        assertEquals("Tamaño no disponible", formatInfinityFileSize(-1))
        assertEquals("0 B", formatInfinityFileSize(0))
        assertEquals("1023 B", formatInfinityFileSize(1023))
        assertEquals("1.0 KB", formatInfinityFileSize(1024))
    }

    @Test fun mimeFormattingHandlesUnknownAndCommonTypes() {
        assertEquals("Tipo no disponible", formatInfinityMimeType(null))
        assertEquals("Documento PDF", formatInfinityMimeType("application/pdf"))
        assertEquals("Imagen PNG", formatInfinityMimeType("image/png"))
    }

    @Test fun unknownSizeSortsAfterKnown() {
        assertEquals(0, infinityFileSizeSortRank(0))
        assertEquals(1, infinityFileSizeSortRank(-1))
    }
}
