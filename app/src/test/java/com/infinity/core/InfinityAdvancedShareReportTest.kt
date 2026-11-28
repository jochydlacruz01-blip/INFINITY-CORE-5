package com.infinity.core

import com.infinity.core.advanced.domain.capabilities.InfinityConnectionState
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationSummary
import com.infinity.core.advanced.domain.usecases.InfinityAdvancedOverviewSnapshot
import com.infinity.core.advanced.presentation.buildInfinityAdvancedShareReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAdvancedShareReportTest {
    @Test
    fun sharedReportContainsOnlyApprovedAggregateDiagnostics() {
        val snapshot = InfinityAdvancedOverviewSnapshot(
            notifications = listOf(
                InfinityNotificationSummary(
                    key = "PRIVATE_NOTIFICATION_KEY_987",
                    source = "PRIVATE_APP_NAME",
                    title = "PRIVATE_MESSAGE_BODY",
                    receivedAtEpochMillis = 123456789L,
                    isSensitive = true
                )
            ),
            deviceMetrics = InfinityDeviceMetrics(
                capturedAtEpochMillis = 123456789L,
                cpuUsagePercent = null,
                memoryUsagePercent = 45f,
                storageUsagePercent = 62f,
                batteryLevelPercent = 81f,
                batteryTemperatureCelsius = 32.5f,
                isCharging = true,
                thermalStatus = "Normal"
            ),
            connectionState = InfinityConnectionState(true, true, "cellular"),
            selectedThemeId = "ocean"
        )

        val report = buildInfinityAdvancedShareReport(snapshot)

        assertTrue(report.contains("Memoria: 45.0%"))
        assertTrue(report.contains("Batería: 81.0%"))
        assertTrue(report.contains("Red: cellular · medida"))
        assertTrue(report.contains("Tema: ocean"))
        listOf(
            "PRIVATE_NOTIFICATION_KEY_987",
            "PRIVATE_APP_NAME",
            "PRIVATE_MESSAGE_BODY",
            "123456789",
            "packageName",
            "/data/",
            "Android ID"
        ).forEach { forbidden -> assertFalse("Report leaked $forbidden", report.contains(forbidden)) }
    }

    @Test
    fun unavailableMetricsProduceSafePlaceholders() {
        val report = buildInfinityAdvancedShareReport(
            InfinityAdvancedOverviewSnapshot(emptyList(), null, null, "midnight")
        )
        assertTrue(report.contains("Memoria: No disponible"))
        assertTrue(report.contains("Red: No disponible"))
        assertFalse(report.contains("null"))
    }
}
