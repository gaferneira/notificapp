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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.BetaBadge
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.SnoozeMode
import dev.gaferneira.notificapp.domain.model.getSnoozeDurationMinutes
import dev.gaferneira.notificapp.domain.model.getSnoozeMode
import dev.gaferneira.notificapp.domain.model.getSnoozeSchedule
import dev.gaferneira.notificapp.domain.model.getThrottleWindowMinutes
import dev.gaferneira.notificapp.domain.model.isBeta
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.util.formatDurationMinutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The "Do" section showing configured actions.
 * Displays action cards with toggle switches, remove buttons, and click-to-edit.
 */
@Composable
fun DoSection(
    actions: ImmutableList<RuleAction>,
    extractDataFieldCount: Int,
    callbacks: ActionCardCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The heading and its help text are rendered by the host SectionHeader.
        // Action cards
        actions.forEach { action ->
            ActionCard(
                action = action,
                subtitle = action.cardSubtitle(extractDataFieldCount),
                onToggle = { callbacks.onToggle(action.id, !action.isEnabled) },
                onRemove = { callbacks.onRemove(action.id) },
                onClick = { callbacks.onEdit(action.id) },
            )
        }
    }
}

/** Secondary line for an action card: snooze duration/schedule, extract-data field count, or none. */
@Composable
private fun RuleAction.cardSubtitle(extractDataFieldCount: Int): String? = when (type) {
    ActionType.SNOOZE_NOTIFICATION -> snoozeSubtitle()
    ActionType.SAVE_DATA -> if (extractDataFieldCount == 0) {
        stringResource(R.string.do_extract_no_fields)
    } else {
        pluralStringResource(R.plurals.do_extract_field_count, extractDataFieldCount, extractDataFieldCount)
    }
    else -> null
}

/** Snooze action subtitle: a fixed duration, or the configured schedule. */
@Composable
private fun RuleAction.snoozeSubtitle(): String = when (getSnoozeMode()) {
    SnoozeMode.DURATION -> formatDurationMinutes(getSnoozeDurationMinutes())
    SnoozeMode.SCHEDULED -> {
        val schedule = getSnoozeSchedule()
        val start = "%02d:%02d".format(schedule?.startHour ?: 0, schedule?.startMinute ?: 0)
        val interval = schedule?.intervalMinutes
        val windowEndHour = schedule?.windowEndHour
        val windowEndMinute = schedule?.windowEndMinute
        if (interval != null && windowEndHour != null && windowEndMinute != null) {
            val end = "%02d:%02d".format(windowEndHour, windowEndMinute)
            stringResource(R.string.do_snooze_every, formatDurationMinutes(interval), start, end)
        } else {
            stringResource(R.string.do_snooze_until, start)
        }
    }
    SnoozeMode.THROTTLE -> stringResource(R.string.do_snooze_throttle, formatDurationMinutes(getThrottleWindowMinutes()))
}

@Composable
private fun ActionCard(
    action: RuleAction,
    subtitle: String?,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val actionLabel = stringResource(action.type.ui().labelRes)
    val configureLabel = stringResource(R.string.rule_editor_a11y_configure_action, actionLabel)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = configureLabel, role = Role.Button, onClick = onClick),
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
            ActionCardLabel(
                action = action,
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
            )

            ActionCardControls(
                actionLabel = actionLabel,
                isEnabled = action.isEnabled,
                onToggle = onToggle,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun ActionCardLabel(
    action: RuleAction,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val meta = action.type.ui()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = meta.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(meta.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (action.type.isBeta) {
                    Spacer(modifier = Modifier.width(6.dp))
                    BetaBadge()
                }
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionCardControls(
    actionLabel: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText = stringResource(
        if (isEnabled) R.string.rule_editor_a11y_state_enabled else R.string.rule_editor_a11y_state_disabled,
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // The switch is announced as "<action>, <Enabled|Disabled>, switch" and toggles from its whole 48dp area.
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .toggleable(value = isEnabled, role = Role.Switch, onValueChange = { onToggle() })
                .semantics {
                    contentDescription = actionLabel
                    stateDescription = stateText
                },
            contentAlignment = Alignment.Center,
        ) {
            Switch(checked = isEnabled, onCheckedChange = null)
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.rule_editor_a11y_remove_action, actionLabel),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(showBackground = true, name = "DoSection Light")
@Preview(showBackground = true, name = "DoSection Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DoSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        DoSection(
            actions = persistentListOf(
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
            extractDataFieldCount = 2,
            callbacks = ActionCardCallbacks(
                onToggle = { _, _ -> },
                onRemove = {},
                onEdit = {},
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoSectionEmptyPreview() {
    NotificappTheme {
        DoSection(
            actions = persistentListOf(),
            extractDataFieldCount = 2,
            callbacks = ActionCardCallbacks(
                onToggle = { _, _ -> },
                onRemove = {},
                onEdit = {},
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoSectionWithSnoozePreview() {
    NotificappTheme {
        DoSection(
            actions = persistentListOf(
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
            extractDataFieldCount = 2,
            callbacks = ActionCardCallbacks(
                onToggle = { _, _ -> },
                onRemove = {},
                onEdit = {},
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
