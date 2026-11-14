package com.baba.callvault.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import com.baba.callvault.R
import com.baba.callvault.data.share.SharePart
import com.baba.callvault.data.share.ShareableCall
import com.baba.callvault.system.shareCallFiles

/** Shares selected audio recordings as one Android file-share action. */
internal suspend fun shareCalls(
    context: Context,
    calls: List<ShareableCall>,
    ticks: Set<SharePart>,
    subject: String,
) {
    val uris: List<Uri> = if (SharePart.RECORDING in ticks) {
        calls.mapNotNull { it.audioUri?.toUri() }
    } else {
        emptyList()
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, R.string.library_nothing_to_share, Toast.LENGTH_SHORT).show()
        return
    }
    val title = calls.singleOrNull()?.title ?: subject
    context.shareCallFiles(uris, title)
}
