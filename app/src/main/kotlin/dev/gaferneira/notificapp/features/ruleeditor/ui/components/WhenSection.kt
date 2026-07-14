package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.features.ruleeditor.contract.displayText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The "When" section showing target apps and configured conditions.
 *
 * @param targetApps Selected target apps (empty = all apps)
 * @param isIncludeMode When true, [targetApps] is an include-list; when false, exclude-list.
 * @param onAppsClick Called when the apps card is clicked
 * @param onAppScopeModeChanged Called when the include/exclude mode changes
 * @param conditions List of matching conditions
 * @param conditionLogic How [conditions] are combined (ALL/ANY). Meaningless with fewer than 2.
 * @param onRemoveCondition Called when a condition should be removed
 * @param onConditionClick Called when a condition card is clicked
 * @param onConditionLogicChanged Called when the ALL/ANY combinator changes
 */
@Composable
fun WhenSection(
    targetApps: ImmutableList<AppInfo>,
    isIncludeMode: Boolean,
    onAppsClick: () -> Unit,
    onAppScopeModeChanged: (Boolean) -> Unit,
    conditions: ImmutableList<RuleCondition>,
    conditionLogic: ConditionCombinator,
    onRemoveCondition: (String) -> Unit,
    onConditionClick: (String) -> Unit,
    onConditionLogicChanged: (ConditionCombinator) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Apps card (always shown, even if empty - shows "All apps" when empty)
        AppsCard(
            selectedApps = targetApps,
            isIncludeMode = isIncludeMode,
            onClick = onAppsClick,
            onAppScopeModeChanged = onAppScopeModeChanged,
        )

        // Combinator only makes sense with 2+ conditions to combine
        if (conditions.size > 1) {
            ConditionLogicToggle(
                conditionLogic = conditionLogic,
                onLogicChanged = onConditionLogicChanged,
            )
        }

        // Condition cards
        conditions.forEach { condition ->
            ConditionCard(
                condition = condition,
                onClick = { onConditionClick(condition.id) },
                onRemove = { onRemoveCondition(condition.id) },
            )
        }
    }
}

@Composable
private fun ConditionLogicToggle(
    conditionLogic: ConditionCombinator,
    onLogicChanged: (ConditionCombinator) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Match",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = conditionLogic == ConditionCombinator.ALL,
                onClick = { onLogicChanged(ConditionCombinator.ALL) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("All conditions") },
            )
            SegmentedButton(
                selected = conditionLogic == ConditionCombinator.ANY,
                onClick = { onLogicChanged(ConditionCombinator.ANY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Any condition") },
            )
        }
    }
}

@Composable
private fun AppsCard(
    selectedApps: ImmutableList<AppInfo>,
    isIncludeMode: Boolean,
    onClick: () -> Unit,
    onAppScopeModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        AppsCardContent(
            selectedApps = selectedApps,
            isIncludeMode = isIncludeMode,
            onAppScopeModeChanged = onAppScopeModeChanged,
        )
    }
}

@Composable
private fun AppsCardContent(
    selectedApps: ImmutableList<AppInfo>,
    isIncludeMode: Boolean,
    onAppScopeModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayText = resolveAppsDisplayText(selectedApps, isIncludeMode)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        AppsCardLabel(displayText = displayText, modifier = Modifier.fillMaxWidth())
        if (selectedApps.isNotEmpty()) {
            Spacer(modifier.height(8.dp))
            AppScopeModeToggle(
                isIncludeMode = isIncludeMode,
                onModeChanged = onAppScopeModeChanged,
                enabled = true,
            )
        }
    }
}

private fun resolveAppsDisplayText(
    selectedApps: ImmutableList<AppInfo>,
    isIncludeMode: Boolean,
): String = when {
    selectedApps.isEmpty() -> "All apps"
    !isIncludeMode -> "All apps except " + if (selectedApps.size == 1) selectedApps.first().name else "${selectedApps.size}"
    selectedApps.size == 1 -> selectedApps.first().name
    else -> "${selectedApps.size} apps selected"
}

@Composable
private fun AppsCardLabel(
    displayText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "APPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Edit apps",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AppScopeModeToggle(
    isIncludeMode: Boolean,
    onModeChanged: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        SegmentedButton(
            selected = isIncludeMode,
            onClick = { onModeChanged(true) },
            shape = SegmentedButtonDefaults.itemShape(
                index = 0,
                count = 2,
            ),
            enabled = enabled,
            label = { Text("Only these apps") },
        )
        SegmentedButton(
            selected = !isIncludeMode,
            onClick = { onModeChanged(false) },
            shape = SegmentedButtonDefaults.itemShape(
                index = 1,
                count = 2,
            ),
            enabled = enabled,
            label = { Text("All apps except these") },
        )
    }
}

@Composable
private fun ConditionCard(
    condition: RuleCondition,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "CONDITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = condition.displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove condition",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "WhenSection Light")
@Preview(showBackground = true, name = "WhenSection Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WhenSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        WhenSection(
            targetApps = persistentListOf(
                AppInfo("com.swish", "Swish"),
                AppInfo("com.bank", "Bank App"),
            ),
            isIncludeMode = true,
            onAppScopeModeChanged = {},
            conditions = persistentListOf(
                RuleCondition.ContentMatchCondition(
                    id = "1",
                    condition = MatchingCondition.TEXT_CONTENT,
                    operator = MatchingOperator.CONTAINS,
                    value = "purchase",
                ),
                RuleCondition.ContentMatchCondition(
                    id = "2",
                    condition = MatchingCondition.TITLE,
                    operator = MatchingOperator.EQUALS,
                    value = "Payment received",
                ),
            ),
            conditionLogic = ConditionCombinator.ALL,
            onAppsClick = {},
            onRemoveCondition = {},
            onConditionClick = {},
            onConditionLogicChanged = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WhenSectionEmptyPreview() {
    NotificappTheme {
        WhenSection(
            targetApps = persistentListOf(),
            isIncludeMode = true,
            onAppScopeModeChanged = {},
            conditions = persistentListOf(),
            conditionLogic = ConditionCombinator.ALL,
            onAppsClick = {},
            onRemoveCondition = {},
            onConditionClick = {},
            onConditionLogicChanged = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WhenSectionAllAppsPreview() {
    NotificappTheme {
        WhenSection(
            targetApps = persistentListOf(),
            isIncludeMode = true,
            onAppScopeModeChanged = {},
            conditions = persistentListOf(
                RuleCondition.ContentMatchCondition(
                    id = "1",
                    condition = MatchingCondition.TEXT_CONTENT,
                    operator = MatchingOperator.CONTAINS,
                    value = "delivery",
                ),
            ),
            conditionLogic = ConditionCombinator.ALL,
            onAppsClick = {},
            onRemoveCondition = {},
            onConditionClick = {},
            onConditionLogicChanged = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
