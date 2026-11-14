/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.system.storage

import com.baba.callvault.system.storage.HideRecordings.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class HideRecordingsTest {

    @Test
    fun `turning it on adds the marker when the folder has none`() {
        assertEquals(Action.CREATE, HideRecordings.decide(wanted = true, exists = false, createdByUs = false))
    }

    @Test
    fun `on and already there - nothing to do`() {
        assertEquals(Action.NONE, HideRecordings.decide(wanted = true, exists = true, createdByUs = true))
        assertEquals(Action.NONE, HideRecordings.decide(wanted = true, exists = true, createdByUs = false))
    }

    @Test
    fun `turning it off removes only a marker CallVault put there`() {
        assertEquals(Action.DELETE, HideRecordings.decide(wanted = false, exists = true, createdByUs = true))
    }

    @Test
    fun `a marker the user placed themselves is never removed`() {
        // Turning the setting off must not un-hide a folder the user had hidden on their own.
        assertEquals(Action.NONE, HideRecordings.decide(wanted = false, exists = true, createdByUs = false))
    }

    @Test
    fun `off and absent - nothing to do`() {
        assertEquals(Action.NONE, HideRecordings.decide(wanted = false, exists = false, createdByUs = true))
    }
}
