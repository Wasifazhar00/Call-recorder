/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import com.baba.callvault.data.StorageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Save and sync" on the new Settings screen: Drive rows only when something is uploaded. */
class SaveAndSyncRowsTest {

    @Test
    fun `device only shows no Drive folder and no upload schedule`() {
        val rows = SaveAndSyncRows.visibleRows(StorageTarget.LOCAL)
        assertFalse(SaveAndSyncRow.DRIVE_FOLDER in rows)
        assertFalse(SaveAndSyncRow.UPLOAD_SCHEDULE in rows)
        assertEquals(
            listOf(
                SaveAndSyncRow.STORAGE_TARGET, SaveAndSyncRow.RECORDING_FOLDER, SaveAndSyncRow.HIDE_RECORDINGS,
                SaveAndSyncRow.METADATA_FILE, SaveAndSyncRow.TRANSCRIPT_SIDECAR,
            ),
            rows,
        )
    }

    @Test
    fun `drive and both show the Drive folder, then the schedule, after the recording folder`() {
        listOf(StorageTarget.DRIVE, StorageTarget.BOTH).forEach { target ->
            assertEquals(SaveAndSyncRow.entries.toList(), SaveAndSyncRows.visibleRows(target))
        }
    }

    @Test
    fun `the recording folder shows for every target, since drive-only records into it first`() {
        StorageTarget.entries.forEach { target ->
            assertTrue(SaveAndSyncRow.RECORDING_FOLDER in SaveAndSyncRows.visibleRows(target))
        }
    }

    @Test
    fun `only device-only means no upload`() {
        assertFalse(SaveAndSyncRows.uploads(StorageTarget.LOCAL))
        assertTrue(SaveAndSyncRows.uploads(StorageTarget.DRIVE))
        assertTrue(SaveAndSyncRows.uploads(StorageTarget.BOTH))
    }

    @Test
    fun `clock time pads hours and minutes`() {
        assertEquals("02:00", clockTime(2, 0))
        assertEquals("23:45", clockTime(23, 45))
    }
}
