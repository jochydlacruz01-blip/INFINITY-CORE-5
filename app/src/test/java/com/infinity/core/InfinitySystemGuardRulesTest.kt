package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinitySystemGuardRulesTest {
    @Test fun ioFailuresAreWarningsWithoutLeakingDetails() {
        val report = infinityGuardReport("Lectura", java.io.IOException("/private/path token=secret"))
        assertEquals("IO", report.category)
        assertEquals(InfinityGuardSeverity.WARNING, report.severity)
        assertEquals("Lectura no pudo completarse. IO", report.userMessage)
    }

    @Test fun permissionFailuresAreWarnings() {
        assertEquals(InfinityGuardSeverity.WARNING, infinityGuardReport("Acceso", SecurityException("secret")).severity)
        assertEquals("PERMISOS", infinityGuardReport("Acceso", SecurityException("secret")).category)
    }

    @Test fun invalidInputIsClassifiedAsInfo() {
        assertEquals(InfinityGuardSeverity.INFO, infinityGuardReport("Guardar", IllegalArgumentException("bad")).severity)
    }

    @Test fun genericExceptionsAreErrors() {
        assertEquals(InfinityGuardSeverity.ERROR, infinityGuardReport("Operación", IllegalStateException("detail")).severity)
        assertEquals("GENERAL", infinityGuardReport("Operación", IllegalStateException("detail")).category)
    }

    @Test fun labelsAreFilteredAndBounded() {
        assertEquals("Archivo seguro", infinityGuardSafeLabel("Archivo seguro"))
        assertEquals("Archivo", infinityGuardSafeLabel("Archivo/\\\u0000"))
        assertEquals("Elemento", infinityGuardSafeLabel("!!!"))
        assertEquals("abcd", infinityGuardSafeLabel("abcdef", 4))
    }

    @Test fun operationLabelsAreBoundedAndBlankSafe() {
        assertEquals("Operación no pudo completarse. GENERAL", infinityGuardReport("", IllegalStateException()).userMessage)
        assertEquals("Operación no pudo completarse. GENERAL", infinityGuardReport(" ".repeat(80), IllegalStateException()).userMessage)
    }
}
