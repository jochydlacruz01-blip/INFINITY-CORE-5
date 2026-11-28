package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectPrivateEndpointPolicyTest {
    @Test fun allowsPrivateLanAndOverlayIpv4Ranges() {
        listOf("10.0.0.8", "172.16.4.2", "172.31.255.254", "192.168.1.20",
            "100.64.0.10", "100.127.255.254").forEach {
            assertTrue("Expected private address: $it", InfinityConnectPrivateEndpointPolicy.isAllowedHost(it))
        }
    }

    @Test fun allowsIpv6UniqueLocalAddresses() {
        assertTrue(InfinityConnectPrivateEndpointPolicy.isAllowedHost("fd12:3456:789a::10"))
    }

    @Test fun rejectsIpv4AndIpv6LoopbackAddresses() {
        listOf("127.0.0.1", "127.255.255.255", "::1").forEach {
            assertFalse("Expected loopback rejection: $it", InfinityConnectPrivateEndpointPolicy.isAllowedHost(it))
        }
    }

    @Test fun rejectsPublicAddressesAndHostnames() {
        listOf("8.8.8.8", "1.1.1.1", "203.0.113.10", "example.com",
            "localhost.evil.example").forEach {
            assertFalse("Expected rejection: $it", InfinityConnectPrivateEndpointPolicy.isAllowedHost(it))
        }
    }

    @Test fun rejectsMalformedAndScopedAddresses() {
        listOf("", "192.168.001.1", "192.168.1.999", "fe80::1%wlan0", "not-an-ip").forEach {
            assertFalse("Expected rejection: $it", InfinityConnectPrivateEndpointPolicy.isAllowedHost(it))
        }
    }
}
