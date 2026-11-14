/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import com.baba.callvault.data.AppPreferences.IgnoreContactsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #51: the ignored-contacts list, applied to app calls by the caller's name or number. */
class VoipIgnoreRuleTest {

    private val mom = "+972541234567"

    private fun ignore(
        caller: String?,
        enabled: Boolean = true,
        modes: Set<IgnoreContactsMode> = setOf(IgnoreContactsMode.SELECTED),
        numbers: Set<String> = setOf(mom),
        names: Set<String> = setOf("Mom"),
        knownNumber: Boolean = false,
        knownName: Boolean = false,
    ) = VoipIgnoreRule.shouldIgnore(caller, enabled, modes, numbers, names, { knownNumber }, { knownName })

    @Test fun `a listed contact's name is ignored`() = assertTrue(ignore("Mom"))

    @Test fun `names match regardless of case, accents, spacing and bidi marks`() {
        assertTrue(ignore("  mom ", names = setOf("Mom")))
        assertTrue(ignore("José  Luis", names = setOf("josé luis")))
        assertTrue(ignore("⁨Mom⁩", names = setOf("Mom")))
    }

    @Test fun `a partial name never matches`() {
        assertFalse(ignore("Mom Work", names = setOf("Mom")))
        assertFalse(ignore("Mo", names = setOf("Mom")))
    }

    @Test fun `an unsaved caller shown as a number matches the listed number`() {
        assertTrue(ignore("+972 54-123-4567"))
        assertTrue(ignore("054-123-4567"))
        assertFalse(ignore("+972 54-765-4321"))
    }

    @Test fun `no name means nothing to match on`() {
        assertFalse(ignore(null))
        assertFalse(ignore("  "))
    }

    @Test fun `off by default means the list is not applied`() = assertFalse(ignore("Mom", enabled = false))

    @Test fun `ignore all contacts applies to any saved contact`() {
        assertTrue(ignore("Dana", modes = setOf(IgnoreContactsMode.ALL), knownName = true))
        assertFalse(ignore("Dana", modes = setOf(IgnoreContactsMode.ALL), knownName = false))
        assertTrue(ignore("+972 50 000 0000", modes = setOf(IgnoreContactsMode.ALL), knownNumber = true))
    }

    @Test fun `record only selected stays phone-calls-only`() =
        assertFalse(ignore("Mom", modes = setOf(IgnoreContactsMode.ONLY_SELECTED)))

    @Test fun `no ignore mode means nothing is ignored`() = assertFalse(ignore("Mom", modes = setOf(IgnoreContactsMode.NONE)))

    @Test fun `only lists in selected mode contribute numbers`() {
        val numbers = VoipIgnoreRule.selectedNumbers(
            IgnoreContactsMode.SELECTED, setOf("1"), IgnoreContactsMode.NONE, setOf("2"),
        )
        assertEquals(setOf("1"), numbers)
    }
}
