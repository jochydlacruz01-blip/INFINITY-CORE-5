package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectPreflightTest {
    @Test fun validEndpointIsReadyButWarnsAboutNetworkSecurity() {
        val items = runInfinityConnectPreflight("192.168.1.20", "3389", "user")
        assertTrue(isInfinityPreflightReady(items))
        assertTrue(items.any { it.code == "network.security" && it.severity == InfinityPreflightSeverity.WARNING })
        assertTrue(items.any { it.code == "session.unverified" })
    }
    @Test fun invalidHostBlocksPreflight() {
        val items = runInfinityConnectPreflight("https://host/path", "3389", "user")
        assertFalse(isInfinityPreflightReady(items))
        assertTrue(items.any { it.code == "host.invalid" && it.severity == InfinityPreflightSeverity.BLOCKED })
    }
    @Test fun invalidPortBlocksPreflight() {
        assertFalse(isInfinityPreflightReady(runInfinityConnectPreflight("host.local", "70000", "user")))
    }
    @Test fun blankUsernameBlocksPreflight() {
        assertFalse(isInfinityPreflightReady(runInfinityConnectPreflight("host.local", "3389", " ")))
    }
    @Test fun customPortProducesWarning() {
        val items = runInfinityConnectPreflight("host.local", "3390", "user")
        assertTrue(items.any { it.code == "port.custom" && it.severity == InfinityPreflightSeverity.WARNING })
    }
}
