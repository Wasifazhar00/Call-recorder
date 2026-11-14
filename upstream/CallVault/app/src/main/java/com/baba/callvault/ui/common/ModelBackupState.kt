/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.baba.callvault.transcription.model.DownloadableModel
import com.baba.callvault.transcription.model.ModelTransferWorker

/**
 * What the backup of an installed model is doing.
 *
 * Kept apart from [ModelDownloadState]: a backup runs on a model that is already installed, so it
 * cannot share that state's "Installed wins" precedence without hiding itself.
 */
sealed interface ModelBackupState {

    /** No backup in flight and none to report. */
    data object Idle : ModelBackupState

    /** Copying out, [percent] of the way through; null until the first figure arrives. */
    data class Running(val percent: Int?) : ModelBackupState

    /** The last backup finished. */
    data object Done : ModelBackupState

    /** The backup stopped. [reason] is one of `ModelTransferWorker`'s `ERROR_` values. */
    data class Failed(val reason: String?) : ModelBackupState

    companion object {

        /** CANCELLED and "no work" both mean the user has nothing to be told, so they are [Idle]. */
        fun from(workState: WorkInfo.State?, percent: Int?, error: String?): ModelBackupState =
            when (workState) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    Running(percent)
                WorkInfo.State.SUCCEEDED -> Done
                WorkInfo.State.FAILED -> Failed(error)
                WorkInfo.State.CANCELLED, null -> Idle
            }
    }
}

/** Observes the backup of [model]. */
@Composable
fun rememberModelBackupState(model: DownloadableModel): State<ModelBackupState> {
    val context = LocalContext.current
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val workName = remember(model.id) { ModelTransferWorker.backupWorkName(model) }

    return produceState<ModelBackupState>(ModelBackupState.Idle, workManager, workName) {
        workManager.getWorkInfosForUniqueWorkFlow(workName).collect { infos ->
            val info = infos.lastOrNull()
            value = ModelBackupState.from(
                workState = info?.state,
                percent = info?.let { ModelTransferWorker.percentOf(it.progress) },
                error = info?.outputData?.getString(ModelTransferWorker.KEY_ERROR)
            )
        }
    }
}
