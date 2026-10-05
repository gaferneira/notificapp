package dev.gaferneira.notificapp.features.settings.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.StorageStats
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiState
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Data section: retention, storage usage and the destructive "clear all data" action. */
@Composable
internal fun DataSection(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRetentionDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.settings_section_data),
        modifier = modifier,
    ) {
        SettingsValueRow(
            icon = Icons.Default.Schedule,
            title = stringResource(R.string.settings_retention_title),
            subtitle = stringResource(R.string.settings_retention_subtitle),
            value = uiState.retentionPeriod.label(),
            onClick = { showRetentionDialog = true },
        )
        SettingsDivider()
        StorageUsage(storageStats = uiState.storageStats)
        SettingsDivider()
        SettingsRow(
            icon = Icons.Default.DeleteForever,
            title = stringResource(R.string.settings_clear_data_title),
            subtitle = stringResource(R.string.settings_clear_data_subtitle),
            onClick = { showClearDialog = true },
        )
    }

    if (showRetentionDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_retention_title),
            options = RetentionPeriod.entries,
            selected = uiState.retentionPeriod,
            optionLabel = { it.label() },
            onSelect = {
                onEvent(UiEvent.RetentionPeriodChanged(it))
                showRetentionDialog = false
            },
            onDismiss = { showRetentionDialog = false },
        )
    }
    if (showClearDialog) {
        ClearDataDialog(
            onConfirm = {
                onEvent(UiEvent.OnClearAllData)
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun ClearDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_data_dialog_title)) },
        text = { Text(stringResource(R.string.settings_clear_data_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_clear_data_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_clear_data_cancel))
            }
        },
    )
}

@Composable
private fun StorageUsage(
    storageStats: StorageStats?,
    modifier: Modifier = Modifier,
) {
    val unavailable = stringResource(R.string.settings_storage_unavailable)
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = stringResource(R.string.settings_storage_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.settings_storage_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StorageStatRow(
            label = stringResource(R.string.settings_storage_database_size),
            value = storageStats?.databaseSizeBytes?.toHumanReadableSize() ?: unavailable,
        )
        StorageStatRow(
            label = stringResource(R.string.settings_storage_notifications),
            value = storageStats?.notificationCount?.toString() ?: unavailable,
        )
        StorageStatRow(
            label = stringResource(R.string.settings_storage_rules),
            value = storageStats?.ruleCount?.toString() ?: unavailable,
        )
        StorageStatRow(
            label = stringResource(R.string.settings_storage_rule_executions),
            value = storageStats?.ruleExecutionCount?.toString() ?: unavailable,
        )
        StorageStatRow(
            label = stringResource(R.string.settings_storage_extracted_values),
            value = storageStats?.extractedFieldValueCount?.toString() ?: unavailable,
        )
        StorageStatRow(
            label = stringResource(R.string.settings_storage_monitored_apps),
            value = storageStats?.selectedAppCount?.toString() ?: unavailable,
        )
    }
}

@Composable
private fun StorageStatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RetentionPeriod.label(): String = stringResource(
    when (this) {
        RetentionPeriod.DAYS_30 -> R.string.settings_retention_30_days
        RetentionPeriod.DAYS_90 -> R.string.settings_retention_90_days
        RetentionPeriod.NEVER -> R.string.settings_retention_forever
    },
)

private fun Long.toHumanReadableSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(this.toDouble()) / ln(BYTES_UNIT)).toInt().coerceIn(0, units.size - 1)
    val size = this / BYTES_UNIT.pow(digitGroups)
    return if (digitGroups == 0) {
        "$this ${units[0]}"
    } else {
        String.format(Locale.ROOT, "%.1f %s", size, units[digitGroups])
    }
}

private const val BYTES_UNIT = 1024.0

private val previewStats = StorageStats(
    databaseSizeBytes = 2_400_000,
    notificationCount = 1284,
    ruleCount = 6,
    ruleExecutionCount = 932,
    extractedFieldValueCount = 2210,
    selectedAppCount = 4,
)

@Preview(showBackground = true)
@Composable
private fun DataSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        DataSection(
            uiState = UiState(storageStats = previewStats, isLoading = false),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DataSectionPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        DataSection(uiState = UiState(isLoading = false), onEvent = {}, modifier = Modifier.padding(16.dp))
    }
}
