package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState

/**
 * Rule settings: the dry-run toggle always, and the delete-raw-content toggle only when the rule
 * has an Extract-data action (see [UiState.showDeleteRawContentToggle]).
 */
@Composable
internal fun SettingsSection(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingToggleRow(
            title = stringResource(R.string.rule_editor_dry_run_title),
            description = stringResource(R.string.rule_editor_dry_run_description),
            checked = uiState.rule.isDryRun,
            onCheckedChange = { onEvent(UiEvent.OnDryRunToggle(it)) },
        )
        if (uiState.showDeleteRawContentToggle) {
            SettingToggleRow(
                title = stringResource(R.string.rule_editor_delete_raw_title),
                description = stringResource(R.string.rule_editor_delete_raw_description),
                checked = uiState.rule.deleteRawContentAfterExtraction,
                onCheckedChange = { onEvent(UiEvent.OnDeleteRawContentToggle(it)) },
            )
        }
    }
}

/** A whole-row toggle: title, one-line explanation and a switch that shares the row's click target. */
@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
