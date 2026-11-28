package com.infinity.core

import android.content.Context
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
 * Host-orchestrated process-death test. The workflow runs the seed test, exits
 * instrumentation, force-stops and relaunches the app with adb, then runs verify.
 * This prevents the test runner itself from being killed by force-stop.
 */
@RunWith(AndroidJUnit4::class)
class InfinityProcessPersistenceInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

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
        assertTrue("Seed snapshot must pass the production validator", infinityIsValidWindowSnapshot(
            prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null)
        ))
    }

    @Test(timeout = 90_000)
    fun verifyThreeWorkspaceStateAfterHostPerformedProcessRestart() {
        val prefs = context.getSharedPreferences("infinity_desktop", Context.MODE_PRIVATE)
        val device = UiDevice.getInstance(instrumentation)
        assertTrue(
            "Relaunched application must be visible in the foreground",
            device.wait(Until.hasObject(By.pkg(context.packageName)), 20_000)
        )
        assertTrue(
            "Workspace 2 name must be restored in the actual application UI",
            device.wait(Until.hasObject(By.text("PROCESS-WORKSPACE-2")), 20_000)
        )
        assertTrue(
            "Workspace 2 AURORA window must be restored in the actual application UI",
            device.wait(Until.hasObject(By.desc("Ventana Ventana proceso 2")), 20_000)
        )

        assertEquals(2, prefs.getInt("active_workspace", -1))
        (1..3).forEach { workspace ->
            assertEquals("Workspace $workspace name", "PROCESS-WORKSPACE-$workspace",
                prefs.getString(infinityVirtualDesktopNameKey(workspace), null))
            assertEquals("Workspace $workspace background preset",
                listOf("nebula", "punk", "aurora")[workspace - 1],
                prefs.getString(infinityVirtualDesktopBackgroundPresetKey(workspace), null))
            assertEquals("Workspace $workspace focus",
                listOf("notes_1", "files_2", "apps_3")[workspace - 1],
                prefs.getString(infinityWorkspaceFocusedWindowKey(workspace), null))
            assertEquals("Workspace $workspace icon X", listOf(41f, 142f, 243f)[workspace - 1],
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "x"), Float.NaN), 0.001f)
            assertEquals("Workspace $workspace icon Y", listOf(61f, 162f, 263f)[workspace - 1],
                prefs.getFloat(infinityWorkspaceIconPositionKey(workspace, "files", "y"), Float.NaN), 0.001f)
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
}
