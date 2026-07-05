package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.features.ruleeditor.contract.ActionBottomSheetContract
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.ActionBottomSheetViewModel

/**
 * Bottom sheet for adding and configuring actions.
 * Uses its own MVI ViewModel for state management.
 * Supports both adding new actions and editing existing ones.
 *
 * @param initialAction Pre-populated action data when editing, or null for new action
 * @param onActionSaved Called when an action is saved (new or updated)
 * @param onDismiss Called when the sheet should be dismissed
 * @param modifier Modifier for the bottom sheet
 * @param viewModel The ViewModel for this bottom sheet (injected by default)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBottomSheet(
    modifier: Modifier = Modifier,
    initialAction: RuleAction? = null,
    onActionSaved: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ActionBottomSheetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Initialize for edit mode when initialAction changes
    LaunchedEffect(initialAction) {
        if (initialAction != null) {
            viewModel.onEvent(
                ActionBottomSheetContract.UiEvent.InitForEdit(initialAction),
            )
        }
    }

    // Collect effects and handle them internally, calling appropriate callbacks
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is ActionBottomSheetContract.UiEffect.ActionCreated -> {
                onActionSaved(effect.action)
            }
            is ActionBottomSheetContract.UiEffect.ActionUpdated -> {
                onActionSaved(effect.action)
            }
            is ActionBottomSheetContract.UiEffect.Dismiss -> {
                onDismiss()
            }
            is ActionBottomSheetContract.UiEffect.ShowError -> {
                // Error is already shown via validation in the bottom sheet
            }
        }
    }

    ModalBottomSheet(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxHeight(),
        onDismissRequest = { viewModel.onEvent(ActionBottomSheetContract.UiEvent.OnDismiss) },
        sheetState = sheetState,
    ) {
        ActionsContent(
            uiState = uiState,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun ActionsContent(
    modifier: Modifier = Modifier,
    uiState: ActionBottomSheetContract.UiState,
    onEvent: (ActionBottomSheetContract.UiEvent) -> Unit,
) {
    val title = when (uiState.mode) {
        ActionBottomSheetContract.UiState.Mode.EDIT -> "Edit Action"
        ActionBottomSheetContract.UiState.Mode.ADD -> "Add Action"
    }

    val buttonText = when (uiState.mode) {
        ActionBottomSheetContract.UiState.Mode.EDIT -> "Update"
        ActionBottomSheetContract.UiState.Mode.ADD -> "Add Action"
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(scrollState),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose what happens when the rule matches a notification.",
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

        // Action type options
        Text(
            text = "Action type",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        ActionTypeCard(
            title = "Save to Data Lab",
            description = "Store extracted data for later viewing and analysis",
            icon = Icons.Default.Save,
            isSelected = uiState.actionType == ActionType.SAVE_DATA,
            onClick = {
                onEvent(
                    ActionBottomSheetContract.UiEvent.OnActionTypeChange(
                        ActionType.SAVE_DATA,
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionTypeCard(
            title = "Create alarm",
            description = "Set an alarm or reminder based on extracted time",
            icon = Icons.Default.Alarm,
            isSelected = uiState.actionType == ActionType.CREATE_ALARM,
            onClick = {
                onEvent(
                    ActionBottomSheetContract.UiEvent.OnActionTypeChange(
                        ActionType.CREATE_ALARM,
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionTypeCard(
            title = "Dismiss notification",
            description = "Dismiss notification after processing",
            icon = Icons.Default.Delete,
            isSelected = uiState.actionType == ActionType.DISMISS_NOTIFICATION,
            onClick = {
                onEvent(
                    ActionBottomSheetContract.UiEvent.OnActionTypeChange(
                        ActionType.DISMISS_NOTIFICATION,
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionTypeCard(
            title = "Snooze notification",
            description = "Temporarily dismiss and remind later",
            icon = Icons.Default.NotificationsPaused,
            isSelected = uiState.actionType == ActionType.SNOOZE_NOTIFICATION,
            onClick = {
                onEvent(
                    ActionBottomSheetContract.UiEvent.OnActionTypeChange(
                        ActionType.SNOOZE_NOTIFICATION,
                    ),
                )
            },
        )

        // Show snooze duration selector when snooze is selected
        if (uiState.actionType == ActionType.SNOOZE_NOTIFICATION) {
            Spacer(modifier = Modifier.height(16.dp))

            SnoozeDurationSelector(
                selectedMinutes = uiState.snoozeDurationMinutes,
                onDurationChange = { minutes ->
                    onEvent(ActionBottomSheetContract.UiEvent.OnSnoozeDurationChange(minutes))
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onEvent(ActionBottomSheetContract.UiEvent.OnDismiss) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Cancel")
            }

            Button(
                onClick = { onEvent(ActionBottomSheetContract.UiEvent.OnConfirm) },
                modifier = Modifier.weight(1f),
                enabled = uiState.actionType != null,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(buttonText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActionTypeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Duration presets for quick snooze selection.
 */
private val SNOOZE_PRESETS = listOf(5, 10, 15, 30, 60)

/**
 * Minimum and maximum snooze duration in minutes.
 */
private const val MIN_SNOOZE_MINUTES = 1
private const val MAX_SNOOZE_MINUTES = 120

/**
 * Composable for selecting snooze duration with preset chips and a slider.
 * Provides the best UX for minute selection on mobile.
 *
 * @param selectedMinutes Currently selected duration in minutes
 * @param onDurationChange Callback when duration changes
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnoozeDurationSelector(
    selectedMinutes: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        // Header with current selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Snooze duration",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Duration display badge
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = formatDurationMinutes(selectedMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset chips for quick selection
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SNOOZE_PRESETS.forEach { preset ->
                val isSelected = selectedMinutes == preset
                FilterChip(
                    selected = isSelected,
                    onClick = { onDurationChange(preset) },
                    label = { Text("${preset}m") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider for fine-grained control
        Text(
            text = "Or drag to set custom time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${MIN_SNOOZE_MINUTES}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Slider(
                value = selectedMinutes.toFloat(),
                onValueChange = { onDurationChange(it.toInt()) },
                valueRange = MIN_SNOOZE_MINUTES.toFloat()..MAX_SNOOZE_MINUTES.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Text(
                text = "${MAX_SNOOZE_MINUTES}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionBottomSheetPreview() {
    NotificappTheme {
        // Preview using the component's structure but with default values
        ActionsContent(
            uiState = ActionBottomSheetContract.UiState(
                actionType = ActionType.SAVE_DATA,
                mode = ActionBottomSheetContract.UiState.Mode.EDIT,
                validationError = null,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionBottomSheetAlarmPreview() {
    NotificappTheme {
        // Preview using the component's structure but with default values
        ActionsContent(
            uiState = ActionBottomSheetContract.UiState(
                actionType = ActionType.CREATE_ALARM,
                mode = ActionBottomSheetContract.UiState.Mode.EDIT,
                validationError = null,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionBottomSheetSnoozePreview() {
    NotificappTheme {
        // Preview showing snooze configuration
        ActionsContent(
            uiState = ActionBottomSheetContract.UiState(
                actionType = ActionType.SNOOZE_NOTIFICATION,
                snoozeDurationMinutes = 30,
                mode = ActionBottomSheetContract.UiState.Mode.ADD,
                validationError = null,
            ),
            onEvent = {},
        )
    }
}
