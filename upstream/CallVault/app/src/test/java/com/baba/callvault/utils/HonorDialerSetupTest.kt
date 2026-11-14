/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.utils

import com.baba.callvault.data.StorageTarget
import com.baba.callvault.utils.HonorDialerSetup.Missing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HonorDialerSetupTest {

    @Test
    fun `the phone app's folder is recognised however it is spelled`() {
        assertTrue(HonorDialerSetup.isDialerFolder("primary:Sounds/CallRecord"))
        assertTrue(HonorDialerSetup.isDialerFolder("primary:sounds/callrecord/"))
    }

    @Test
    fun `any other folder is not`() {
        assertFalse(HonorDialerSetup.isDialerFolder(null))
        assertFalse(HonorDialerSetup.isDialerFolder("primary:Recordings"))
        assertFalse(HonorDialerSetup.isDialerFolder("primary:Sounds/CallRecord/CallVault"))
        assertFalse(HonorDialerSetup.isDialerFolder("1234-5678:Sounds/CallRecord"))
    }

    @Test
    fun `nothing is missing once folder, codec and storage are right`() {
        assertEquals(emptyList<Missing>(), HonorDialerSetup.missing("primary:Sounds/CallRecord", "aac", StorageTarget.BOTH))
    }

    @Test
    fun `everything that is wrong is listed in order`() {
        assertEquals(
            listOf(Missing.FOLDER, Missing.CODEC, Missing.STORAGE_TARGET),
            HonorDialerSetup.missing("primary:Recordings", "opus", StorageTarget.DRIVE)
        )
    }
}
