/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import com.baba.callvault.data.AppPreferences.IgnoreContactsMode
import com.baba.callvault.services.call.ContactIgnoreRule
import java.text.Normalizer
import java.util.Locale

/**
 * Whether an app call's caller is on the user's ignore list (issue #51).
 *
 * An app call has no number of its own — only what the app's call notification shows: the contact's name
 * (the phone's contact name, for WhatsApp) or, for someone not saved, their number. So the list is matched
 * two ways: a number by the same rule phone calls use, a name against the saved names of the listed
 * numbers — exactly, after folding case, accents, spacing and bidi marks. Never partially: "Mom Work" is not
 * "Mom", and skipping the wrong person's call is a recording lost for good.
 *
 * App calls have no direction, so the incoming and outgoing lists both apply. "Record only selected
 * contacts" stays phone-calls-only: an app call whose name cannot be resolved would otherwise never record.
 */
object VoipIgnoreRule {

    fun shouldIgnore(
        caller: String?,
        enabled: Boolean,
        modes: Set<IgnoreContactsMode>,
        selectedNumbers: Set<String>,
        selectedNames: Set<String>,
        isKnownNumber: (String) -> Boolean,
        isKnownName: (String) -> Boolean,
    ): Boolean {
        if (!enabled) return false
        val who = caller?.trim().orEmpty()
        if (who.isEmpty()) return false
        val ignoreSelected = IgnoreContactsMode.SELECTED in modes
        val ignoreAll = IgnoreContactsMode.ALL in modes
        if (looksLikeNumber(who)) {
            if (ignoreSelected && selectedNumbers.any { ContactIgnoreRule.sameNumber(it, who) }) return true
            return ignoreAll && runCatching { isKnownNumber(who) }.getOrDefault(false)
        }
        val name = foldName(who)
        if (name.isEmpty()) return false
        if (ignoreSelected && selectedNames.any { foldName(it) == name }) return true
        return ignoreAll && runCatching { isKnownName(name) }.getOrDefault(false)
    }

    /** The numbers of every list whose mode is "ignore selected" — a list kept in another mode does not count. */
    fun selectedNumbers(
        incomingMode: IgnoreContactsMode, incoming: Set<String>,
        outgoingMode: IgnoreContactsMode, outgoing: Set<String>,
    ): Set<String> =
        (if (incomingMode == IgnoreContactsMode.SELECTED) incoming else emptySet()) +
            (if (outgoingMode == IgnoreContactsMode.SELECTED) outgoing else emptySet())

    /** Case, accents, runs of spaces and the bidi marks notifications wrap names in — none of them a different name. */
    fun foldName(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(BIDI_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()

    private fun looksLikeNumber(s: String): Boolean =
        s.count { it.isDigit() } >= MIN_DIGITS && s.all { it.isDigit() || it in PHONE_PUNCTUATION }

    private const val MIN_DIGITS = 6
    private const val PHONE_PUNCTUATION = "+-() ./ "
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val BIDI_MARKS = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val WHITESPACE = Regex("\\s+")
}
