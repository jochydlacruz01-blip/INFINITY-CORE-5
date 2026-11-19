package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectRulesTest {

    @Test
    fun acceptsHostnamesAndIpLiteralsWithoutUrlSyntax() {
        assertTrue(isValidInfinityHost("192.168.1.100"))
        assertTrue(isValidInfinityHost("office-pc.local"))
        assertTrue(isValidInfinityHost("büro.local"))
        assertTrue(isValidInfinityHost("2001:db8::1"))
        assertTrue(isValidInfinityHost("fe80::1%wlan0"))
        assertFalse(isValidInfinityHost(""))
        assertFalse(isValidInfinityHost("https://office-pc"))
        assertFalse(isValidInfinityHost("office pc"))
        assertFalse(isValidInfinityHost("office..pc"))
        assertFalse(isValidInfinityHost("999.999.1.1"))
        assertFalse(isValidInfinityHost("-office.local"))
        assertFalse(isValidInfinityHost("office-.local"))
        assertFalse(isValidInfinityHost("2001:db8::1%wlan0%extra"))
        assertFalse(isValidInfinityHost("host:3389"))
    }

    @Test
    fun acceptsValidPortBoundaries() {
        assertEquals(1, parseInfinityPort("1"))
        assertEquals(3389, parseInfinityPort("3389"))
        assertEquals(65535, parseInfinityPort("65535"))
        assertEquals(3389, parseInfinityPort(" 3389 "))
    }

    @Test
    fun rejectsInvalidPorts() {
        assertEquals(null, parseInfinityPort(""))
        assertEquals(null, parseInfinityPort("0"))
        assertEquals(null, parseInfinityPort("65536"))
        assertEquals(null, parseInfinityPort("-1"))
        assertEquals(null, parseInfinityPort("abc"))
    }

    @Test
    fun detectsDuplicateHostAndPortIgnoringHostCase() {
        val saved = listOf("PC-OFICINA" to "3389", "192.168.1.20" to "3390")

        assertTrue(hasDuplicateInfinityEndpoint(" pc-oficina ", "3389", saved))
        assertTrue(hasDuplicateInfinityEndpoint("192.168.1.20", "3390", saved))
        assertFalse(hasDuplicateInfinityEndpoint("pc-oficina", "3391", saved))
        assertFalse(hasDuplicateInfinityEndpoint("pc-casa", "3389", saved))
    }

    @Test
    fun excludesProfileBeingEditedFromDuplicateCheck() {
        val saved = listOf("PC-OFICINA" to "3389", "PC-CASA" to "3389")

        assertFalse(
            hasDuplicateInfinityEndpoint(
                host = "pc-oficina",
                port = "3389",
                savedEndpoints = saved,
                excludedIndex = 0
            )
        )
        assertTrue(
            hasDuplicateInfinityEndpoint(
                host = "pc-oficina",
                port = "3389",
                savedEndpoints = saved,
                excludedIndex = 1
            )
        )
    }

    @Test
    fun invalidCandidatePortDoesNotMatchSavedEndpoint() {
        assertFalse(
            hasDuplicateInfinityEndpoint(
                host = "PC-OFICINA",
                port = "70000",
                savedEndpoints = listOf("PC-OFICINA" to "3389")
            )
        )
    }

    @Test
    fun formatsFileSizesAcrossUnits() {
        assertEquals("0 B", formatInfinityFileSize(0))
        assertEquals("512 B", formatInfinityFileSize(512))
        assertEquals("1.0 KB", formatInfinityFileSize(1024))
        assertEquals("1.0 MB", formatInfinityFileSize(1024L * 1024L))
        assertEquals("1.00 GB", formatInfinityFileSize(1024L * 1024L * 1024L))
        assertEquals("Tamaño no disponible", formatInfinityFileSize(-1))
    }

    @Test
    fun formatsCommonMimeTypesForPeople() {
        assertEquals("Documento PDF", formatInfinityMimeType("application/pdf"))
        assertEquals("Imagen PNG", formatInfinityMimeType("image/png"))
        assertEquals("Instalador APK", formatInfinityMimeType("application/vnd.android.package-archive"))
        assertEquals("Tipo no disponible", formatInfinityMimeType(null))
    }
    @Test
    fun sortsEntriesWithUnknownSizesAfterKnownSizes() {
        assertEquals(0, infinityFileSizeSortRank(0))
        assertEquals(0, infinityFileSizeSortRank(1024))
        assertEquals(1, infinityFileSizeSortRank(-1))
        assertEquals(1, infinityFileSizeSortRank(-100))
    }

    @Test
    fun mimeFormattingIsCaseInsensitiveAndHasReadableFallback() {
        assertEquals("Documento PDF", formatInfinityMimeType("APPLICATION/PDF"))
        assertEquals("JPEG", formatInfinityMimeType("application/jpeg"))
        assertEquals("X CUSTOM FORMAT", formatInfinityMimeType("application/x-custom-format"))
    }

    @Test
    fun respectsStorageProviderOperationFlags() {
        assertTrue(infinitySupportsDocumentFlag(null, 1L))
        assertTrue(infinitySupportsDocumentFlag(1L or 2L, 1L))
        assertFalse(infinitySupportsDocumentFlag(2L, 1L))
        assertFalse(infinitySupportsDocumentFlag(0L, 1L))
    }

    @Test
    fun detectsSameFolderPasteTargets() {
        assertTrue(isSameInfinityFolder("folder-1", "folder-1"))
        assertFalse(isSameInfinityFolder("folder-1", "folder-2"))
        assertFalse(isSameInfinityFolder(null, "folder-1"))
        assertFalse(isSameInfinityFolder("folder-1", null))
        assertFalse(isSameInfinityFolder("", ""))
    }

    @Test
    fun mimeFallbackUsesLocaleIndependentUppercase() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals("CUSTOM", formatInfinityMimeType("image/custom"))
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun portableFileNamesRejectUnsafeAndOverlongValues() {
        assertTrue(isValidInfinityDocumentName("Informe final.pdf"))
        assertFalse(isValidInfinityDocumentName(""))
        assertFalse(isValidInfinityDocumentName("  "))
        assertFalse(isValidInfinityDocumentName("."))
        assertFalse(isValidInfinityDocumentName(".."))
        assertFalse(isValidInfinityDocumentName("folder/name"))
        assertFalse(isValidInfinityDocumentName("folder\\\\name"))
        assertFalse(isValidInfinityDocumentName("x".repeat(256)))
        assertFalse(isValidInfinityDocumentName("line\\nfeed"))
    }

}
