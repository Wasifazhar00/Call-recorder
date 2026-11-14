/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.utils

import com.baba.callvault.data.StorageTarget
import com.baba.callvault.integrations.scrcpy.ScrcpyAudioCodec

/**
 * What else has to be true, beyond the file name, for Honor's and Huawei's phone app to play a recording from
 * the call history (#56): the file must sit in `Sounds/CallRecord` on the phone and be `.m4a`, which here
 * means the AAC codec, and it must stay on the phone. Pure, so it can be tested without Android.
 */
object HonorDialerSetup {

    /** The SAF tree document id of [HONOR_DIALER_FOLDER] on the phone's own storage. */
    const val TREE_DOCUMENT_ID = "primary:Sounds/CallRecord"

    /** A setting that still has to change, in the order the hint lists them. */
    enum class Missing { FOLDER, CODEC, STORAGE_TARGET }

    /** True when [treeDocumentId] (of the chosen recording folder) is the phone app's folder. */
    fun isDialerFolder(treeDocumentId: String?): Boolean =
        treeDocumentId?.trimEnd('/')?.equals(TREE_DOCUMENT_ID, ignoreCase = true) == true

    /** The settings that still stand between the user and a playable recording in the call history. */
    fun missing(treeDocumentId: String?, codecKey: String, target: StorageTarget): List<Missing> = buildList {
        if (!isDialerFolder(treeDocumentId)) add(Missing.FOLDER)
        if (!codecKey.equals(ScrcpyAudioCodec.AAC.cliKey, ignoreCase = true)) add(Missing.CODEC)
        if (target == StorageTarget.DRIVE) add(Missing.STORAGE_TARGET)
    }
}
