/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.transcripts.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the user wrote about a call, in their own words.
 *
 * A note records context the audio cannot preserve as structured data — the price agreed, the thing
 * to chase, or the reason to ring back.
 *
 * It lives outside the recordings catalog because that catalog is a rebuildable cache with a
 * destructive fallback. A note cannot be regenerated from the audio. Losing one loses it for good.
 */
@Entity(tableName = "recording_notes")
data class RecordingNoteEntry(
    @PrimaryKey val displayName: String,
    val text: String,
    val updatedAt: Long = 0L
)

/**
 * A recording's drawn shape, cached so it is computed once rather than on every visit.
 *
 * Purely derived — it can be rebuilt from the audio — so losing it costs only the decode. It sits here
 * anyway because it is keyed by the same recording and dies in the same cascade; a separate cache
 * would be a second thing to remember to clean up when a call is deleted.
 */
@Entity(tableName = "recording_waveforms")
data class RecordingWaveformEntry(
    @PrimaryKey val displayName: String,
    /** Peaks encoded by [com.baba.callvault.data.waveform.Waveform.encode]. */
    val peaks: String,
    val updatedAt: Long = 0L
)
