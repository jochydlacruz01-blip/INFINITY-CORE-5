package com.infinity.core

import android.view.KeyEvent
import com.infinity.core.advanced.data.capabilities.InfinityAndroidApplicationLauncherAdapter
import com.infinity.core.advanced.domain.capabilities.LaunchCapabilityResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class InfinityAndroidApplicationLauncherInstrumentedTest {
    @Test
    fun launchesAnInstalledAndroidSettingsApplication() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result = InfinityAndroidApplicationLauncherAdapter(context).launch("com.android.settings")

        assertEquals(LaunchCapabilityResult.Launched, result)
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    @Test
    fun reportsMissingInstalledApplicationWithoutThrowing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = InfinityAndroidApplicationLauncherAdapter(context)
            .launch("com.infinity.application.that.does.not.exist")

        assertEquals(LaunchCapabilityResult.AppNotFound, result)
    }
}
