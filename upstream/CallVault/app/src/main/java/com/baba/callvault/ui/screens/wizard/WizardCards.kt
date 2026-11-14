/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baba.callvault.ui.common.CvCard

/**
 * A branded radio-style option card: a one-line title, an (i) beside it when there is something longer to say
 * ([explanation]), a teal check that appears when selected, and a teal border + faint teal tint in the
 * selected state. Replaces stock RadioButtons.
 */
@Composable
internal fun OptionCard(
    title: String,
    explanation: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val container =
        if (selected) primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
    val borderColor by animateColorAsState(
        targetValue = if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
        label = "optionBorder"
    )

    CvCard(
        onClick = onClick,
        color = container,
        border = false,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
        modifier = Modifier.border(
            width = if (selected) 1.5.dp else 1.dp,
            color = borderColor,
            shape = MaterialTheme.shapes.large
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            if (!explanation.isNullOrBlank()) {
                SettingsInfoButton(label = title, explanation = explanation)
            }
            Spacer(Modifier.width(4.dp))
            SelectionDot(selected = selected)
        }
    }
}

/** A circular selection indicator: a hollow ring when unselected, a filled teal check when selected. */
@Composable
private fun SelectionDot(selected: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    if (selected) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}

/**
 * A coral note card — for a choice that leaves CallVault doing nothing.
 *
 * Every colour is stated: the scheme's M3 defaults resolve to coral here, so a card that leant on them
 * would be indistinguishable from an ordinary note.
 */
@Composable
internal fun WarningCard(text: String) {
    CvCard(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
        border = false,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
