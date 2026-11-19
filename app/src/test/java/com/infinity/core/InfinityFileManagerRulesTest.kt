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
        assertEquals("1.0 MB", formatInfinityFileSize(1024L * 1024L))
        assertEquals("1.00 GB", formatInfinityFileSize(1024L * 1024L * 1024L))
    }

    @Test fun mimeFormattingHandlesUnknownAndCommonTypes() {
        assertEquals("Tipo no disponible", formatInfinityMimeType(null))
        assertEquals("Tipo no disponible", formatInfinityMimeType(""))
        assertEquals("Documento PDF", formatInfinityMimeType("application/pdf"))
        assertEquals("Texto", formatInfinityMimeType("text/plain"))
        assertEquals("Archivo ZIP", formatInfinityMimeType("application/zip"))
        assertEquals("Imagen JPEG", formatInfinityMimeType("image/jpeg"))
        assertEquals("Imagen PNG", formatInfinityMimeType("image/png"))
        assertEquals("Imagen WebP", formatInfinityMimeType("image/webp"))
        assertEquals("Imagen GIF", formatInfinityMimeType("image/gif"))
        assertEquals("Audio MP3", formatInfinityMimeType("audio/mpeg"))
        assertEquals("Audio OGG", formatInfinityMimeType("audio/ogg"))
        assertEquals("Vídeo MP4", formatInfinityMimeType("video/mp4"))
        assertEquals("Instalador APK", formatInfinityMimeType("application/vnd.android.package-archive"))
    }

    @Test fun mimeMatchingIsCaseInsensitive() {
        assertEquals("Imagen PNG", formatInfinityMimeType("IMAGE/PNG"))
    }

    @Test fun unknownSizeSortsAfterKnown() {
        assertEquals(0, infinityFileSizeSortRank(0))
        assertEquals(0, infinityFileSizeSortRank(1))
        assertEquals(1, infinityFileSizeSortRank(-1))
    }

    @Test fun documentNameValidationRejectsInvalidAndAcceptsNormalNames() {
        assertTrue(isValidInfinityDocumentName("informe final.pdf"))
        assertTrue(isValidInfinityDocumentName("  nombre  "))
        assertFalse(isValidInfinityDocumentName(""))
        assertFalse(isValidInfinityDocumentName("   "))
        assertFalse(isValidInfinityDocumentName("../escape"))
        assertFalse(isValidInfinityDocumentName("folder\\\\name"))
        assertFalse(isValidInfinityDocumentName("bad\\u0000name"))
        assertFalse(isValidInfinityDocumentName("a".repeat(256)))
    }

    @Test fun moveRetryKeepsOnlyFailedIdsInOriginalOrder() {
        assertEquals(listOf("c", "a"), infinityRemainingMoveIds(listOf("c", "b", "a"), setOf("c", "a")))
        assertEquals(emptyList<String>(), infinityRemainingMoveIds(listOf("a", "b"), emptySet()))
    }

    @Test fun failedDeletesRemainSelectedInOriginalOrder() { assertEquals(listOf("b", "d"), infinityRemainingDeleteIds(listOf("a", "b", "c", "d"), setOf("b", "d"))) }
    @Test fun allDeleteFailuresRetainAllTargets() { assertEquals(listOf("a", "b"), infinityRemainingDeleteIds(listOf("a", "b"), setOf("a", "b"))) }
    @Test fun moveRetryPreservesDuplicatesAndIgnoresUnknownFailures() { assertEquals(listOf("a", "a", "c"), infinityRemainingMoveIds(listOf("a", "a", "b", "c"), setOf("a", "c", "missing"))) }
    @Test fun filenameValidationAcceptsMaximumLengthAndRejectsDotNames() { assertTrue(isValidInfinityDocumentName("a".repeat(255))); assertFalse(isValidInfinityDocumentName(".")); assertFalse(isValidInfinityDocumentName("..")); assertFalse(isValidInfinityDocumentName(" . ")) }
    @Test fun unknownMimePreservesUsefulSubtype() { assertEquals("OCTET STREAM", formatInfinityMimeType("application/octet-stream")); assertEquals("Tipo no disponible", formatInfinityMimeType("  ")) }
    @Test fun successfulDeletesClearTargets() { assertEquals(emptyList<String>(), infinityRemainingDeleteIds(listOf("a", "b"), emptySet())) }
    @Test fun refreshRetainsRetrySelectionOnlyForVisibleIds() { assertEquals(setOf("b", "d"), infinityRetainedSelection(setOf("a", "b", "d"), setOf("b", "d", "x"))); assertEquals(emptySet<String>(), infinityRetainedSelection(setOf("a"), setOf("b"))) }

    @Test fun interruptedBatchRetainsOnlyUnconfirmedItemsInOriginalOrder() {
        assertEquals(listOf("b", "d"), infinityUnfinishedOperationIds(listOf("a", "b", "c", "d"), setOf("a", "c")))
        assertEquals(listOf("a", "b"), infinityUnfinishedOperationIds(listOf("a", "b"), emptySet()))
        assertEquals(emptyList<String>(), infinityUnfinishedOperationIds(listOf("a", "b"), setOf("a", "b")))
    }
