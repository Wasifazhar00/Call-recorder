/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.recordings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The "Honor / Huawei phone app" preset (#56): `{name}@{number}_{yyyyMMddHHmmss}`, the shape Honor's own
 * recorder writes and its dialer links to a call. Confirmed by the reporter on an Honor REA-NX9 with
 * `Баланс@087011_20261020103708.m4a`. Local time, no milliseconds, no offset.
 */
class HonorFileNameParsingTest {

    private fun parse(name: String) = RecordingsRepository.parseName(name)

    private val compact = "20261020103708"
    private val startedAt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(compact)!!.time

    @Test
    fun `the reporter's real file reads back`() {
        val p = parse("Баланс@087011_$compact.m4a")
        assertEquals("087011", p.number)
        assertEquals("Баланс", p.contactName)
        assertEquals(startedAt, p.startedAtMillis)
        assertEquals("2026-10-20 10:37", p.displayDate)
        assertNull("the name carries no direction; the list takes it from the call log", p.direction)
    }

    @Test
    fun `an unsaved number repeated as the name is not shown as a name`() {
        val p = parse("972541234567@+972541234567_$compact.m4a")
        assertEquals("+972541234567", p.number)
        assertNull(p.contactName)
        assertEquals(startedAt, p.startedAtMillis)
    }

    @Test
    fun `a name that itself holds an at sign splits on the last one`() {
        val p = parse("dana@work@0541234567_$compact.ogg")
        assertEquals("dana@work", p.contactName)
        assertEquals("0541234567", p.number)
    }

    @Test
    fun `an honor name is a carrier call whose direction comes from the call log`() {
        assertTrue(RecordingsRepository.isCarrierCallWithoutDirection("Баланс@087011_$compact.m4a"))
    }
}
