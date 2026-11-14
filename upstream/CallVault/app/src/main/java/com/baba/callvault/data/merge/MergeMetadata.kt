/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.merge

import android.content.Context
import com.baba.callvault.data.recordings.db.MergePartEntry
import com.baba.callvault.data.transcripts.db.RecordingFlagEntry
import com.baba.callvault.data.transcripts.db.RecordingNoteEntry
import com.baba.callvault.data.transcripts.db.RecordingTagEntry
import com.baba.callvault.data.transcripts.db.TranscriptDatabase
import com.baba.callvault.utils.AppLogger

/**
 * Carries a merged recording's marks, tags, favourite and note across from its parts.
 *
 * **Copies, never moves.** Each part keeps its own rows, untouched, even though its audio file is
 * gone. They are a few kilobytes of text, and keeping them makes un-merge trivial and safe: putting
 * the calls back is a matter of deleting what the merge added, with everything the parts had already
 * sitting where it always was. Redistributing rows back by time range would be the alternative, and
 * it would be both more code and a chance to lose something.
 *
 * This relies on the merged recording having a name of its own, distinct from every part — see
 * `MergeService.freeName`. If it shared the primary's name, deleting "what the merge added" could
 * delete the primary's own metadata.
 */
object MergeMetadata {

    private const val TAG = "CV:MergeMeta"

    /**
     * Builds the merged recording's side-data from [parts].
     *
     * Marks are shifted by each part's position on the merged timeline.
     */
    suspend fun onMerge(context: Context, mergedName: String, parts: List<MergePartEntry>) {
        val db = TranscriptDatabase.get(context)

        // --- marks, plus one at each seam ------------------------------------------------------
        val marks = parts.flatMap { part ->
            val offsetMs = part.startTimeUs / 1000
            db.flagDao().flagsFor(part.partName).map { it + offsetMs }
        }.toMutableList()
        // The gap between two calls is not padded with silence, so without this the join would be
        // invisible. A mark keeps the information and makes the seam somewhere you can jump to.
        parts.drop(1).forEach { marks += it.startTimeUs / 1000 }
        if (marks.isNotEmpty()) {
            db.flagDao().upsertAll(marks.distinct().sorted().map { RecordingFlagEntry(mergedName, it) })
        }

        // --- tags: the union, since every part's labels describe the same conversation ----------
        parts.flatMap { db.tagDao().tagsFor(it.partName) }.distinct().forEach { tag ->
            db.tagDao().add(RecordingTagEntry(mergedName, tag))
        }

        // --- star: if any part was worth keeping, so is the whole call -------------------------
        if (parts.any { db.favouriteDao().isFavourite(it.partName) }) {
            db.favouriteDao().add(mergedName)
        }

        // --- note: joined, labelled, in playback order -----------------------------------------
        val notes = parts.mapNotNull { part ->
            db.noteDao().note(part.partName)?.text?.takeIf { it.isNotBlank() }
        }
        if (notes.isNotEmpty()) {
            db.noteDao().upsertNote(
                RecordingNoteEntry(mergedName, notes.joinToString("\n\n"), System.currentTimeMillis())
            )
        }

        // Waveforms are caches and are redrawn by the playback screen.
        AppLogger.i(
            TAG,
            "Merged metadata onto $mergedName: ${marks.size} marks, ${notes.size} note(s)"
        )
    }

    /**
     * Removes everything the merge created, leaving each part's own rows as they always were.
     *
     * That is the whole of un-merging the metadata, and it is why [onMerge] copies rather than moves.
     */
    suspend fun onUnMerge(context: Context, mergedName: String) {
        val db = TranscriptDatabase.get(context)
        db.flagDao().deleteFor(mergedName)
        db.tagDao().deleteFor(mergedName)
        db.favouriteDao().deleteFor(mergedName)
        db.noteDao().deleteNote(mergedName)
        db.noteDao().deleteWaveform(mergedName)
        AppLogger.i(TAG, "Cleared the merged metadata for $mergedName; the parts kept their own")
    }
}
