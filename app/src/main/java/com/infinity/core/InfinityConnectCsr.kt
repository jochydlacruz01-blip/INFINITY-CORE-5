package com.infinity.core

import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Minimal bounded PKCS#10 encoder for the existing Android Keystore P-256 identity key.
 * The private key is never exported; the caller supplies a signer backed by Keystore.
 */
internal object InfinityConnectCsr {
    private const val MAX_PUBLIC_KEY_BYTES = 1024
    private const val MAX_CSR_BYTES = 16 * 1024

    fun create(
        commonName: String,
        encodedSubjectPublicKeyInfo: ByteArray,
        signCertificationRequestInfo: (ByteArray) -> ByteArray
    ): ByteArray {
        require(commonName.length in 1..64 && commonName.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-'
        }) { "Device common name must be 1-64 ASCII letters, digits, dot, underscore or hyphen." }
        require(encodedSubjectPublicKeyInfo.isNotEmpty() &&
            encodedSubjectPublicKeyInfo.size <= MAX_PUBLIC_KEY_BYTES) {
            "Subject public key is outside the supported size."
        }

        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(encodedSubjectPublicKeyInfo)) as? ECPublicKey
            ?: throw IllegalArgumentException("An EC public key is required.")
        require(publicKey.params.order.bitLength() == 256) {
            "Only the NIST P-256 identity curve is supported."
        }

        val commonNameAttribute = sequence(
            oid("2.5.4.3"),
            utf8String(commonName)
        )
        val subject = sequence(set(commonNameAttribute))
        val certificationRequestInfo = sequence(
            integerZero(),
            subject,
            encodedSubjectPublicKeyInfo.copyOf(),
            byteArrayOf(0xA0.toByte(), 0x00)
        )
        val signature = signCertificationRequestInfo(certificationRequestInfo.copyOf())
        require(signature.isNotEmpty() && signature.size <= 256) {
            "The identity-key signature is outside the supported size."
        }
        val signatureAlgorithm = sequence(oid("1.2.840.10045.4.3.2"))
        val csr = sequence(
            certificationRequestInfo,
            signatureAlgorithm,
            bitString(signature)
        )
        require(csr.size <= MAX_CSR_BYTES) { "The certificate request exceeds the supported size." }
        return csr
    }

    private fun integerZero() = tlv(0x02, byteArrayOf(0))
    private fun utf8String(value: String) = tlv(0x0C, value.toByteArray(Charsets.UTF_8))
    private fun bitString(value: ByteArray) = tlv(0x03, byteArrayOf(0) + value)
    private fun sequence(vararg values: ByteArray) = tlv(0x30, values.fold(ByteArray(0)) { a, b -> a + b })
    private fun set(value: ByteArray) = tlv(0x31, value)

    private fun oid(value: String): ByteArray {
        val arcs = value.split('.').map { it.toLong() }
        require(arcs.size >= 2 && arcs[0] in 0L..2L && arcs[1] >= 0 &&
            (arcs[0] == 2L || arcs[1] <= 39))
        val body = ByteArray(0) + base128(arcs[0] * 40 + arcs[1]) +
            arcs.drop(2).fold(ByteArray(0)) { acc, arc ->
                require(arc >= 0)
                acc + base128(arc)
            }
        return tlv(0x06, body)
    }

    private fun base128(value: Long): ByteArray {
        require(value >= 0)
        var remaining = value
        val bytes = ArrayList<Int>()
        bytes.add((remaining and 0x7F).toInt())
        remaining = remaining ushr 7
        while (remaining > 0) {
            bytes.add(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        return bytes.asReversed().map { it.toByte() }.toByteArray()
    }

    private fun tlv(tag: Int, body: ByteArray): ByteArray {
        val length = when {
            body.size < 128 -> byteArrayOf(body.size.toByte())
            body.size <= 0xFF -> byteArrayOf(0x81.toByte(), body.size.toByte())
            body.size <= 0xFFFF -> byteArrayOf(
                0x82.toByte(), (body.size ushr 8).toByte(), body.size.toByte()
            )
            else -> throw IllegalArgumentException("ASN.1 element is too large.")
        }
        return byteArrayOf(tag.toByte()) + length + body
    }
}
