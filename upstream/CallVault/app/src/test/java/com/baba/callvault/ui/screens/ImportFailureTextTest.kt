/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import com.baba.callvault.R
import com.baba.callvault.summary.SummaryModel
import com.baba.callvault.transcription.model.ModelTransferWorker
import com.baba.callvault.transcription.model.TranscriptionModel
import com.baba.callvault.ui.common.TranscriptionLabels
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which sentence a failed import shows: each named failure its own remedy, anything else "pick it again". */
class ImportFailureTextTest {

    @Test
    fun `each named import failure maps to its own message`() {
        // Arrange
        val expected = mapOf(
            ModelTransferWorker.ERROR_WRONG_FILE to R.string.model_import_failed_wrong_file,
            ModelTransferWorker.ERROR_OTHER_MODEL to R.string.model_import_failed_other_model,
            ModelTransferWorker.ERROR_UNREADABLE to R.string.model_import_failed_unreadable,
            ModelTransferWorker.ERROR_NO_SPACE to R.string.model_import_failed_space,
        )

        // Act
        val actual = expected.keys.associateWith { importFailureRes(it) }

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun `the wrong-file message names the model's file, not its tier label`() {
        // Arrange
        val model = TranscriptionModel.SMALL_Q5_1

        // Act
        val wrong = importFailureMessage(ModelTransferWorker.ERROR_WRONG_FILE, model)
        val unreadable = importFailureMessage(ModelTransferWorker.ERROR_UNREADABLE, model)

        // Assert
        assertEquals(ImportFailureMessage(R.string.model_import_failed_wrong_file, listOf(FailureArg.Text(model.fileName))), wrong)
        assertEquals(ImportFailureMessage(R.string.model_import_failed_unreadable, emptyList()), unreadable)
    }

    @Test
    fun `an unknown or missing reason reads as unreadable, never as a download failure`() {
        assertEquals(R.string.model_import_failed_unreadable, importFailureRes(null))
        assertEquals(R.string.model_import_failed_unreadable, importFailureRes(ModelTransferWorker.ERROR_WRITE_FAILED))
        assertEquals(R.string.model_import_failed_unreadable, importFailureRes("something-new"))
    }

    @Test
    fun `the other-model message names a speech model by its picker label`() {
        // Arrange
        val target = TranscriptionModel.SMALL_Q5_1
        val other = TranscriptionModel.LARGE_V3_TURBO_Q8_0

        // Act
        val message = importFailureMessage(ModelTransferWorker.ERROR_OTHER_MODEL, target, other.id)

        // Assert
        assertEquals(
            ImportFailureMessage(
                R.string.model_import_failed_other_model,
                listOf(FailureArg.Res(TranscriptionLabels.titleOf(other)))
            ),
            message
        )
    }

    @Test
    fun `the other-model message names the summariser by its file name`() {
        // Arrange
        val target = TranscriptionModel.SMALL_Q5_1
        val other = SummaryModel.entries.first()

        // Act
        val message = importFailureMessage(ModelTransferWorker.ERROR_OTHER_MODEL, target, other.id)

        // Assert
        assertEquals(
            ImportFailureMessage(R.string.model_import_failed_other_model, listOf(FailureArg.Text(other.fileName))),
            message
        )
    }

    @Test
    fun `an other-model id the catalogue does not know still fills the placeholder`() {
        val message = importFailureMessage(ModelTransferWorker.ERROR_OTHER_MODEL, TranscriptionModel.SMALL_Q5_1, "gone")

        assertEquals(listOf<FailureArg>(FailureArg.Text("gone")), message.args)
    }
}
