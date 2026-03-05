package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.TriggerUiModel
import dev.gaferneira.notificapp.features.ruleeditor.contract.displayName
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AddButton
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AppSelectionPicker
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.MatchingLogicViewModel

/**
 * Bottom sheet for configuring matching logic (trigger conditions).
 * Uses its own MVI ViewModel for state management.
 *
 * @param isVisible Whether the bottom sheet is visible
 * @param editingTriggerId The ID of the trigger being edited, or null for new trigger
 * @param initialTrigger Pre-populated trigger data when editing, or null for new trigger
 * @param onEffect Callback for effects that need to be handled by the parent
 * @param viewModel The ViewModel for this bottom sheet (injected by default)
 * @param modifier Modifier for the bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchingLogicBottomSheet(
    isVisible: Boolean,
    editingTriggerId: String? = null,
    initialTrigger: TriggerUiModel? = null,
    onEffect: (MatchingLogicContract.UiEffect) -> Unit,
    viewModel: MatchingLogicViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Initialize for edit mode when editingTriggerId changes
    LaunchedEffect(editingTriggerId, initialTrigger) {
        if (editingTriggerId != null && initialTrigger != null) {
            viewModel.onEvent(
                MatchingLogicContract.UiEvent.InitForEdit(
                    triggerId = editingTriggerId,
                    triggerType = initialTrigger.type,
                    condition = initialTrigger.condition,
                    operator = initialTrigger.operator,
                    value = initialTrigger.value,
                    selectedApps = initialTrigger.selectedApps,
                ),
            )
        }
    }

    // Collect effects and forward to parent
    CollectOneOffEffects(viewModel.effect) { effect ->
        onEffect(effect)
    }

    val title = when (uiState.mode) {
        MatchingLogicContract.UiState.Mode.EDIT -> {
            if (uiState.triggerType == MatchingLogicContract.TriggerType.CONDITION) "Edit Condition" else "Edit Apps"
        }
        MatchingLogicContract.UiState.Mode.ADD -> "Add Trigger"
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

            // Description based on trigger type
            val description = when (uiState.triggerType) {
                MatchingLogicContract.TriggerType.CONDITION ->
                    "Define the condition under which this rule should trigger."
                MatchingLogicContract.TriggerType.APP ->
                    "Select the apps for which this rule should trigger."
            }
            Text(
                text = description,
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

            // Mode-specific content
            when (uiState.triggerType) {
                MatchingLogicContract.TriggerType.CONDITION -> {
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
                }
                MatchingLogicContract.TriggerType.APP -> {
                    AppModeContent(
                        selectedApps = uiState.selectedApps,
                        onRemoveApp = {
                            viewModel.onEvent(MatchingLogicContract.UiEvent.OnRemoveApp(it))
                        },
                        onAddApps = {
                            viewModel.onEvent(MatchingLogicContract.UiEvent.OnShowAppPicker)
                        },
                    )
                }
            }

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
                    Text(if (uiState.mode == MatchingLogicContract.UiState.Mode.EDIT) "Update" else "Add Trigger")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // App Selection Picker - shown on top of the matching logic sheet
    if (uiState.isAppPickerVisible) {
        AppSelectionPicker(
            isVisible = true,
            selectedApps = uiState.selectedApps,
            onDismiss = {
                viewModel.onEvent(MatchingLogicContract.UiEvent.OnDismissAppPicker)
            },
            onConfirm = { apps ->
                viewModel.onEvent(MatchingLogicContract.UiEvent.OnAppsSelected(apps))
            },
        )
    }
}

@Composable
private fun ConditionModeContent(
    condition: MatchingLogicContract.MatchingCondition,
    operator: MatchingLogicContract.MatchingOperator,
    value: String,
    onConditionChange: (MatchingLogicContract.MatchingCondition) -> Unit,
    onOperatorChange: (MatchingLogicContract.MatchingOperator) -> Unit,
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
                options = MatchingLogicContract.MatchingCondition.entries.toList(),
                onSelect = onConditionChange,
                displayName = { it.displayName() },
                modifier = Modifier.weight(1f),
            )

            MatchingDropdown(
                label = "Operator",
                selected = operator,
                options = MatchingLogicContract.MatchingOperator.entries.toList(),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppModeContent(
    selectedApps: List<MatchingLogicContract.AppInfo>,
    onRemoveApp: (String) -> Unit,
    onAddApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "SELECTED APPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedApps.isEmpty()) {
            Text(
                text = "No apps selected. This rule will trigger for all apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedApps.forEach { app ->
                    AppChip(
                        appName = app.name,
                        onRemove = { onRemoveApp(app.packageName) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add apps button
        AddButton(
            text = "Add apps",
            onClick = onAddApps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppChip(appName: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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
private fun MatchingLogicBottomSheetConditionPreview() {
    NotificappTheme {
        // Preview using the component's structure but with default values
        val previewState = MatchingLogicContract.UiState(
            mode = MatchingLogicContract.UiState.Mode.ADD,
            triggerType = MatchingLogicContract.TriggerType.CONDITION,
            matchingCondition = MatchingLogicContract.MatchingCondition.TEXT_CONTENT,
            matchingOperator = MatchingLogicContract.MatchingOperator.CONTAINS,
            matchingValue = "delivery",
            selectedApps = emptyList(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Text(
                text = "Add Trigger",
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

@Preview(showBackground = true)
@Composable
private fun MatchingLogicBottomSheetAppPreview() {
    NotificappTheme {
        val previewApps = listOf(
            MatchingLogicContract.AppInfo("com.ica.banken", "ICA Banken"),
            MatchingLogicContract.AppInfo("com.postnord", "PostNord"),
            MatchingLogicContract.AppInfo("com.klarna", "Klarna"),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Text(
                text = "Add Trigger",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select the apps for which this rule should trigger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            AppModeContent(
                selectedApps = previewApps,
                onRemoveApp = {},
                onAddApps = {},
            )
        }
    }
}
