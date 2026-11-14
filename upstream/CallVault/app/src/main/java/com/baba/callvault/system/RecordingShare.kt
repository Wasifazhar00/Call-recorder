/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.system

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.baba.callvault.R
import com.baba.callvault.utils.AppLogger

private const val TAG = "CV:Share"

/** Used when the provider cannot say what the file is — every recording is audio regardless. */
private const val FALLBACK_MIME = "audio/*"

/** Shares an audio clip through the Android share sheet. */
fun Context.shareClip(file: java.io.File, mime: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The flag alone covers only what the extras name, so the URI has to be in the clip data too.
        clipData = ClipData.newRawUri(file.name, uri)
    }
    val chooser = Intent.createChooser(intent, getString(R.string.share_clip_chooser)).apply {
        if (this@shareClip !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Length only, never the words: this log can end up in a report.
    AppLogger.i(TAG, "Sharing audio clip (${file.length()} bytes)")
    runCatching { startActivity(chooser) }
        .onFailure { AppLogger.w(TAG, "Could not open the share sheet for the clip '${file.name}': ${it.message}") }
}

/**
 * Sends one or more call recordings together in one Android share action.
 *
 * The recording URIs come from the documents provider, while temporary clips use our FileProvider. Every URI
 * goes into [ClipData] so the read grant reaches all shared audio files. The type follows the files when they
 * agree (several recordings), audio when they are all audio, and any type otherwise.
 */
fun Context.shareCallFiles(uris: List<Uri>, subject: String) {
    if (uris.isEmpty()) return
    val types = uris.mapNotNull { runCatching { contentResolver.getType(it) }.getOrNull() }.distinct()
    val mime = types.singleOrNull()
        ?: if (types.isNotEmpty() && types.all { it.startsWith("audio/") }) FALLBACK_MIME else "*/*"
    val clip = ClipData.newRawUri(subject, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.single()) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
    }.apply {
        type = mime
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = clip
    }
    val chooser = Intent.createChooser(intent, getString(R.string.home_share_chooser)).apply {
        if (this@shareCallFiles !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Counts only: names can be contacts, and this log can end up in a report.
    AppLogger.i(TAG, "Sharing ${uris.size} file(s) of type $mime")
    runCatching { startActivity(chooser) }
        .onFailure { AppLogger.w(TAG, "Could not open the share sheet for ${uris.size} file(s): ${it.message}") }
}

