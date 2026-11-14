/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.system.permissions.PermissionChecks
import com.baba.callvault.utils.AppLogger

/**
 * [VoipIgnoreRule] fed from the user's settings and contacts (issue #51).
 *
 * Without contacts permission only numbers can match — a name cannot be tied to a listed number — which
 * fails toward recording, never toward skipping.
 */
object VoipIgnoredCallers {

    private const val TAG = "CV:VoipIgnore"

    fun isIgnored(context: Context, caller: String?): Boolean {
        val prefs = AppPreferences(context)
        if (!prefs.isVoipApplyIgnoredContactsEnabled() || caller.isNullOrBlank()) return false
        val modeIn = prefs.getIgnoreContactsModeIncoming()
        val modeOut = prefs.getIgnoreContactsModeOutgoing()
        val numbers = VoipIgnoreRule.selectedNumbers(
            modeIn, prefs.getIgnoredContactsIncoming(), modeOut, prefs.getIgnoredContactsOutgoing(),
        )
        val canReadContacts = PermissionChecks.hasContactsPermission(context)
        val ignored = VoipIgnoreRule.shouldIgnore(
            caller = caller,
            enabled = true,
            modes = setOf(modeIn, modeOut),
            selectedNumbers = numbers,
            selectedNames = if (canReadContacts) numbers.mapNotNull { nameFor(context, it) }.toSet() else emptySet(),
            isKnownNumber = { canReadContacts && nameFor(context, it) != null },
            isKnownName = { folded -> canReadContacts && allContactNames(context).any { VoipIgnoreRule.foldName(it) == folded } },
        )
        if (ignored) AppLogger.i(TAG, "The app call's caller is on the ignore list")
        return ignored
    }

    private fun nameFor(context: Context, number: String): String? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.onFailure { AppLogger.d(TAG, "Contact lookup failed: ${it.message}") }.getOrNull()

    private fun allContactNames(context: Context): List<String> = runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null,
        )?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let(::add) } }.orEmpty()
    }.onFailure { AppLogger.d(TAG, "Contact list read failed: ${it.message}") }.getOrDefault(emptyList())
}
