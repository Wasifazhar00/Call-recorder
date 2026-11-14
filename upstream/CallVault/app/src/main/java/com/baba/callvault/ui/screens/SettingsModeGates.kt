/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import android.provider.Settings
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.baba.callvault.R
import kotlinx.coroutines.flow.first
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.data.ModeCapability

/**
 * Opt-in "Record VoIP calls" toggle (experimental).
 *
 * Unlike the other opt-ins this one carries a confirmation, for two reasons that are not the app's to
 * decide for the user: consent law for VoIP recording is stricter than for carrier calls in many
 * places, and support is per-app — an app that opts out of capture cannot be recorded at all, and we
 * can only tell once a call is under way.
 */
/**
 * Whether [capability] works in the mode the user has chosen, for greying a row out.
 *
 * Read on each recomposition rather than remembered: the mode can change from the privileges section
 * on this very screen, and a row that stayed enabled after the switch would offer something that
 * cannot work.
 */
@Composable
internal fun capabilityAvailable(capability: ModeCapability): Boolean {
    val context = LocalContext.current
    // Settings provides the mode so a switch reaches every row; the wizard and other screens fall back
    // to reading it, which is right there because their mode cannot change while they are open.
    val mode = LocalPrivilegedMode.current ?: AppPreferences(context).getPrivilegedMode()
    return capability.isAvailable(mode, android.os.Build.VERSION.SDK_INT)
}

/** The privileged mode the current screen is drawn for; see [capabilityAvailable]. */
internal val LocalPrivilegedMode = compositionLocalOf<com.baba.callvault.data.PrivilegedMode?> { null }

/**
 * The mode to draw a row for. Never `remember {}` the mode in a row: a mode switch happens while
 * Settings is open, and a remembered mode kept the old mode's greyed state on screen (OP9, 2026-10-19).
 * `ModeAwareUiTest` fails the build on that pattern.
 */
@Composable
internal fun currentPrivilegedMode(): com.baba.callvault.data.PrivilegedMode =
    LocalPrivilegedMode.current ?: AppPreferences(LocalContext.current).getPrivilegedMode()

/**
 * The one-line reason a row is greyed out, or null when it is not. The phone's Android version is named
 * first when it is the reason (backlog #1): no mode switch can fix that, so offering Shizuku's reason
 * would send the user looking for a fix that does not exist.
 */
@Composable
internal fun unavailableReason(capability: ModeCapability): String? = when {
    capabilityAvailable(capability) -> null
    !capability.isAvailableOn(android.os.Build.VERSION.SDK_INT) -> stringResource(
        R.string.settings_needs_newer_android,
        capability.minAndroidRelease,
        android.os.Build.VERSION.RELEASE.orEmpty(),
    )
    else -> stringResource(R.string.settings_unavailable_in_shizuku)
}
