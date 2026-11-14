/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an existing Shizuku binding still counts. Measured on the OP9 2026-10-19: the recorder process was
 * killed mid-call, the binding stayed "bound", and every rebind answered "Already bound" and then waited
 * 10 s for a binder that never came — so the recorder (and VoIP arming) never returned.
 */
class ShizukuBindingTest {

    @Test
    fun `a binding whose recorder is alive is kept`() {
        assertFalse(ShizukuBinding.isStale(deliveredAlive = true, msSinceBind = 600_000))
    }

    @Test
    fun `a binding whose recorder died is stale`() {
        assertTrue(ShizukuBinding.isStale(deliveredAlive = false, msSinceBind = 5_000))
    }

    @Test
    fun `a bind still waiting for its answer is left to finish`() {
        assertFalse(ShizukuBinding.isStale(deliveredAlive = null, msSinceBind = 3_000))
    }

    @Test
    fun `a bind that never answered is stale`() {
        assertTrue(ShizukuBinding.isStale(deliveredAlive = null, msSinceBind = ShizukuBinding.ANSWER_TIMEOUT_MS + 1))
    }
}
