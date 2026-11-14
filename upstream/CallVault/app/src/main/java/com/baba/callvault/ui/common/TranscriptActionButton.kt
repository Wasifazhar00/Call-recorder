/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baba.callvault.data.transcripts.TranscriptStatus

/**
 * The single transcript affordance on a recording row.
 *
 * One slot that changes meaning: transcribe → in progress → read. Which it is comes from
 * [TranscriptRowAction], which is unit-tested; this only draws it.
 *
 * @param onTranscribe start a transcription for this recording.
 * @param onOpen       open the finished transcript.
 * @param onRetry      clear a failure and try again.
 */
@Composable
fun TranscriptActionButton(
    status: TranscriptStatus,
    onTranscribe: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** How far into this recording, 0-100. Zero means not known, and spins instead. */
    percent: Int = 0
) {
    // Transcription is excluded from the CallVault recorder-only product profile.
    // Keep the stable composable signature for upstream call sites and upgrade compatibility.
}

