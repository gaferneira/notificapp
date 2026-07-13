package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Bottom sheet for configuring matching logic (conditions), across all three [RuleCondition]
 * families: content-match, day-of-week, and time-range. A type picker dispatches to the relevant
 * config body (per TD-13 the sheet only dispatches, it doesn't own each family's UI).
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

            Spacer(modifier = Modifier.height(16.dp))

            ConditionTypePicker(
                selected = uiState.conditionType,
                onSelect = { viewModel.onEvent(MatchingLogicContract.UiEvent.OnConditionTypeChange(it)) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Validation error
            uiState.validationError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // Condition content, dispatched by family
            when (uiState.conditionType) {
                MatchingLogicContract.ConditionType.CONTENT -> ConditionModeContent(
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
                MatchingLogicContract.ConditionType.DAY_OF_WEEK -> DayOfWeekModeContent(
                    selectedDays = uiState.selectedDays,
                    onDayToggled = { viewModel.onEvent(MatchingLogicContract.UiEvent.OnDayToggled(it)) },
                )
                MatchingLogicContract.ConditionType.TIME_RANGE -> TimeRangeModeContent(
                    start = uiState.startTime,
                    end = uiState.endTime,
                    onStartChange = { viewModel.onEvent(MatchingLogicContract.UiEvent.OnStartTimeChange(it)) },
                    onEndChange = { viewModel.onEvent(MatchingLogicContract.UiEvent.OnEndTimeChange(it)) },
                )
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
                    Text(if (uiState.mode == MatchingLogicContract.UiState.Mode.EDIT) "Update" else "Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionTypePicker(
    selected: MatchingLogicContract.ConditionType,
    onSelect: (MatchingLogicContract.ConditionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        MatchingLogicContract.ConditionType.entries.forEach { type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = RoundedCornerShape(8.dp),
                label = { Text(type.displayName()) },
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekModeContent(
    selectedDays: Set<DayOfWeek>,
    onDayToggled: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Trigger only on these days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in selectedDays,
                    onClick = { onDayToggled(day) },
                    label = { Text(day.name.substring(0, 3)) },
                )
            }
        }
    }
}

@Composable
private fun TimeRangeModeContent(
    start: LocalTime,
    end: LocalTime,
    onStartChange: (LocalTime) -> Unit,
    onEndChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "From",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TimePickerButton(hour = start.hour, minute = start.minute, onTimePicked = { h, m -> onStartChange(LocalTime.of(h, m)) })

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TimePickerButton(hour = end.hour, minute = end.minute, onTimePicked = { h, m -> onEndChange(LocalTime.of(h, m)) })

        if (start > end) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "This range spans midnight ($start – $end covers overnight).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerButton(
    hour: Int,
    minute: Int,
    onTimePicked: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("%02d:%02d".format(hour, minute))
    }

    if (showDialog) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimePicked(state.hour, state.minute)
                        showDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
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
