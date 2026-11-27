package com.infinity.core

import com.infinity.core.advanced.data.capabilities.infinityNotificationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityNotificationCapabilityAdapterTest {

    @Test
    fun mapsNotificationMetadataWithoutExposingMessageBody() {
        val summary = infinityNotificationSummary(
            InfinityNotification(
                key = "notification-key",
                packageName = "com.example.app",
                appName = "Example",
                title = "New alert",
                message = "private message body",
                timestamp = 1234L,
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )

        assertEquals("notification-key", summary.key)
        assertEquals("Example", summary.source)
        assertEquals("New alert", summary.title)
        assertEquals(1234L, summary.receivedAtEpochMillis)
        assertTrue(!summary.isSensitive)
    }

    @Test
    fun redactedNotificationRemainsRedactedInAdvancedCapability() {
        val summary = infinityNotificationSummary(
            InfinityNotification(
                key = "private-key",
                packageName = "com.example.app",
                appName = "Example",
                title = "",
                message = "",
                timestamp = 5678L,
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )

        assertNull(summary.title)
        assertTrue(summary.isSensitive)
    }
}
