/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.navigation

/** The destination requested by an app notification. */
enum class NotificationDestination(val key: String, val requestCode: Int) {
    None("none", 0),
    Status("status", 5001),
    Update("update", 5002),
    Pairing("pairing", 5003),
    Debug("debug", 5004);

    companion object {
        const val EXTRA = "com.baba.callvault.extra.DESTINATION"

        fun fromIntentExtra(key: String?, relaunchedFromHistory: Boolean): NotificationDestination {
            if (relaunchedFromHistory) return None
            return entries.firstOrNull { it.key == key } ?: None
        }
    }
}
