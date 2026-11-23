package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinitySystemGuardSafeLabelTest {
    @Test fun labelsRemoveUnsafeCharactersAndNormalizeWhitespace() {
        assertEquals("Ruta segura", infinityGuardSafeLabel("  Ruta/// segura\n"))
        assertEquals("A B C", infinityGuardSafeLabel("A@B#C"))
    }

    @Test fun labelsAreBoundedAndUseFallbackForBlankInput() {
        assertEquals("abc", infinityGuardSafeLabel("abcdef", maxLength = 3))
        assertEquals("Elemento", infinityGuardSafeLabel("///"))
        assertEquals("Respaldo", infinityGuardSafeLabel(" ", fallback = "Respaldo"))
    }

    @Test fun guardReportDoesNotExposeExceptionDetails() {
        val report = infinityGuardReport("Abrir ruta /private/path", RuntimeException("token=secret host=example"))
        assertEquals("GENERAL", report.category)
        assertEquals(InfinityGuardSeverity.ERROR, report.severity)
        assertTrue(report.userMessage.startsWith("Abrir ruta private path no pudo completarse."))
        assertTrue(!report.userMessage.contains("secret"))
        assertTrue(!report.userMessage.contains("example"))
    }

    @Test fun exceptionTypesMapToBoundedSeverityAndCategory() {
        assertEquals("IO", infinityGuardReport("Leer", java.io.IOException("private path")).category)
        assertEquals(InfinityGuardSeverity.WARNING, infinityGuardReport("Leer", java.io.IOException()).severity)
        assertEquals("PERMISOS", infinityGuardReport("Permiso", SecurityException()).category)
        assertEquals("ENTRADA", infinityGuardReport("Validar", IllegalArgumentException()).category)
    }
}
