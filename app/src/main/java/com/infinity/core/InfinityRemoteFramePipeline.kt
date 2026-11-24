package com.infinity.core

/**
 * Bounded, engine-neutral remote framebuffer packet.
 *
 * The native RDP adapter may publish compressed frames here. This type deliberately
 * does not decode untrusted image bytes; decoding belongs to a bounded codec stage.
 */
class InfinityRemoteFramePacket(
    val sequence: Long,
    val width: Int,
    val height: Int,
    val encoding: Encoding,
    payload: ByteArray,
    val capturedAtEpochMillis: Long
) {
    enum class Encoding { JPEG, PNG, RGBA_8888 }

    private val payloadBytes = payload.copyOf()
    val payload: ByteArray get() = payloadBytes.copyOf()
    val payloadSize: Int get() = payloadBytes.size

    init {
        require(sequence >= 0L) { "Frame sequence must not be negative." }
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            "Frame dimensions are outside the supported range."
        }
        require(width.toLong() * height.toLong() <= MAX_PIXELS) {
            "Frame pixel count exceeds the safety limit."
        }
        require(payloadBytes.isNotEmpty() && payloadBytes.size <= MAX_PAYLOAD_BYTES) {
            "Frame payload is empty or exceeds the safety limit."
        }
        require(capturedAtEpochMillis >= 0L) { "Frame timestamp must not be negative." }
        if (encoding == Encoding.RGBA_8888) {
            require(payloadBytes.size == width.toLong() * height.toLong() * 4L) {
                "RGBA payload length does not match its dimensions."
            }
        }
    }

    internal fun copyForMailbox(): InfinityRemoteFramePacket =
        InfinityRemoteFramePacket(sequence, width, height, encoding, payloadBytes, capturedAtEpochMillis)

    companion object {
        const val MAX_DIMENSION = 8192
        const val MAX_PIXELS = 33_554_432L
        const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    }
}

enum class InfinityFrameOfferResult {
    ACCEPTED,
    STALE_SEQUENCE
}

/**
 * Single-slot latest-frame mailbox. Replacing an unread frame is intentional:
 * interactive remote desktop should prefer freshness over rendering a growing backlog.
 */
class InfinityRemoteFrameMailbox {
    private var latest: InfinityRemoteFramePacket? = null
    private var lastAcceptedSequence = -1L
    private var acceptedCount = 0L
    private var replacedCount = 0L

    @Synchronized
    fun offer(frame: InfinityRemoteFramePacket): InfinityFrameOfferResult {
        if (frame.sequence <= lastAcceptedSequence) return InfinityFrameOfferResult.STALE_SEQUENCE
        if (latest != null) replacedCount++
        latest = frame.copyForMailbox()
        lastAcceptedSequence = frame.sequence
        acceptedCount++
        return InfinityFrameOfferResult.ACCEPTED
    }

    /** Returns and removes the freshest pending frame, or null when the mailbox is empty. */
    @Synchronized
    fun takeLatest(): InfinityRemoteFramePacket? {
        val frame = latest
        latest = null
        return frame
    }

    @Synchronized
    fun clear(resetSequence: Boolean = false) {
        latest = null
        if (resetSequence) lastAcceptedSequence = -1L
    }

    @Synchronized
    fun snapshot(): InfinityFrameMailboxStats = InfinityFrameMailboxStats(
        hasPendingFrame = latest != null,
        lastAcceptedSequence = lastAcceptedSequence,
        acceptedCount = acceptedCount,
        replacedCount = replacedCount
    )
}

data class InfinityFrameMailboxStats(
    val hasPendingFrame: Boolean,
    val lastAcceptedSequence: Long,
    val acceptedCount: Long,
    val replacedCount: Long
)
