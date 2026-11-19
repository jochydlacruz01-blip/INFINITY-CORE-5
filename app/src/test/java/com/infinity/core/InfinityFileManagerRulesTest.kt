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
}
