package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.features.ruleeditor.contract.displayName
import dev.gaferneira.notificapp.features.ruleeditor.ui.formatDurationMinutes

/**
 * The "Do" section showing configured actions.
 * Displays action cards with toggle switches, remove buttons, and click-to-edit.
 */
@Composable
fun DoSection(
    actions: List<RuleAction>,
    onToggleAction: (String, Boolean) -> Unit,
    onRemoveAction: (String) -> Unit,
    onEditAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Column {
            Text(
                text = "Do",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Actions to perform with the extracted data, like saving it or sending a new alert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Action cards
        actions.forEach { action ->
            ActionCard(
                action = action,
                onToggle = { onToggleAction(action.id, !action.isEnabled) },
                onRemove = { onRemoveAction(action.id) },
                onClick = { onEditAction(action.id) },
            )
        }
    }
}

@Composable
private fun ActionCard(
    action: RuleAction,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (action.type) {
        ActionType.SAVE_DATA -> Icons.AutoMirrored.Filled.List
        ActionType.DISMISS_NOTIFICATION -> Icons.Default.Delete
        ActionType.CREATE_ALARM -> Icons.Default.Alarm
        ActionType.SNOOZE_NOTIFICATION -> Icons.Default.NotificationsPaused
    }

    // Get subtitle text for actions with configuration
    val subtitle = when (action.type) {
        ActionType.SNOOZE_NOTIFICATION -> {
            val minutes = action.getSnoozeDurationMinutes()
            formatDurationMinutes(minutes)
        }
        else -> null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Icon container
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toggle switch
                Switch(
                    checked = action.isEnabled,
                    onCheckedChange = { onToggle() },
                )

                // Remove button
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove action",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DoSectionPreview() {
    NotificappTheme {
        DoSection(
            actions = listOf(
                RuleAction(
                    id = "1",
                    type = ActionType.SAVE_DATA,
                    isEnabled = true,
                ),
                RuleAction(
                    id = "2",
                    type = ActionType.CREATE_ALARM,
                    isEnabled = false,
                ),
            ),
            onToggleAction = { _, _ -> },
            onRemoveAction = {},
            onEditAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoSectionEmptyPreview() {
    NotificappTheme {
        DoSection(
            actions = emptyList(),
            onToggleAction = { _, _ -> },
            onRemoveAction = {},
            onEditAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoSectionWithSnoozePreview() {
    NotificappTheme {
        DoSection(
            actions = listOf(
                RuleAction(
                    id = "1",
                    type = ActionType.SAVE_DATA,
                    isEnabled = true,
                ),
                RuleAction.createSnooze(
                    id = "2",
                    durationMinutes = 30,
                    isEnabled = true,
                ),
            ),
            onToggleAction = { _, _ -> },
            onRemoveAction = {},
            onEditAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
