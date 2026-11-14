/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.system.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.baba.callvault.data.AppPreferences
import com.baba.callvault.utils.AppLogger
import java.io.File

/**
 * "Hide Recordings": keeps calls out of music players, galleries and other media apps.
 *
 * Done the way Android itself defines it — an empty `.nomedia` file in the recordings folder, which the
 * media index honours by skipping the folder. The recordings keep their names and `.ogg` extension, so
 * syncing, PC copies and BCR-style tools are untouched. File managers still show the files; that is the
 * honest limit, and the setting's hint says so.
 *
 * Removes only a marker CallVault itself added: a folder the user hid on their own stays hidden when the
 * setting is turned off.
 */
object HideRecordings {

    private const val TAG = "CV:HideRecordings"
    const val MARKER = ".nomedia"

    enum class Action { CREATE, DELETE, NONE }

    fun decide(wanted: Boolean, exists: Boolean, createdByUs: Boolean): Action = when {
        wanted && !exists -> Action.CREATE
        !wanted && exists && createdByUs -> Action.DELETE
        else -> Action.NONE
    }

    /** Brings the recordings folder in line with the setting, off the main thread. Safe to call often. */
    fun applyAsync(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { apply(app) }.onFailure { AppLogger.w(TAG, "Could not apply: ${it.message}") } }
            .apply { isDaemon = true; name = "cv-hide-recordings" }.start()
    }

    @Synchronized
    fun apply(context: Context) {
        val prefs = AppPreferences(context)
        val folderUri = prefs.getRecordingFolderUri() ?: return
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return
        val marker = findMarker(context, folderUri, folder)
        when (decide(prefs.isHideRecordingsEnabled(), marker != null, prefs.isNomediaCreatedByUs())) {
            Action.CREATE -> {
                val created = folder.createFile("application/octet-stream", MARKER)
                if (created == null || created.name != MARKER) {
                    // A provider that appended an extension made a file that hides nothing; take it back.
                    created?.delete()
                    AppLogger.w(TAG, "The folder would not take a $MARKER file (got ${created?.name})")
                    return
                }
                prefs.setNomediaCreatedByUs(true)
                AppLogger.i(TAG, "Recordings hidden from media apps")
                rescan(context, folderUri)
            }
            Action.DELETE -> {
                if (marker?.delete() == true) {
                    prefs.setNomediaCreatedByUs(false)
                    AppLogger.i(TAG, "Recordings visible to media apps again")
                    rescan(context, folderUri)
                }
            }
            Action.NONE -> Unit
        }
    }

    /**
     * Asks the media index to look at the folder again, so recordings it already listed disappear (or
     * reappear) now rather than at its next full scan. Only possible for primary shared storage, where the
     * tree maps to a real path; elsewhere the index catches up on its own.
     */
    private fun rescan(context: Context, folderUri: Uri) {
        val path = primaryPathOf(folderUri) ?: return
        // A real Context, never null: on Android 14 the scan callback dereferences it on a background
        // thread, and a null one crashed the app outside any try (OP9, 2026-10-19).
        runCatching {
            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(File(path, MARKER).path, path), null, null)
        }.onFailure { AppLogger.d(TAG, "Rescan request failed: ${it.message}") }
    }

    /**
     * The marker, looked up by its document id rather than by listing the folder: the external-storage
     * provider leaves dot-files out of listings, so `findFile` never sees `.nomedia` (measured on the OP9 —
     * every start tried to create it again, and turning the setting off could not remove it). Listing is
     * kept as the fallback for providers whose ids are not paths.
     */
    private fun findMarker(context: Context, treeUri: Uri, folder: DocumentFile): DocumentFile? {
        val direct = runCatching {
            val folderId = DocumentsContract.getDocumentId(folder.uri)
            val doc = DocumentFile.fromSingleUri(
                context, DocumentsContract.buildDocumentUriUsingTree(treeUri, "$folderId/$MARKER"),
            )
            doc?.takeIf { it.exists() }
        }.getOrNull()
        return direct ?: folder.findFile(MARKER)
    }

    private fun primaryPathOf(treeUri: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        if (!docId.startsWith("primary:")) return null
        File(Environment.getExternalStorageDirectory(), docId.removePrefix("primary:")).path
    }.getOrNull()
}
