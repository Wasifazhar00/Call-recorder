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
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.baba.callvault.transcription.model.DownloadableModel
import com.baba.callvault.transcription.model.ModelDownloadWorker
import com.baba.callvault.transcription.model.ModelRepository
import com.baba.callvault.transcription.model.ModelTransferWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * What a model download is doing, as far as anyone looking at the screen is concerned.
 *
 * The download of the summarisation model takes 3.46 GB over an unmetered network — long enough
 * that a button which simply stops saying "Download" one day is not an acceptable answer. Until
 * now nothing observed the worker's progress at all: `ModelDownloadWorker.percentOf` existed and
 * had no callers, so the transcription model's row flipped straight from Download to Installed with
 * nothing in between.
 */
sealed interface ModelDownloadState {

    /** On the device and complete. */
    data object Installed : ModelDownloadState

    /** Nothing is happening and there is nothing on disk. */
    data object Absent : ModelDownloadState

    /**
     * Stopped part-way, with [downloadedBytes] already fetched and kept.
     *
     * Distinct from [Absent] because it is the opposite of it. The partial file survives a cancel
     * deliberately — the next attempt resumes with a Range request, verified against HuggingFace,
     * so those bytes are banked rather than wasted. Left rendering as "Download, 3.5 GB" that fact
     * is invisible, and a user who stopped a download reasonably assumes they have to start again.
     */
    data class Paused(val downloadedBytes: Long) : ModelDownloadState

    /**
     * Queued but not yet started — usually waiting for an unmetered network.
     *
     * Distinct from [Downloading] because 0% and "waiting for Wi-Fi" look identical on a progress
     * bar and mean very different things to someone wondering why nothing is happening.
     */
    data object Waiting : ModelDownloadState

    /** Running, [percent] of the way through. */
    data class Downloading(val percent: Int) : ModelDownloadState

    /** Stopped and will not resume on its own. [reason] is one of the worker's `ERROR_` values. */
    data class Failed(val reason: String?) : ModelDownloadState

    /**
     * A file the user picked is being checked and copied in. [percent] is null while it is queued
     * or has not yet reported a figure, so the row can show an indeterminate bar.
     */
    data class Importing(val percent: Int?) : ModelDownloadState

    /**
     * The import stopped. Separate from [Failed] so the row says "import" rather than "download":
     * the remedies differ (pick another file vs. try the network again). [reason] is one of
     * `ModelTransferWorker`'s `ERROR_` values; [otherModelId] names the catalogue model the picked
     * file turned out to be, when [reason] is `ERROR_OTHER_MODEL`. [partialBytes] are the bytes of
     * an earlier download still banked on disk, so the download row can keep saying "Resume" rather
     * than hiding them behind the import's failure.
     */
    data class ImportFailed(
        val reason: String?,
        val otherModelId: String?,
        val partialBytes: Long = 0L
    ) : ModelDownloadState

    companion object {

        private fun isActive(state: WorkInfo.State?): Boolean =
            state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED ||
                state == WorkInfo.State.BLOCKED

        /**
         * True when the import failed no earlier than the download. A failure without a time (one
         * from before the time was recorded) counts as the older; with neither timed, or a tie, the
         * import wins, as it did before the times existed.
         */
        private fun isImportNewer(importFailedAt: Long?, downloadFailedAt: Long?): Boolean =
            (importFailedAt ?: Long.MIN_VALUE) >= (downloadFailedAt ?: Long.MIN_VALUE)

        /**
         * The state to show, given what the catalogue and WorkManager each say.
         *
         * **Installed wins over everything.** A finished download leaves its work in the terminal
         * SUCCEEDED state, and a failed attempt leaves a FAILED one behind that WorkManager keeps
         * for its own bookkeeping — neither should contradict a file that is verifiably on disk.
         *
         * Kept separate from the observation below so it can be reasoned about, and tested, without
         * a WorkManager instance.
         */
        fun from(
            isInstalled: Boolean,
            workState: WorkInfo.State?,
            percent: Int?,
            error: String?,
            partialBytes: Long = 0L,
            importState: WorkInfo.State? = null,
            importPercent: Int? = null,
            importError: String? = null,
            importOtherModelId: String? = null,
            downloadFailedAt: Long? = null,
            importFailedAt: Long? = null
        ): ModelDownloadState =
            when {
                isInstalled -> Installed
                workState == WorkInfo.State.RUNNING ->
                    percent?.let { Downloading(it) } ?: Waiting
                // ENQUEUED covers both the first moment after a tap and a retry waiting on its
                // backoff, which is also the shape an interrupted download comes back in.
                workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.BLOCKED -> Waiting
                // A live import outranks any failure. Between two failures the newer one is shown:
                // WorkManager keeps both FAILED records, so a stale import failure would otherwise
                // hide a download that failed after it.
                isActive(importState) -> Importing(importPercent)
                importState == WorkInfo.State.FAILED &&
                    (workState != WorkInfo.State.FAILED || isImportNewer(importFailedAt, downloadFailedAt)) ->
                    ImportFailed(importError, importOtherModelId, partialBytes)
                workState == WorkInfo.State.FAILED -> Failed(error)
                // Nothing running. SUCCEEDED without the file means it was verified then removed;
                // CANCELLED means the user stopped it; null means no attempt this process. What
                // separates them is only whether bytes are banked on disk.
                partialBytes > 0L -> Paused(partialBytes)
                else -> Absent
            }
    }
}

