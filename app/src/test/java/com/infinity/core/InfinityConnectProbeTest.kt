package com.infinity.core

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityConnectProbeTest {
    @Test
    fun classifiesSuccessfulAndInvalidEndpointStates() {
        assertEquals(InfinityProbeStatus.CONNECTED, InfinityProbeStatus.CONNECTED)
        assertEquals(InfinityProbeStatus.INVALID_ENDPOINT, infinityProbeFailureKind(IllegalArgumentException()))
    }

    @Test
    fun classifiesCommonTcpConnectionFailures() {
        assertEquals(InfinityProbeStatus.HOST_NOT_FOUND, infinityProbeFailureKind(UnknownHostException()))
        assertEquals(InfinityProbeStatus.TIMEOUT, infinityProbeFailureKind(SocketTimeoutException()))
        assertEquals(InfinityProbeStatus.REFUSED, infinityProbeFailureKind(ConnectException()))
        assertEquals(InfinityProbeStatus.NETWORK_ERROR, infinityProbeFailureKind(java.io.IOException()))
        assertEquals(InfinityProbeStatus.BLOCKED, infinityProbeFailureKind(SecurityException()))
    }
}
