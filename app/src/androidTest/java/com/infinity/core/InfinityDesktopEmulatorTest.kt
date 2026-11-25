package com.infinity.core

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class InfinityDesktopEmulatorTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before
    fun resetDesktopState() {
        context.getSharedPreferences("infinity_desktop", 0).edit().clear().commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        device.setOrientationNatural()
        device.waitForIdle()
    }

    @Test
    fun exerciseDesktopCaptureRealScreensAcrossOrientations() {
        val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
        capture(if (isTablet) "01-desktop-tablet-portrait" else "10-desktop-compact-phone")

        // START: open, search, launch a real desktop tool.
        compose.onNodeWithContentDescription("START").performClick()
        compose.onNodeWithText("INFINITY START").assertExists()
        capture("02-start-open")
        compose.onNode(hasSetTextAction()).performTextInput("Mis")
        compose.onNodeWithText("Mis Archivos").assertExists()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onAllNodesWithText("Mis Archivos").onLast().performClick()
        compose.onNodeWithText("Notas rápidas").assertExists()
        capture("07-aurora-single-window")

        // Move, resize, minimize, maximize and restore on the wide tablet viewport.
        if (isTablet) {
            compose.onNodeWithContentDescription("Maximizar o restaurar").performClick()
            compose.onNodeWithContentDescription("Maximizar o restaurar").performClick()
            compose.onNodeWithTag("infinity-window-title-notes")
                .performTouchInput { swipe(center, center + Offset(44f, 30f), durationMillis = 420) }
            compose.onNodeWithTag("infinity-window-resize-notes")
                .performTouchInput { swipe(center, center + Offset(46f, 38f), durationMillis = 420) }
        }
        compose.onNodeWithContentDescription("Minimizar").performClick()
        compose.onNodeWithContentDescription("START").performClick()
        compose.onAllNodesWithText("Mis Archivos").onLast().performClick()
        compose.onNodeWithText("Notas rápidas").assertExists()

        // Open a second independent window and retain both in the desktop task stack.
        compose.onNodeWithContentDescription("START").performClick()
        compose.onAllNodesWithText("Este Equipo").onLast().performClick()
        compose.onNodeWithText("Explorador de archivos").assertExists()
        capture("08-aurora-multiple-windows")
        if (isTablet) {
            compose.onNodeWithContentDescription("Maximizar o restaurar").onFirst().performClick()
            compose.onNodeWithContentDescription("Maximizar o restaurar").onFirst().performClick()
        }
        compose.onNodeWithContentDescription("Cerrar").onFirst().performClick()

        // ORBIT open/close and Dock accessibility while a panel is open.
        compose.onNodeWithContentDescription("ORBIT").performClick()
        compose.onNodeWithText("CERRAR ORBIT").assertExists()
        compose.onNodeWithContentDescription("START").assertExists()
        capture("03-orbit-open")
        compose.onNodeWithText("CERRAR ORBIT").performClick()

        // CONTROL: interact with a real theme option and capture the resulting panel.
        compose.onNodeWithContentDescription("CONTROL").performClick()
        compose.onNodeWithText("CONTROL CENTER").assertExists()
        compose.onNodeWithContentDescription("Tema ocean").performClick()
        capture("05-control-center")

        // ADVANCED: verify notification area and its empty-state presentation.
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        compose.onNodeWithText("INFINITY ADVANCED").assertExists()
        compose.onNodeWithText("NOTIFICACIONES").assertExists()
        capture("06-advanced-notifications")

        // Dock stays reachable after opening and closing the desktop panels.
        compose.onNodeWithContentDescription("HOME").performClick()
        capture("04-dock-operational")

        // Rotate with START open; validate recovery of the main shell and Dock.
        compose.onNodeWithContentDescription("START").performClick()
        device.setOrientationLeft()
        device.waitForIdle()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("START").assertExists()
        if (isTablet) {
            capture("09-desktop-tablet-landscape")
            device.setOrientationNatural()
            device.waitForIdle()
            compose.waitForIdle()
            capture("01-desktop-tablet-portrait-after-rotation")
        } else {
            capture("10-desktop-compact-phone-landscape")
        }
    }

    private fun capture(name: String) {
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
