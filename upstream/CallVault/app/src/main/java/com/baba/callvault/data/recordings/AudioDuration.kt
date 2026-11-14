package com.baba.callvault.data.recordings

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Container duration lookup for ordinary audio playback and import validation. */
object AudioDuration {
    fun milliseconds(context: Context, uri: Uri): Long {
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
}
