package com.infinity.core

import com.infinity.core.advanced.data.capabilities.infinityIsSafeAdvancedPreferenceKey
import com.infinity.core.advanced.data.capabilities.infinityNormalizeAdvancedThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAdvancedPreferencesAdapterPolicyTest {

    @Test
    fun supportedThemeIdsAreNormalized() {
        assertEquals("ocean", infinityNormalizeAdvancedThemeId(" OCEAN "))
        assertEquals("midnight", infinityNormalizeAdvancedThemeId("unknown"))
    }

    @Test
    fun preferenceKeysRejectSensitiveCredentialNames() {
        assertTrue(infinityIsSafeAdvancedPreferenceKey("dashboard_density"))
        assertFalse(infinityIsSafeAdvancedPreferenceKey("api_token"))
        assertFalse(infinityIsSafeAdvancedPreferenceKey("user_password"))
        assertFalse(infinityIsSafeAdvancedPreferenceKey("   "))
    }

    @Test
    fun preferenceKeysHaveBoundedSimpleNames() {
        assertFalse(infinityIsSafeAdvancedPreferenceKey("key with spaces"))
        assertFalse(infinityIsSafeAdvancedPreferenceKey("x".repeat(81)))
    }
}
