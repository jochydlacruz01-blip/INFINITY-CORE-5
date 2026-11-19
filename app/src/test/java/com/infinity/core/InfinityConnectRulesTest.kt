package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectRulesTest {

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

}
