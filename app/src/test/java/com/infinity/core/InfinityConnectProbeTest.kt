package com.infinity.core

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityConnectProbeTest {
    @Test
    fun reportsConnectedWhenLocalTcpEndpointAcceptsConnection() = runBlocking {
        ServerSocket(0).use { server ->
            val accepted = CountDownLatch(1)
            val listener = Thread {
                server.accept().use { accepted.countDown() }
            }.apply { isDaemon = true; start() }

            val result = probeInfinityTcpEndpoint(
                host = "127.0.0.1",
                port = server.localPort.toString(),
                timeoutMs = 1_000
            )

            assertEquals(InfinityProbeStatus.CONNECTED, result)
            assertEquals(true, accepted.await(1, TimeUnit.SECONDS))
            listener.join(1_000)
        }
    }

    @Test
    fun rejectsInvalidEndpointBeforeOpeningSocket() = runBlocking {
        assertEquals(
            InfinityProbeStatus.INVALID_ENDPOINT,
            probeInfinityTcpEndpoint(" ", "3389")
        )
        assertEquals(
            InfinityProbeStatus.INVALID_ENDPOINT,
            probeInfinityTcpEndpoint("127.0.0.1", "70000")
        )
        assertEquals(
            InfinityProbeStatus.INVALID_ENDPOINT,
            probeInfinityTcpEndpoint("127.0.0.1", "3389", timeoutMs = 0)
        )
    }

    @Test
    fun classifiesCommonTcpConnectionFailures() {
        assertEquals(
            InfinityProbeStatus.HOST_NOT_FOUND,
            infinityProbeFailureKind(java.net.UnknownHostException())
        )
        assertEquals(
            InfinityProbeStatus.TIMEOUT,
            infinityProbeFailureKind(java.net.SocketTimeoutException())
        )
        assertEquals(
            InfinityProbeStatus.REFUSED,
            infinityProbeFailureKind(java.net.ConnectException())
        )
        assertEquals(
            InfinityProbeStatus.NETWORK_ERROR,
            infinityProbeFailureKind(java.io.IOException())
        )
        assertEquals(
            InfinityProbeStatus.BLOCKED,
            infinityProbeFailureKind(SecurityException())
        )
        assertEquals(
            InfinityProbeStatus.INVALID_ENDPOINT,
            infinityProbeFailureKind(IllegalArgumentException())
        )
    }
}
