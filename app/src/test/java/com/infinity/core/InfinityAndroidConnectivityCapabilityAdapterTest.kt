package com.infinity.core

import com.infinity.core.advanced.data.capabilities.INFINITY_OFFLINE_CONNECTION_STATE
import com.infinity.core.advanced.data.capabilities.infinityMapConnectionState
import com.infinity.core.advanced.domain.capabilities.InfinityConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityAndroidConnectivityCapabilityAdapterTest {
    @Test fun offlineStateDoesNotReportMeteredOrTransport() {
        assertEquals(INFINITY_OFFLINE_CONNECTION_STATE,
            infinityMapConnectionState(false, false, null))
    }

    @Test fun meteredCellularConnectionIsReportedAccurately() {
        assertEquals(InfinityConnectionState(true, true, "cellular"),
            infinityMapConnectionState(true, false, "cellular"))
    }

    @Test fun unmeteredWifiConnectionIsReportedAccurately() {
        assertEquals(InfinityConnectionState(true, false, "wifi"),
            infinityMapConnectionState(true, true, "wifi"))
    }

    @Test fun unknownTransportIsNotExposedAsAValidTransport() {
        assertEquals(InfinityConnectionState(true, false, null),
            infinityMapConnectionState(true, true, "unrecognized"))
    }

    @Test fun connectionWithoutInternetIsNotReportedAsConnected() {
        assertEquals(InfinityConnectionState(false, false, "wifi"),
            infinityMapConnectionState(false, false, "wifi"))
    }
}
