package com.infinity.core

import com.infinity.core.advanced.data.capabilities.infinityIsValidLauncherPackageName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAndroidApplicationLauncherAdapterTest {
    @Test
    fun acceptsNormalAndroidPackageNames() {
        assertTrue(infinityIsValidLauncherPackageName("com.example.app"))
        assertTrue(infinityIsValidLauncherPackageName("org.example_app.client2"))
    }

    @Test
    fun rejectsMalformedPackageNames() {
        assertFalse(infinityIsValidLauncherPackageName(""))
        assertFalse(infinityIsValidLauncherPackageName("com"))
        assertFalse(infinityIsValidLauncherPackageName("com..app"))
        assertFalse(infinityIsValidLauncherPackageName(".example.app"))
        assertFalse(infinityIsValidLauncherPackageName("com.example;start"))
        assertFalse(infinityIsValidLauncherPackageName("com.example/app"))
    }
}
