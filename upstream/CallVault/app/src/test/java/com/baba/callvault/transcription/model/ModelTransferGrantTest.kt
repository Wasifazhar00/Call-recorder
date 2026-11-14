/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.transcription.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a finished import or backup lets go of the user's URI grant: on its own end, or on the user's
 * cancel — never on a system stop or a REPLACE, which re-run and still need it.
 */
class ModelTransferGrantTest {

    private val modelId = "small-q5_1"

    @Test
    fun `a stopped run the user cancelled releases the grant and clears the mark`() {
        // Arrange
        val cancelled = mutableSetOf(modelId)

        // Act
        val isReleased = ModelTransferWorker.shouldReleaseGrant(cancelled, modelId, isStopped = true)

        // Assert
        assertTrue(isReleased)
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun `a stopped run nobody cancelled keeps the grant`() {
        // Arrange
        val cancelled = mutableSetOf("other-model")

        // Act
        val isReleased = ModelTransferWorker.shouldReleaseGrant(cancelled, modelId, isStopped = true)

        // Assert
        assertFalse(isReleased)
        assertTrue(cancelled == setOf("other-model"))
    }

    @Test
    fun `a run that ended on its own releases the grant`() {
        assertTrue(ModelTransferWorker.shouldReleaseGrant(mutableSetOf(), modelId, isStopped = false))
    }

    @Test
    fun `a run that ended on its own still clears a stale mark`() {
        // Arrange
        val cancelled = mutableSetOf(modelId)

        // Act
        val isReleased = ModelTransferWorker.shouldReleaseGrant(cancelled, modelId, isStopped = false)

        // Assert
        assertTrue(isReleased)
        assertTrue(cancelled.isEmpty())
    }
}
