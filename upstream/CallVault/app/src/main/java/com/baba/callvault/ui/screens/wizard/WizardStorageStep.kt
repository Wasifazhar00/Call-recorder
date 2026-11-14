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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baba.callvault.R
import com.baba.callvault.data.StorageTarget
import com.baba.callvault.ui.common.CvCard

@Composable
internal fun StorageStep(
    storageTarget: StorageTarget,
    recordingFolderLabel: String?,
    driveFolderLabel: String?,
    usesDrive: Boolean,
    onSelectStorageTarget: (StorageTarget) -> Unit,
    onPickRecordingFolder: () -> Unit,
    onPickDriveFolder: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StorageTarget.entries.forEach { target ->
            OptionCard(
                title = stringResource(storageTargetTitleRes(target)),
                explanation = stringResource(storageTargetDescRes(target)),
                selected = storageTarget == target,
                onClick = { onSelectStorageTarget(target) }
            )
        }

        Spacer(Modifier.height(2.dp))

        FolderRow(
            icon = Icons.Filled.Folder,
            label = stringResource(R.string.settings_recording_folder_label),
            chosenName = recordingFolderLabel,
            onClick = onPickRecordingFolder
        )

        if (usesDrive) {
            FolderRow(
                icon = Icons.Filled.CloudUpload,
                label = stringResource(R.string.settings_drive_folder_label),
                chosenName = driveFolderLabel,
                onClick = onPickDriveFolder
            )
        }
    }
}

/**
 * A folder picker as a Settings row. Until a folder is chosen the value reads "Choose folder" in the
 * accent colour: that is the step's one requirement, so it stays on screen rather than behind an (i).
 */
@Composable
private fun FolderRow(icon: ImageVector, label: String, chosenName: String?, onClick: () -> Unit) {
    CvCard {
        NavigationRow(
            icon = icon,
            label = label,
            value = chosenName ?: stringResource(R.string.wizard_ui_folder_choose),
            onClick = onClick,
            valueColor = if (chosenName != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
        )
    }
}

internal fun storageTargetTitleRes(target: StorageTarget): Int = when (target) {
    StorageTarget.LOCAL -> R.string.storage_target_local
    StorageTarget.DRIVE -> R.string.storage_target_drive
    StorageTarget.BOTH -> R.string.storage_target_both
}

internal fun storageTargetDescRes(target: StorageTarget): Int = when (target) {
    StorageTarget.LOCAL -> R.string.wizard_ui_storage_local_desc
    StorageTarget.DRIVE -> R.string.wizard_ui_storage_drive_desc
    StorageTarget.BOTH -> R.string.wizard_ui_storage_both_desc
}
