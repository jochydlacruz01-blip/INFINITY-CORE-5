package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class InfinityConnectFeatureGatesTest {
    @Test fun remoteCapabilitiesRemainDisabledUntilPhysicalInteroperabilityValidation() {
        assertFalse(InfinityConnectFeatureGates.REMOTE_DESKTOP_VALIDATED)
        assertFalse(InfinityConnectFeatureGates.FILE_TRANSFER_VALIDATED)
    }

    @Test fun identityOnlySessionDoesNotRequireRemoteFeatureGates() {
        InfinityConnectFeatureGates.requireCapabilitiesEnabled(setOf("identity.read"))
    }

    @Test fun remoteSessionCreationIsBlockedAtTransportBoundary() {
        assertThrows(IllegalStateException::class.java) {
            InfinityConnectFeatureGates.requireCapabilitiesEnabled(
                setOf("identity.read", "remote.display", "remote.input")
            )
        }
    }

    @Test fun fileTransferSessionCreationIsBlockedAtTransportBoundary() {
        assertThrows(IllegalStateException::class.java) {
            InfinityConnectFeatureGates.requireCapabilitiesEnabled(
                setOf("identity.read", "file.transfer")
            )
        }
    }

    @Test fun directRemoteTunnelOpeningIsBlocked() {
        assertThrows(IllegalStateException::class.java) {
            InfinityConnectFeatureGates.requireRemoteDesktopEnabled()
        }
    }

    @Test fun directFileOperationsAreBlocked() {
        assertThrows(IllegalStateException::class.java) {
            InfinityConnectFeatureGates.requireFileTransferEnabled()
        }
    }
}
