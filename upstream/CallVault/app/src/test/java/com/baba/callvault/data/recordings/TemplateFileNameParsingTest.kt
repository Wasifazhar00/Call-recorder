/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.recordings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every file-name preset reads back. Five of the eight carry no in/out marker, and the parser used to
 * read nothing at all from them — no date, number or name — which cost the user the direction arrow,
 * merging and search (field report 2026-10-19, "Contact · Number · Date").
 */
class TemplateFileNameParsingTest {

    private fun parse(name: String) = RecordingsRepository.parseName(name)

    private val date = "20261009_143052.123+0300"
    private val startedAt = 1_791_545_452_123L // 2026-10-09 14:30:52.123 +03:00

    @Test
    fun `contact, number, date`() {
        val p = parse("Dana Levi_0541234567_$date.ogg")
        assertEquals("0541234567", p.number)
        assertEquals("Dana Levi", p.contactName)
        assertEquals(startedAt, p.startedAtMillis)
        assertNotNull(p.displayDate)
        assertNull("this preset carries no direction", p.direction)
    }

    @Test
    fun `contact, number, date with no contact known`() {
        // The formatter leaves the contact slot empty when the template already has the number.
        val p = parse("_0541234567_$date.ogg")
        assertEquals("0541234567", p.number)
        assertNull(p.contactName)
        assertEquals(startedAt, p.startedAtMillis)
    }

    @Test
    fun `number, contact, date`() {
        val p = parse("+972541234567_Dana Levi_$date.ogg")
        assertEquals("+972541234567", p.number)
        assertEquals("Dana Levi", p.contactName)
        assertEquals(startedAt, p.startedAtMillis)
    }

    @Test
    fun `contact, date`() {
        val p = parse("Dana Levi_$date.ogg")
        assertNull(p.number)
        assertEquals("Dana Levi", p.contactName)
        assertEquals(startedAt, p.startedAtMillis)
    }

    @Test
    fun `contact, date where the contact slot holds the number`() {
        // No contact at record time: the formatter falls back to the number in the contact slot.
        val p = parse("0541234567_$date.ogg")
        assertEquals("0541234567", p.number)
        assertNull(p.contactName)
    }

    @Test
    fun `number, date`() {
        val p = parse("0541234567_$date.ogg")
        assertEquals("0541234567", p.number)
        assertEquals(startedAt, p.startedAtMillis)
    }

    @Test
    fun `date, number`() {
        val p = parse("${date}_0541234567.ogg")
        assertEquals("0541234567", p.number)
        assertEquals(startedAt, p.startedAtMillis)
    }

    @Test
    fun `date, contact`() {
        val p = parse("${date}_Dana Levi.ogg")
        assertEquals("Dana Levi", p.contactName)
        assertNull(p.number)
        assertEquals(startedAt, p.startedAtMillis)
    }

    @Test
    fun `a name with an underscore in it stays whole`() {
        val p = parse("Dana_Levi_0541234567_$date.ogg")
        assertEquals("Dana_Levi", p.contactName)
        assertEquals("0541234567", p.number)
    }

    @Test
    fun `the two presets with a direction read exactly as before`() {
        val withNumber = parse("${date}_in_0541234567.ogg")
        assertEquals(RecordingDirection.INCOMING, withNumber.direction)
        assertEquals("0541234567", withNumber.number)
        val withContact = parse("${date}_out_Dana Levi.ogg")
        assertEquals(RecordingDirection.OUTGOING, withContact.direction)
        assertEquals("Dana Levi", withContact.number)
    }

    @Test
    fun `a name with no date in it reads as nothing, as before`() {
        val p = parse("my recording.ogg")
        assertNull(p.startedAtMillis)
        assertNull(p.number)
        assertNull(p.contactName)
    }

    @Test
    fun `only a carrier call without a marker asks the call log for its direction`() {
        assertEquals(true, RecordingsRepository.isCarrierCallWithoutDirection("Dana Levi_0541234567_$date.ogg"))
        assertEquals(false, RecordingsRepository.isCarrierCallWithoutDirection("${date}_in_0541234567.ogg"))
        assertEquals(false, RecordingsRepository.isCarrierCallWithoutDirection("${date}_voip.ogg"))
        assertEquals(false, RecordingsRepository.isCarrierCallWithoutDirection("${date}_voip-WhatsApp_Dana.ogg"))
    }
}
