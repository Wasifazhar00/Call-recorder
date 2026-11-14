package com.baba.callvault.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baba.callvault.R
import com.baba.callvault.ui.common.CvCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon

/** Selects all currently visible recordings without leaving selection mode on clear. */
@Composable
internal fun LibrarySelectAllRow(
    allSelected: Boolean,
    someSelected: Boolean,
    selectedCount: Int,
    onClick: () -> Unit,
) {
    CvCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (allSelected) R.string.selection_clear_all else R.string.selection_select_all),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (selectedCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = pluralStringResource(R.plurals.home_selected_count, selectedCount, selectedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            TriStateCheckbox(
                state = when {
                    allSelected -> ToggleableState.On
                    someSelected -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                },
                onClick = null,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}
