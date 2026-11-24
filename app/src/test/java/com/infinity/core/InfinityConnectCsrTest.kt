package com.infinity.core

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectCsrTest {
    @Test fun createsPkcs10RequestSignedByMatchingP256IdentityKey() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val csr = InfinityConnectCsr.create("infinity-device-01", pair.public.encoded) { info ->
            Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(info)
                sign()
            }
        }

        val outer = readElement(csr, 0)
        assertEquals(0x30, outer.tag)
        val info = readElement(outer.body, 0)
        val algorithm = readElement(outer.body, info.end)
        val signatureBits = readElement(outer.body, algorithm.end)
        assertEquals(0x30, info.tag)
        assertEquals(0x30, algorithm.tag)
        assertEquals(0x03, signatureBits.tag)
        assertEquals(0, signatureBits.body[0].toInt())

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pair.public)
        verifier.update(info.encoded)
        assertTrue(verifier.verify(signatureBits.body.copyOfRange(1, signatureBits.body.size)))
        assertTrue(info.encoded.toString(Charsets.ISO_8859_1).contains("infinity-device-01"))
    }

    @Test fun rejectsInvalidCommonNameAndNonP256Key() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val signer: (ByteArray) -> ByteArray = { byteArrayOf(1) }
        assertFalse(runCatching {
            InfinityConnectCsr.create("../device", pair.public.encoded, signer)
        }.isSuccess)

        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(runCatching {
            InfinityConnectCsr.create("device-01", rsa.public.encoded, signer)
        }.isSuccess)
    }

    private data class Element(val tag: Int, val body: ByteArray, val encoded: ByteArray, val end: Int)

    private fun readElement(source: ByteArray, offset: Int): Element {
        val tag = source[offset].toInt() and 0xFF
        var cursor = offset + 1
        val firstLength = source[cursor++].toInt() and 0xFF
        val length = if (firstLength < 0x80) firstLength else {
            val count = firstLength and 0x7F
            require(count in 1..2)
            var parsed = 0
            repeat(count) { parsed = (parsed shl 8) or (source[cursor++].toInt() and 0xFF) }
            parsed
        }
        val end = cursor + length
        require(end <= source.size)
        return Element(tag, source.copyOfRange(cursor, end), source.copyOfRange(offset, end), end)
    }
}
