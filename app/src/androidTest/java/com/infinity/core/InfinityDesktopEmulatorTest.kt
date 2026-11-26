package com.infinity.core

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
import org.json.JSONArray
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@RunWith(AndroidJUnit4::class)
class InfinityDesktopEmulatorTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val diagnostics = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            Log.e("INFINITY_TEST_DIAGNOSTIC", "FAILED " + description.methodName + ": " + e.stackTraceToString())
            runCatching { compose.onRoot(useUnmergedTree = true).printToLog("INFINITY_DEBUG") }
                .onFailure { Log.e("INFINITY_TEST_DIAGNOSTIC", "Semantics dump failed", it) }
        }
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before
    fun resetDesktopState() {
        // The desktop includes ambient/infinite Compose animations; freeze the test clock
        // so UI idleness is deterministic while gestures and state transitions are exercised.
        compose.mainClock.autoAdvance = false
        context.getSharedPreferences("infinity_desktop", 0).edit().clear().commit()
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        device.setOrientationNatural()
        device.waitForIdle()
    }

    @Test(timeout = 360_000)
    fun exerciseDesktopCaptureRealScreensAcrossOrientations() {
        val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
        capture("00-initial-desktop")
        if (isTablet) {
            // Workspace changes intentionally close ORBIT; reopen it for each selection.
            // Exercise real workspace transitions through ORBIT, not only its labels.
            listOf(2, 3, 1).forEach { targetWorkspace ->
                compose.onNodeWithTag("infinity-orbit-panel").assertDoesNotExist()
                compose.onNodeWithTag("infinity-dock-orbit").performClick()
                // The test clock is deliberately frozen in this suite. A click updates
                // Compose state, but the conditional ORBIT subtree is not committed until
                // the next frame; waitForIdle alone does not advance a frozen clock.
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                waitForTag("infinity-orbit-panel")
                compose.onNodeWithTag("infinity-orbit-panel").assertExists()
                Log.i("INFINITY_TEST_DIAGNOSTIC", "ORBIT workspace selector visible " + targetWorkspace)
                val workspaceSelector = compose.onNodeWithTag(
                    "infinity-orbit-workspace-$targetWorkspace"
                )
                workspaceSelector.assertExists()
                val label = when (targetWorkspace) {
                    1 -> "Espacio 1, Principal"
                    2 -> "Espacio 2, Trabajo"
                    else -> "Espacio 3, Personal"
                }
                compose.onNodeWithContentDescription(label).assertExists()
                Log.i("INFINITY_TEST_DIAGNOSTIC", "ORBIT click workspace " + targetWorkspace)
                workspaceSelector.performClick()
                settleComposeFrame()
                val persistedWorkspace = context.getSharedPreferences("infinity_desktop", 0)
                    .getInt("active_workspace", -1)
                assert(persistedWorkspace == targetWorkspace) {
                    "ORBIT selected workspace $targetWorkspace but persisted $persistedWorkspace"
                }
                compose.onNodeWithTag("infinity-orbit-panel").assertDoesNotExist()
            }
        }
        capture(if (isTablet) "01-desktop-tablet-portrait" else "10-desktop-compact-phone")

        // START: open, search, launch a real desktop tool.
        compose.onNodeWithContentDescription("START").assertExists()
        Log.i("INFINITY_TEST_DIAGNOSTIC", "START click")
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        waitForText("INFINITY START")
        capture("02-start-open")
        compose.onNode(hasSetTextAction()).performTextInput("Notas")
        settleComposeFrame()
        // Search can expose both the desktop icon and the matching result.
        compose.onNodeWithTag("infinity-start-app-notes").assertExists()
        // Launch the filtered result before clearing the query. With the emulator
        // clock frozen, clearing the LazyColumn filter first can leave a stale
        // semantics node for one frame and make the subsequent click a no-op.
        Log.i("INFINITY_TEST_DIAGNOSTIC", "START click notes catalog item")
        compose.onNodeWithTag("infinity-start-app-notes").performClick()
        settleComposeFrame()
        waitForContentDescription("Ventana Notas rápidas")
        compose.onNodeWithTag("infinity-window-notes").assertExists()
        compose.onNodeWithText("Bloc de ideas").assertExists()
        capture("07-aurora-single-window")

        // Move, resize, minimize, maximize and restore on the wide tablet viewport.
        if (isTablet) {
            compose.onAllNodesWithContentDescription("Maximizar o restaurar").get(0).performClick()
            settleComposeFrame()
            compose.onAllNodesWithContentDescription("Maximizar o restaurar").get(0).performClick()
            settleComposeFrame()
            compose.onNodeWithTag("infinity-window-title-notes")
                .performTouchInput { swipe(center, center + Offset(44f, 30f), durationMillis = 420) }
            compose.onNodeWithTag("infinity-window-resize-notes", useUnmergedTree = true)
                .performTouchInput { swipe(center, center + Offset(46f, 38f), durationMillis = 420) }
        }
        compose.onNodeWithContentDescription("Minimizar").performClick()
        settleComposeFrame()
        compose.onNodeWithContentDescription("Ventana Notas rápidas").assertDoesNotExist()
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-start-app-notes").performClick()
        settleComposeFrame()
        waitForContentDescription("Ventana Notas rápidas")
        compose.onNodeWithTag("infinity-window-notes").assertExists()
        compose.onNodeWithText("Bloc de ideas").assertExists()

        // Close the real window, verify removal, then reopen it from START.
        compose.onNodeWithContentDescription("Cerrar").performClick()
        settleComposeFrame()
        compose.onNodeWithContentDescription("Ventana Notas rápidas").assertDoesNotExist()
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-start-app-notes").performClick()
        settleComposeFrame()
        waitForContentDescription("Ventana Notas rápidas")
        compose.onNodeWithTag("infinity-window-notes").assertExists()

        // Close Notes before exercising START search so its text field cannot
        // be confused with the START search field in the Compose semantics tree.
        compose.onNodeWithContentDescription("Cerrar").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-window-notes").assertDoesNotExist()

        // START -> Files -> close -> START search reset. Repeat the real UI cycle
        // to catch stale LazyColumn results or state leaking between openings.
        repeat(2) { cycle ->
            Log.i("INFINITY_TEST_DIAGNOSTIC", "START files cycle ${cycle + 1}: open")
            compose.onNodeWithContentDescription("START").performClick()
            settleComposeFrame()
            waitForTag("infinity-start-app-files")
            val startSearch = compose.onNode(hasSetTextAction())
            assertTrue(
                "START search should be empty when reopened",
                startSearch.fetchSemanticsNode().config[SemanticsProperties.EditableText].text.isEmpty()
            )
            startSearch.performTextInput("Este Equipo")
            settleComposeFrame()
            startSearch.assertTextEquals("Este Equipo")
            waitForTag("infinity-start-app-files")
            compose.onNodeWithTag("infinity-start-app-files").assertExists()
            Log.i("INFINITY_TEST_DIAGNOSTIC", "START files cycle ${cycle + 1}: select")
            compose.onNodeWithTag("infinity-start-app-files").performClick()
            settleComposeFrame()
            waitForText("Explorador de archivos")
            compose.onNodeWithText("Explorador de archivos").assertExists()
            capture(if (cycle == 0) "08-aurora-files-window-first-cycle" else "08-aurora-files-window-second-cycle")

            compose.onNodeWithContentDescription("Cerrar").performClick()
            settleComposeFrame()
            compose.onNodeWithTag("infinity-window-files").assertDoesNotExist()
            compose.onNodeWithText("Explorador de archivos").assertDoesNotExist()
            Log.i("INFINITY_TEST_DIAGNOSTIC", "START files cycle ${cycle + 1}: closed")
        }

        // ORBIT open/close and Dock accessibility while a panel is open.
        Log.i("INFINITY_TEST_DIAGNOSTIC", "ORBIT dock click")
        compose.onNodeWithTag("infinity-dock-orbit").performClick()
        settleComposeFrame()
        waitForTag("infinity-orbit-panel")
        compose.onNodeWithText("CERRAR ORBIT").assertExists()
        compose.onNodeWithContentDescription("START").assertExists()

        // Opening ADVANCED from inside ORBIT must replace ORBIT, never stack panels.
        val openAdvancedFromOrbit = compose.onNodeWithText("ABRIR CENTRO DE NOTIFICACIONES")
        scrollOrbitUntilVisible("ABRIR CENTRO DE NOTIFICACIONES")
        openAdvancedFromOrbit.performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-orbit-panel").assertDoesNotExist()
        compose.onNodeWithTag("infinity-advanced-panel").assertExists()
        compose.onNodeWithTag("infinity-advanced-close").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-advanced-panel").assertDoesNotExist()

        // Reopen ORBIT to continue its independent close/accessibility regression.
        compose.onNodeWithTag("infinity-dock-orbit").performClick()
        settleComposeFrame()
        waitForTag("infinity-orbit-panel")
        capture("03-orbit-open")
        // ORBIT close stays at the top of the panel so it remains reachable on
        // phone and tablet without scrolling a long diagnostics list.
        val closeOrbit = compose.onNodeWithTag("infinity-orbit-close")
        closeOrbit.assertIsDisplayed()
        closeOrbit.assertHasClickAction()
        Log.i("INFINITY_TEST_DIAGNOSTIC", "ORBIT close target is visible; invoking its semantic click action.")
        closeOrbit.performClick()
        repeat(3) { settleComposeFrame() }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("infinity-orbit-panel").fetchSemanticsNodes().isEmpty()
        }
        Log.i("INFINITY_TEST_DIAGNOSTIC", "ORBIT panel removed after close action.")
        compose.onNodeWithTag("infinity-orbit-panel").assertDoesNotExist()

        // CONTROL: interact with a real theme option and capture the resulting panel.
        compose.onNodeWithContentDescription("CONTROL").assertExists()
        compose.onNodeWithContentDescription("CONTROL").performClick()
        settleComposeFrame()
        compose.onNodeWithText("CONTROL CENTER").assertExists()
        compose.onNodeWithContentDescription("Tema ocean").performClick()
        settleComposeFrame()
        capture("05-control-center")

        // Seed an in-app notification model item and clear it through the actual panel UI.
        InfinityNotificationStore.update(
            InfinityNotification(
                key = "emulator-ui-notification",
                packageName = context.packageName,
                appName = "INFINITY UI TEST",
                title = "Emulator notification",
                message = "Notification feed interaction test",
                timestamp = System.currentTimeMillis(),
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )
        compose.waitForIdle()

        compose.onNodeWithContentDescription("ADVANCED").assertExists()
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        compose.onNodeWithText("INFINITY ADVANCED").assertExists()
        compose.onNodeWithText("NOTIFICACIONES").assertExists()
        compose.onNodeWithText("INFINITY UI TEST").assertExists()
        capture("06-advanced-notifications")
        // The notifications section is below the quick-action cards in a scrollable panel.
        val advancedPanel = compose.onNodeWithTag("infinity-advanced-panel")
        val clearNotifications = compose.onNodeWithTag("infinity-notifications-clear")
        for (attempt in 0 until 6) {
            if (runCatching { clearNotifications.assertIsDisplayed() }.isSuccess) break
            advancedPanel.performTouchInput { swipeUp() }
            settleComposeFrame()
        }
        clearNotifications.assertIsDisplayed()
        clearNotifications.assertHasClickAction()
        Log.i("INFINITY_TEST_DIAGNOSTIC", "Notification clear action is visible; invoking click.")
        clearNotifications.performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.isEmpty()
        }
        settleComposeFrame()
        compose.onNodeWithText("Todavía no hay notificaciones disponibles.").assertExists()

        // Dock stays reachable after opening and closing the desktop panels.
        compose.onNodeWithContentDescription("HOME").assertExists()
        compose.onNodeWithContentDescription("HOME").performClick()
        settleComposeFrame()
        capture("04-dock-operational")

        // Rotate with START open; validate recovery of the main shell and Dock.
        compose.onNodeWithContentDescription("START").assertExists()
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        device.setOrientationLeft()
        device.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        settleComposeFrame()
        compose.onNodeWithContentDescription("START").assertExists()
        if (isTablet) {
            capture("09-desktop-tablet-landscape")
            device.setOrientationNatural()
            device.waitForIdle()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            settleComposeFrame()
            capture("01-desktop-tablet-portrait-after-rotation")
        } else {
            capture("10-desktop-compact-phone-landscape")
        }
    }

    @Test(timeout = 180_000)
    fun orbitMinimizeAllOnlyAffectsTheActiveWorkspace() {
        // Create one task in workspace 1.
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-start-app-notes").performClick()
        settleComposeFrame()
        waitForTag("infinity-window-notes")

        // Switch to workspace 2 through ORBIT and create a second task there.
        compose.onNodeWithTag("infinity-dock-orbit").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-orbit-workspace-2").performClick()
        settleComposeFrame()
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-start-app-files").performClick()
        settleComposeFrame()
        waitForTag("infinity-window-files")

        // Minimize-all in workspace 2 must not alter the task in workspace 1.
        compose.onNodeWithTag("infinity-dock-orbit").performClick()
        settleComposeFrame()
        val minimizeAll = compose.onNodeWithTag("infinity-orbit-minimize-all")
        scrollOrbitUntilVisible("infinity-orbit-minimize-all")
        minimizeAll.performClick()
        settleComposeFrame()

        val saved = JSONArray(
            context.getSharedPreferences("infinity_desktop", 0)
                .getString("saved_windows", "[]")
        )
        val states = (0 until saved.length()).mapNotNull { index ->
            saved.optJSONObject(index)?.let { item ->
                item.optString("kind") to (
                    item.optInt("workspace", 1) to item.optBoolean("minimized", false)
                )
            }
        }.toMap()
        assertTrue("Workspace 1 Notes task must remain visible", states["notes"] == (1 to false))
        assertTrue("Workspace 2 Files task must be minimized", states["files"] == (2 to true))
        assertTrue(
            "Focus must be cleared after minimizing all active workspace windows",
            context.getSharedPreferences("infinity_desktop", 0)
                .getString("focused_window_id", null) == null
        )
    }

    private fun scrollOrbitUntilVisible(targetTag: String) {
        val target = compose.onNodeWithTag(targetTag)
        val panel = compose.onNodeWithTag("infinity-orbit-panel")
        repeat(8) {
            if (runCatching { target.assertIsDisplayed() }.isSuccess) return
            panel.performTouchInput { swipeUp() }
            settleComposeFrame()
        }
        target.assertIsDisplayed()
    }

    private fun settleComposeFrame() {
        // This suite freezes Compose's clock; explicitly commit the UI state transition.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        device.waitForIdle()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        device.waitForIdle()
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "CI screenshot export requires scoped MediaStore support (Android 10+)."
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Android emulator screenshot capture returned null: $name")
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/infinity-ui-captures"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("Cannot create MediaStore screenshot row: $name")
        try {
            val output = resolver.openOutputStream(uri, "w")
                ?: throw AssertionError("Cannot open MediaStore screenshot output: $name")
            output.use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                    "Failed to encode emulator screenshot: $name"
                }
            }
            val publish = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            check(resolver.update(uri, publish, null, null) == 1) {
                "Failed to publish emulator screenshot: $name"
            }
            Log.i("INFINITY_TEST_DIAGNOSTIC", "Screenshot persisted to shared MediaStore: $name")
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        } finally {
            bitmap.recycle()
        }
    }
}
