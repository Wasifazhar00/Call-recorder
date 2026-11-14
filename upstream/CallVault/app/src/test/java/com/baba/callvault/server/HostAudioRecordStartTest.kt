/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Issue #53: Honor's MagicOS 11 (Android 17) added an audit block to the top of `AudioRecord.startRecording()`
 * that calls `Objects.requireNonNull(ActivityThread.currentApplication())`. The recorder host has no
 * Application, so every start threw before recording began. The start is repaired only for that crash.
 */
class HostAudioRecordStartTest {

    private fun npe(vararg frames: Pair<String, String>) = NullPointerException().apply {
        stackTrace = frames.map { (c, m) -> StackTraceElement(c, m, "X.java", 1) }.toTypedArray()
    }

    /** The trace from the report (Honor Magic 9 Pro Max, MagicOS 11.0.0.111, 2.4.6). */
    private fun honorCrash() = npe(
        "java.util.Objects" to "requireNonNull",
        "android.media.AudioRecord" to "startRecording",
        "com.baba.callvault.server.VoipCaptureSession" to "startInternal",
    )

    @Test
    fun the_honor_crash_as_reported_is_recognised() {
        assertTrue(VendorStartCrash.matches(honorCrash()))
    }

    @Test
    fun a_null_pointer_inside_aosp_startRecording_itself_is_not_a_vendor_crash() {
        val aosp = npe(
            "android.media.AudioRecord" to "startRecording",
            "com.baba.callvault.server.VoipCaptureSession" to "startInternal",
        )
        assertFalse(VendorStartCrash.matches(aosp))
    }

    @Test
    fun other_errors_are_not_vendor_start_crashes() {
        assertFalse(VendorStartCrash.matches(IllegalStateException("startRecording() called on an uninitialized AudioRecord")))
        assertFalse(VendorStartCrash.matches(npe("java.util.Objects" to "requireNonNull", "com.example.Other" to "run")))
    }

    @Test
    fun a_normal_start_never_repairs() {
        var starts = 0
        var repairs = 0
        HostAudioRecord.startWithRepair("test", start = { starts++ }, repair = { repairs++; true })
        assertEquals(1, starts)
        assertEquals(0, repairs)
    }

    @Test
    fun the_honor_crash_is_repaired_and_the_same_record_started_again() {
        var starts = 0
        var repairs = 0
        HostAudioRecord.startWithRepair(
            "test",
            start = { starts++; if (starts == 1) throw honorCrash() },
            repair = { repairs++; true },
        )
        assertEquals(2, starts)
        assertEquals(1, repairs)
    }

    @Test
    fun any_other_failure_is_rethrown_untouched_and_nothing_is_repaired() {
        val boom = IllegalStateException("startRecording() called on an uninitialized AudioRecord")
        var repairs = 0
        try {
            HostAudioRecord.startWithRepair("test", start = { throw boom }, repair = { repairs++; true })
            fail("expected the start failure to propagate")
        } catch (e: IllegalStateException) {
            assertSame(boom, e)
        }
        assertEquals(0, repairs)
    }

    @Test
    fun a_failed_repair_rethrows_the_original_crash_without_a_second_start() {
        val crash = honorCrash()
        var starts = 0
        try {
            HostAudioRecord.startWithRepair("test", start = { starts++; throw crash }, repair = { false })
            fail("expected the crash to propagate")
        } catch (e: NullPointerException) {
            assertSame(crash, e)
        }
        assertEquals(1, starts)
    }
}
