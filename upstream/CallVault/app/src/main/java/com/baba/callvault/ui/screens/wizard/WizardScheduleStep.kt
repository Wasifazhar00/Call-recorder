/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baba.callvault.R
import com.baba.callvault.data.SyncScheduleMode
import com.baba.callvault.ui.common.CvCard
import com.baba.callvault.ui.common.M3DropdownField
import com.baba.callvault.ui.common.OptionItem
import com.baba.callvault.ui.common.SyncScheduleLabels

@Composable
internal fun ScheduleStep(
    scheduleMode: SyncScheduleMode,
    hour: Int,
    minute: Int,
    dayOfWeek: Int,
    onSelectMode: (SyncScheduleMode) -> Unit,
    onSelectHour: (Int) -> Unit,
    onSelectMinute: (Int) -> Unit,
    onSelectDayOfWeek: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SyncScheduleMode.entries.forEach { mode ->
            OptionCard(
                title = stringResource(SyncScheduleLabels.titleOf(mode)),
                explanation = stringResource(SyncScheduleLabels.descriptionOf(mode)),
                selected = scheduleMode == mode,
                onClick = { onSelectMode(mode) }
            )
        }

        if (scheduleMode == SyncScheduleMode.DAILY || scheduleMode == SyncScheduleMode.WEEKLY) {
            CvCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                if (scheduleMode == SyncScheduleMode.WEEKLY) {
                    val dayOptions = SyncScheduleLabels.DAY_OF_WEEK_OPTIONS.map { day ->
                        OptionItem(day.toString(), stringResource(SyncScheduleLabels.dayOfWeekOf(day)))
                    }
                    M3DropdownField(
                        label = stringResource(R.string.wizard_schedule_day_label),
                        selected = dayOptions.find { it.key == dayOfWeek.toString() } ?: dayOptions.first(),
                        options = dayOptions,
                        onOptionSelected = { onSelectDayOfWeek(it.key.toInt()) }
                    )
                }
                val hourOptions = (0..23).map { OptionItem(it.toString(), it.toString().padStart(2, '0')) }
                M3DropdownField(
                    label = stringResource(R.string.wizard_schedule_hour_label),
                    selected = hourOptions.find { it.key == hour.toString() } ?: hourOptions.first(),
                    options = hourOptions,
                    onOptionSelected = { onSelectHour(it.key.toInt()) }
                )
                val minuteOptions = SyncScheduleLabels.MINUTE_OPTIONS.map { OptionItem(it.toString(), it.toString().padStart(2, '0')) }
                M3DropdownField(
                    label = stringResource(R.string.wizard_schedule_minute_label),
                    selected = minuteOptions.find { it.key == minute.toString() } ?: minuteOptions.first(),
                    options = minuteOptions,
                    onOptionSelected = { onSelectMinute(it.key.toInt()) }
                )
            }
        }
    }
}
