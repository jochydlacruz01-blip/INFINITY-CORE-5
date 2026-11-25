package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectEngineContractTest {
    @Test fun targetDefaultsToStandardRdpPort() {
        val target = InfinityConnectTarget(host = "example.local", username = "user")
        assertEquals(3389, target.port)
    }

    @Test fun targetSupportsCustomPortAndOptionalSecret() {
        val target = InfinityConnectTarget(host = "10.0.0.2", port = 3390, username = "admin", password = charArrayOf('x'))
        assertEquals(3390, target.port)
        assertEquals('x', target.password?.first())
    }

    @Test fun eventFailureCarriesMessage() {
        val event = InfinityConnectEngineEvent.Failure("TLS rejected")
        assertEquals("TLS rejected", event.message)
    }
}
