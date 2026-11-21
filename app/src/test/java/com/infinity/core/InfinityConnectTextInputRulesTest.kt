package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectTextInputRulesTest {
    @Test fun emptyTextProducesNoChunks() { assertTrue(InfinityConnectTextInputRules.chunk("").isEmpty()) }
    @Test fun preservesUnicodeAndSurrogatePairs() {
        val text = "Hola 🌎🚀"
        val chunks = InfinityConnectTextInputRules.chunk(text, 2)
        assertEquals(text, chunks.joinToString("") { it.text })
        chunks.forEach { chunk ->
            for (i in chunk.text.indices) {
                if (chunk.text[i].isHighSurrogate()) assertTrue(i + 1 < chunk.text.length && chunk.text[i + 1].isLowSurrogate())
                if (chunk.text[i].isLowSurrogate()) assertTrue(i > 0 && chunk.text[i - 1].isHighSurrogate())
            }
        }
    }
    @Test fun chunksRespectCodePointLimit() {
        val chunks = InfinityConnectTextInputRules.chunk("a😀b🚀c", 2)
        assertEquals(listOf("a😀", "b🚀", "c"), chunks.map { it.text })
    }
    @Test fun rejectsNullAndOverLimit() {
        assertNotNull(InfinityConnectTextInputRules.validate("bad\u0000text"))
        assertNotNull(InfinityConnectTextInputRules.validate("x".repeat(InfinityConnectTextInputRules.MAX_TEXT_LENGTH + 1)))
        assertNull(InfinityConnectTextInputRules.validate("ok\n✓"))
    }
    @Test fun invalidChunkSizeRejected() {
        try { InfinityConnectTextInputRules.chunk("abc", 0); fail("Expected validation") }
        catch (_: IllegalArgumentException) { }
    }
}
