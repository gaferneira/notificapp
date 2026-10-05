package dev.gaferneira.notificapp.features.ruleeditor.ui

import android.content.res.Configuration
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.AdvancedStateItem
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
import dev.gaferneira.notificapp.features.ruleeditor.domain.advancedStateSummary
import dev.gaferneira.notificapp.features.ruleeditor.domain.shouldExpandAdvancedInitially
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AdvancedSettingsSection
import kotlinx.collections.immutable.persistentListOf

/**
 * Collapsible "Advanced" rule settings: the test-mode toggle always, and the delete-raw-content
 * toggle only when the rule has an Extract-data action (see [UiState.showDeleteRawContentToggle]).
 * The collapsed header summarises the current state and the section opens by default when a
 * non-default option is on. Home for future per-rule options.
 */
@Composable
internal fun SettingsSection(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    val rule = uiState.rule
    val showRaw = uiState.showDeleteRawContentToggle
    AdvancedSettingsSection(
        modifier = modifier,
        summary = advancedStateSummary(rule.isDryRun, showRaw, rule.deleteRawContentAfterExtraction).summaryText(),
        expandInitially = shouldExpandAdvancedInitially(rule.isDryRun, showRaw, rule.deleteRawContentAfterExtraction),
    ) {
        AdvancedSettingsContent(uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun List<AdvancedStateItem>.summaryText(): String = map { item ->
    stringResource(
        when (item) {
            AdvancedStateItem.TEST_MODE_ON -> R.string.rule_editor_advanced_state_test_on
            AdvancedStateItem.TEST_MODE_OFF -> R.string.rule_editor_advanced_state_test_off
            AdvancedStateItem.ORIGINAL_TEXT_DELETED -> R.string.rule_editor_advanced_state_raw_deleted
        },
    )
}.reduce { acc, text -> stringResource(R.string.rule_editor_advanced_state_join, acc, text) }

@Composable
internal fun AdvancedSettingsContent(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
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

private val previewExtractAction = RuleAction(id = "1", type = ActionType.SAVE_DATA, isEnabled = true)

@Composable
private fun AdvancedPreviewHost(rule: RuleUiModel) {
    NotificappTheme(dynamicColor = false) {
        SettingsSection(uiState = UiState(rule = rule), onEvent = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, name = "Advanced collapsed")
@Preview(showBackground = true, name = "Advanced collapsed dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Advanced collapsed 2x", fontScale = 2f)
@Composable
private fun AdvancedCollapsedPreview() {
    AdvancedPreviewHost(RuleUiModel())
}

@Preview(showBackground = true, name = "Advanced test mode")
@Preview(showBackground = true, name = "Advanced test mode dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Advanced test mode 2x", fontScale = 2f)
@Composable
private fun AdvancedTestModePreview() {
    AdvancedPreviewHost(RuleUiModel(isDryRun = true))
}

@Preview(showBackground = true, name = "Advanced delete raw")
@Preview(showBackground = true, name = "Advanced delete raw dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "Advanced delete raw 2x", fontScale = 2f)
@Composable
private fun AdvancedDeleteRawPreview() {
    AdvancedPreviewHost(
        RuleUiModel(
            actions = persistentListOf(previewExtractAction),
            deleteRawContentAfterExtraction = true,
        ),
    )
}
