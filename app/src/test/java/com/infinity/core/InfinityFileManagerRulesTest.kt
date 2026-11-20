package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityFileManagerRulesTest {
    @Test
    fun centralizedSortKeepsFoldersFirstAndNamesCaseInsensitive() {
        val entries = listOf(
            InfinityFileEntry("z", "zeta.txt", "text/plain", 10, 0, null),
            InfinityFileEntry("folder", "Folder", "vnd.android.document/directory", -1, 0, null),
            InfinityFileEntry("a", "Alpha.txt", "text/plain", 10, 0, null)
        )
        assertEquals(listOf("folder", "a", "z"), sortInfinityFileEntries(entries, "", "Nombre", java.util.Locale.ROOT).map { it.id })
    }

    @Test
    fun typeSortUsesDisplayTypeAndKeepsFoldersFirst() {
        val entries = listOf(
            InfinityFileEntry("image", "photo.png", "image/png", 20, 0, null),
            InfinityFileEntry("folder", "folder", "vnd.android.document/directory", -1, 0, null),
            InfinityFileEntry("pdf", "report.pdf", "application/pdf", 10, 0, null)
        )
        assertEquals(listOf("folder", "pdf", "image"), sortInfinityFileEntries(entries, "", "Tipo", java.util.Locale.ROOT).map { it.id })
    }

    @Test
    fun sizeSortPlacesUnknownSizesAfterKnownAndBreaksTiesByName() {
        val entries = listOf(
            InfinityFileEntry("unknown", "unknown.bin", null, -1, 0, null),
            InfinityFileEntry("small", "small.bin", null, 10, 0, null),
            InfinityFileEntry("large", "large.bin", null, 100, 0, null),
            InfinityFileEntry("folder", "folder", "vnd.android.document/directory", -1, 0, null)
        )
        assertEquals(listOf("folder", "large", "small", "unknown"), sortInfinityFileEntries(entries, "", "Tamaño", java.util.Locale.ROOT).map { it.id })
    }

    @Test
    fun dateSortKeepsFoldersFirstThenNewestAndNameForTies() {
        val entries = listOf(
            InfinityFileEntry("older", "older", null, 1, 10, null),
            InfinityFileEntry("newer-b", "b", null, 1, 20, null),
            InfinityFileEntry("folder", "folder", "vnd.android.document/directory", -1, 0, null),
            InfinityFileEntry("newer-a", "a", null, 1, 20, null)
        )
        assertEquals(listOf("folder", "newer-a", "newer-b", "older"), sortInfinityFileEntries(entries, "", "Fecha", java.util.Locale.ROOT).map { it.id })
    }

    @Test
    fun sortSearchTrimsQueryAndMatchesNamesCaseInsensitively() {
        val entries = listOf(
            InfinityFileEntry("one", "Report-final.pdf", "application/pdf", 1, 0, null),
            InfinityFileEntry("two", "notes.txt", "text/plain", 1, 0, null)
        )
        assertEquals(listOf("one"), sortInfinityFileEntries(entries, "  REPORT  ", "Nombre", java.util.Locale.ROOT).map { it.id })
        assertEquals(2, sortInfinityFileEntries(entries, "   ", "Nombre", java.util.Locale.ROOT).size)
    }

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
        assertEquals("1.00 TB", formatInfinityFileSize(1024L * 1024L * 1024L * 1024L))
        assertEquals("1.00 PB", formatInfinityFileSize(1024L * 1024L * 1024L * 1024L * 1024L))
    }
    @Test fun fileSizeFormattingHandlesLongMaximumWithoutOverflow() {
        val formatted = formatInfinityFileSize(Long.MAX_VALUE)
        assertEquals("8.00 EB", formatted)
        assertFalse(formatted.startsWith("-"))
        assertEquals("1023 B", formatInfinityFileSize(1023L))
        assertEquals("1.0 KB", formatInfinityFileSize(1024L))
        assertEquals("1.0 MB", formatInfinityFileSize(1024L * 1024L))
    }


    @Test fun mimeFormattingHandlesUnknownAndCommonTypes() {
        assertEquals("Tipo no disponible", formatInfinityMimeType(null))
        assertEquals("Tipo no disponible", formatInfinityMimeType(""))
        assertEquals("Documento PDF", formatInfinityMimeType("application/pdf"))
        assertEquals("Texto", formatInfinityMimeType("text/plain"))
        assertEquals("Hoja CSV", formatInfinityMimeType("text/csv"))
        assertEquals("Documento Word", formatInfinityMimeType("application/msword"))
        assertEquals("Documento Word", formatInfinityMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals("Hoja Excel", formatInfinityMimeType("application/vnd.ms-excel"))
        assertEquals("Hoja Excel", formatInfinityMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertEquals("Presentación PowerPoint", formatInfinityMimeType("application/vnd.ms-powerpoint"))
        assertEquals("Presentación PowerPoint", formatInfinityMimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
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

    @Test fun optionalMimeMetadataHandlesMissingColumnAndBlankValues() {
        assertEquals(null, infinityOptionalMimeType(false, "image/png"))
        assertEquals(null, infinityOptionalMimeType(true, null))
        assertEquals(null, infinityOptionalMimeType(true, "  "))
        assertEquals("image/png", infinityOptionalMimeType(true, "image/png"))
    }

    @Test fun staleFolderQueryCannotClearNewerLoadingState() {
        assertFalse(infinityShouldClearLoading(4, 5))
        assertTrue(infinityShouldClearLoading(5, 5))
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

@Test fun copyRetryKeepsOnlyFailedIdsInOriginalOrder() {
        assertEquals(listOf("c", "a"), infinityRemainingOperationIds(listOf("c", "b", "a"), setOf("c", "a")))
        assertEquals(emptyList<String>(), infinityRemainingOperationIds(listOf("a", "b"), emptySet()))
    }

    @Test fun copyRetryIgnoresUnknownFailuresAndPreservesRepeatedIds() {
        assertEquals(listOf("a", "a", "c"), infinityRemainingOperationIds(listOf("a", "a", "b", "c"), setOf("a", "c", "missing")))
    }

    @Test fun interruptedCopyRetainsOnlyUnconfirmedItemsForRetry() {
        assertEquals(
            listOf("second", "fourth"),
            infinityUnfinishedOperationIds(
                listOf("first", "second", "third", "fourth"),
                setOf("first", "third")
            )
        )
        assertEquals(
            emptyList<String>(),
            infinityUnfinishedOperationIds(listOf("first", "second"), setOf("first", "second"))
        )
    }

    @Test fun folderNavigationRejectsCurrentAndAncestorIdsButAllowsNewChildren() {
        assertTrue(infinityCanNavigateFolder(listOf("root", "docs"), "images"))
        assertFalse(infinityCanNavigateFolder(listOf("root", "docs"), "root"))
        assertFalse(infinityCanNavigateFolder(listOf("root", "docs"), "docs"))
        assertFalse(infinityCanNavigateFolder(listOf("root"), "  "))
    }

    @Test fun malformedSafDocumentIdsAreSkippedAndNamesGetSafeFallbacks() {
        assertEquals(null, infinityDocumentIdOrNull(null))
        assertEquals(null, infinityDocumentIdOrNull(""))
        assertEquals(null, infinityDocumentIdOrNull("   "))
        assertEquals("document-42", infinityDocumentIdOrNull("document-42"))
        assertEquals("Sin nombre", infinityDocumentDisplayName(null))
        assertEquals("Sin nombre", infinityDocumentDisplayName(""))
        assertEquals("Sin nombre", infinityDocumentDisplayName("  "))
        assertEquals("reporte.pdf", infinityDocumentDisplayName("reporte.pdf"))
    }

    @Test
    fun breadcrumbNavigationReturnsMatchingRootAndAncestorPrefixes() {
        val ids = listOf("root", "documents", "reports")
        val names = listOf("Inicio", "Documentos", "Informes")
        assertEquals(InfinityFolderNavigationTarget(listOf("root"), listOf("Inicio")), infinityFolderNavigationAt(ids, names, 0))
        assertEquals(InfinityFolderNavigationTarget(listOf("root", "documents"), listOf("Inicio", "Documentos")), infinityFolderNavigationAt(ids, names, 1))
        assertEquals(InfinityFolderNavigationTarget(ids, names), infinityFolderNavigationAt(ids, names, 2))
    }

    @Test
    fun breadcrumbNavigationRejectsInvalidIndexesAndMismatchedPaths() {
        val ids = listOf("root", "documents")
        val names = listOf("Inicio", "Documentos")
        assertEquals(null, infinityFolderNavigationAt(ids, names, -1))
        assertEquals(null, infinityFolderNavigationAt(ids, names, 2))
        assertEquals(null, infinityFolderNavigationAt(emptyList(), emptyList(), 0))
        assertEquals(null, infinityFolderNavigationAt(ids, listOf("Inicio"), 0))
        assertEquals(null, infinityFolderNavigationAt(listOf("root", "root"), listOf("Inicio", "Duplicado"), 1))
        assertEquals(null, infinityFolderNavigationAt(listOf("root", " "), listOf("Inicio", "Carpeta"), 1))
        assertEquals(null, infinityFolderNavigationAt(listOf("root", "docs"), listOf("Inicio", " "), 1))
    }

}
