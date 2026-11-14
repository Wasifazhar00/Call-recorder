package com.baba.callvault.data.share

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves the audio copies available for a multi-recording share. */
object ShareableCalls {
    suspend fun load(
        context: Context,
        displayNames: List<String>,
        titleOf: (String) -> String,
        audioOf: (String) -> Uri?,
    ): List<ShareableCall> = withContext(Dispatchers.IO) {
        displayNames.distinct().map { name ->
            ShareableCall(
                displayName = name,
                title = titleOf(name),
                audioUri = audioOf(name)?.toString(),
            )
        }
    }
}
