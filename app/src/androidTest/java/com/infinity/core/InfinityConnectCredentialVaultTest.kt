package com.infinity.core

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfinityConnectCredentialVaultTest {
    private lateinit var context: Context

    @Before
    fun clearCredentialStore() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("infinity_connect_secrets", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun credentialIsRecoverableOnlyWithItsStableProfileAndEndpoint() {
        val firstVault = InfinityConnectCredentialVault(context)
        assertTrue(firstVault.save("profile-A", "pc.example.net:3389", "secret-value"))

        val reopenedVault = InfinityConnectCredentialVault(context)
        assertEquals("secret-value", reopenedVault.retrieve("profile-A", "pc.example.net:3389"))
        assertNull(reopenedVault.retrieve("profile-B", "pc.example.net:3389"))
        assertNull(reopenedVault.retrieve("profile-A", "other.example.net:3389"))
    }

    @Test
    fun deletingProfileCredentialRemovesItsStoredSecret() {
        val vault = InfinityConnectCredentialVault(context)
        assertTrue(vault.save("profile-A", "pc.example.net:3389", "secret-value"))

        vault.delete("profile-A", "pc.example.net:3389")

        assertNull(vault.retrieve("profile-A", "pc.example.net:3389"))
    }
}
