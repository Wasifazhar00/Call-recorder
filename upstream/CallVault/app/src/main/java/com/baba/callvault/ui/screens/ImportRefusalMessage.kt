/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import com.baba.callvault.R
import com.baba.callvault.data.recordings.AudioImport

/** User-facing explanation for a rejected audio import. Shared by every import entry point. */
internal fun importRefusalMessage(reason: AudioImport.Reason): Int = when (reason) {
    AudioImport.Reason.NOT_AUDIO -> R.string.import_failed_not_audio
    AudioImport.Reason.EMPTY -> R.string.import_failed_empty
    AudioImport.Reason.NO_FOLDER -> R.string.import_failed_no_folder
    AudioImport.Reason.COPY_FAILED -> R.string.import_failed_copy
    AudioImport.Reason.UNDECODABLE -> R.string.import_failed_undecodable
}
