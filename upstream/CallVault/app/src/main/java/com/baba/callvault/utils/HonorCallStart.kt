/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.utils

import com.baba.callvault.data.recordings.RecordingDirection

/**
 * Which call-log time an Honor-preset file name must carry (#56).
 *
 * Honor's phone app (decompiled HnContacts, MagicOS 6/8/10) shows a recording only when the time in its name lies
 * within [DATE − 1 s, DATE + duration + 1 s] of the call-log entry. DATE is when the call began — ringing, for an
 * incoming call — while we start recording at answer, so a call that rang longer than it lasted fell outside.
 * Taking DATE from the log itself puts the name inside the window by construction. Pure, for testing.
 */
object HonorCallStart {

    /** One call-log row, as much of it as matching needs. */
    data class LogEntry(val date: Long, val number: String, val direction: RecordingDirection)

    /** How long before our recording started its call may have begun ringing or dialling. */
    const val MAX_RING_MS = 3 * 60_000L

    /** How far after our start a log DATE may be and still be this call: clocks and callbacks jitter. */
    const val CLOCK_SLACK_MS = 5_000L

    /**
     * The DATE of the call this recording belongs to: the latest entry with the same direction and number that
     * began at most [MAX_RING_MS] before [recordingStartMillis]. Null when none matches or the number is unknown,
     * in which case the name keeps our own start time.
     *
     * @param sameNumber the number comparison; on a phone, `PhoneNumberUtils.compare`, the one HnContacts uses.
     */
    fun pick(
        entries: List<LogEntry>,
        recordingStartMillis: Long,
        direction: RecordingDirection,
        number: String?,
        sameNumber: (String, String) -> Boolean,
    ): Long? {
        if (number.isNullOrBlank()) return null
        return entries
            .filter { it.direction == direction }
            .filter { it.date in (recordingStartMillis - MAX_RING_MS)..(recordingStartMillis + CLOCK_SLACK_MS) }
            .filter { sameNumber(it.number, number) }
            .maxOfOrNull { it.date }
    }
}