/**
 * Observes the download of [model].
 *
 * **What is on disk is re-read on every emission, not remembered.** An earlier version took
 * `isInstalled` from the caller, where it was held in a `remember(updateTrigger)` — and nothing
 * bumps that trigger when a download *finishes on its own*. Observed on the emulator: after the
 * full 3.46 GB landed and was verified, the row went back to reading "Download the summariser,
 * 3.5 GB", inviting the user to fetch it all again. The work state and the file have to be read at
 * the same moment or they contradict each other.
 *
 * @param refreshKey re-runs the producer when it changes. Needed because deleting a model touches
 *   the filesystem without touching WorkManager, so nothing would otherwise emit.
 */
@Composable
fun rememberModelDownloadState(
    model: DownloadableModel,
    refreshKey: Int = 0
): State<ModelDownloadState> {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val workName = remember(model.id) { ModelDownloadWorker.workNameFor(model) }
    val importName = remember(model.id) { ModelTransferWorker.importWorkName(model) }

    return produceState<ModelDownloadState>(
        initialValue = ModelDownloadState.from(
            isInstalled = ModelRepository.isInstalled(appContext, model),
            workState = null,
            percent = null,
            error = null,
            partialBytes = ModelRepository.partialBytes(appContext, model)
        ),
        workManager, workName, importName, model.id, refreshKey
    ) {
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(workName),
            workManager.getWorkInfosForUniqueWorkFlow(importName)
        ) { downloads, imports -> downloads.lastOrNull() to imports.lastOrNull() }
            .collect { (info, importInfo) ->
            // Unique work, so there is at most one of each that matters; a retry replaces.
            val onDisk = withContext(Dispatchers.IO) {
                ModelRepository.isInstalled(appContext, model) to
                    ModelRepository.partialBytes(appContext, model)
            }
            value = ModelDownloadState.from(
                isInstalled = onDisk.first,
                workState = info?.state,
                percent = info?.let { ModelDownloadWorker.percentOf(it.progress) },
                error = info?.outputData?.getString(ModelDownloadWorker.KEY_ERROR),
                partialBytes = onDisk.second,
                importState = importInfo?.state,
                importPercent = importInfo?.let { ModelTransferWorker.percentOf(it.progress) },
                importError = importInfo?.outputData?.getString(ModelTransferWorker.KEY_ERROR),
                importOtherModelId =
                    importInfo?.outputData?.getString(ModelTransferWorker.KEY_OTHER_MODEL_ID),
                downloadFailedAt = info?.outputData?.finishedAt(),
                importFailedAt = importInfo?.outputData?.finishedAt()
            )
        }
    }
}

/** The failure time a model worker stamped into its output, or null when it wrote none. */
private fun Data.finishedAt(): Long? =
    getLong(ModelDownloadWorker.KEY_FINISHED_AT, NO_TIME).takeIf { it != NO_TIME }

private const val NO_TIME = -1L
