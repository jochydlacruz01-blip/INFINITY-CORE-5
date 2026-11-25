package com.infinity.core

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream

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
        compose.onNodeWithContentDescription("START").performScrollTo()
        Log.i("INFINITY_TEST_DIAGNOSTIC", "START click")
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        waitForText("INFINITY START")
        capture("02-start-open")
        compose.onNode(hasSetTextAction()).performTextInput("Mis")
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
        capture("03-orbit-open")
        compose.onNodeWithText("CERRAR ORBIT").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-orbit-panel").assertDoesNotExist()

        // CONTROL: interact with a real theme option and capture the resulting panel.
        compose.onNodeWithContentDescription("CONTROL").performScrollTo()
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

        compose.onNodeWithContentDescription("ADVANCED").performScrollTo()
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        compose.onNodeWithText("INFINITY ADVANCED").assertExists()
        compose.onNodeWithText("NOTIFICACIONES").assertExists()
        compose.onNodeWithText("INFINITY UI TEST").assertExists()
        capture("06-advanced-notifications")
        compose.onNodeWithText("LIMPIAR").performClick()
        settleComposeFrame()
        compose.onNodeWithText("Todavía no hay notificaciones disponibles.").assertExists()

        // Dock stays reachable after opening and closing the desktop panels.
        compose.onNodeWithContentDescription("HOME").performScrollTo()
        compose.onNodeWithContentDescription("HOME").performClick()
        settleComposeFrame()
        capture("04-dock-operational")

        // Rotate with START open; validate recovery of the main shell and Dock.
        compose.onNodeWithContentDescription("START").performScrollTo()
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
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Android emulator screenshot capture returned null: $name")
        val root = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "infinity-ui-captures"
        )
        check(root.mkdirs() || root.isDirectory) { "Cannot create screenshot folder: $root" }
        val file = File(root, "$name.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to encode emulator screenshot: $name"
            }
        }
        bitmap.recycle()
        check(file.length() > 10_000L) { "Screenshot is unexpectedly small: $file" }
    }
}
