/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.baba.callvault.R
import com.baba.callvault.ui.common.ContactSelectionDialog
import com.baba.callvault.ui.viewmodels.ContactPickerState
import com.baba.callvault.ui.viewmodels.ContactPickerType
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

// Pieces the Settings screen ([ScopedSettingsContent]) uses, some shared with the wizard.

/**
 * Back up or restore settings — a section-less row at the very bottom of Settings. Export writes a JSON
 * file of your device-independent preferences; Import reads one back (after a confirm).
 */
@Composable
internal fun ExportImportRow(onExport: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_backup_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_backup_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_export))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_import))
            }
        }
    }
}

/** The open-source licences, full screen. Shared by both Settings screens. */
@Composable
internal fun LicensesDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Text(
                    text = stringResource(R.string.general_licenses),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                val libraries by produceLibraries(R.raw.aboutlibraries)
                LibrariesContainer(libraries,Modifier
                    .fillMaxSize()
                    .weight(1f),
                    showAuthor = true, showLicenseBadges = true, showFundingBadges = false, showVersion = true, showDescription = true)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(8.dp)
                ) {
                    Text(stringResource(R.string.general_close))
                }
            }
        }
    }
}

/** The contact-picker dialog that sits on top of either Settings screen while it is open. */
@Composable
internal fun SettingsContactPicker(
    contactPickerState: ContactPickerState?,
    onConfirmContacts: (Set<String>) -> Unit,
    onDismissContacts: () -> Unit,
) {
    contactPickerState?.let { picker ->
        ContactSelectionDialog(
            title = when (picker.type) {
                ContactPickerType.INCOMING ->
                    if (picker.recordOnly) stringResource(R.string.settings_select_record_only_incoming)
                    else stringResource(R.string.settings_select_contacts_incoming)
                ContactPickerType.OUTGOING ->
                    if (picker.recordOnly) stringResource(R.string.settings_select_record_only_outgoing)
                    else stringResource(R.string.settings_select_contacts_outgoing)
            },
            contacts = picker.contacts,
            initialSelection = picker.selectedNumbers,
            onConfirm = onConfirmContacts,
            onDismiss = onDismissContacts
        )
    }
}
