package com.baba.callvault.data.recordings

import android.content.Context
import androidx.room.withTransaction
import com.baba.callvault.data.transcripts.db.TranscriptDatabase

/** Removes only user-authored metadata attached to recordings whose last audio copy was deleted. */
object RecordingMetadataCascade {
    suspend fun deleteFor(context: Context, displayNames: Collection<String>) {
        if (displayNames.isEmpty()) return
        val db = TranscriptDatabase.get(context)
        db.withTransaction {
            displayNames.distinct().forEach { name ->
                db.noteDao().deleteNote(name)
                db.noteDao().deleteWaveform(name)
                db.tagDao().deleteFor(name)
                db.favouriteDao().deleteFor(name)
                db.flagDao().deleteFor(name)
                db.labelDao().deleteFor(name)
            }
        }
    }
}
