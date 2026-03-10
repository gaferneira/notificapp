package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
    initialAction: RuleAction? = null,
    onActionSaved: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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
        onDismissRequest = { viewModel.onEvent(ActionBottomSheetContract.UiEvent.OnDismiss) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        ActionsContent(uiState, viewModel::onEvent)
    }
}

@Composable
private fun ActionsContent(
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
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
            title = "Delete notification",
            description = "Remove the notification after processing",
            icon = Icons.Default.Delete,
            isSelected = uiState.actionType == ActionType.DELETE_NOTIFICATION,
            onClick = {
                onEvent(
                    ActionBottomSheetContract.UiEvent.OnActionTypeChange(
                        ActionType.DELETE_NOTIFICATION,
                    ),
                )
            },
        )

        // Show Save to Data Lab toggle when that action is selected
        if (uiState.actionType == ActionType.SAVE_DATA) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Save to Data Lab",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Turn this on to save extracted data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
