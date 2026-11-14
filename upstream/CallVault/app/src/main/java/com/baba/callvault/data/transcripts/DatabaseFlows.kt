package com.baba.callvault.data.transcripts

import android.content.Context
import com.baba.callvault.data.transcripts.db.TranscriptDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Reads the metadata database without creating it merely to draw empty note/tag/star/flag state.
 *
 * When the first user-authored metadata row creates the database, an existing collector notices
 * the file and switches to its Room flow. Polling is bounded to one lightweight filesystem check
 * per second and stops automatically when the collector is cancelled.
 */
internal fun <T> onceMetadataDatabaseExists(
    context: Context,
    initial: T,
    observe: (TranscriptDatabase) -> Flow<T>,
): Flow<T> = flow {
    if (!TranscriptDatabase.exists(context)) {
        emit(initial)
        while (!TranscriptDatabase.exists(context)) delay(1_000L)
    }
    emitAll(observe(TranscriptDatabase.get(context)))
}
