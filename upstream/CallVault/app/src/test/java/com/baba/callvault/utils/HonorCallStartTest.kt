/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.utils

import com.baba.callvault.data.recordings.RecordingDirection
import com.baba.callvault.utils.HonorCallStart.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Honor's phone app shows a recording only if its file-name time lies within
 * [call-log DATE − 1 s, DATE + duration + 1 s] (decompiled HnContacts, MagicOS 6/8/10). For an incoming call
 * DATE is when it started ringing and we start recording at answer, so the name must take DATE from the log.
 */
class HonorCallStartTest {

    private val sameNumber: (String, String) -> Boolean = { a, b -> a.takeLast(9) == b.takeLast(9) }
    private val recordingStart = 1_800_000_015_000L

    private fun incoming(date: Long, number: String = "0541234567") =
        LogEntry(date = date, number = number, direction = RecordingDirection.INCOMING)

    @Test
    fun `an incoming call that rang for 15 s takes the ring start from the log`() {
        val ringStart = recordingStart - 15_000
        assertEquals(ringStart, HonorCallStart.pick(listOf(incoming(ringStart)), recordingStart, RecordingDirection.INCOMING, "0541234567", sameNumber))
    }

    @Test
    fun `the latest matching entry before the recording wins`() {
        val earlier = incoming(recordingStart - 600_000)
        val thisCall = incoming(recordingStart - 8_000)
        assertEquals(thisCall.date, HonorCallStart.pick(listOf(thisCall, earlier), recordingStart, RecordingDirection.INCOMING, "0541234567", sameNumber))
    }

    @Test
    fun `another number, another direction or a call long before are ignored`() {
        val entries = listOf(
            incoming(recordingStart - 5_000, number = "0529999999"),
            LogEntry(recordingStart - 5_000, "0541234567", RecordingDirection.OUTGOING),
            incoming(recordingStart - HonorCallStart.MAX_RING_MS - 1),
        )
        assertNull(HonorCallStart.pick(entries, recordingStart, RecordingDirection.INCOMING, "0541234567", sameNumber))
    }

    @Test
    fun `a log entry a little after our start still counts, for clock jitter`() {
        val date = recordingStart + 2_000
        assertEquals(date, HonorCallStart.pick(listOf(incoming(date)), recordingStart, RecordingDirection.INCOMING, "+972541234567", sameNumber))
    }

    @Test
    fun `with no number to match, nothing is picked`() {
        assertNull(HonorCallStart.pick(listOf(incoming(recordingStart - 1_000)), recordingStart, RecordingDirection.INCOMING, null, sameNumber))
    }
}
