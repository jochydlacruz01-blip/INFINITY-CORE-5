package com.infinity.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.freerdp.freerdpcore.application.GlobalApp
import com.freerdp.freerdpcore.application.SessionState
import com.freerdp.freerdpcore.services.LibFreeRDP
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FreeRDP adapter for INFINITY's mTLS-authorized loopback tunnel.
 * It never connects to the Windows network address directly and never uses /cert:ignore.
 */
internal class InfinityFreeRdpClient(
    private val context: Context,
    private val tunnel: InfinityConnectRdpTunnel.Handle,
    expectedRdpCertificateSha256: String,
    private val rdpCertificateName: String,
    private val credentials: RdpCredentials,
    private val grantedCapabilities: Set<String>,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
        fun onFrame(frame: Bitmap)
        fun onResolutionChanged(width: Int, height: Int)
        fun onRdpCertificateRejected(host: String, observedFingerprint: String)
    }

    internal class RdpCredentials(
        val username: String,
        val domain: String,
        password: CharArray
    ) {
        private val secret = password.copyOf()
        private val consumed = AtomicBoolean(false)

        fun consume(into: StringBuilder): Boolean {
            if (!consumed.compareAndSet(false, true)) return false
            into.setLength(0)
            into.append(secret)
            secret.fill('\u0000')
            return true
        }

        fun clear() {
            secret.fill('\u0000')
            consumed.set(true)
        }
    }

    private val expectedRdpPin = normalizeRdpFingerprint(expectedRdpCertificateSha256)
        ?: throw IllegalArgumentException("A valid SHA-256 RDP server certificate pin is required.")
    private val safeCertificateName = rdpCertificateName.trim().also {
        require(it.matches(Regex("[A-Za-z0-9.-]{1,253}"))) {
            "RDP certificate name is invalid."
        }
    }
    private val instanceLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private var session: SessionState? = null
    private var frameBitmap: Bitmap? = null
    private var connected = false

    init {
        require(grantedCapabilities.containsAll(setOf("remote.display", "remote.input"))) {
            "Both remote.display and remote.input must be explicitly granted."
        }
        require(tunnel.localHost == "127.0.0.1" && tunnel.localPort in 1024..65535) {
            "FreeRDP must use the authenticated loopback tunnel."
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        check(!closed.get()) { "RDP client has been closed." }
        check(session == null) { "RDP client has already been initialized." }

        val uri = Uri.Builder()
            .scheme("freerdp")
            .authority("127.0.0.1:${tunnel.localPort}")
            .appendPath("connect")
            .appendQueryParameter("sec", "nla")
            .appendQueryParameter("cert", "fingerprint:sha256:$expectedRdpPin")
            .appendQueryParameter("clipboard", "-")
            .build()

        val created = GlobalApp.createSession(uri, context)
        session = created
        created.setUIEventListener(createUiListener(created))
        GlobalApp.registerSessionListener(
            created.instance,
            object : GlobalApp.SessionEventListener {
                override fun onConnectionSuccess() {
                    connected = true
                    mainHandler.post { listener.onConnected() }
                }

                override fun onConnectionFailure() {
                    connected = false
                    mainHandler.post { listener.onError("Windows rechazó o no pudo completar la conexión RDP.") }
                }

                override fun onDisconnected() {
                    connected = false
                    mainHandler.post { listener.onDisconnected() }
                }
            }
        )

        try {
            check(LibFreeRDP.setConnectionInfo(context, created.instance, uri)) {
                "FreeRDP rejected the constrained NLA connection arguments."
            }
            check(LibFreeRDP.connect(created.instance)) {
                "FreeRDP could not start the RDP connection."
            }
        } catch (error: Throwable) {
            GlobalApp.unregisterSessionListener(created.instance)
            GlobalApp.freeSession(created.instance)
            session = null
            throw error
        }
    }

    private fun createUiListener(owner: SessionState) = object : LibFreeRDP.UIEventListener {
        override fun OnSettingsChanged(width: Int, height: Int, bpp: Int) {
            replaceFrame(owner, width, height)
        }

        override fun OnReconnecting() {
            mainHandler.post { listener.onError("Windows está intentando restablecer la sesión RDP.") }
        }

        override fun OnAuthenticate(
            username: StringBuilder,
            domain: StringBuilder,
            password: StringBuilder
        ): Boolean {
            username.setLength(0)
            username.append(credentials.username)
            domain.setLength(0)
            domain.append(credentials.domain)
            return credentials.consume(password)
        }

        override fun OnGatewayAuthenticate(
            username: StringBuilder,
            domain: StringBuilder,
            password: StringBuilder
        ): Boolean = false

        override fun OnVerifiyCertificateEx(
            host: String,
            port: Long,
            commonName: String,
            subject: String,
            issuer: String,
            fingerprint: String,
            flags: Long
        ): Int {
            val accepted = normalizeRdpFingerprint(fingerprint) == expectedRdpPin
            if (!accepted) mainHandler.post {
                listener.onRdpCertificateRejected(host, fingerprint)
            }
            return if (accepted) 2 else 0 // temporary trust only; persistent pin remains INFINITY-owned
        }

        override fun OnVerifyChangedCertificateEx(
            host: String,
            port: Long,
            commonName: String,
            subject: String,
            issuer: String,
            fingerprint: String,
            oldSubject: String,
            oldIssuer: String,
            oldFingerprint: String,
            flags: Long
        ): Int {
            val accepted = normalizeRdpFingerprint(fingerprint) == expectedRdpPin
            if (!accepted) mainHandler.post {
                listener.onRdpCertificateRejected(host, fingerprint)
            }
            return if (accepted) 2 else 0
        }

        override fun OnExperimentalFeature(feature: Int): Boolean = false

        override fun OnGraphicsUpdate(x: Int, y: Int, width: Int, height: Int) {
            synchronized(instanceLock) {
                val bitmap = frameBitmap ?: return
                if (closed.get()) return
                if (LibFreeRDP.updateGraphics(owner.instance, bitmap, x, y, width, height)) {
                    mainHandler.post { if (!closed.get()) listener.onFrame(bitmap) }
                }
            }
        }

        override fun OnGraphicsResize(width: Int, height: Int, bpp: Int) {
            replaceFrame(owner, width, height)
        }

        override fun OnRemoteClipboardChanged(data: String) = Unit
        override fun OnRemoteClipboardImageChanged(data: ByteArray) = Unit
        override fun OnPointerSet(pixels: IntArray, width: Int, height: Int, hotX: Int, hotY: Int) = Unit
        override fun OnPointerSetNull() = Unit
        override fun OnPointerSetDefault() = Unit
        override fun OnRailWindowUpdate(windowId: Long, width: Int, height: Int, pixels: IntArray) = Unit
        override fun OnRailWindowMove(windowId: Long, x: Int, y: Int, w: Int, h: Int) = Unit
        override fun OnRailWindowHide(windowId: Long) = Unit
        override fun OnRailWindowDestroy(windowId: Long) = Unit
        override fun OnRailSessionEnd() = Unit
        override fun OnRailMonitoredDesktop(windowIds: LongArray, activeWindowId: Long) = Unit
    }

    private fun replaceFrame(owner: SessionState, width: Int, height: Int) {
        if (width !in 320..4096 || height !in 240..4096 ||
            width.toLong() * height.toLong() > MAX_FRAME_PIXELS || closed.get())
            return

        val replacement = runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return

        synchronized(instanceLock) {
            if (closed.get()) {
                replacement.recycle()
                return
            }
            val old = frameBitmap
            frameBitmap = replacement
            owner.surface = BitmapDrawable(context.resources, replacement)
            old?.recycle()
        }
        mainHandler.post {
            if (!closed.get()) {
                listener.onResolutionChanged(width, height)
                listener.onFrame(replacement)
            }
        }
    }

    /** x/y are remote desktop coordinates; flags use FreeRDP's Mouse event bitmask. */
    fun sendPointer(x: Int, y: Int, flags: Int): Boolean {
        if (!canSendInput()) return false
        val bitmap = synchronized(instanceLock) { frameBitmap } ?: return false
        val boundedX = x.coerceIn(0, bitmap.width - 1)
        val boundedY = y.coerceIn(0, bitmap.height - 1)
        val current = session ?: return false
        return LibFreeRDP.sendCursorEvent(current.instance, boundedX, boundedY, flags)
    }

    /** scanCode must be a FreeRDP/Windows scan code, not an Android keyCode. */
    fun sendScanCode(scanCode: Int, down: Boolean): Boolean {
        if (!canSendInput()) return false
        val current = session ?: return false
        return LibFreeRDP.sendKeyEvent(current.instance, scanCode, down)
    }

    fun sendUnicode(scanCode: Int, codePoint: Int, modifierMask: Int, down: Boolean): Boolean {
        if (!canSendInput()) return false
        val current = session ?: return false
        return LibFreeRDP.sendKeyAsUnicodeEvent(
            current.instance,
            scanCode,
            codePoint,
            modifierMask,
            down
        )
    }

    private fun canSendInput(): Boolean =
        !closed.get() && connected && grantedCapabilities.contains("remote.input")

    suspend fun closeSession() = withContext(Dispatchers.IO) {
        if (!closed.compareAndSet(false, true)) return@withContext
        credentials.clear()
        val current = session
        session = null
        if (current != null) {
            GlobalApp.unregisterSessionListener(current.instance)
            GlobalApp.freeSession(current.instance)
        }
        synchronized(instanceLock) {
            frameBitmap?.recycle()
            frameBitmap = null
        }
        tunnel.close()
    }

    companion object {
        private const val MAX_FRAME_PIXELS = 8_388_608L

        internal fun normalizeRdpFingerprint(value: String): String? {
            val trimmed = value.trim()
            val withoutPrefix = if (trimmed.startsWith("sha256:", ignoreCase = true))
                trimmed.substringAfter(':') else trimmed
            val normalized = withoutPrefix.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            return normalized.uppercase().takeIf { it.length == 64 }
        }
    }
}
