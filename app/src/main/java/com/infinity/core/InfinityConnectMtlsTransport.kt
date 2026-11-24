package com.infinity.core

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS 1.3 mutual-authenticated identity transport.
 * Server trust is pinned to a fingerprint explicitly confirmed by the user.
 * This endpoint is identity-only; it does not expose remote-control operations.
 */
internal class InfinityConnectMtlsTransport(
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) },
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS
) {
    data class VerifiedIdentity(
        val deviceId: String,
        val product: String,
        val version: String,
        val transportSecurity: String,
        val capability: String
    )

    fun fetchWindowsIdentity(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String? = null
    ): VerifiedIdentity {
        require(InfinityConnectIdentityKeyStore.isValidAlias(clientKeyAlias)) {
            "Identity key alias is invalid."
        }
        val serverPin = normalizePin(trustedServerSha256)
            ?: throw IllegalArgumentException("A valid Windows server SHA-256 fingerprint is required.")
        val url = URL(baseUrl.trimEnd('/') + "/v1/identity")
        require(url.protocol.equals("https", ignoreCase = true) &&
            !url.host.isNullOrBlank() && url.userInfo == null && url.query == null && url.ref == null) {
            "Only a direct HTTPS Companion URL is accepted."
        }
        require(keyStore.getKey(clientKeyAlias, null) != null &&
            keyStore.getCertificateChain(clientKeyAlias)?.isNotEmpty() == true) {
            "The Android client certificate has not been installed."
        }

        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(keyManagers.keyManagers, arrayOf<TrustManager>(PinnedServerTrustManager(serverPin)), null)

        val connection = (url.openConnection() as? HttpsURLConnection)
            ?: throw IllegalArgumentException("The Companion endpoint must use HTTPS.")
        try {
            connection.connectTimeout = connectTimeoutMs.coerceIn(1_000, MAX_TIMEOUT_MS)
            connection.readTimeout = readTimeoutMs.coerceIn(1_000, MAX_TIMEOUT_MS)
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.sslSocketFactory = sslContext.socketFactory
            connection.connect()

            require(connection.responseCode == 200) {
                "Windows Companion rejected the identity request (HTTP ${connection.responseCode})."
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_RESPONSE_BYTES) {
                        "Windows identity response exceeds the supported size."
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val json = JSONObject(body.toString(Charsets.UTF_8))
            val identity = VerifiedIdentity(
                deviceId = json.getString("deviceId"),
                product = json.getString("product"),
                version = json.getString("version"),
                transportSecurity = json.getString("transportSecurity"),
                capability = json.getString("capability")
            )
            require(identity.deviceId.isNotBlank() && identity.deviceId.length <= 128) {
                "Windows returned an invalid device identity."
            }
            if (expectedDeviceId != null) require(identity.deviceId == expectedDeviceId) {
                "The authenticated Companion device ID does not match the saved profile."
            }
            return identity
        } finally {
            connection.disconnect()
        }
    }

    private class PinnedServerTrustManager(private val expectedPin: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw java.security.cert.CertificateException("Client certificates are not accepted by this trust manager.")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty() || chain.size > MAX_CHAIN_CERTIFICATES) {
                throw java.security.cert.CertificateException("Windows certificate chain is missing or too large.")
            }
            val leaf = chain[0]
            val now = System.currentTimeMillis()
            if (now < leaf.notBefore.time || now > leaf.notAfter.time) {
                throw java.security.cert.CertificateException("Windows server certificate is expired or not yet valid.")
            }
            val eku = try { leaf.extendedKeyUsage } catch (error: Exception) {
                throw java.security.cert.CertificateException("Windows server EKU is malformed.", error)
            }
            if (eku?.contains(SERVER_AUTH_OID) != true) {
                throw java.security.cert.CertificateException("Windows certificate does not permit server authentication.")
            }
            val keyUsage = leaf.keyUsage
            if (keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])) {
                throw java.security.cert.CertificateException("Windows certificate does not permit digital signatures.")
            }
            for (index in 0 until chain.lastIndex) {
                val child = chain[index]
                val issuer = chain[index + 1]
                if (child.issuerX500Principal != issuer.subjectX500Principal) {
                    throw java.security.cert.CertificateException("Windows certificate chain issuer mismatch.")
                }
                if (issuer.basicConstraints < 0) {
                    throw java.security.cert.CertificateException("Windows issuer certificate is not a CA.")
                }
                val issuerUsage = issuer.keyUsage
                if (issuerUsage != null && (issuerUsage.size <= 5 || !issuerUsage[5])) {
                    throw java.security.cert.CertificateException("Windows issuer cannot sign certificates.")
                }
                try { child.verify(issuer.publicKey) } catch (error: Exception) {
                    throw java.security.cert.CertificateException("Windows certificate chain signature is invalid.", error)
                }
                if (now < issuer.notBefore.time || now > issuer.notAfter.time) {
                    throw java.security.cert.CertificateException("Windows issuer certificate is outside its validity period.")
                }
            }
            val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            val expected = expectedPin.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (!MessageDigest.isEqual(actual, expected)) {
                throw java.security.cert.CertificateException("Windows server certificate fingerprint does not match the trusted device.")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun normalizePin(value: String): String? {
        val normalized = value.replace(":", "").replace(" ", "").uppercase()
        return normalized.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SERVER_AUTH_OID = "1.3.6.1.5.5.7.3.1"
        private const val DEFAULT_TIMEOUT_MS = 10_000
        private const val MAX_TIMEOUT_MS = 30_000
        private const val MAX_RESPONSE_BYTES = 8 * 1024
        private const val MAX_CHAIN_CERTIFICATES = 4
    }
}
