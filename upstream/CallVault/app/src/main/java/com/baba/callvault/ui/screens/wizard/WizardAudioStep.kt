/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baba.callvault.R
import com.baba.callvault.integrations.scrcpy.AUDIO_BIT_RATE_OPTIONS
import com.baba.callvault.integrations.scrcpy.ScrcpyAudioCodec
import com.baba.callvault.ui.common.CvCard
import com.baba.callvault.ui.common.M3DropdownField
import com.baba.callvault.ui.common.OptionItem

@Composable
internal fun AudioStep(
    audioCodec: String,
    audioBitRate: Int,
    onSelectCodec: (String) -> Unit,
    onSelectBitRate: (Int) -> Unit
) {
    CvCard(contentPadding = PaddingValues(vertical = 8.dp)) {
        val codecOptions = ScrcpyAudioCodec.entries.map { OptionItem(it.cliKey, stringResource(it.titleResId)) }
        val codecLabel = stringResource(R.string.settings_audio_codec)
        // The "try AAC" tip, only while another codec is chosen — the same condition as Settings.
        val aacAdvice = stringResource(R.string.settings_audio_bitrate_recommendation)
            .takeIf { audioCodec != ScrcpyAudioCodec.AAC.cliKey }
        WithInfo(codecLabel, aacAdvice) {
            M3DropdownField(
                label = codecLabel,
                selected = codecOptions.find { it.key == audioCodec } ?: codecOptions.first(),
                options = codecOptions,
                onOptionSelected = { onSelectCodec(it.key) }
            )
        }

        val bitrateOptions = AUDIO_BIT_RATE_OPTIONS.map {
            OptionItem(it.toString(), stringResource(R.string.audio_bitrate_kbps, it / 1000))
        }
        M3DropdownField(
            label = stringResource(R.string.settings_audio_bitrate),
            selected = bitrateOptions.find { it.key == audioBitRate.toString() } ?: bitrateOptions.first(),
            options = bitrateOptions,
            onOptionSelected = { onSelectBitRate(it.key.toInt()) }
        )
    }
}
