package com.infinity.core

import org.json.JSONObject
import org.json.JSONArray
import android.util.Base64
import android.os.CancellationSignal
import android.annotation.SuppressLint
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS 1.3 mutual-authenticated identity transport.
 * Server trust is pinned to a fingerprint explicitly confirmed by the user.
 * Capability requests are explicit and are constrained by Windows administrator policy.
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
        val capability: String,
        val allowedCapabilities: Set<String>
    )

    fun fetchWindowsIdentity(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String? = null,
        cancellationSignal: CancellationSignal? = null
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
            attachCancellation(connection, cancellationSignal)
            cancellationSignal?.throwIfCanceled()
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
                    cancellationSignal?.throwIfCanceled()
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
                capability = json.getString("capability"),
                allowedCapabilities = json.optJSONArray("allowedCapabilities")?.let { values ->
                    (0 until values.length()).map { values.getString(it) }.toSet()
                } ?: setOf("identity.read")
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
            clearCancellation(cancellationSignal)
        }
    }

    data class AuthorizedIdentitySession(
        val sessionId: String,
        val token: String,
        val expiresAtUtc: String,
        val capabilities: List<String>
    )

    fun createIdentitySession(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String,
        requestedCapabilities: Set<String> = setOf("identity.read")
    ): AuthorizedIdentitySession {
        InfinityConnectFeatureGates.requireCapabilitiesEnabled(requestedCapabilities)
        require(requestedCapabilities.contains("identity.read") &&
            requestedCapabilities.size in 1..4 &&
            requestedCapabilities.all { it in KNOWN_CAPABILITIES }) {
            "Requested session capabilities are invalid."
        }
        require(requestedCapabilities.contains("remote.display") ==
            requestedCapabilities.contains("remote.input")) {
            "Remote display and input must be requested together."
        }
        val windowsIdentity = fetchWindowsIdentity(
            baseUrl,
            clientKeyAlias,
            trustedServerSha256,
            expectedDeviceId
        )
        require(windowsIdentity.deviceId == expectedDeviceId) {
            "The authenticated Windows identity changed before session authorization."
        }
        require(windowsIdentity.allowedCapabilities.containsAll(requestedCapabilities)) {
            "Windows administrator policy does not allow all requested capabilities."
        }
        val connection = openMtlsConnection(
            baseUrl.trimEnd('/') + "/v1/sessions",
            clientKeyAlias,
            trustedServerSha256
        )
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val request = JSONObject()
                .put("consent", true)
                .put("requestedCapabilities", org.json.JSONArray().apply {
                    requestedCapabilities.sorted().forEach(::put)
                })
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(request) }
            require(connection.responseCode == 200) {
                "Windows rechazó la autorización de sesión (HTTP ${connection.responseCode})."
            }
            val json = JSONObject(readBoundedResponse(connection).toString(Charsets.UTF_8))
            val sessionId = json.getString("sessionId")
            val token = json.getString("token")
            val expiry = json.getString("expiresAtUtc")
            val capabilitiesJson = json.getJSONArray("capabilities")
            val capabilities = (0 until capabilitiesJson.length()).map { capabilitiesJson.getString(it) }
            require(runCatching { java.util.UUID.fromString(sessionId).toString() == sessionId }.getOrDefault(false)) {
                "Windows devolvió un ID de sesión inválido."
            }
            require(token.matches(Regex("[A-Za-z0-9_-]{43}"))) {
                "Windows devolvió un token de sesión inválido."
            }
            val expiresAt = java.time.Instant.parse(expiry)
            require(expiresAt.isAfter(java.time.Instant.now()) &&
                expiresAt.isBefore(java.time.Instant.now().plusSeconds(20 * 60))) {
                "La vigencia de sesión devuelta por Windows está fuera del límite."
            }
            require(capabilities.toSet() == requestedCapabilities &&
                capabilities.size == requestedCapabilities.size) {
                "Windows concedió un conjunto de capacidades distinto al solicitado."
            }
            require(expectedDeviceId.isNotBlank()) { "El perfil de dispositivo esperado está vacío." }
            return AuthorizedIdentitySession(sessionId, token, expiry, capabilities)
        } finally {
            connection.disconnect()
        }
    }

    fun createRemoteSession(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String
    ): AuthorizedIdentitySession = createIdentitySession(
        baseUrl,
        clientKeyAlias,
        trustedServerSha256,
        expectedDeviceId,
        setOf("identity.read", "remote.display", "remote.input")
    )

    fun createFileTransferSession(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String
    ): AuthorizedIdentitySession = createIdentitySession(
        baseUrl,
        clientKeyAlias,
        trustedServerSha256,
        expectedDeviceId,
        setOf("identity.read", "file.transfer")
    )

    fun revokeIdentitySession(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        sessionId: String,
        sessionToken: String,
        expectedDeviceId: String
    ): Boolean {
        require(fetchWindowsIdentity(
            baseUrl,
            clientKeyAlias,
            trustedServerSha256,
            expectedDeviceId
        ).deviceId == expectedDeviceId) {
            "The authenticated Windows identity changed before session revocation."
        }
        require(runCatching { java.util.UUID.fromString(sessionId).toString() == sessionId }.getOrDefault(false)) {
            "Session ID is invalid."
        }
        require(sessionToken.matches(Regex("[A-Za-z0-9_-]{43}"))) {
            "Session token is invalid."
        }
        val connection = openMtlsConnection(
            baseUrl.trimEnd('/') + "/v1/sessions/$sessionId",
            clientKeyAlias,
            trustedServerSha256
        )
        try {
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            return connection.responseCode == 204
        } finally {
            connection.disconnect()
        }
    }

    data class FileTransferReceipt(
        val fileId: String,
        val fileName: String,
        val length: Long,
        val sha256: String,
        val createdAtUtc: String
    )

    fun uploadFile(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String,
        sessionId: String,
        sessionToken: String,
        fileName: String,
        length: Long,
        expectedFileSha256: String,
        source: InputStream,
        cancellationSignal: CancellationSignal? = null
    ): FileTransferReceipt {
        InfinityConnectFeatureGates.requireFileTransferEnabled()
        validateSessionCredentials(sessionId, sessionToken)
        validateTransferFileName(fileName)
        require(length in 0..MAX_FILE_BYTES) { "File exceeds the 50 MiB transfer limit." }
        val expectedPin = normalizePin(expectedFileSha256)
            ?: throw IllegalArgumentException("A valid file SHA-256 digest is required.")
        cancellationSignal?.throwIfCanceled()
        require(fetchWindowsIdentity(baseUrl, clientKeyAlias, trustedServerSha256, expectedDeviceId, cancellationSignal).deviceId == expectedDeviceId) {
            "Windows device identity changed before file upload."
        }

        val connection = openMtlsConnection(
            baseUrl.trimEnd('/') + "/v1/files",
            clientKeyAlias,
            trustedServerSha256,
            cancellationSignal
        )
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.readTimeout = FILE_TRANSFER_TIMEOUT_MS
            connection.setFixedLengthStreamingMode(length)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            connection.setRequestProperty("X-INFINITY-Session-ID", sessionId)
            connection.setRequestProperty(
                "X-INFINITY-File-Name",
                Base64.encodeToString(
                    fileName.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            )
            connection.setRequestProperty("X-INFINITY-File-SHA256", expectedPin)

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.outputStream.use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    cancellationSignal?.throwIfCanceled()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= length && total <= MAX_FILE_BYTES) {
                        "Source stream exceeded its declared length or transfer limit."
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            require(total == length) { "Source stream length changed during upload." }
            val actualDigest = digest.digest()
            val expectedDigest = expectedPin.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val digestMatches = MessageDigest.isEqual(actualDigest, expectedDigest)
            actualDigest.fill(0)
            expectedDigest.fill(0)
            require(digestMatches) { "Source file changed or its SHA-256 digest is incorrect." }

            require(connection.responseCode == 200) {
                "Windows rejected file upload (HTTP ${connection.responseCode})."
            }
            val json = JSONObject(readBoundedResponse(connection).toString(Charsets.UTF_8))
            val receipt = FileTransferReceipt(
                fileId = json.getString("fileId"),
                fileName = json.getString("fileName"),
                length = json.getLong("length"),
                sha256 = json.getString("sha256"),
                createdAtUtc = json.getString("createdAtUtc")
            )
            require(runCatching { java.util.UUID.fromString(receipt.fileId).toString() == receipt.fileId }.getOrDefault(false) &&
                receipt.length == length && normalizePin(receipt.sha256) == expectedPin &&
                receipt.fileName == fileName) {
                "Windows returned a file receipt that does not match the upload."
            }
            return receipt
        } finally {
            connection.disconnect()
            clearCancellation(cancellationSignal)
        }
    }

    fun downloadFileTo(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String,
        sessionId: String,
        sessionToken: String,
        fileId: String,
        destination: OutputStream,
        cancellationSignal: CancellationSignal? = null
    ): FileTransferReceipt {
        InfinityConnectFeatureGates.requireFileTransferEnabled()
        validateSessionCredentials(sessionId, sessionToken)
        require(runCatching { java.util.UUID.fromString(fileId).toString() == fileId }.getOrDefault(false)) {
            "File ID is invalid."
        }
        cancellationSignal?.throwIfCanceled()
        require(fetchWindowsIdentity(baseUrl, clientKeyAlias, trustedServerSha256, expectedDeviceId, cancellationSignal).deviceId == expectedDeviceId) {
            "Windows device identity changed before file download."
        }

        val connection = openMtlsConnection(
            baseUrl.trimEnd('/') + "/v1/files/$fileId",
            clientKeyAlias,
            trustedServerSha256,
            cancellationSignal
        )
        try {
            connection.requestMethod = "GET"
            connection.readTimeout = FILE_TRANSFER_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            connection.setRequestProperty("X-INFINITY-Session-ID", sessionId)
            require(connection.responseCode == 200) {
                "Windows rejected file download (HTTP ${connection.responseCode})."
            }
            val length = connection.contentLengthLong
            require(length in 0..MAX_FILE_BYTES) { "Downloaded file size is invalid." }
            val expectedPin = normalizePin(connection.getHeaderField("X-INFINITY-File-SHA256"))
                ?: throw IllegalStateException("Windows did not provide a valid file digest.")
            val encodedName = connection.getHeaderField("X-INFINITY-File-Name")
                ?: throw IllegalStateException("Windows did not provide a file name.")
            val fileNameBytes = Base64.decode(encodedName, Base64.URL_SAFE or Base64.NO_WRAP)
            val fileName = try { fileNameBytes.toString(Charsets.UTF_8) } finally { fileNameBytes.fill(0) }
            validateTransferFileName(fileName)

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    cancellationSignal?.throwIfCanceled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= length && total <= MAX_FILE_BYTES) {
                        "Windows sent more bytes than declared."
                    }
                    digest.update(buffer, 0, count)
                    destination.write(buffer, 0, count)
                }
            }
            destination.flush()
            require(total == length) { "Downloaded file length does not match Content-Length." }
            val actualDigest = digest.digest()
            val expectedDigest = expectedPin.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val digestMatches = MessageDigest.isEqual(actualDigest, expectedDigest)
            actualDigest.fill(0)
            expectedDigest.fill(0)
            require(digestMatches) { "Downloaded file failed SHA-256 integrity verification." }

            return FileTransferReceipt(fileId, fileName, length, expectedPin, "")
        } finally {
            connection.disconnect()
            clearCancellation(cancellationSignal)
        }
    }

    fun listRemoteFiles(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String,
        sessionId: String,
        sessionToken: String,
        cancellationSignal: CancellationSignal? = null
    ): List<FileTransferReceipt> {
        InfinityConnectFeatureGates.requireFileTransferEnabled()
        validateSessionCredentials(sessionId, sessionToken)
        cancellationSignal?.throwIfCanceled()
        require(fetchWindowsIdentity(baseUrl, clientKeyAlias, trustedServerSha256, expectedDeviceId, cancellationSignal).deviceId == expectedDeviceId) {
            "Windows device identity changed before file listing."
        }
        val connection = openMtlsConnection(
            baseUrl.trimEnd('/') + "/v1/files",
            clientKeyAlias,
            trustedServerSha256,
            cancellationSignal
        )
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            connection.setRequestProperty("X-INFINITY-Session-ID", sessionId)
            require(connection.responseCode == 200) {
                "Windows rejected file listing (HTTP ${connection.responseCode})."
            }
            val array = JSONArray(readBoundedResponse(connection, MAX_FILE_LIST_RESPONSE_BYTES).toString(Charsets.UTF_8))
            require(array.length() <= MAX_REMOTE_FILES) { "Windows returned too many files." }
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val receipt = FileTransferReceipt(
                    fileId = item.getString("fileId"),
                    fileName = item.getString("fileName"),
                    length = item.getLong("length"),
                    sha256 = item.getString("sha256"),
                    createdAtUtc = item.getString("createdAtUtc")
                )
                require(runCatching { java.util.UUID.fromString(receipt.fileId).toString() == receipt.fileId }.getOrDefault(false) &&
                    receipt.length in 0..MAX_FILE_BYTES &&
                    normalizePin(receipt.sha256) != null) {
                    "Windows returned invalid file metadata."
                }
                validateTransferFileName(receipt.fileName)
                receipt
            }
        } finally {
            connection.disconnect()
            clearCancellation(cancellationSignal)
        }
    }

    fun deleteRemoteFile(
        baseUrl: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        expectedDeviceId: String,
        sessionId: String,
        sessionToken: String,
        fileId: String,
        cancellationSignal: CancellationSignal? = null
    ): Boolean {
        InfinityConnectFeatureGates.requireFileTransferEnabled()
        validateSessionCredentials(sessionId, sessionToken)
        require(runCatching { java.util.UUID.fromString(fileId).toString() == fileId }.getOrDefault(false)) {
            "File ID is invalid."
        }
        cancellationSignal?.throwIfCanceled()
        require(fetchWindowsIdentity(baseUrl, clientKeyAlias, trustedServerSha256, expectedDeviceId, cancellationSignal).deviceId == expectedDeviceId) {
            "Windows device identity changed before file deletion."
        }
        val connection = openMtlsConnection(
            baseUrl.trimEnd('/') + "/v1/files/$fileId",
            clientKeyAlias,
            trustedServerSha256,
            cancellationSignal
        )
        try {
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            connection.setRequestProperty("X-INFINITY-Session-ID", sessionId)
            return connection.responseCode == 204
        } finally {
            connection.disconnect()
            clearCancellation(cancellationSignal)
        }
    }

    private fun validateSessionCredentials(sessionId: String, sessionToken: String) {
        require(runCatching { java.util.UUID.fromString(sessionId).toString() == sessionId }.getOrDefault(false)) {
            "Session ID is invalid."
        }
        require(sessionToken.matches(Regex("[A-Za-z0-9_-]{43}"))) {
            "Session bearer token is invalid."
        }
    }

    private fun validateTransferFileName(fileName: String) {
        require(fileName.isNotBlank() && fileName.length <= 128 &&
            fileName != "." && fileName != ".." &&
            fileName.none { it.isISOControl() || it in charArrayOf('/', '\\', ':', '<', '>', '"', '|', '?', '*') } &&
            !fileName.endsWith(' ') && !fileName.endsWith('.')) {
            "File name is invalid."
        }
        val stem = fileName.substringBefore('.').trimEnd(' ', '.')
        require(stem.uppercase() !in setOf("CON", "PRN", "AUX", "NUL") &&
            !(stem.length == 4 && (stem.startsWith("COM", true) || stem.startsWith("LPT", true)) &&
                stem[3] in '1'..'9')) {
            "File name is reserved by Windows."
        }
    }

    private fun openMtlsConnection(
        endpoint: String,
        clientKeyAlias: String,
        trustedServerSha256: String,
        cancellationSignal: CancellationSignal? = null
    ): HttpsURLConnection {
        val url = URL(endpoint)
        require(url.protocol.equals("https", ignoreCase = true) &&
            !url.host.isNullOrBlank() && url.userInfo == null && url.query == null && url.ref == null) {
            "Only a direct HTTPS Companion URL is accepted."
        }
        val tls = createPinnedTls13Context(clientKeyAlias, trustedServerSha256)
        val connection = url.openConnection() as? HttpsURLConnection
            ?: throw IllegalArgumentException("The Companion endpoint must use HTTPS.")
        connection.connectTimeout = connectTimeoutMs.coerceIn(1_000, MAX_TIMEOUT_MS)
        connection.readTimeout = readTimeoutMs.coerceIn(1_000, MAX_TIMEOUT_MS)
        connection.instanceFollowRedirects = false
        connection.sslSocketFactory = tls.first.socketFactory
        attachCancellation(connection, cancellationSignal)
        cancellationSignal?.throwIfCanceled()
        return connection
    }

    private fun attachCancellation(
        connection: HttpsURLConnection,
        cancellationSignal: CancellationSignal?
    ) {
        cancellationSignal?.setOnCancelListener { connection.disconnect() }
    }

    private fun clearCancellation(cancellationSignal: CancellationSignal?) {
        cancellationSignal?.setOnCancelListener(null)
    }

    private fun readBoundedResponse(connection: HttpsURLConnection, maxBytes: Int = MAX_RESPONSE_BYTES): ByteArray =
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxBytes) {
                    "Windows response exceeds the supported size."
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }

    internal fun createPinnedTls13Context(
        clientKeyAlias: String,
        trustedServerSha256: String
    ): Pair<SSLContext, X509TrustManager> {
        require(InfinityConnectIdentityKeyStore.isValidAlias(clientKeyAlias)) {
            "Identity key alias is invalid."
        }
        val serverPin = normalizePin(trustedServerSha256)
            ?: throw IllegalArgumentException("A valid Windows server SHA-256 fingerprint is required.")
        require(keyStore.getKey(clientKeyAlias, null) != null &&
            keyStore.getCertificateChain(clientKeyAlias)?.isNotEmpty() == true) {
            "The Android client certificate has not been installed."
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }
        val trustManager = PinnedServerTrustManager(serverPin)
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(keyManagers.keyManagers, arrayOf<TrustManager>(trustManager), null)
        return sslContext to trustManager
    }

    /**
     * Platform PKIX validation runs first. The exact leaf pin is an additional identity
     * constraint, not a replacement for path validation. HttpsURLConnection retains its
     * default hostname verifier; this trust manager never disables endpoint identification.
     */
    @SuppressLint("CustomX509TrustManager") // Intentional pinning wrapper: delegates PKIX to Android before enforcing the user-approved leaf pin.
    private class PinnedServerTrustManager(private val expectedPin: String) : X509TrustManager {
        private val platformTrustManager: X509TrustManager = run {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            factory.trustManagers.filterIsInstance<X509TrustManager>().singleOrNull()
                ?: throw java.security.cert.CertificateException(
                    "Android platform X.509 trust manager is unavailable."
                )
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw java.security.cert.CertificateException(
                "Client certificates are not accepted by this server-trust manager."
            )
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty() || chain.size > MAX_CHAIN_CERTIFICATES) {
                throw java.security.cert.CertificateException(
                    "Windows certificate chain is missing or too large."
                )
            }

            // Delegates complete PKIX path validation (trust anchor, signatures, constraints,
            // critical extensions, validity and platform policy) to Android's configured trust store.
            platformTrustManager.checkServerTrusted(chain, authType)

            val leaf = chain[0]
            val eku = try { leaf.extendedKeyUsage } catch (error: Exception) {
                throw java.security.cert.CertificateException("Windows server EKU is malformed.", error)
            }
            if (eku?.contains(SERVER_AUTH_OID) != true) {
                throw java.security.cert.CertificateException(
                    "Windows certificate does not permit server authentication."
                )
            }
            val keyUsage = leaf.keyUsage
            if (keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])) {
                throw java.security.cert.CertificateException(
                    "Windows certificate does not permit digital signatures."
                )
            }

            val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            val expected = expectedPin.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val matches = MessageDigest.isEqual(actual, expected)
            actual.fill(0)
            expected.fill(0)
            if (!matches) {
                throw java.security.cert.CertificateException(
                    "Windows server certificate fingerprint does not match the trusted device."
                )
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            platformTrustManager.acceptedIssuers
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
        private const val MAX_FILE_BYTES = 50L * 1024 * 1024
        private const val MAX_FILE_LIST_RESPONSE_BYTES = 128 * 1024
        private const val MAX_REMOTE_FILES = 100
        private const val FILE_TRANSFER_TIMEOUT_MS = 60_000
        private val KNOWN_CAPABILITIES = setOf("identity.read", "remote.display", "remote.input", "file.transfer")
    }
}
