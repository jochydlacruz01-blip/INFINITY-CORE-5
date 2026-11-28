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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The host workflow runs seed, capture, force-stop, relaunch and verify as separate
 * phases. The test APK's own preferences hold the post-start baseline while Android
 * ActivityManager terminates the target application's process.
 */
@RunWith(AndroidJUnit4::class)
class InfinityProcessPersistenceInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val baseline get() = instrumentation.context.getSharedPreferences(
        "infinity_process_persistence_baseline", Context.MODE_PRIVATE
    )


    /**
     * Runs the complete lifecycle in one instrumentation session. Keeping seed,
     * capture and verification in one Gradle invocation prevents app reinstall or
     * test-runner setup from changing the target package's data between phases.
     * UiDevice shell commands perform a real ActivityManager force-stop.
     */
    @Test(timeout = 180_000)
    fun stateSurvivesRealTargetProcessDeathInSingleSession() {
        seedThreeWorkspaceStateBeforeHostForceStop()
        captureStateAfterInitialAppLaunch()

        val device = UiDevice.getInstance(instrumentation)
        val beforePid = device.executeShellCommand("pidof ${context.packageName}").trim()
        assertTrue("The original INFINITY process must be running", beforePid.isNotEmpty())

        device.executeShellCommand("am force-stop ${context.packageName}")
        var stopped = false
        repeat(40) {
            if (device.executeShellCommand("pidof ${context.packageName}").trim().isEmpty()) {
                stopped = true
                return@repeat
            }
            Thread.sleep(250)
        }
        assertTrue("ActivityManager must terminate the original INFINITY process", stopped)

        launchDesktop()
        assertDesktopUi(device)
        var afterPid = ""
        repeat(40) {
            afterPid = device.executeShellCommand("pidof ${context.packageName}").trim()
            if (afterPid.isNotEmpty()) return@repeat
            Thread.sleep(250)
        }
        assertTrue("INFINITY must relaunch after force-stop", afterPid.isNotEmpty())
        assertTrue("Relaunch must create a different process", beforePid != afterPid)

        verifyStateAfterHostPerformedProcessRestart()
    }

    @Test fun seedThreeWorkspaceStateBeforeHostForceStop() {
        val prefs = context.getSharedPreferences("infinity_desktop", Context.MODE_PRIVATE)
        val windows = """[
            {"id":"notes_1","title":"Ventana proceso 1","kind":"notes","x":31,"y":91,"width":410,"height":320,"minimized":true,"workspace":1,"maximized":false},
            {"id":"files_2","title":"Ventana proceso 2","kind":"files","x":142,"y":152,"width":520,"height":420,"minimized":false,"workspace":2,"maximized":false},
            {"id":"apps_3","title":"Ventana proceso 3","kind":"apps","x":253,"y":213,"width":480,"height":380,"minimized":false,"workspace":3,"maximized":true}
        ]""".trimIndent()
        val committed = prefs.edit().clear()
            .putInt("active_workspace", 2)
            .putString(infinityVirtualDesktopNameKey(1), "PROCESS-WORKSPACE-1")
            .putString(infinityVirtualDesktopNameKey(2), "PROCESS-WORKSPACE-2")
            .putString(infinityVirtualDesktopNameKey(3), "PROCESS-WORKSPACE-3")
            .putString(infinityVirtualDesktopBackgroundPresetKey(1), "nebula")
            .putString(infinityVirtualDesktopBackgroundPresetKey(2), "punk")
            .putString(infinityVirtualDesktopBackgroundPresetKey(3), "aurora")
            .putString(infinityVirtualDesktopWallpaperKey(1), "content://invalid.infinity.test/wallpaper-one")
            .putString(infinityVirtualDesktopWallpaperKey(2), "content://invalid.infinity.test/wallpaper-two")
            .putString(infinityVirtualDesktopWallpaperKey(3), "content://invalid.infinity.test/wallpaper-three")
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
            .putString(INFINITY_SAVED_WINDOWS_KEY, windows)
            .commit()
        assertTrue("Seed state must be durably committed", committed)
        assertTrue("Seed snapshot must pass the production validator",
            infinityIsValidWindowSnapshot(prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null)))
    }

    @Test(timeout = 90_000)
    fun captureStateAfterInitialAppLaunch() {
        val device = UiDevice.getInstance(instrumentation)
        launchDesktop()
        assertDesktopUi(device)
        Thread.sleep(1200)

        val prefs = context.getSharedPreferences("infinity_desktop", Context.MODE_PRIVATE)
        val editor = baseline.edit().clear()
            .putInt("active_workspace", prefs.getInt("active_workspace", -1))
            .putString("saved_windows", prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null))
            .putString("valid_windows_backup", prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null))
            .putString("theme_choice", prefs.getString("theme_choice", null))
            .putString("appearance", prefs.getString("appearance", null))
            .putString("icon_pack", prefs.getString("icon_pack", null))
            .putString("desktop_wallpaper_preset", prefs.getString("desktop_wallpaper_preset", null))
            .putString("taskbar_style", prefs.getString("taskbar_style", null))
            .putFloat("icon_size", prefs.getFloat("icon_size", Float.NaN))
        (1..3).forEach { workspace ->
            editor.putString("name:$workspace", prefs.getString(infinityVirtualDesktopNameKey(workspace), null))
            editor.putString("preset:$workspace", prefs.getString(infinityVirtualDesktopBackgroundPresetKey(workspace), null))
            editor.putString("wallpaper:$workspace", prefs.getString(infinityVirtualDesktopWallpaperKey(workspace), null))
            editor.putString("shortcuts:$workspace", prefs.getString(infinityWorkspaceDesktopShortcutKey(workspace), null))
            editor.putString("focus:$workspace", prefs.getString(infinityWorkspaceFocusedWindowKey(workspace), null))
            editor.putFloat("icon-x:$workspace", prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "x"), Float.NaN))
            editor.putFloat("icon-y:$workspace", prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "y"), Float.NaN))
            val uri = prefs.getString(infinityVirtualDesktopWallpaperKey(workspace), null)
            editor.putBoolean("wallpaper-grant:$workspace",
                uri != null && infinityHasPersistedWallpaperReadPermission(context, android.net.Uri.parse(uri)))
        }
        assertTrue("Post-launch baseline must be durably saved in the test package", editor.commit())
    }

    @Test(timeout = 90_000)
    fun verifyStateAfterHostPerformedProcessRestart() {
        val device = UiDevice.getInstance(instrumentation)
        launchDesktop()
        assertDesktopUi(device)
        val prefs = context.getSharedPreferences("infinity_desktop", Context.MODE_PRIVATE)

        assertEquals(baseline.getInt("active_workspace", -1), prefs.getInt("active_workspace", -2))
        assertEquals(baseline.getString("theme_choice", null), prefs.getString("theme_choice", null))
        assertEquals(baseline.getString("appearance", null), prefs.getString("appearance", null))
        assertEquals(baseline.getString("icon_pack", null), prefs.getString("icon_pack", null))
        assertEquals(baseline.getString("desktop_wallpaper_preset", null), prefs.getString("desktop_wallpaper_preset", null))
        assertEquals(baseline.getString("taskbar_style", null), prefs.getString("taskbar_style", null))
        assertEquals(baseline.getFloat("icon_size", Float.NaN), prefs.getFloat("icon_size", Float.NaN), 0.001f)

        (1..3).forEach { workspace ->
            assertEquals("Workspace $workspace name",
                baseline.getString("name:$workspace", null),
                prefs.getString(infinityVirtualDesktopNameKey(workspace), null))
            assertEquals("Workspace $workspace background preset",
                baseline.getString("preset:$workspace", null),
                prefs.getString(infinityVirtualDesktopBackgroundPresetKey(workspace), null))
            assertEquals("Workspace $workspace wallpaper URI",
                baseline.getString("wallpaper:$workspace", null),
                prefs.getString(infinityVirtualDesktopWallpaperKey(workspace), null))
            assertEquals("Workspace $workspace shortcuts",
                baseline.getString("shortcuts:$workspace", null),
                prefs.getString(infinityWorkspaceDesktopShortcutKey(workspace), null))
            assertEquals("Workspace $workspace focused window",
                baseline.getString("focus:$workspace", null),
                prefs.getString(infinityWorkspaceFocusedWindowKey(workspace), null))
            assertEquals("Workspace $workspace icon X",
                baseline.getFloat("icon-x:$workspace", Float.NaN),
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "x"), Float.NaN), 0.001f)
            assertEquals("Workspace $workspace icon Y",
                baseline.getFloat("icon-y:$workspace", Float.NaN),
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "y"), Float.NaN), 0.001f)
            val uri = prefs.getString(infinityVirtualDesktopWallpaperKey(workspace), null)
            val hasGrant = uri != null &&
                infinityHasPersistedWallpaperReadPermission(context, android.net.Uri.parse(uri))
            assertEquals("Workspace $workspace persisted URI grant state",
                baseline.getBoolean("wallpaper-grant:$workspace", false), hasGrant)
        }

        val expectedWindows = JSONArray(baseline.getString("saved_windows", "[]"))
        val actualWindows = JSONArray(prefs.getString(INFINITY_SAVED_WINDOWS_KEY, "[]"))
        assertEquals("Window count", expectedWindows.length(), actualWindows.length())
        for (index in 0 until expectedWindows.length()) {
            val expected = expectedWindows.getJSONObject(index)
            val actual = actualWindows.getJSONObject(index)
            assertEquals("Window z-order at $index", expected.getString("id"), actual.getString("id"))
            assertEquals(expected.getString("title"), actual.getString("title"))
            assertEquals(expected.getString("kind"), actual.getString("kind"))
            assertEquals(expected.getInt("workspace"), actual.getInt("workspace"))
            assertEquals(expected.getDouble("x"), actual.getDouble("x"), 0.01)
            assertEquals(expected.getDouble("y"), actual.getDouble("y"), 0.01)
            assertEquals(expected.getDouble("width"), actual.getDouble("width"), 0.01)
            assertEquals(expected.getDouble("height"), actual.getDouble("height"), 0.01)
            assertEquals(expected.getBoolean("minimized"), actual.getBoolean("minimized"))
            assertEquals(expected.getBoolean("maximized"), actual.getBoolean("maximized"))
        }
        assertTrue(actualWindows.getJSONObject(0).getBoolean("minimized"))
        assertTrue(actualWindows.getJSONObject(2).getBoolean("maximized"))
    }

    private fun launchDesktop() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: throw AssertionError("INFINITY launcher activity is not registered")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }

    private fun assertDesktopUi(device: UiDevice) {
        assertTrue("INFINITY app must be visible",
            device.wait(Until.hasObject(By.pkg(context.packageName)), 20_000))
        // The active workspace label is conditionally rendered by desktop layout.
        // Verify its source-of-truth state here; the process-restart phase compares it
        // again after ActivityManager has killed and relaunched the app process.
        val desktopPrefs = context.getSharedPreferences("infinity_desktop", Context.MODE_PRIVATE)
        assertEquals("The seeded workspace must remain active after launch", 2,
            desktopPrefs.getInt("active_workspace", -1))
        (1..3).forEach { id ->
            assertEquals("Workspace $id name must be restored into app state",
                "PROCESS-WORKSPACE-$id",
                desktopPrefs.getString(infinityVirtualDesktopNameKey(id), null))
        }
        val savedWindows = JSONArray(desktopPrefs.getString(INFINITY_SAVED_WINDOWS_KEY, "[]"))
        assertTrue("Restored snapshot must include the workspace 2 AURORA window",
            (0 until savedWindows.length()).any { index ->
                val window = savedWindows.getJSONObject(index)
                window.optString("id") == "files_2" && window.optInt("workspace") == 2
            })
    }
}
