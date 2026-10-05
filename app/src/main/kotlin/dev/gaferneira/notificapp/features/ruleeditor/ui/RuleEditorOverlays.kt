package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.availableActionTypes
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionTypePickerDialog
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AppSelectionPicker
import dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.ExtractDataBottomSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.MatchingLogicBottomSheet
import kotlinx.collections.immutable.toImmutableList

/*
 * Dialogs and sheets layered over the editor. They depend only on UiState/UiEvent, so both the
 * guided flow and the single page share them unchanged.
 */

@Composable
internal fun RuleEditorDialogs(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
) {
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { onEvent(UiEvent.OnDeleteDismissed) },
            title = { Text(stringResource(R.string.rule_editor_delete_title)) },
            text = { Text(stringResource(R.string.rule_editor_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(UiEvent.OnDeleteConfirmed) }) {
                    Text(stringResource(R.string.rule_editor_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(UiEvent.OnDeleteDismissed) }) {
                    Text(stringResource(R.string.rule_editor_action_cancel))
                }
            },
        )
    }

    if (uiState.showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(UiEvent.OnDiscardDismissed) },
            title = { Text(stringResource(R.string.rule_editor_discard_title)) },
            text = { Text(stringResource(R.string.rule_editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(UiEvent.OnDiscardConfirmed) }) {
                    Text(stringResource(R.string.rule_editor_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(UiEvent.OnDiscardDismissed) }) {
                    Text(stringResource(R.string.rule_editor_discard_keep))
                }
            },
        )
    }
}

@Composable
internal fun RuleEditorBottomSheets(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
) {
    ConditionAndAppSheets(uiState = uiState, onEvent = onEvent)

    if (uiState.isActionTypePickerVisible) {
        ActionTypePickerDialog(
            availableTypes = availableActionTypes(uiState.rule.actions.map { it.type }),
            onTypeSelected = { type -> onEvent(UiEvent.OnActionTypeSelected(type)) },
            onDismiss = { onEvent(UiEvent.OnDismissActionTypePicker) },
        )
    }

    if (uiState.isActionSheetVisible) {
        ActionSheetForType(uiState = uiState, onEvent = onEvent)
    }

    if (uiState.pendingExtractDataRemovalId != null) {
        ExtractDataRemovalDialog(onEvent = onEvent)
    }

    uiState.backtestResults?.let { results ->
        BacktestResultsBottomSheet(
            results = results,
            testedCount = uiState.backtestTestedCount,
            fields = uiState.rule.fields,
            onDismiss = { onEvent(UiEvent.OnDismissBacktestResults) },
        )
    }
}

/** Routes to the type-scoped sheet for the action being added (`pendingActionType`) or edited (`editingAction`). */
@Composable
private fun ActionSheetForType(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
) {
    val editing = uiState.editingAction
    val onSave: (RuleAction) -> Unit = { action -> onEvent(UiEvent.OnActionSaved(action)) }
    val onSheetDismiss: () -> Unit = { onEvent(UiEvent.OnDismissSheet) }
    when (editing?.type ?: uiState.pendingActionType) {
        ActionType.SNOOZE_NOTIFICATION ->
            SnoozeBottomSheet(initial = editing, onSave = onSave, onDismiss = onSheetDismiss)
        ActionType.CREATE_ALARM ->
            AlarmBottomSheet(
                initial = editing,
                onSave = onSave,
                onDismiss = onSheetDismiss,
            )
        ActionType.FLASH_ALERT ->
            FlashBottomSheet(initial = editing, onSave = onSave, onDismiss = onSheetDismiss)
        ActionType.SAVE_DATA ->
            ExtractDataBottomSheet(
                initialFields = uiState.rule.fields,
                isEditingAction = editing?.type == ActionType.SAVE_DATA,
                notification = uiState.sampleNotification,
                targetPackages = uiState.rule.targetApps.map { it.packageName }.takeIf { it.isNotEmpty() },
                onCommitted = { fields -> onEvent(UiEvent.OnExtractDataCommitted(fields)) },
                onDismiss = { onEvent(UiEvent.OnDismissSheet) },
            )
        ActionType.SEND_WEBHOOK ->
            WebhookConfigBottomSheet(
                initial = editing,
                ruleFields = uiState.rule.fields,
                onSave = onSave,
                onDismiss = onSheetDismiss,
            )
        ActionType.READ_ALOUD ->
            ReadAloudBottomSheet(
                initial = editing,
                ruleFields = uiState.rule.fields,
                onSave = onSave,
                onDismiss = onSheetDismiss,
            )
        ActionType.SEND_REPLY ->
            SendReplyBottomSheet(
                initial = editing,
                ruleFields = uiState.rule.fields,
                onSave = onSave,
                onDismiss = onSheetDismiss,
            )
        // Dismiss adds directly (no sheet) and Extract-data uses its own sheet.
        else -> Unit
    }
}

@Composable
private fun ConditionAndAppSheets(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
) {
    if (uiState.isMatchingLogicSheetVisible) {
        MatchingLogicBottomSheet(
            initialCondition = uiState.editingCondition,
            onConditionSaved = { condition ->
                onEvent(UiEvent.OnConditionSaved(condition))
            },
            onDismiss = { onEvent(UiEvent.OnDismissSheet) },
        )
    }

    if (uiState.isAppSheetVisible) {
        AppSelectionPicker(
            selectedApps = uiState.rule.targetApps,
            enabledApps = uiState.enabledApps,
            onConfirm = { apps ->
                onEvent(UiEvent.OnAppsSelected(apps.toImmutableList()))
            },
            onDismiss = { onEvent(UiEvent.OnDismissSheet) },
        )
    }
}

@Composable
private fun ExtractDataRemovalDialog(
    onEvent: (UiEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(UiEvent.OnDismissExtractDataRemoval) },
        title = { Text(stringResource(R.string.rule_editor_remove_extract_title)) },
        text = { Text(stringResource(R.string.rule_editor_remove_extract_message)) },
        confirmButton = {
            TextButton(onClick = { onEvent(UiEvent.OnConfirmExtractDataRemoval) }) {
                Text(stringResource(R.string.rule_editor_remove_extract_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(UiEvent.OnDismissExtractDataRemoval) }) {
                Text(stringResource(R.string.rule_editor_action_cancel))
            }
        },
    )
}
