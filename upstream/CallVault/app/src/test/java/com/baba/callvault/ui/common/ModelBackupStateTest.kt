/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.common

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a model row shows about its backup, which is independent of whether the model is installed. */
class ModelBackupStateTest {

    @Test
    fun `a finished backup is done`() {
        assertEquals(ModelBackupState.Done, ModelBackupState.from(WorkInfo.State.SUCCEEDED, null, null))
    }

    @Test
    fun `a running backup reports its percentage`() {
        assertEquals(ModelBackupState.Running(70), ModelBackupState.from(WorkInfo.State.RUNNING, 70, null))
    }

    @Test
    fun `a cancelled backup is idle`() {
        assertEquals(ModelBackupState.Idle, ModelBackupState.from(WorkInfo.State.CANCELLED, null, null))
    }

    @Test
    fun `no backup work is idle`() {
        assertEquals(ModelBackupState.Idle, ModelBackupState.from(null, null, null))
    }

    @Test
    fun `a failed backup carries its reason`() {
        assertEquals(
            ModelBackupState.Failed("write-failed"),
            ModelBackupState.from(WorkInfo.State.FAILED, null, "write-failed")
        )
    }
}
