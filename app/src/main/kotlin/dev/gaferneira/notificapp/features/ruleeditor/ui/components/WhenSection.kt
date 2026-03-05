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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.TriggerUiModel

/**
 * The "When" section showing all configured triggers.
 */
@Composable
fun WhenSection(
    triggers: List<TriggerUiModel>,
    onRemoveTrigger: (String) -> Unit,
    onTriggerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        triggers.forEach { trigger ->
            TriggerCard(
                trigger = trigger,
                onClick = { onTriggerClick(trigger.id) },
                onRemove = { onRemoveTrigger(trigger.id) },
            )
        }
    }
}

@Composable
private fun TriggerCard(
    trigger: TriggerUiModel,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, iconColor, label, labelColor) = when (trigger.type) {
        MatchingLogicContract.TriggerType.CONDITION ->
            Quadruple(
                Icons.Default.Notifications,
                MaterialTheme.colorScheme.secondary,
                "CONDITION",
                MaterialTheme.colorScheme.secondary,
            )
        MatchingLogicContract.TriggerType.APP ->
            Quadruple(
                Icons.Default.Apps,
                MaterialTheme.colorScheme.tertiary,
                "APP TRIGGER",
                MaterialTheme.colorScheme.tertiary,
            )
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
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (trigger.type) {
                                MatchingLogicContract.TriggerType.CONDITION -> MaterialTheme.colorScheme.secondaryContainer
                                MatchingLogicContract.TriggerType.APP -> MaterialTheme.colorScheme.tertiaryContainer
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = trigger.displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (trigger.type == MatchingLogicContract.TriggerType.CONDITION) {
                // Remove button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove trigger",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// Helper data class for 4 values
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Preview(showBackground = true)
@Composable
private fun WhenSectionPreview() {
    NotificappTheme {
        WhenSection(
            triggers = listOf(
                TriggerUiModel(
                    id = "2",
                    type = MatchingLogicContract.TriggerType.APP,
                    selectedApps = listOf(
                        MatchingLogicContract.AppInfo("com.swish", "Swish"),
                        MatchingLogicContract.AppInfo("com.bank", "Bank App"),
                    ),
                ),
                TriggerUiModel(
                    id = "1",
                    type = MatchingLogicContract.TriggerType.CONDITION,
                    condition = MatchingLogicContract.MatchingCondition.TEXT_CONTENT,
                    operator = MatchingLogicContract.MatchingOperator.CONTAINS,
                    value = "purchase",
                ),
            ),
            onRemoveTrigger = {},
            onTriggerClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WhenSectionEmptyPreview() {
    NotificappTheme {
        WhenSection(
            triggers = emptyList(),
            onRemoveTrigger = {},
            onTriggerClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
