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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertHasClickAction
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
        context.getSharedPreferences("infinity_privacy", 0).edit()
            .putBoolean("hide_notification_content", true)
            .commit()
        InfinityNotificationStore.setHideContent(true)
        InfinityNotificationStore.clearAll()
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
        Log.i("INFINITY_TEST_DIAGNOSTIC", "START click")
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        waitForText("INFINITY START")
        val startPanelBottom = compose.onNodeWithTag("infinity-start-panel")
            .fetchSemanticsNode().boundsInRoot.bottom
        val dockStartTop = compose.onNodeWithTag("infinity-dock-start")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "INFINITY START must stay completely above the Dock: panelBottom=$startPanelBottom dockTop=$dockStartTop",
            startPanelBottom <= dockStartTop
        )
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
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        compose.onNodeWithText("INFINITY ADVANCED").assertExists()
        compose.onNodeWithText("NOTIFICACIONES").assertExists()
        compose.onNodeWithText("Aplicación").assertExists()
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

        // Verify privacy persistence across Activity recreation. The test setup
        // explicitly starts with privacy enabled, so the first toggle must disable it.
        val privacySwitch = compose.onNodeWithTag("advanced-privacy-hide-content")
        privacySwitch.assertExists()
        privacySwitch.performClick()
        settleComposeFrame()
        assertTrue(
            "Disabling privacy must be persisted",
            !context.getSharedPreferences("infinity_privacy", 0)
                .getBoolean("hide_notification_content", true)
        )

        compose.activityRule.scenario.recreate()
        settleComposeFrame()
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        waitForTag("infinity-advanced-panel")
        val restoredPrivacySwitch = compose.onNodeWithTag("advanced-privacy-hide-content")
        restoredPrivacySwitch.performScrollTo()
        restoredPrivacySwitch.assertIsOff()
        assertTrue(
            "Privacy preference must survive Activity recreation",
            !context.getSharedPreferences("infinity_privacy", 0)
                .getBoolean("hide_notification_content", true)
        )

        // Seed an existing unredacted item, then verify enabling privacy
        // scrubs already-retained content rather than only future notifications.
        InfinityNotificationStore.update(
            InfinityNotification(
                key = "privacy-existing-item",
                packageName = context.packageName,
                appName = "PRIVATE SOURCE",
                title = "Existing private title",
                message = "Existing private body",
                timestamp = System.currentTimeMillis(),
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.any { it.key == "privacy-existing-item" }
        }

        // Re-enable privacy and verify both persistence and immediate redaction.
        restoredPrivacySwitch.performClick()
        settleComposeFrame()
        assertTrue(
            "Re-enabling privacy must be persisted",
            context.getSharedPreferences("infinity_privacy", 0)
                .getBoolean("hide_notification_content", false)
        )
        InfinityNotificationStore.update(
            InfinityNotification(
                key = "privacy-redaction-regression",
                packageName = context.packageName,
                appName = "PRIVACY TEST",
                title = "Sensitive title",
                message = "Sensitive message",
                timestamp = System.currentTimeMillis(),
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.any { it.key == "privacy-redaction-regression" }
        }
        assertTrue(
            "Private content must be redacted before it is retained",
            InfinityNotificationStore.notifications.first { it.key == "privacy-redaction-regression" }
                .let { it.title.isEmpty() && it.message.isEmpty() }
        )
        assertTrue(
            "Enabling privacy must scrub content already in the feed",
            InfinityNotificationStore.notifications.first { it.key == "privacy-existing-item" }
                .let { it.title.isEmpty() && it.message.isEmpty() }
        )
        compose.onNodeWithText("PRIVACY TEST").assertDoesNotExist()
        assertTrue("Private mode should display a generic source label", compose.onAllNodesWithText("Aplicación").fetchSemanticsNodes().isNotEmpty())

        // Dock stays reachable after opening and closing the desktop panels.
        compose.onNodeWithContentDescription("HOME").performClick()
        settleComposeFrame()
        capture("04-dock-operational")

        // Rotate with START open; validate recovery of the main shell and Dock.
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

    @Test(timeout = 120_000)
    fun dockFavoriteCanBePinnedAndPersistsAcrossActivityRecreation() {
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        // Use the app under test as a controlled launcher fixture. Selecting the
        // first arbitrary installed app made this test depend on emulator image contents
        // and package visibility after Activity recreation (e.g. com.android.camera2).
        val candidate = context.packageManager.queryIntentActivities(launcherIntent, 0)
            .firstOrNull { it.activityInfo.packageName == context.packageName }
            ?: throw AssertionError("La Activity principal de INFINITY no aparece en el catálogo LAUNCHER del emulador")
        val packageName = candidate.activityInfo.packageName
        val label = candidate.loadLabel(context.packageManager).toString()
        val preferences = context.getSharedPreferences("infinity_desktop", 0)

        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        compose.onNode(hasSetTextAction()).performTextInput(label)
        settleComposeFrame()
        compose.onNodeWithTag("infinity-start-installed-app-$packageName").assertExists()
        compose.onNodeWithTag("infinity-dock-favorite-toggle-$packageName")
            .assertExists()
            .performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            preferences.getString("dock_favorites", "").orEmpty()
                .lineSequence().any { it.trim() == packageName }
        }
        waitForComposeCondition(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("infinity-dock-app-$packageName")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.activityRule.scenario.recreate()
        settleComposeFrame()
        assertTrue(
            "El favorito debe sobrevivir a la recreación de la Activity",
            preferences.getString("dock_favorites", "").orEmpty()
                .lineSequence().any { it.trim() == packageName }
        )
        waitForComposeCondition(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("infinity-dock-app-$packageName")
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        compose.onNode(hasSetTextAction()).performTextInput(label)
        settleComposeFrame()
        val removeFavorite = compose.onNodeWithTag("infinity-dock-favorite-toggle-$packageName")
        removeFavorite.assertExists()
        removeFavorite.assertTextEquals("QUITAR")
        removeFavorite.performScrollTo()
        removeFavorite.assertIsDisplayed()
        android.util.Log.d("INFINITY_TEST_DIAGNOSTIC", "before remove touch favorites=${preferences.getString("dock_favorites", "")}")
        removeFavorite.performTouchInput { click() }
        android.util.Log.d("INFINITY_TEST_DIAGNOSTIC", "after remove touch favorites=${preferences.getString("dock_favorites", "")}")
        waitForComposeCondition(timeoutMillis = 5_000) {
            val persistedRemoval = preferences.getString("dock_favorites", "").orEmpty()
                .lineSequence().none { it.trim() == packageName }
            val updatedControl = runCatching {
                compose.onNodeWithTag("infinity-dock-favorite-toggle-$packageName")
                    .assertTextEquals("ANCLAR")
            }.isSuccess
            persistedRemoval && updatedControl
        }
        removeFavorite.assertTextEquals("ANCLAR")
        waitForComposeCondition(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("infinity-dock-app-$packageName")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test(timeout = 120_000)
    fun notificationDismissActionRespectsAndroidClearability() {
        context.getSharedPreferences("infinity_privacy", 0).edit()
            .putBoolean("hide_notification_content", false)
            .commit()
        InfinityNotificationStore.setHideContent(false)

        InfinityNotificationStore.update(
            InfinityNotification(
                key = "ongoing-notification",
                packageName = context.packageName,
                appName = "ONGOING TEST",
                title = "Persistent service",
                message = "This notification cannot be dismissed by the user",
                timestamp = System.currentTimeMillis(),
                ongoing = true,
                clearable = false,
                contentIntent = null
            )
        )
        InfinityNotificationStore.update(
            InfinityNotification(
                key = "clearable-notification",
                packageName = context.packageName,
                appName = "CLEARABLE TEST",
                title = "Normal notification",
                message = "This notification can be dismissed",
                timestamp = System.currentTimeMillis() + 1,
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.size == 2
        }

        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        waitForTag("infinity-advanced-panel")

        val protectedDismiss = compose.onNodeWithTag(
            "infinity-notification-dismiss-ongoing"
        )
        protectedDismiss.assertExists()
        protectedDismiss.assertIsNotEnabled()

        val allowedDismiss = compose.onNodeWithTag(
            "infinity-notification-dismiss-clearable"
        )
        allowedDismiss.assertExists()
        allowedDismiss.assertIsEnabled()

        // With the listener disconnected, DESCARTAR must still remove the stale
        // local entry instead of silently doing nothing. With a live listener,
        // Android remains the source of truth through onNotificationRemoved.
        allowedDismiss.performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.none { it.key == "clearable-notification" }
        }
        compose.onNodeWithTag("infinity-notification-dismiss-ongoing")
            .assertIsNotEnabled()

        // LIMPIAR removes the remaining local feed; it is distinct from dismissing
        // all Android notifications in the system notification shade.
        val clearFeed = compose.onNodeWithTag("infinity-notifications-clear")
        clearFeed.assertExists()
        clearFeed.performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.isEmpty()
        }
    }

    @Test(timeout = 180_000)
    fun advancedQuickActionsReturnToInfinityAfterSettingsHandoff() {
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        waitForTag("infinity-advanced-panel")

        val actions = listOf(
            "advanced-action-wifi" to "Wi-Fi",
            "advanced-action-bluetooth" to "Bluetooth",
            "advanced-action-display" to "Display",
            "advanced-action-sound" to "Sound"
        )
        actions.forEach { (tag, expectedSection) ->
            val action = compose.onNodeWithTag(tag)
            action.assertExists().assertHasClickAction()
            action.performClick()

            // Prove that the click really left INFINITY and opened an external
            // Android Settings screen; a no-op click must not pass this test.
            device.waitForIdle()
            val settingsPackage = device.currentPackageName
            assertTrue(
                "$tag must hand off to Android Settings, foreground was $settingsPackage",
                !settingsPackage.isNullOrBlank() && settingsPackage != context.packageName
            )
            val hierarchyOutput = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchyOutput)
            val settingsHierarchy = hierarchyOutput.toString(Charsets.UTF_8.name())
            assertTrue(
                "$tag must open the $expectedSection settings section",
                settingsHierarchy.contains(expectedSection, ignoreCase = true)
            )

            // Return through the real system Back action and ensure the same
            // desktop Activity and ADVANCED panel are still usable.
            device.pressBack()
            device.waitForIdle()
            assertTrue(
                "$tag must return to the INFINITY Activity after Back",
                device.currentPackageName == context.packageName
            )
            settleComposeFrame()
            waitForTag("infinity-advanced-panel")
            compose.onNodeWithTag(tag).assertExists()
            compose.onNodeWithContentDescription("HOME").assertExists()
        }
    }

    @Test(timeout = 120_000)
    fun notificationAccessSettingsRefreshAndFeedSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        waitForTag("infinity-advanced-panel")

        val permissionSettings = compose.onNodeWithTag(
            "advanced-notification-permission-settings"
        )
        permissionSettings.assertExists().assertHasClickAction()
        permissionSettings.performClick()
        device.waitForIdle()

        val settingsPackage = device.currentPackageName
        assertTrue(
            "Notification access action must open Android Settings, foreground was $settingsPackage",
            !settingsPackage.isNullOrBlank() && settingsPackage != context.packageName
        )
        device.pressBack()
        device.waitForIdle()
        assertTrue(
            "Returning from notification access Settings must restore INFINITY",
            device.currentPackageName == context.packageName
        )

        // The Activity refreshes the secure listener allow-list on resume.
        InfinityNotificationStore.refreshAccess(context)
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val expectedGrant = enabledListeners.split(':').any { flattened ->
            android.content.ComponentName.unflattenFromString(flattened)?.let { component ->
                component.packageName == context.packageName &&
                    component.className == InfinityNotificationListener::class.java.name
            } == true
        }
        assertTrue(
            "INFINITY must reflect Android's actual notification-listener permission state",
            InfinityNotificationStore.accessGranted.value == expectedGrant
        )

        // The notification feed is an in-memory projection of Android's active
        // notifications. Recreating the desktop Activity must not erase it.
        context.getSharedPreferences("infinity_privacy", 0).edit()
            .putBoolean("hide_notification_content", false)
            .commit()
        InfinityNotificationStore.setHideContent(false)
        InfinityNotificationStore.update(
            InfinityNotification(
                key = "activity-recreation-notification",
                packageName = context.packageName,
                appName = "LIFECYCLE TEST",
                title = "Lifecycle notification",
                message = "Feed should remain available after Activity recreation",
                timestamp = System.currentTimeMillis(),
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.any {
                it.key == "activity-recreation-notification"
            }
        }
        compose.activityRule.scenario.recreate()
        settleComposeFrame()
        assertTrue(
            "Activity recreation must not discard the active notification feed",
            InfinityNotificationStore.notifications.any {
                it.key == "activity-recreation-notification"
            }
        )
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        waitForTag("infinity-advanced-panel")
        compose.onNodeWithText("LIFECYCLE TEST").assertExists()
    }

    @Test(timeout = 120_000)
    fun orbitMinimizeAllOnlyAffectsTheActiveWorkspace() {
        // Keep a visible Notes window in workspace 1.
        openStartApp("Notas", "infinity-start-app-notes")
        waitForTag("infinity-window-notes")

        // Create a second visible window in workspace 2.
        selectWorkspaceThroughOrbit(2)
        openStartApp("Este Equipo", "infinity-start-app-files")
        waitForTag("infinity-window-files")

        // Minimize All must affect only workspace 2, then close ORBIT.
        compose.onNodeWithTag("infinity-dock-orbit").performClick()
        settleComposeFrame()
        val minimizeAll = compose.onNodeWithTag("infinity-orbit-minimize-all")
        minimizeAll.assertIsDisplayed()
        minimizeAll.assertHasClickAction()
        minimizeAll.performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-window-files").assertDoesNotExist()

        // Returning to workspace 1 proves its window was not minimized as a side effect.
        selectWorkspaceThroughOrbit(1)
        compose.onNodeWithTag("infinity-window-notes").assertExists()
        compose.onNodeWithText("Bloc de ideas").assertExists()
    }

    private fun openStartApp(query: String, appTag: String) {
        compose.onNodeWithContentDescription("START").performClick()
        settleComposeFrame()
        waitForText("INFINITY START")
        val searchField = compose.onNode(hasSetTextAction())
        searchField.performTextInput(query)
        settleComposeFrame()
        waitForTag(appTag)
        compose.onNodeWithTag(appTag).performClick()
        settleComposeFrame()
    }

    private fun selectWorkspaceThroughOrbit(targetWorkspace: Int) {
        compose.onNodeWithTag("infinity-dock-orbit").performClick()
        settleComposeFrame()
        waitForTag("infinity-orbit-panel")
        compose.onNodeWithTag("infinity-orbit-workspace-$targetWorkspace").performClick()
        settleComposeFrame()
        compose.waitUntil(timeoutMillis = 10_000) {
            context.getSharedPreferences("infinity_desktop", 0)
                .getInt("active_workspace", -1) == targetWorkspace
        }
    }

    private fun settleComposeFrame() {
        // This suite freezes Compose's clock; explicitly commit the UI state transition.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** Advances virtual Compose frames until the requested UI state is observable. */
    private fun waitForComposeCondition(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        do {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (condition()) return
        } while (System.currentTimeMillis() < deadline)
        throw AssertionError("Compose UI state did not settle within ${timeoutMillis}ms")
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

    @Test(timeout = 180_000)
    fun advancedPanelSurvivesRotationAndAndroidSettingsRoundTrips() {
        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-advanced-panel").assertExists()

        compose.onNodeWithTag("advanced-share-report").assertExists()

        device.setOrientationLeft()
        device.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        settleComposeFrame()
        compose.onNodeWithTag("infinity-advanced-panel").assertExists()
        compose.onNodeWithTag("advanced-share-report").assertExists()

        device.setOrientationNatural()
        device.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.activity.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_PORTRAIT
        }
        settleComposeFrame()
        compose.onNodeWithTag("infinity-advanced-panel").assertExists()

        // Select each supported theme through ADVANCED and verify the desktop's
        // canonical preference changes (the palette unit tests verify each palette).
        val desktopPreferences = context.getSharedPreferences("infinity_desktop", 0)
        listOf("midnight", "ocean", "violet", "emerald", "sunset").forEach { themeId ->
            if (desktopPreferences.getString("theme_choice", "midnight") != themeId) {
                val themeButton = compose.onNodeWithTag("advanced-theme-$themeId")
                themeButton.assertExists()
                themeButton.performClick()
                compose.waitUntil(timeoutMillis = 5_000) {
                    desktopPreferences.getString("theme_choice", "midnight") == themeId
                }
            }
        }
        assertTrue("The final selected theme must be persisted", desktopPreferences.getString("theme_choice", "") == "sunset")
    }


    @Test(timeout = 120_000)
    fun automationConsentRevocationPersistsAndDisablesExecution() {
        val automationPrefs = context.getSharedPreferences("infinity_advanced_automation", 0)
        automationPrefs.edit()
            .putString("target_package", "com.android.settings")
            .putBoolean("enabled", true)
            .putBoolean("consent", true)
            .commit()

        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        val consent = compose.onNodeWithTag("advanced-automation-consent")
        consent.assertExists()
        consent.performClick()
        settleComposeFrame()

        assertTrue("Consent revocation must persist immediately", !automationPrefs.getBoolean("consent", true))
        assertTrue("Consent revocation must disable the rule", !automationPrefs.getBoolean("enabled", true))
        compose.onNodeWithTag("advanced-automation-run").assertIsNotEnabled()

        // A fresh Activity must not silently restore the previous authorization.
        compose.activityRule.scenario.recreate()
        settleComposeFrame()
        compose.onNodeWithTag("infinity-advanced-panel").assertExists()
        assertTrue("Consent must remain revoked after Activity recreation", !automationPrefs.getBoolean("consent", true))
        assertTrue("Rule must remain disabled after Activity recreation", !automationPrefs.getBoolean("enabled", true))
        compose.onNodeWithTag("advanced-automation-run").assertIsNotEnabled()
    }


    @Test(timeout = 120_000)
    fun notificationPrivacyRedactsExistingAndFutureContent() {
        val privacyPreferences = context.getSharedPreferences("infinity_privacy", 0)
        privacyPreferences.edit().putBoolean("hide_notification_content", false).commit()
        InfinityNotificationStore.setHideContent(false)
        InfinityNotificationStore.update(
            InfinityNotification(
                key = "privacy-existing-acceptance",
                packageName = context.packageName,
                appName = "PRIVATE SOURCE",
                title = "Private title",
                message = "Private body",
                timestamp = System.currentTimeMillis(),
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.any { it.key == "privacy-existing-acceptance" }
        }

        compose.onNodeWithContentDescription("ADVANCED").performClick()
        settleComposeFrame()
        waitForTag("infinity-advanced-panel")
        compose.onNodeWithText("PRIVATE SOURCE").assertExists()
        compose.onNodeWithText("Private title").assertExists()
        compose.onNodeWithText("Private body").assertExists()

        compose.onNodeWithTag("advanced-privacy-hide-content").performClick()
        settleComposeFrame()
        assertTrue(privacyPreferences.getBoolean("hide_notification_content", false))
        assertTrue(
            "Enabling privacy must scrub existing titles and messages",
            InfinityNotificationStore.notifications.first { it.key == "privacy-existing-acceptance" }
                .let { it.title.isEmpty() && it.message.isEmpty() }
        )
        compose.onNodeWithText("PRIVATE SOURCE").assertDoesNotExist()
        compose.onNodeWithText("Private title").assertDoesNotExist()
        compose.onNodeWithText("Private body").assertDoesNotExist()
        compose.onNodeWithText("Aplicación").assertExists()

        InfinityNotificationStore.update(
            InfinityNotification(
                key = "privacy-future-acceptance",
                packageName = context.packageName,
                appName = "FUTURE PRIVATE SOURCE",
                title = "Future private title",
                message = "Future private body",
                timestamp = System.currentTimeMillis() + 1,
                ongoing = false,
                clearable = true,
                contentIntent = null
            )
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            InfinityNotificationStore.notifications.any { it.key == "privacy-future-acceptance" }
        }
        assertTrue(
            "Privacy must redact newly received content before it is retained",
            InfinityNotificationStore.notifications.first { it.key == "privacy-future-acceptance" }
                .let { it.title.isEmpty() && it.message.isEmpty() }
        )
        compose.onNodeWithText("FUTURE PRIVATE SOURCE").assertDoesNotExist()
        compose.onNodeWithText("Future private title").assertDoesNotExist()
        compose.onNodeWithText("Future private body").assertDoesNotExist()
    }

}
