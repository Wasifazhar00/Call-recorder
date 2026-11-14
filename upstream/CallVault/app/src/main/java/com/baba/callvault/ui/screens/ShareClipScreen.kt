/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import android.media.MediaMetadataRetriever

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baba.callvault.R
import com.baba.callvault.data.clips.ClipCutter
import com.baba.callvault.data.clips.ClipFiles
import com.baba.callvault.data.clips.ClipNaming
import com.baba.callvault.data.clips.ClipRange
import com.baba.callvault.data.recordings.RecordingsRepository.RecordingItem
import com.baba.callvault.system.shareClip
import com.baba.callvault.ui.common.CvCard
import com.baba.callvault.ui.common.RecordingLabel
import com.baba.callvault.ui.common.CvScaffold
import com.baba.callvault.ui.viewmodels.RecordingPlaybackController
import com.baba.callvault.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Share button is doing. */
private sealed interface ClipWork {
    data object Idle : ClipWork
    data object Cutting : ClipWork
    data object CutFailed : ClipWork
}

/**
 * "Share a part": choose and share an audio range without modifying the original recording.
 */
@Composable
internal fun ShareClipScreen(
    item: RecordingItem,
    initialPositionMs: Long?,
    playback: RecordingPlaybackController.PlaybackState,
    peaks: FloatArray,
    flags: List<Long>,
    onPlayFrom: (Long) -> Unit,
    onPause: () -> Unit,
    onSyncPosition: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durationMs by produceState(item.durationSeconds?.times(1000) ?: 0L, item.uri) {
        val read = withContext(Dispatchers.IO) { readDurationMs(context, item.uri) }
        if (read > 0) value = read
    }
    // Null while it is being read; "" when the recording cannot be read at all.
    val mime by produceState<String?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { ClipCutter.mimeOf(context, item.uri) }.orEmpty()
    }

    // The user's choice, kept apart from the duration: the length is first a guess from the call log and then the
    // container's own figure, and keying the selection on it threw an early edit away (review 2026-11-04).
    var chosen by remember { mutableStateOf<ClipRange?>(null) }
    val range = chosen?.let { ClipRange.of(it.startMs, it.endMs, durationMs) }
        ?: initialPositionMs?.let { ClipRange.fromPosition(it, durationMs) }
        ?: ClipRange.whole(durationMs)
    var work by remember { mutableStateOf<ClipWork>(ClipWork.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }

    val playbackNow by rememberUpdatedState(playback)
    val isThis = playback.activeUri == item.uri
    val playing = isThis && playback.phase == RecordingPlaybackController.Phase.PLAYING
    var previewing by remember { mutableStateOf(false) }
    val rangeNow by rememberUpdatedState(range)

    // Preview: play the part and stop at its end. Polled faster than the app's 500 ms tick so it stops on time.
    LaunchedEffect(previewing) {
        while (previewing) {
            onSyncPosition()
            delay(PREVIEW_POLL_MS)
            // playbackNow, not playback: this loop outlives the composition that started it (review 2026-11-04).
            if (playbackNow.activeUri == item.uri && playbackNow.positionMs >= rangeNow.endMs) {
                onPause()
                previewing = false
            }
        }
    }
    LaunchedEffect(playing) { if (!playing) previewing = false }

    fun share(clip: ClipCutter.Result) {
        context.shareClip(clip.file, ClipCutter.shareMimeFor(clip.mime), null)
        work = ClipWork.Idle
    }

    val busy = work == ClipWork.Cutting

    fun start() {
        val clipMime = mime ?: return
        // Set before launching, so a second tap in the same frame cannot start a second job.
        if (busy) return
        work = ClipWork.Cutting
        // The part as it was when Share was tapped. Audio, name and text all use this one, even if a handle moves.
        val cutRange = range
        job = scope.launch {
            // Named after who the call was with, as the list shows it, not the recording's raw file name.
            val name = ClipNaming.fileName(item.contactName ?: item.number ?: item.displayName.substringBeforeLast('.'), cutRange, ClipCutter.extensionFor(clipMime))
            val clip = withContext(Dispatchers.IO) {
                runCatching { ClipCutter.cut(context, item.uri, cutRange, ClipFiles.newFile(context, name)) }
                    .onFailure { AppLogger.w(TAG, "Clip failed: ${it.message}") }
                    .getOrNull()
            }
            if (clip == null) {
                work = ClipWork.CutFailed
                return@launch
            }
            share(clip)
        }
    }

    CvScaffold(
        modifier = modifier.fillMaxSize(),
        title = stringResource(R.string.share_clip_title),
        subtitle = RecordingLabel.of(item) ?: item.displayName,
        onBack = onBack,
        bottomBar = {
            ShareBar(
                lengthMs = range.lengthMs,
                work = work,
                enabled = !mime.isNullOrEmpty() && ClipCutter.canClip(mime) && durationMs > 0,
                onShare = { start() },
                onCancel = {
                    job?.cancel()
                    work = ClipWork.Idle
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mime != null && (mime.isNullOrEmpty() || !ClipCutter.canClip(mime))) {
                CvCard { Text(stringResource(R.string.share_clip_unsupported), style = MaterialTheme.typography.bodyMedium) }
                return@Column
            }
            CvCard {
                ClipRangeWaveform(
                    peaks = peaks,
                    durationMs = durationMs,
                    range = range,
                    flags = flags,
                    playheadMs = if (isThis) playback.positionMs.toLong() else null,
                    // Fixed while the part is being prepared, so what is shared is what was chosen.
                    onRangeChange = { if (!busy) chosen = it },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TimeLabel(R.string.share_clip_start, range.startMs)
                    TimeLabel(R.string.share_clip_length, range.lengthMs)
                    TimeLabel(R.string.share_clip_end, range.endMs)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { chosen = range.withStart(playback.positionMs.toLong(), durationMs) },
                    enabled = isThis && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.share_clip_start_here), maxLines = 1) }
                FilledIconButton(
                    onClick = {
                        if (playing) {
                            onPause()
                        } else {
                            onPlayFrom(range.startMs)
                            previewing = true
                        }
                    },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.share_clip_pause else R.string.share_clip_preview),
                    )
                }
                OutlinedButton(
                    onClick = { chosen = range.withEnd(playback.positionMs.toLong(), durationMs) },
                    enabled = isThis && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.share_clip_end_here), maxLines = 1) }
            }
            Text(
                stringResource(R.string.share_clip_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeLabel(label: Int, ms: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ClipRange.clock(ms), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ShareBar(
    lengthMs: Long,
    work: ClipWork,
    enabled: Boolean,
    onShare: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (work) {
            ClipWork.Idle, ClipWork.CutFailed -> {
                if (work == ClipWork.CutFailed) {
                    Text(stringResource(R.string.share_clip_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onShare, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_clip_share, ClipRange.clock(lengthMs)))
                }
            }
            ClipWork.Cutting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.share_clip_working_cut), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_clip_back))
                }
            }
        }
    }
}

private fun readDurationMs(context: android.content.Context, uri: android.net.Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private const val TAG = "CV:ShareClip"
private const val PREVIEW_POLL_MS = 100L

