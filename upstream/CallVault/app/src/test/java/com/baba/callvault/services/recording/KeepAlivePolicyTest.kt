/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import com.baba.callvault.data.PrivilegedMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAlivePolicyTest {

    @Test
    fun `standalone always runs it - it keeps our own daemon alive`() {
        assertTrue(KeepAlivePolicy.shouldRun(PrivilegedMode.STANDALONE, voipEnabled = false))
        assertTrue(KeepAlivePolicy.shouldRun(PrivilegedMode.STANDALONE, voipEnabled = true))
    }

    @Test
    fun `shizuku runs it only to detect app calls`() {
        assertFalse(KeepAlivePolicy.shouldRun(PrivilegedMode.SHIZUKU, voipEnabled = false))
        assertTrue(KeepAlivePolicy.shouldRun(PrivilegedMode.SHIZUKU, voipEnabled = true))
    }

    @Test
    fun `only standalone may use our own adb to recover`() {
        assertTrue(KeepAlivePolicy.mayUseAdb(PrivilegedMode.STANDALONE))
        assertFalse(KeepAlivePolicy.mayUseAdb(PrivilegedMode.SHIZUKU))
    }
}
