package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.mapping.availableActionTypes
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep
import dev.gaferneira.notificapp.features.ruleeditor.domain.NameNextField
import dev.gaferneira.notificapp.features.ruleeditor.domain.nameNextField
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionCardCallbacks
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AddButton
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.DoSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.WhenSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.proseKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.rememberClearFocusKeyboardActions

/*
 * Section bodies shared by the guided flow and the single-page editor: both presentations render
 * the same composables over the same UiState/UiEvent, only the surrounding layout differs.
 */

/** Title + one-line help above a section's content (guided steps). */
@Composable
internal fun SectionHeader(title: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A titled card holding one section of the single-page editor. */
@Composable
internal fun EditorSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    TonalCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = title, description = description)
            content()
        }
    }
}

/** Apps scope, conditions, match logic and the history test. */
@Composable
internal fun WhenEditor(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhenSection(
            targetApps = uiState.rule.targetApps,
            isIncludeMode = uiState.rule.isIncludeMode,
            onAppsClick = { onEvent(UiEvent.OnAppsClicked) },
            onAppScopeModeChanged = { onEvent(UiEvent.OnAppScopeModeChanged(it)) },
            conditions = uiState.rule.triggers,
            conditionLogic = uiState.rule.conditionLogic,
            onRemoveCondition = { onEvent(UiEvent.OnRemoveConditionClicked(it)) },
            onConditionClick = { onEvent(UiEvent.OnConditionItemClicked(it)) },
            onConditionLogicChanged = { onEvent(UiEvent.OnConditionLogicChanged(it)) },
        )
        StepNotices(uiState = uiState, step = EditorStep.WHEN)
        AddButton(
            text = stringResource(R.string.rule_editor_add_condition),
            onClick = { onEvent(UiEvent.OnAddConditionClicked) },
        )
        TestAgainstHistoryButton(uiState = uiState, onEvent = onEvent)
    }
}

/** Actions list (Extract data lives here as an action) plus the add-action button. */
@Composable
internal fun DoEditor(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DoSection(
            actions = uiState.rule.actions,
            extractDataFieldCount = uiState.rule.fields.size,
            callbacks = ActionCardCallbacks(
                onToggle = { id, enabled -> onEvent(UiEvent.OnToggleActionClicked(id, enabled)) },
                onRemove = { onEvent(UiEvent.OnRemoveActionClicked(it)) },
                onEdit = { onEvent(UiEvent.OnEditActionClicked(it)) },
            ),
        )
        StepNotices(uiState = uiState, step = EditorStep.DO)
        // Hidden once every action type is configured (one action per type)
        if (availableActionTypes(uiState.rule.actions.map { it.type }).isNotEmpty()) {
            AddButton(
                text = stringResource(R.string.rule_editor_add_action),
                onClick = { onEvent(UiEvent.OnAddActionClicked) },
            )
        }
    }
}

@Composable
private fun TestAgainstHistoryButton(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { onEvent(UiEvent.OnTestAgainstHistoryClicked) },
            enabled = !uiState.isBacktesting && uiState.canTestAgainstHistory,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(
                    if (uiState.isBacktesting) R.string.rule_editor_test_history_running else R.string.rule_editor_test_history,
                ),
            )
        }
        if (!uiState.canTestAgainstHistory) {
            Text(
                text = stringResource(R.string.rule_editor_test_history_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Name (required) with optional description and category. [nameFocusRequester] lets the screen focus the name on demand. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NameFields(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    nameFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val descriptionFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }
    val nextField = nameNextField(uiState.showDescription, uiState.showCategory)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val nameError = uiState.validationErrors["name"]
        OutlinedTextField(
            value = uiState.rule.name,
            onValueChange = { onEvent(UiEvent.OnNameChange(it)) },
            label = { Text(stringResource(R.string.rule_editor_field_name)) },
            placeholder = { Text(stringResource(R.string.rule_editor_field_name_placeholder)) },
            isError = nameError != null,
            supportingText = when {
                nameError != null -> {
                    { Text(nameError.asString()) }
                }
                uiState.rule.name.isBlank() -> {
                    { Text(stringResource(R.string.rule_editor_field_name_helper)) }
                }
                else -> null
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocusRequester),
            singleLine = true,
            keyboardOptions = proseKeyboardOptions(if (nextField == NameNextField.NONE) ImeAction.Done else ImeAction.Next),
            keyboardActions = nameKeyboardActions(nextField, descriptionFocusRequester, categoryFocusRequester),
        )

        OptionalFields(
            uiState = uiState,
            onEvent = onEvent,
            descriptionFocusRequester = descriptionFocusRequester,
            categoryFocusRequester = categoryFocusRequester,
        )
    }
}

/** Done hides the keyboard; Next jumps straight to the first optional field that is shown. */
@Composable
private fun nameKeyboardActions(
    nextField: NameNextField,
    descriptionFocusRequester: FocusRequester,
    categoryFocusRequester: FocusRequester,
): KeyboardActions {
    val clearFocusActions = rememberClearFocusKeyboardActions()
    return when (nextField) {
        NameNextField.DESCRIPTION -> KeyboardActions(onNext = { descriptionFocusRequester.requestFocus() })
        NameNextField.CATEGORY -> KeyboardActions(onNext = { categoryFocusRequester.requestFocus() })
        NameNextField.NONE -> clearFocusActions
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionalFields(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    descriptionFocusRequester: FocusRequester,
    categoryFocusRequester: FocusRequester,
) {
    if (!uiState.showDescription || !uiState.showCategory) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.showDescription) {
                OptionalFieldChip(
                    text = stringResource(R.string.rule_editor_add_description),
                    onClick = { onEvent(UiEvent.OnAddDescriptionClicked) },
                )
            }
            if (!uiState.showCategory) {
                OptionalFieldChip(
                    text = stringResource(R.string.rule_editor_add_category),
                    onClick = { onEvent(UiEvent.OnAddCategoryClicked) },
                )
            }
        }
    }

    if (uiState.showDescription) {
        // Multi-line: the Enter key inserts a line break, so there is no Next/Done on this field.
        OutlinedTextField(
            value = uiState.rule.description,
            onValueChange = { onEvent(UiEvent.OnDescriptionChange(it)) },
            label = { Text(stringResource(R.string.rule_editor_field_description)) },
            placeholder = { Text(stringResource(R.string.rule_editor_field_description_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(descriptionFocusRequester),
            minLines = 2,
            keyboardOptions = proseKeyboardOptions(ImeAction.Default),
        )
    }

    if (uiState.showCategory) {
        OutlinedTextField(
            value = uiState.rule.category,
            onValueChange = { onEvent(UiEvent.OnCategoryChange(it)) },
            label = { Text(stringResource(R.string.rule_editor_field_category)) },
            placeholder = { Text(stringResource(R.string.rule_editor_field_category_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(categoryFocusRequester),
            singleLine = true,
            keyboardOptions = proseKeyboardOptions(ImeAction.Done),
            keyboardActions = rememberClearFocusKeyboardActions(),
        )
    }
}

@Composable
private fun OptionalFieldChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text)
    }
}
