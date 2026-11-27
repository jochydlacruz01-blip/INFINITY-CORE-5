package com.infinity.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.Date
import java.util.UUID

/** Offline verifier for the Windows Companion signed pairing challenge v1. No network or enrollment side effects. */
internal object InfinityConnectPairingChallengeV1 {
    const val MAX_PAYLOAD_BYTES = 4096
    const val MAX_LIFETIME_MILLIS = 300_000L
    private const val DOMAIN = "INFINITY-CONNECT-PAIRING-CHALLENGE-V1\u0000"
    private val fields = setOf("protocolVersion", "requestId", "companionDeviceId", "issuedAtUnixMs", "expiresAtUnixMs", "nonce", "caSha256", "signature")

    data class Challenge(
        val protocolVersion: Int, val requestId: UUID, val companionDeviceId: UUID,
        val issuedAtUnixMs: Long, val expiresAtUnixMs: Long,
        val nonce: ByteArray, val caSha256: ByteArray, val signature: ByteArray
    )

    /** Accepts only a flat JSON object with the exact v1 schema; rejects duplicate/unknown/missing fields. */
    fun parse(payload: ByteArray): Challenge? {
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD_BYTES) return null
        val json = runCatching {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(payload)).toString()
        }.getOrNull() ?: return null
        val map = FlatJson(json).parse() ?: return null
        if (map.keys != fields) return null
        val version = map["protocolVersion"]?.toIntOrNull() ?: return null
        val request = uuid(map["requestId"]) ?: return null
        val companion = uuid(map["companionDeviceId"]) ?: return null
        val issued = map["issuedAtUnixMs"]?.toLongOrNull() ?: return null
        val expires = map["expiresAtUnixMs"]?.toLongOrNull() ?: return null
        val nonce = base64Url(map["nonce"], 32, 32) ?: return null
        val pinText = map["caSha256"] ?: return null
        if (pinText.length != 64 || pinText.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
        val pin = pinText.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val signature = base64Url(map["signature"], 8, 104) ?: return null
        if (version != 1 || request == UUID(0, 0) || companion == UUID(0, 0)) return null
        if (issued < 0 || expires <= issued || expires - issued > MAX_LIFETIME_MILLIS) return null
        return Challenge(version, request, companion, issued, expires, nonce, pin, signature)
    }

    /** pinnedCaDer must have been independently verified out-of-band before calling this method. */
    fun verify(
        challenge: Challenge, pinnedCaDer: ByteArray, expectedCompanionId: UUID, nowMillis: Long,
        allowedFutureClockSkewMillis: Long = 30_000L
    ): Boolean {
        if (pinnedCaDer.isEmpty() || pinnedCaDer.size > 64 * 1024 || expectedCompanionId == UUID(0, 0) ||
            nowMillis < 0 || allowedFutureClockSkewMillis < 0 || challenge.protocolVersion != 1 ||
            challenge.companionDeviceId != expectedCompanionId || challenge.issuedAtUnixMs < 0 ||
            challenge.expiresAtUnixMs <= challenge.issuedAtUnixMs ||
            challenge.expiresAtUnixMs - challenge.issuedAtUnixMs > MAX_LIFETIME_MILLIS ||
            (challenge.issuedAtUnixMs > nowMillis && challenge.issuedAtUnixMs - nowMillis > allowedFutureClockSkewMillis) ||
            nowMillis >= challenge.expiresAtUnixMs || challenge.nonce.size != 32 ||
            challenge.caSha256.size != 32 || challenge.signature.size !in 8..104) return false
        return try {
            val actualPin = MessageDigest.getInstance("SHA-256").digest(pinnedCaDer)
            if (!MessageDigest.isEqual(actualPin, challenge.caSha256)) return false
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(pinnedCaDer.inputStream()) as? X509Certificate ?: return false
            cert.checkValidity(Date(nowMillis))
            if (cert.basicConstraints < 0) return false
            val usage = cert.keyUsage
            if (usage != null && (usage.size <= 5 || !usage[5])) return false
            val key = cert.publicKey as? ECPublicKey ?: return false
            if (key.params.curve.field.fieldSize != 384) return false
            Signature.getInstance("SHA384withECDSA").run {
                initVerify(key)
                update(signatureInput(challenge))
                verify(challenge.signature)
            }
        } catch (_: Exception) { false }
    }

    internal fun signatureInput(c: Challenge): ByteArray {
        val domain = DOMAIN.toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(domain.size + 2 + 16 + 16 + 8 + 8 + 32 + 32)
            .order(ByteOrder.BIG_ENDIAN).apply {
                put(domain); putShort(c.protocolVersion.toShort())
                putLong(c.requestId.mostSignificantBits); putLong(c.requestId.leastSignificantBits)
                putLong(c.companionDeviceId.mostSignificantBits); putLong(c.companionDeviceId.leastSignificantBits)
                putLong(c.issuedAtUnixMs); putLong(c.expiresAtUnixMs); put(c.nonce); put(c.caSha256)
            }.array()
    }

    private fun uuid(s: String?): UUID? {
        if (s == null || s.length != 36 || s != s.lowercase()) return null
        return runCatching { UUID.fromString(s) }.getOrNull()?.takeIf { it.toString() == s }
    }

    private fun base64Url(s: String?, min: Int, max: Int): ByteArray? {
        if (s.isNullOrEmpty() || s.length > 140 ||
            s.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' }) return null
        val bytes = runCatching { Base64.getUrlDecoder().decode(s) }.getOrNull() ?: return null
        return bytes.takeIf { it.size in min..max && Base64.getUrlEncoder().withoutPadding().encodeToString(it) == s }
    }

    /** Minimal flat-schema parser; values are strings or integer tokens only. */
    private class FlatJson(private val s: String) {
        private var p = 0
        fun parse(): Map<String, String>? {
            ws(); if (!take('{')) return null
            val out = linkedMapOf<String, String>(); ws()
            if (take('}')) { ws(); return out.takeIf { p == s.length } }
            while (true) {
                ws(); val k = string() ?: return null
                if (out.containsKey(k)) return null
                ws(); if (!take(':')) return null
                ws(); val v = if (peek() == '"') string() ?: return null else integer() ?: return null
                out[k] = v; ws()
                if (take('}')) break
                if (!take(',')) return null
            }
            ws(); return out.takeIf { p == s.length }
        }
        private fun integer(): String? {
            val start = p
            if (peek() == '-') p++
            if (p >= s.length || s[p] !in '0'..'9') return null
            if (s[p] == '0') p++ else while (p < s.length && s[p] in '0'..'9') p++
            if (p < s.length && s[p] in ".eE+") return null
            return s.substring(start, p)
        }
        private fun string(): String? {
            if (!take('"')) return null
            val b = StringBuilder()
            while (p < s.length) {
                val c = s[p++]
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> {
                        if (p >= s.length) return null
                        when (val e = s[p++]) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                            'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                            'u' -> {
                                if (p + 4 > s.length) return null
                                val code = s.substring(p, p + 4).toIntOrNull(16) ?: return null
                                b.append(code.toChar()); p += 4
                            }
                            else -> return null
                        }
                    }
                    c.code < 0x20 -> return null
                    else -> b.append(c)
                }
            }
            return null
        }
        private fun ws() { while (p < s.length && s[p] in charArrayOf(' ', '\n', '\r', '\t')) p++ }
        private fun peek(): Char? = s.getOrNull(p)
        private fun take(c: Char) = if (peek() == c) { p++; true } else false
    }
}
