/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.baba.callvault.R
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.system.updates.UpdateScheduler
import com.baba.callvault.ui.common.CvCard

/** Whether CallVault checks GitHub for new releases. Off-by-default settings need asking about once. */
@Composable
internal fun UpdatesStep() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    var enabled by remember { mutableStateOf(prefs.isUpdateCheckEnabled()) }
    CvCard {
        SettingsToggleRow(
            icon = Icons.Filled.SystemUpdate,
            label = stringResource(R.string.settings_update_check_label),
            description = stringResource(R.string.settings_update_check_description),
            checked = enabled,
            onCheckedChange = { turnOn ->
                enabled = turnOn
                prefs.setUpdateCheckEnabled(turnOn)
                // Reconcile the periodic worker immediately, exactly as Settings does — persisting the
                // flag alone would leave the schedule disagreeing with it.
                UpdateScheduler.apply(context)
            },
        )
    }
}
