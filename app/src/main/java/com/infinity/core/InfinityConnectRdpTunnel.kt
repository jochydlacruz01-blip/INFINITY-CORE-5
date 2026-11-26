package com.infinity.core

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Bridges a loopback-only FreeRDP TCP socket through the authenticated Windows WebSocket tunnel.
 * The Companion server can reach only 127.0.0.1:3389; this client binds only 127.0.0.1.
 */
internal class InfinityConnectRdpTunnel(
    private val transportFactory: () -> InfinityConnectMtlsTransport = { InfinityConnectMtlsTransport() }
) {
    data class Config(
        val companionHost: String,
        val companionPort: Int,
        val clientKeyAlias: String,
        val trustedServerSha256: String,
        val expectedWindowsDeviceId: String,
        val sessionId: String,
        val sessionToken: String,
        val grantedCapabilities: Set<String>
    )

    class Handle internal constructor(
        val localHost: String,
        val localPort: Int,
        private val closeAction: () -> Unit
    ) : Closeable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (closed.compareAndSet(false, true)) closeAction()
        }
    }

    suspend fun open(config: Config): Handle {
        require(config.grantedCapabilities.containsAll(setOf("remote.display", "remote.input"))) {
            "Windows has not granted both remote.display and remote.input."
        }
        require(config.companionPort in 1024..65535) { "Companion port is invalid." }
        require(UUID.fromString(config.sessionId).toString() == config.sessionId) {
            "Session ID is invalid."
        }
        require(config.sessionToken.matches(Regex("[A-Za-z0-9_-]{43}"))) {
            "Session bearer token is invalid."
        }
        val host = config.companionHost.trim().removePrefix("[").removeSuffix("]")
        require(host.isNotBlank() && host.none {
            it.isWhitespace() || it == '/' || it == '@' || it == '?' || it == '#'
        }) { "Companion host is invalid." }

        val baseUrl = "https://${if (host.contains(':')) "[$host]" else host}:${config.companionPort}/"
        val transport = transportFactory()
        transport.fetchWindowsIdentity(
            baseUrl,
            config.clientKeyAlias,
            config.trustedServerSha256,
            config.expectedWindowsDeviceId
        )

        val tls = transport.createPinnedTls13Context(
            config.clientKeyAlias,
            config.trustedServerSha256
        )
        val localServer = ServerSocket()
        localServer.reuseAddress = false
        localServer.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1)

        val inboundFrames = Channel<ByteString>(capacity = MAX_QUEUED_FRAMES)
        val localSocketRef = AtomicReference<Socket?>(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = OkHttpClient.Builder()
            .sslSocketFactory(tls.first.socketFactory, tls.second)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .port(config.companionPort)
            .addPathSegments("v1/tunnel/rdp")
            .addQueryParameter("sessionId", config.sessionId)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.sessionToken}")
            .build()
        val opened = kotlinx.coroutines.CompletableDeferred<Unit>()
        val socketRef = AtomicReference<WebSocket?>(null)

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size > MAX_FRAME_BYTES || !inboundFrames.trySend(bytes).isSuccess) {
                    webSocket.close(1013, "INFINITY tunnel receive queue is full")
                    inboundFrames.close(IOException("Remote tunnel exceeded receive capacity."))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.close(1003, "Only binary RDP tunnel frames are accepted")
                inboundFrames.close(IOException("Unexpected text frame from Companion."))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                opened.completeExceptionally(IOException("Windows closed the tunnel before it became ready."))
                inboundFrames.close()
                localSocketRef.getAndSet(null)?.let { socket -> runCatching { socket.close() } }
                runCatching { localServer.close() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                opened.completeExceptionally(IOException("Windows closed the RDP tunnel."))
                inboundFrames.close()
                localSocketRef.getAndSet(null)?.let { socket -> runCatching { socket.close() } }
                runCatching { localServer.close() }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                opened.completeExceptionally(error)
                inboundFrames.close(error)
                localSocketRef.getAndSet(null)?.let { socket -> runCatching { socket.close() } }
                runCatching { localServer.close() }
            }
        })
        socketRef.set(webSocket)

        try {
            withTimeout(15_000) { opened.await() }
        } catch (error: Throwable) {
            webSocket.cancel()
            inboundFrames.close(error)
            localServer.close()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            scope.cancel()
            throw error
        }

        val relayJob = scope.launch {
            try {
                val localSocket = localServer.accept()
                localSocket.tcpNoDelay = true
                localSocketRef.set(localSocket)
                val output = localSocket.getOutputStream()
                val input = localSocket.getInputStream()

                val localToRemote = launch {
                    val buffer = ByteArray(MAX_FRAME_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (webSocket.queueSize() > MAX_QUEUED_BYTES) {
                            throw IOException("RDP tunnel send queue exceeded its safety limit.")
                        }
                        if (!webSocket.send(buffer.copyOf(count).toByteString())) {
                            throw IOException("Windows WebSocket tunnel is no longer accepting data.")
                        }
                    }
                    inboundFrames.close()
                }

                try {
                    for (frame in inboundFrames) {
                        output.write(frame.toByteArray())
                        output.flush()
                    }
                } finally {
                    localToRemote.cancel()
                    localSocketRef.getAndSet(null)?.runCatching { close() }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                inboundFrames.close(error)
                webSocket.close(1011, "Local RDP relay stopped")
            }
        }

        return Handle("127.0.0.1", localServer.localPort) {
            relayJob.cancel()
            scope.cancel()
            inboundFrames.close()
            localSocketRef.getAndSet(null)?.runCatching { close() }
            runCatching { localServer.close() }
            socketRef.getAndSet(null)?.close(1000, "INFINITY RDP session closed")
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    companion object {
        private const val MAX_FRAME_BYTES = 32 * 1024
        private const val MAX_QUEUED_FRAMES = 64
        private const val MAX_QUEUED_BYTES = 1024L * 1024L
    }
}
