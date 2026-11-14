/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recorder-only sharing exposes the audio file and nothing generated from it. */
class CallSharePlanTest {

    private fun call(audio: Boolean = true, transcript: Boolean = false, summary: Boolean = false) =
        ShareableCall("Mom_20261106_150812.ogg", "Mom", if (audio) "content://a" else null, transcript, summary)

    @Test
    fun `only the recording is offered even if legacy text rows exist`() {
        assertEquals(
            setOf(SharePart.RECORDING),
            CallSharePlan.available(listOf(call(transcript = true, summary = true))),
        )
    }

    @Test
    fun `no shareable audio means no available parts`() {
        assertTrue(CallSharePlan.available(listOf(call(audio = false, transcript = true, summary = true))).isEmpty())
    }

    @Test
    fun `the recording starts ticked when available`() {
        val available = CallSharePlan.available(listOf(call()))
        assertEquals(setOf(SharePart.RECORDING), CallSharePlan.initialTicks(SharePart.RECORDING, available))
    }

    @Test
    fun `a legacy transcript page falls back to recording sharing`() {
        val available = CallSharePlan.available(listOf(call()))
        assertEquals(setOf(SharePart.RECORDING), CallSharePlan.initialTicks(SharePart.TRANSCRIPT, available))
    }

    @Test
    fun `recording shares as a file`() {
        assertTrue(CallSharePlan.asFiles(setOf(SharePart.RECORDING)))
    }
}
