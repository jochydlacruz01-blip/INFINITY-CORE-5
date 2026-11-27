package com.infinity.core

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectRdpTunnelTest {
    @Test fun identityOnlySessionCannotOpenRdpTunnel() = runBlocking {
        val error = runCatching {
            InfinityConnectRdpTunnel().open(
                InfinityConnectRdpTunnel.Config(
                    companionHost = "127.0.0.1",
                    companionPort = 7443,
                    clientKeyAlias = "infinity-connect-client-v1",
                    trustedServerSha256 = "A".repeat(64),
                    expectedWindowsDeviceId = UUID.randomUUID().toString(),
                    sessionId = UUID.randomUUID().toString(),
                    sessionToken = "A".repeat(43),
                    grantedCapabilities = setOf("identity.read")
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("remote.display") == true)
    }

    @Test fun validRemoteCapabilitiesAreStillBlockedByLocalFeatureGate() = runBlocking {
        val error = runCatching {
            InfinityConnectRdpTunnel(
                remoteDesktopGate = {
                    InfinityConnectFeatureGates.requireRemoteDesktopEnabled(
                        isValidationBuild = false,
                        expiresAtEpochSeconds = 0L,
                        nowEpochSeconds = 1_000L
                    )
                }
            ).open(
                InfinityConnectRdpTunnel.Config(
                    companionHost = "127.0.0.1",
                    companionPort = 7443,
                    clientKeyAlias = "infinity-connect-client-v1",
                    trustedServerSha256 = "A".repeat(64),
                    expectedWindowsDeviceId = UUID.randomUUID().toString(),
                    sessionId = UUID.randomUUID().toString(),
                    sessionToken = "A".repeat(43),
                    grantedCapabilities = setOf("identity.read", "remote.display", "remote.input")
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("blocked") == true)
    }

    @Test fun displayWithoutInputCapabilityCannotOpenRdpTunnel() = runBlocking {
        val error = runCatching {
            InfinityConnectRdpTunnel().open(
                InfinityConnectRdpTunnel.Config(
                    companionHost = "127.0.0.1",
                    companionPort = 7443,
                    clientKeyAlias = "infinity-connect-client-v1",
                    trustedServerSha256 = "A".repeat(64),
                    expectedWindowsDeviceId = UUID.randomUUID().toString(),
                    sessionId = UUID.randomUUID().toString(),
                    sessionToken = "A".repeat(43),
                    grantedCapabilities = setOf("remote.display")
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
