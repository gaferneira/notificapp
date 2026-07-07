package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.displayName
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.MatchingLogicViewModel

/**
 * Bottom sheet for configuring matching logic (conditions).
 * Uses its own MVI ViewModel for state management.
 *
 * @param initialCondition Pre-populated condition data when editing, or null for new condition
 * @param onConditionSaved Called when a condition is saved (new or updated)
 * @param onDismiss Called when the sheet should be dismissed
 * @param viewModel The ViewModel for this bottom sheet (injected by default)
 * @param modifier Modifier for the bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingLogicBottomSheet(
    modifier: Modifier = Modifier,
    viewModel: MatchingLogicViewModel = hiltViewModel(),
    initialCondition: RuleCondition? = null,
    onConditionSaved: (RuleCondition) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialCondition) {
        if (initialCondition != null) {
            viewModel.onEvent(
                MatchingLogicContract.UiEvent.InitForEdit(initialCondition),
            )
        }
    }

    // Collect effects and handle them internally, calling appropriate callbacks
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is MatchingLogicContract.UiEffect.ConditionCreated -> {
                onConditionSaved(effect.condition)
            }
            is MatchingLogicContract.UiEffect.ConditionUpdated -> {
                onConditionSaved(effect.condition)
            }
            is MatchingLogicContract.UiEffect.Dismiss -> {
                onDismiss()
            }
            is MatchingLogicContract.UiEffect.ShowError -> {
                // Error is already shown via validation in the bottom sheet
            }
        }
    }

    val title = when (uiState.mode) {
        MatchingLogicContract.UiState.Mode.EDIT -> "Edit Condition"
        MatchingLogicContract.UiState.Mode.ADD -> "Add Condition"
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(MatchingLogicContract.UiEvent.OnDismiss) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = "Define the condition under which this rule should trigger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Validation error
            uiState.validationError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // Condition content
            ConditionModeContent(
                condition = uiState.matchingCondition,
                operator = uiState.matchingOperator,
                value = uiState.matchingValue,
                onConditionChange = {
                    viewModel.onEvent(MatchingLogicContract.UiEvent.OnMatchingConditionChange(it))
                },
                onOperatorChange = {
                    viewModel.onEvent(MatchingLogicContract.UiEvent.OnMatchingOperatorChange(it))
                },
                onValueChange = {
                    viewModel.onEvent(MatchingLogicContract.UiEvent.OnMatchingValueChange(it))
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(MatchingLogicContract.UiEvent.OnDismiss) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { viewModel.onEvent(MatchingLogicContract.UiEvent.OnConfirm) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (uiState.mode == MatchingLogicContract.UiState.Mode.EDIT) "Update" else "Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConditionModeContent(
    condition: MatchingCondition,
    operator: MatchingOperator,
    value: String,
    onConditionChange: (MatchingCondition) -> Unit,
    onOperatorChange: (MatchingOperator) -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Condition and Operator dropdowns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MatchingDropdown(
                label = "Condition",
                selected = condition,
                options = MatchingCondition.entries.toList(),
                onSelect = onConditionChange,
                displayName = { it.displayName() },
                modifier = Modifier.weight(1f),
            )

            MatchingDropdown(
                label = "Operator",
                selected = operator,
                options = MatchingOperator.entries.toList(),
                onSelect = onOperatorChange,
                displayName = { it.displayName() },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Value input
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Value to match") },
            placeholder = { Text("e.g., Your purchase") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> MatchingDropdown(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    displayName: (T) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { newExpanded -> expanded = newExpanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayName(selected),
            onValueChange = { /* Read-only */ },
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayName(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MatchingLogicBottomSheetPreview() {
    NotificappTheme {
        // Preview using the component's structure but with default values
        val previewState = MatchingLogicContract.UiState(
            mode = MatchingLogicContract.UiState.Mode.ADD,
            matchingCondition = MatchingCondition.TEXT_CONTENT,
            matchingOperator = MatchingOperator.CONTAINS,
            matchingValue = "delivery",
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = "Add Condition",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Define the condition under which this rule should trigger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            ConditionModeContent(
                condition = previewState.matchingCondition,
                operator = previewState.matchingOperator,
                value = previewState.matchingValue,
                onConditionChange = {},
                onOperatorChange = {},
                onValueChange = {},
            )
        }
    }
}
