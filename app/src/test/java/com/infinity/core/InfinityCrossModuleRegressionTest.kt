package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Cross-module regression checks for boundaries shared by INFINITY modules. */
class InfinityCrossModuleRegressionTest {
    @Test
    fun untrustedNotificationTextStaysBoundedAndPrivateBeforeDisplay() {
        val rawTitle = "  Alert\u0000a " + "😀".repeat(200)
        val visible = infinityNotificationContent(rawTitle, "message\u0007body", false)
        assertTrue(visible.first.length <= 300)
        assertTrue(visible.second.length <= 2_000)
        assertFalse(visible.first.any { it.isISOControl() })
        assertFalse(visible.second.any { it.isISOControl() })
        assertEquals("" to "", infinityNotificationContent(rawTitle, "sensitive", true))
        assertEquals("Aplicación", infinityNotificationAppNameOrFallback("\u0000", "\u0007"))
    }

    @Test
    fun connectEndpointValidationAndFormattingAgreeAtBoundaries() {
        assertTrue(isValidInfinityHost("büro.example"))
        assertTrue(isValidInfinityHost("2001:db8::1"))
        assertEquals("[2001:db8::1]:3389", formatInfinityEndpointAddress(" 2001:db8::1 ", "3389"))
        assertEquals("büro.example:65535", formatInfinityEndpointAddress("büro.example", "65535"))
        assertEquals("", formatInfinityEndpointAddress("server", "65536"))
        assertFalse(isValidInfinityHost("https://server"))
        assertNull(parseInfinityPort("0"))
        assertNull(parseInfinityPort("65536"))
    }

    @Test
    fun fileManagerKeepsFoldersFirstAndUnknownSizesLastAcrossSortModes() {
        val entries = listOf(
            InfinityFileEntry("unknown", "a-unknown.bin", "application/octet-stream", -1L, 1L, null),
            InfinityFileEntry("large", "z-large.bin", "application/octet-stream", 2_048L, 2L, null),
            InfinityFileEntry("folder", "z-folder", "vnd.android.document/directory", 0L, 0L, null)
        )
        val bySize = sortInfinityFileEntries(entries, "", "Tamaño", Locale.ROOT)
        assertEquals(listOf("folder", "large", "unknown"), bySize.map { it.id })
        assertEquals(listOf("large"), sortInfinityFileEntries(entries, " LARGE ", "Nombre", Locale.ROOT).map { it.id })
        assertEquals(listOf("failed-b"), infinityRemainingOperationIds(
            listOf("done-a", "failed-b", "done-c"), setOf("done-a", "done-c")
        ))
    }

    @Test
    fun orbitTelemetryNeverEscapesDisplayRangesForExtremeInputs() {
        for (available in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) {
            val percent = infinityUsagePercent(Long.MAX_VALUE, available)
            assertTrue(percent == null || percent in 0..100)
        }
        assertNull(infinityBatteryPercent(Int.MIN_VALUE))
        assertNull(infinityBatteryPercent(Int.MAX_VALUE))
        assertEquals("--", infinityBatteryTemperature(Int.MAX_VALUE))
        assertEquals("NO DISPONIBLE", infinityThermalState(Int.MAX_VALUE))
    }
}
