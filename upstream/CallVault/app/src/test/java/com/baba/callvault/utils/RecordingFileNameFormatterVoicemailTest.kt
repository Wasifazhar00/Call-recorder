/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.utils

import android.Manifest
import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.baba.callvault.data.recordings.RecordingDirection
import com.baba.callvault.data.recordings.RecordingMetadata
import com.baba.callvault.integrations.scrcpy.ScrcpyAudioCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // Robolectric 4.14 max; project targets SDK 36
class RecordingFileNameFormatterVoicemailTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setDeviceVoicemailNumber() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        shadowOf(telephonyManager).setVoiceMailNumber("123")
    }

    @Test
    fun contact_name_placeholder_falls_back_to_voicemail_label() {
        val metadata = RecordingMetadata(rawPhoneNumber = "123", direction = RecordingDirection.OUTGOING)

        val fileName = RecordingFileNameFormatter.formatFileName(
            context, metadata, ScrcpyAudioCodec.OPUS, customFormat = "{contact_name}"
        )

        assertEquals("Voicemail.ogg", fileName)
    }

    @Test
    fun contact_name_placeholder_falls_back_to_number_for_unsaved_number() {
        // No saved contact and not voicemail: the filename must still identify the call.
        val metadata = RecordingMetadata(rawPhoneNumber = "0612345678", direction = RecordingDirection.OUTGOING)

        val fileName = RecordingFileNameFormatter.formatFileName(
            context, metadata, ScrcpyAudioCodec.OPUS, customFormat = "{contact_name}"
        )

        assertEquals("0612345678.ogg", fileName)
    }

    @Test
    fun number_fallback_is_skipped_when_template_already_has_the_number() {
        // {phone_number} already carries the number — an unsaved contact must not duplicate it.
        val metadata = RecordingMetadata(rawPhoneNumber = "0612345678", direction = RecordingDirection.OUTGOING)

        val fileName = RecordingFileNameFormatter.formatFileName(
            context, metadata, ScrcpyAudioCodec.OPUS, customFormat = "{phone_number}_{contact_name}"
        )

        assertEquals("0612345678_.ogg", fileName)
    }

    @Test
    fun voicemail_label_still_wins_over_number_fallback() {
        val metadata = RecordingMetadata(rawPhoneNumber = "123", direction = RecordingDirection.OUTGOING)

        val fileName = RecordingFileNameFormatter.formatFileName(
            context, metadata, ScrcpyAudioCodec.OPUS, customFormat = "{contact_name}"
        )

        assertEquals("Voicemail.ogg", fileName)
    }

    // -------- The Honor / Huawei phone-app preset (#56)

    private val callStart = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).parse("20261020103708")!!

    @Test
    fun honor_preset_writes_name_at_number_and_the_call_start_to_the_second() {
        // No contacts permission here, so the name falls back to the number, as for an unsaved caller.
        val metadata = RecordingMetadata(rawPhoneNumber = "087011", direction = RecordingDirection.INCOMING)

        val fileName = RecordingFileNameFormatter.formatFileName(
            context, metadata, ScrcpyAudioCodec.AAC, customFormat = HONOR_DIALER_TEMPLATE, startedAt = callStart
        )

        assertEquals("087011@087011_20261020103708.m4a", fileName)
    }

    @Test
    fun honor_preset_writes_the_number_as_dialled_without_spaces_or_dashes() {
        val metadata = RecordingMetadata(
            rawPhoneNumber = "+380 50 123-45-67",
            standardizedNumber = "+380501234567",
            direction = RecordingDirection.OUTGOING
        )

        val fileName = RecordingFileNameFormatter.formatFileName(
            context, metadata, ScrcpyAudioCodec.AAC, customFormat = HONOR_DIALER_TEMPLATE, startedAt = callStart
        )

        assertEquals("380501234567@+380501234567_20261020103708.m4a", fileName)
    }

    @Test
    fun the_date_comes_from_the_given_call_start_not_from_now() {
        val metadata = RecordingMetadata(rawPhoneNumber = "0612345678", direction = RecordingDirection.OUTGOING)

        val fileName = RecordingFileNameFormatter.formatFileName(
            context, metadata, ScrcpyAudioCodec.OPUS, customFormat = "{date}", startedAt = callStart
        )

        assertTrue(fileName, fileName.startsWith("20261020_103708.000"))
    }

    @Test
    fun dialled_number_keeps_a_plus_behind_spaces_or_direction_marks_and_drops_placeholders() {
        assertEquals("+972501234567", RecordingFileNameFormatter.dialledNumber(" \u200E+972 50-123 4567"))
        assertEquals("087011", RecordingFileNameFormatter.dialledNumber("087011"))
        assertEquals(null, RecordingFileNameFormatter.dialledNumber("-1"))
        assertEquals(null, RecordingFileNameFormatter.dialledNumber("Unknown 5"))
        assertEquals(null, RecordingFileNameFormatter.dialledNumber(null))
    }

    @Test
    fun honor_contact_name_drops_underscores_and_regex_characters() {
        // HnContacts strips '_' before the '@' and then uses the name as a regex.
        assertEquals("Dana Levi Work", RecordingFileNameFormatter.honorSafeName("Dana_Levi (Work)"))
        assertEquals("Mum", RecordingFileNameFormatter.honorSafeName("[Mum]*"))
        assertEquals("Баланс", RecordingFileNameFormatter.honorSafeName("Баланс"))
        assertEquals("", RecordingFileNameFormatter.honorSafeName("+?"))
    }
}
