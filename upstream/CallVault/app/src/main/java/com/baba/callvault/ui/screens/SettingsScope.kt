/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import com.baba.callvault.data.StorageTarget
import com.baba.callvault.ui.navigation.HomeSection

/** Which page Settings was opened from. */
enum class SettingsScope { RECORDINGS, ALL }

/** One recorder setting group. */
enum class SettingsGroup(val key: String) {
    CALLS_TO_RECORD("group_calls_to_record"),
    CALLS_TO_SKIP("group_calls_to_skip"),
    SOUND_AND_NAMES("group_sound_and_names"),
    SAVE_AND_SYNC("group_save_and_sync"),
    CLEANING_UP("group_cleaning_up"),
    CONNECTION("group_connection"),
    APP("group_app"),
    HELP_AND_ABOUT("group_help_and_about");

    companion object {
        fun fromKey(key: String?): SettingsGroup? = entries.firstOrNull { it.key == key }
    }
}

object SettingsGroups {
    private val RECORDINGS = listOf(
        SettingsGroup.CALLS_TO_RECORD,
        SettingsGroup.CALLS_TO_SKIP,
        SettingsGroup.SOUND_AND_NAMES,
        SettingsGroup.SAVE_AND_SYNC,
        SettingsGroup.CLEANING_UP,
        SettingsGroup.CONNECTION,
    )
    private val ALL = RECORDINGS + listOf(SettingsGroup.APP, SettingsGroup.HELP_AND_ABOUT)

    fun forScope(scope: SettingsScope): List<SettingsGroup> = when (scope) {
        SettingsScope.RECORDINGS -> RECORDINGS
        SettingsScope.ALL -> ALL
    }

    fun scopeFor(section: HomeSection): SettingsScope = when (section) {
        HomeSection.Hub -> SettingsScope.ALL
        HomeSection.Recordings -> SettingsScope.RECORDINGS
    }

    fun rowsOf(group: SettingsGroup): List<String> = when (group) {
        SettingsGroup.CALLS_TO_RECORD -> listOf(
            "CarrierRecordingRow", "VoipRecordingSwitch", "VoipAppPicker",
            "AutoRecordIncomingRow", "AutoRecordOutgoingRow", "RecordFromAnswerRow", "VoipAutoStartRow",
            "VibrationRow", "AfterCallModeRow", "AfterCallAskStyleRow", "AfterCallUnansweredRow",
        )
        SettingsGroup.CALLS_TO_SKIP -> listOf(
            "IgnoreContactsIncomingRow", "IgnoreContactsOutgoingRow", "IgnoreAnonymousIncomingRow",
            "IgnoreCrossCountryIncomingRow", "IgnoreCrossCountryOutgoingRow", "VoipIgnoreContactsRow",
            "MinDurationRow",
        )
        SettingsGroup.SOUND_AND_NAMES -> listOf("AudioSection", "FileNameTemplateRow", "SimLabelRow")
        SettingsGroup.SAVE_AND_SYNC -> listOf(
            "StorageTargetRow", "RecordingFolderRow", "DriveFolderRow", "UploadScheduleSubSection",
            "HideRecordingsRow", "MetadataFileRow",
        )
        SettingsGroup.CLEANING_UP -> listOf("RetentionPolicyRows", "MergeSubSection")
        SettingsGroup.CONNECTION -> listOf("ConnectionRows")
        SettingsGroup.APP -> listOf("AppearanceRows", "PrivacySubSection", "UpdatesSubSection", "ExportImportRow")
        SettingsGroup.HELP_AND_ABOUT -> listOf("BugReportSection", "AboutSection")
    }

    fun settingKeysOf(group: SettingsGroup): List<String> = when (group) {
        SettingsGroup.CALLS_TO_RECORD -> listOf(
            "carrier_recording_enabled", "voip_recording_enabled", "voip_recording", "voip_excluded_packages",
            "auto_record_incoming", "auto_record_outgoing", "record_from_answer", "voip_auto_start",
            "vibration_enabled", "after_call_mode", "after_call_ask_style", "after_call_unanswered",
        )
        SettingsGroup.CALLS_TO_SKIP -> listOf(
            "ignore_contacts_mode_incoming", "ignore_contacts_mode_outgoing",
            "ignored_contacts_incoming", "ignored_contacts_outgoing",
            "record_only_contacts_incoming", "record_only_contacts_outgoing",
            "ignore_anonymous_incoming", "ignore_cross_country_incoming", "ignore_cross_country_outgoing",
            "voip_apply_ignored_contacts", "min_duration_seconds",
        )
        SettingsGroup.SOUND_AND_NAMES -> listOf("audio_source", "audio_codec", "audio_bitrate", "file_name_template", "show_sim_label")
        SettingsGroup.SAVE_AND_SYNC -> listOf(
            "storage_target", "recording_folder_uri", "drive_folder_uri",
            "sync_schedule_mode", "sync_time_hour", "sync_time_minute", "sync_day_of_week",
            "hide_recordings", "write_metadata_file",
        )
        SettingsGroup.CLEANING_UP -> listOf(
            "retention_linked", "retention_local_days", "retention_drive_days",
            "retention_time_hour", "retention_time_minute", "storage_cap_bytes", "keep_originals_after_merge",
        )
        SettingsGroup.CONNECTION -> listOf(
            "handoff_persist", "offline_recording", "wd_enforced", "wd_auto_approve_network", "wd_never_turn_off",
        )
        SettingsGroup.APP -> listOf("theme_mode", "dynamic_color", "show_toasts", "update_check_enabled")
        SettingsGroup.HELP_AND_ABOUT -> listOf("logging_enabled")
    }
}

/** The rows of "Save and sync", in screen order. */
enum class SaveAndSyncRow {
    STORAGE_TARGET, RECORDING_FOLDER, DRIVE_FOLDER, UPLOAD_SCHEDULE, HIDE_RECORDINGS, METADATA_FILE,
}

object SaveAndSyncRows {
    fun uploads(target: StorageTarget): Boolean = target != StorageTarget.LOCAL

    fun visibleRows(target: StorageTarget): List<SaveAndSyncRow> =
        SaveAndSyncRow.entries.filter { row ->
            when (row) {
                SaveAndSyncRow.DRIVE_FOLDER, SaveAndSyncRow.UPLOAD_SCHEDULE -> uploads(target)
                else -> true
            }
        }
}
