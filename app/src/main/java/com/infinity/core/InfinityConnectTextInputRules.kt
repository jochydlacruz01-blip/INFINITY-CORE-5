package com.infinity.core

/** Unicode text input contract for a future RDP adapter. It deliberately does not simulate keycodes. */
data class ConnectTextChunk(val text: String)
object InfinityConnectTextInputRules {
    const val MAX_TEXT_LENGTH = 16_384
    const val MAX_CHUNK_CODE_POINTS = 256

    /** Rejects NUL and excessive payloads; preserves other Unicode including emoji and newlines. */
    fun validate(text: String): String? = when {
        text.length > MAX_TEXT_LENGTH -> "Text input exceeds the supported limit."
        text.contains('\u0000') -> "Text input contains an unsupported NUL character."
        else -> null
    }

    /** Splits by Unicode code points so surrogate pairs are never separated. */
    fun chunk(text: String, maxCodePoints: Int = MAX_CHUNK_CODE_POINTS): List<ConnectTextChunk> {
        require(maxCodePoints > 0) { "Chunk size must be positive." }
        require(validate(text) == null) { "Text is invalid or exceeds the supported limit." }
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<ConnectTextChunk>()
        var offset = 0
        while (offset < text.length) {
            val end = text.offsetByCodePoints(offset, minOf(maxCodePoints, text.codePointCount(offset, text.length)))
            out += ConnectTextChunk(text.substring(offset, end))
            offset = end
        }
        return out
    }
}
