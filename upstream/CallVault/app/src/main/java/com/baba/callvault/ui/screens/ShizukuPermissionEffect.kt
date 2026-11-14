/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import com.baba.callvault.server.ShizukuBackend

/**
 * Runs [onAnswer] when the user answers Shizuku's "Allow CallVault?" prompt, while this composable is on
 * screen. Screens that show Shizuku's status use it to re-read that status the moment it changes.
 */
@Composable
internal fun OnShizukuPermissionAnswer(onAnswer: () -> Unit) {
    val latest = rememberUpdatedState(onAnswer)
    DisposableEffect(Unit) {
        val stop = ShizukuBackend.observePermissionResult { latest.value() }
        onDispose { stop() }
    }
}
