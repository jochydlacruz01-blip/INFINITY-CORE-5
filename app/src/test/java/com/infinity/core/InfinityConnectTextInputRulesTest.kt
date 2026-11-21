package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectTextInputRulesTest {
    @Test fun emptyTextProducesNoChunks() { assertTrue(InfinityConnectTextInputRules.chunk("").isEmpty()) }
    @Test fun preservesUnicodeAndSurrogatePairs() {
        val text = "Hola 🌎🚀"
        val chunks = InfinityConnectTextInputRules.chunk(text, 2)
        assertEquals(text, chunks.joinToString("") { it.text })
        assertTrue(chunks.all { it.text.none { c -> c.isHighSurrogate() && (it.text.indexOf(c)+1 >= it.text.length || !it.text[it.text.indexOf(c)+1].isLowSurrogate()) } })
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
