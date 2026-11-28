package com.infinity.core

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This is a process-death acceptance test, not a layout/device-form-factor suite.
 * The test runner remains in the separate test package while ActivityManager
 * force-stops and relaunches the application process.
 */
@RunWith(AndroidJUnit4::class)
class InfinityProcessPersistenceInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Test(timeout = 180_000)
    fun allThreeWorkspacesSurviveRealForceStopAndFreshProcessLaunch() {
        val prefs = context.getSharedPreferences("infinity_desktop", Context.MODE_PRIVATE)
        val expectedWindows = """[
            {"id":"notes_1","title":"Ventana proceso 1","kind":"notes","x":31,"y":91,"width":410,"height":320,"minimized":true,"workspace":1,"maximized":false},
            {"id":"files_2","title":"Ventana proceso 2","kind":"files","x":142,"y":152,"width":520,"height":420,"minimized":false,"workspace":2,"maximized":false},
            {"id":"apps_3","title":"Ventana proceso 3","kind":"apps","x":253,"y":213,"width":480,"height":380,"minimized":false,"workspace":3,"maximized":true}
        ]""".trimIndent()
        val editor = prefs.edit().clear()
            .putInt("active_workspace", 2)
            .putString(infinityVirtualDesktopNameKey(1), "PROCESS-WORKSPACE-1")
            .putString(infinityVirtualDesktopNameKey(2), "PROCESS-WORKSPACE-2")
            .putString(infinityVirtualDesktopNameKey(3), "PROCESS-WORKSPACE-3")
            .putString(infinityVirtualDesktopBackgroundPresetKey(1), "nebula")
            .putString(infinityVirtualDesktopBackgroundPresetKey(2), "punk")
            .putString(infinityVirtualDesktopBackgroundPresetKey(3), "aurora")
            .putString(infinityWorkspaceDesktopShortcutKey(1), infinitySerializeDesktopShortcutPackages(listOf("com.android.settings")))
            .putString(infinityWorkspaceDesktopShortcutKey(2), infinitySerializeDesktopShortcutPackages(listOf("com.android.settings", "com.android.contacts")))
            .putString(infinityWorkspaceDesktopShortcutKey(3), infinitySerializeDesktopShortcutPackages(listOf("com.android.camera")))
            .putFloat(infinityWorkspaceIconPositionKey(1, "files", "x"), 41f)
            .putFloat(infinityWorkspaceIconPositionKey(1, "files", "y"), 61f)
            .putFloat(infinityWorkspaceIconPositionKey(2, "files", "x"), 142f)
            .putFloat(infinityWorkspaceIconPositionKey(2, "files", "y"), 162f)
            .putFloat(infinityWorkspaceIconPositionKey(3, "files", "x"), 243f)
            .putFloat(infinityWorkspaceIconPositionKey(3, "files", "y"), 263f)
            .putString(infinityWorkspaceFocusedWindowKey(1), "notes_1")
            .putString(infinityWorkspaceFocusedWindowKey(2), "files_2")
            .putString(infinityWorkspaceFocusedWindowKey(3), "apps_3")
            .putString(INFINITY_SAVED_WINDOWS_KEY, expectedWindows)
        assertTrue("Seed state must be durably committed before process termination", editor.commit())

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: throw AssertionError("INFINITY launcher activity is not registered")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        assertTrue("Initial app process must become visible", device.wait(Until.hasObject(By.pkg(context.packageName)), 20_000))
        val firstPid = waitForAppPid(20_000)

        // This is a real ActivityManager force-stop, not ActivityScenario.recreate().
        device.executeShellCommand("am force-stop ${context.packageName}")
        val stopped = device.wait(Until<Boolean>({ currentPid().isBlank() }, 10_000))
        assertTrue("The original app process must actually terminate", stopped)

        context.startActivity(launchIntent)
        assertTrue("The app must relaunch into a fresh process", device.wait(Until.hasObject(By.pkg(context.packageName)), 20_000))
        val secondPid = waitForAppPid(20_000)
        assertNotEquals("The relaunched app must have a different Linux process ID", firstPid, secondPid)

        assertTrue(
            "The active workspace name must be restored in the real app UI",
            device.wait(Until.hasObject(By.text("PROCESS-WORKSPACE-2")), 15_000)
        )
        assertTrue(
            "The active workspace AURORA window must be restored in the real app UI",
            device.wait(Until.hasObject(By.desc("Ventana Ventana proceso 2")), 15_000)
        )

        assertEquals(2, prefs.getInt("active_workspace", -1))
        (1..3).forEach { workspace ->
            assertEquals("Workspace $workspace name", "PROCESS-WORKSPACE-$workspace",
                prefs.getString(infinityVirtualDesktopNameKey(workspace), null))
            assertEquals("Workspace $workspace background preset",
                listOf("nebula", "punk", "aurora")[workspace - 1],
                prefs.getString(infinityVirtualDesktopBackgroundPresetKey(workspace), null))
            assertEquals("Workspace $workspace focus", listOf("notes_1", "files_2", "apps_3")[workspace - 1],
                prefs.getString(infinityWorkspaceFocusedWindowKey(workspace), null))
            assertEquals("Workspace $workspace icon X", listOf(41f, 142f, 243f)[workspace - 1],
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "x"), Float.NaN))
            assertEquals("Workspace $workspace icon Y", listOf(61f, 162f, 263f)[workspace - 1],
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "y"), Float.NaN))
        }
        assertEquals(listOf("com.android.settings"),
            infinityParseDesktopShortcutPackages(prefs.getString(infinityWorkspaceDesktopShortcutKey(1), null)))
        assertEquals(listOf("com.android.settings", "com.android.contacts"),
            infinityParseDesktopShortcutPackages(prefs.getString(infinityWorkspaceDesktopShortcutKey(2), null)))
        assertEquals(listOf("com.android.camera"),
            infinityParseDesktopShortcutPackages(prefs.getString(infinityWorkspaceDesktopShortcutKey(3), null)))

        val restored = JSONArray(prefs.getString(INFINITY_SAVED_WINDOWS_KEY, "[]"))
        assertEquals(3, restored.length())
        val byId = (0 until restored.length()).map { restored.getJSONObject(it) }
            .associateBy { it.getString("id") }
        assertEquals(31.0, byId.getValue("notes_1").getDouble("x"), 0.01)
        assertTrue(byId.getValue("notes_1").getBoolean("minimized"))
        assertEquals(142.0, byId.getValue("files_2").getDouble("x"), 0.01)
        assertFalse(byId.getValue("files_2").getBoolean("minimized"))
        assertTrue(byId.getValue("apps_3").getBoolean("maximized"))
    }

    private fun currentPid(): String =
        device.executeShellCommand("pidof ${context.packageName}").trim()

    private fun waitForAppPid(timeoutMillis: Long): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val pid = currentPid()
            if (pid.isNotBlank()) return pid
            Thread.sleep(250)
        }
        throw AssertionError("App process did not start within $timeoutMillis ms")
    }
}
