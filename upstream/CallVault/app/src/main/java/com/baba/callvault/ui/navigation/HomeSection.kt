/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.navigation

/** The sections available in the recorder app. */
enum class HomeSection(val key: String) {
    Hub("hub"),
    Recordings("recordings");

    companion object {
        fun opening(resolvedScreen: AppScreen, storedKey: String?): HomeSection? {
            if (resolvedScreen != AppScreen.Home) return null
            return entries.firstOrNull { it.key == storedKey } ?: Hub
        }

        fun forNotification(
            resolvedScreen: AppScreen,
            destination: NotificationDestination,
        ): HomeSection? {
            if (resolvedScreen != AppScreen.Home) return null
            return when (destination) {
                NotificationDestination.None -> null
                NotificationDestination.Status,
                NotificationDestination.Update,
                NotificationDestination.Pairing,
                NotificationDestination.Debug -> Hub
            }
        }
    }
}
