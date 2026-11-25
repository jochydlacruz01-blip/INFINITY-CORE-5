package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityPortablePcPolicyTest {
    @Test fun transportNotImplementedNeverClaimsConnectionReadiness() {
        val check = infinityPortablePcChecklist("192.168.1.20", true, false, true)
        assertFalse(check.canAttemptConnection)
        assertFalse(check.releaseReady)
    }
    @Test fun privateVpnIsRecommendedFlagOnlyWhenAbsent() {
        assertFalse(infinityPortablePcChecklist("pc.local", true, true, true).vpnRecommended)
        assertTrue(infinityPortablePcChecklist("pc.local", true, true, false).vpnRecommended)
    }
    @Test fun invalidHostBlocksReadiness() {
        assertFalse(infinityPortablePcChecklist("https://bad/path", true, true, true).canAttemptConnection)
    }
    @Test fun releaseReadinessRequiresSecureCredentialsAndTransport() {
        assertFalse(infinityPortablePcChecklist("pc.local", false, true, true).releaseReady)
        assertFalse(infinityPortablePcChecklist("pc.local", true, false, true).releaseReady)
        assertTrue(infinityPortablePcChecklist("pc.local", true, true, true).releaseReady)
    }
    @Test fun remoteControlsOnlyEnabledWhenConnected() {
        assertFalse(infinityPortablePcRemoteControlsEnabled(InfinityPortablePcConnectionState.CONNECTING))
        assertTrue(infinityPortablePcRemoteControlsEnabled(InfinityPortablePcConnectionState.CONNECTED))
    }
    @Test fun lifecycleTransitionsAreBounded() {
        assertEquals(InfinityPortablePcConnectionState.CHECKING,
            infinityPortablePcNextState(InfinityPortablePcConnectionState.DISCONNECTED, "check"))
        assertEquals(InfinityPortablePcConnectionState.CONNECTING,
            infinityPortablePcNextState(InfinityPortablePcConnectionState.CHECKING, "connect"))
        assertEquals(InfinityPortablePcConnectionState.CONNECTED,
            infinityPortablePcNextState(InfinityPortablePcConnectionState.CONNECTING, "connected"))
        assertEquals(InfinityPortablePcConnectionState.DISCONNECTED,
            infinityPortablePcNextState(InfinityPortablePcConnectionState.CONNECTED, "disconnect"))
    }
}
