/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.recordings

import com.baba.callvault.data.health.CallLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogMatchTest {

    private val t = 1_791_545_452_000L

    @Test
    fun `the nearest call within tolerance gives the duration and the direction`() {
        val entries = listOf(
            CallLogEntry(t - 5_000, 42, isIncoming = true, label = null),
            CallLogEntry(t + 600_000, 10, isIncoming = false, label = null),
        )
        val m = CallDurationLookup.match(entries, listOf(t))[t]!!
        assertEquals(42L, m.durationSeconds)
        assertTrue(m.isIncoming)
    }

    @Test
    fun `a call that never connected still says which way it went`() {
        // Zero seconds has no duration worth showing, but the direction is still known.
        val m = CallDurationLookup.match(listOf(CallLogEntry(t, 0, isIncoming = false, label = null)), listOf(t))[t]!!
        assertNull(m.durationSeconds)
        assertEquals(false, m.isIncoming)
    }

    @Test
    fun `nothing within tolerance is no match`() {
        val entries = listOf(CallLogEntry(t - 10 * 60_000, 30, isIncoming = true, label = null))
        assertTrue(CallDurationLookup.match(entries, listOf(t)).isEmpty())
    }
}
