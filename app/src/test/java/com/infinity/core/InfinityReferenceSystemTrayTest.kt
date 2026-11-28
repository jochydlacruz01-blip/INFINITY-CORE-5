package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfinityReferenceSystemTrayTest {
    @Test
    fun networkLabelPrefersWifiThenCellularThenEthernet() {
        assertEquals("WIFI", infinitySystemTrayNetworkLabel(true, true, true, true))
        assertEquals("LTE", infinitySystemTrayNetworkLabel(true, false, true, true))
        assertEquals("LAN", infinitySystemTrayNetworkLabel(true, false, false, true))
    }

    @Test
    fun disconnectedNetworkIsReportedWithoutLeakingNetworkDetails() {
        assertEquals("SIN RED", infinitySystemTrayNetworkLabel(false, false, false, false))
    }

    @Test
    fun batteryPercentRejectsUnavailableAndOutOfRangeValues() {
        assertNull(infinitySystemTrayBatteryPercent(-1))
        assertNull(infinitySystemTrayBatteryPercent(101))
        assertEquals(0, infinitySystemTrayBatteryPercent(0))
        assertEquals(100, infinitySystemTrayBatteryPercent(100))
    }
}
