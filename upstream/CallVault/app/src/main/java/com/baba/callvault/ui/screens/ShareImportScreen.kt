package com.baba.callvault.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baba.callvault.R
import com.baba.callvault.ui.common.CvCard
import com.baba.callvault.data.recordings.AudioImport
import com.baba.callvault.data.recordings.SharedAudio
import com.baba.callvault.ui.viewmodels.ShareImportViewModel

@Composable
fun ShareImportScreen(
    state: ShareImportViewModel.State,
    onChoose: () -> Unit,
    onOpenRecording: (String) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    val isWorking = state is ShareImportViewModel.State.Importing
    BackHandler(enabled = isWorking) { }

    Box(
        modifier = Modifier.fillMaxSize().background(SCRIM).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CvCard(
            modifier = Modifier.widthIn(max = CARD_MAX_WIDTH),
            color = MaterialTheme.colorScheme.surface,
        ) {
            when (state) {
                ShareImportViewModel.State.Reading,
                ShareImportViewModel.State.Importing -> WorkingBody()

                is ShareImportViewModel.State.Deciding -> ChoiceBody(
                    displayName = state.displayName,
                    sizeBytes = state.sizeBytes,
                    onChoose = onChoose,
                    onClose = onClose,
                )

                is ShareImportViewModel.State.Imported -> OutcomeBody(
                    icon = Icons.Filled.CheckCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.share_import_queued_keep_title),
                    body = stringResource(R.string.share_import_queued_keep_body),
                    primaryLabel = stringResource(R.string.share_import_open),
                    onPrimary = { onOpenRecording(state.displayName) },
                    onClose = onClose,
                )

                is ShareImportViewModel.State.ShareRefused -> when (state.reason) {
                    SharedAudio.Reason.NOT_SET_UP -> OutcomeBody(
                        icon = Icons.Filled.Settings,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.share_import_not_set_up_title),
                        body = stringResource(R.string.share_import_not_set_up_body),
                        primaryLabel = stringResource(R.string.share_import_open_app),
                        onPrimary = onOpenApp,
                        onClose = onClose,
                    )
                    SharedAudio.Reason.NOT_A_SHARE,
                    SharedAudio.Reason.NOTHING_SHARED -> RefusedBody(
                        body = stringResource(R.string.share_import_nothing),
                        onClose = onClose,
                    )
                }

                is ShareImportViewModel.State.ImportRefused -> RefusedBody(
                    body = stringResource(importRefusalMessage(state.reason)),
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun ChoiceBody(
    displayName: String?,
    sizeBytes: Long,
    onChoose: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.share_import_choice_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = listOfNotNull(
            displayName?.takeIf { it.isNotBlank() },
            sizeBytes.takeIf { it > 0L }?.let { Formatter.formatShortFileSize(context, it) },
        ).joinToString(" · ").ifBlank { stringResource(R.string.share_import_choice_unnamed) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.import_choice_keep))
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.import_choice_keep_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.share_import_close)) }
    }
}

@Composable
private fun WorkingBody() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.share_import_choice_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.share_import_working_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutcomeBody(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onClose: () -> Unit,
) {
    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primaryLabel) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.share_import_close)) }
    }
}

@Composable
private fun RefusedBody(body: String, onClose: () -> Unit) {
    Icon(
        Icons.Filled.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(28.dp),
    )
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.share_import_close))
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.58f)
private val CARD_MAX_WIDTH = 480.dp
