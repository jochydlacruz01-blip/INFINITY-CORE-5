package com.infinity.core

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InfinityWindowSnapshotRecoveryInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs
        get() = context.getSharedPreferences("infinity_desktop", Context.MODE_PRIVATE)

    @Before fun clearWindowPersistence() {
        prefs.edit().remove(INFINITY_SAVED_WINDOWS_KEY)
            .remove(INFINITY_VALID_WINDOWS_BACKUP_KEY)
            .remove("saved_windows_recovery_backup")
            .remove("saved_windows_recovery_backup_2")
            .remove("saved_windows_recovery_backup_3")
            .remove("saved_windows_recovery_backup_4")
            .remove("saved_windows_recovery_backup_5")
            .commit()
    }

    @After fun clearWindowPersistenceAfterTest() {
        clearWindowPersistence()
    }

    private fun snapshot(
        id: String = "notes_1",
        kind: String = "notes",
        workspace: Int = 1,
        minimized: Boolean = false,
        maximized: Boolean = false,
        x: Int = 36
    ): String = """[{"id":"$id","title":"Snapshot $workspace","kind":"$kind","x":$x,"y":92,"width":420,"height":340,"minimized":$minimized,"workspace":$workspace,"maximized":$maximized}]"""

    @Test fun validPrimaryIsLoadedWithoutChangingValidBackup() {
        val primary = snapshot()
        val backup = snapshot(id = "files_2", kind = "files", workspace = 2)
        prefs.edit().putString(INFINITY_SAVED_WINDOWS_KEY, primary)
            .putString(INFINITY_VALID_WINDOWS_BACKUP_KEY, backup).commit()

        val result = infinityRecoverWindowSnapshot(prefs)

        assertEquals(primary, result.json)
        assertFalse(result.restoredFromBackup)
        assertFalse(result.preservedCorruptSnapshot)
        assertEquals(primary, prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null))
        assertEquals(backup, prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null))
    }

    @Test fun corruptPrimaryRestoresValidatedBackupAndPreservesEvidence() {
        val corrupt = """[{"id":"broken","kind":"notes"}"""
        val backup = snapshot(id = "files_2", kind = "files", workspace = 2)
        prefs.edit().putString(INFINITY_SAVED_WINDOWS_KEY, corrupt)
            .putString(INFINITY_VALID_WINDOWS_BACKUP_KEY, backup).commit()

        val result = infinityRecoverWindowSnapshot(prefs)

        assertEquals(backup, result.json)
        assertTrue(result.restoredFromBackup)
        assertTrue(result.preservedCorruptSnapshot)
        assertEquals(backup, prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null))
        assertEquals(backup, prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null))
        assertEquals(corrupt, prefs.getString("saved_windows_recovery_backup", null))
    }

    @Test fun corruptPrimaryAndCorruptBackupFallBackWithoutPromotingEither() {
        val corruptPrimary = """[{"id":"broken-primary"}"""
        val corruptBackup = """not-json-backup"""
        prefs.edit().putString(INFINITY_SAVED_WINDOWS_KEY, corruptPrimary)
            .putString(INFINITY_VALID_WINDOWS_BACKUP_KEY, corruptBackup).commit()

        val result = infinityRecoverWindowSnapshot(prefs)

        assertEquals("[]", result.json)
        assertFalse(result.restoredFromBackup)
        assertTrue(result.preservedCorruptSnapshot)
        assertEquals("[]", prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null))
        assertEquals(corruptBackup, prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null))
        assertEquals(corruptPrimary, prefs.getString("saved_windows_recovery_backup", null))
        assertFalse(infinityIsValidWindowSnapshot(prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null)))
    }

    @Test fun repeatedCorruptionNeverDegradesTheLastKnownGoodBackup() {
        val original = snapshot(id = "notes_1", kind = "notes", workspace = 1)
        val updated = snapshot(id = "files_2", kind = "files", workspace = 2, minimized = true, x = 180)
        assertTrue(infinityCommitWindowSnapshot(prefs, original))
        assertTrue(infinityCommitWindowSnapshot(prefs, updated))
        assertEquals(original, prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null))

        repeat(3) { cycle ->
            val corrupt = """[{"cycle":$cycle,"truncated":"""
            prefs.edit().putString(INFINITY_SAVED_WINDOWS_KEY, corrupt).commit()
            val result = infinityRecoverWindowSnapshot(prefs)
            assertEquals("The valid snapshot must be restored on cycle $cycle", original, result.json)
            assertEquals("The valid backup must not change on cycle $cycle", original,
                prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null))
            assertEquals("The live snapshot must be repaired on cycle $cycle", original,
                prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null))
        }
        assertEquals("""[{"cycle":0,"truncated":""", prefs.getString("saved_windows_recovery_backup", null))
        assertEquals("""[{"cycle":1,"truncated":""", prefs.getString("saved_windows_recovery_backup_2", null))
        assertEquals("""[{"cycle":2,"truncated":""", prefs.getString("saved_windows_recovery_backup_3", null))
    }

    @Test fun malformedFieldsAndCrossWorkspaceIdsAreRejectedAsWholeSnapshots() {
        assertFalse(infinityIsValidWindowSnapshot("""[{"id":"notes_1","title":"x","kind":"notes","x":1,"y":2,"width":420,"height":340,"minimized":false,"workspace":1}]"""))
        assertFalse(infinityIsValidWindowSnapshot(snapshot(id = "notes_2", kind = "notes", workspace = 1)))
        assertFalse(infinityIsValidWindowSnapshot(snapshot(id = "notes_4", kind = "notes", workspace = 4)))
        assertFalse(infinityIsValidWindowSnapshot(snapshot(x = 9000)))
        assertFalse(infinityIsValidWindowSnapshot("""[null]"""))
        assertTrue(infinityIsValidWindowSnapshot("[]"))
    }

    @Test fun invalidNewSnapshotCannotReplacePrimaryOrValidBackup() {
        val primary = snapshot()
        val backup = snapshot(id = "apps_3", kind = "apps", workspace = 3, maximized = true)
        prefs.edit().putString(INFINITY_SAVED_WINDOWS_KEY, primary)
            .putString(INFINITY_VALID_WINDOWS_BACKUP_KEY, backup).commit()

        assertFalse(infinityCommitWindowSnapshot(prefs, """[{"id":"invalid"}]"""))
        assertEquals(primary, prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null))
        assertEquals(backup, prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null))
    }
}
