package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Test

class InfinityConnectFeatureGatesTest {
    @Test fun remoteCapabilitiesRemainDisabledUntilPhysicalInteroperabilityValidation() {
        assertFalse(InfinityConnectFeatureGates.REMOTE_DESKTOP_VALIDATED)
        assertFalse(InfinityConnectFeatureGates.FILE_TRANSFER_VALIDATED)
    }
}
