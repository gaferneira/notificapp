package dev.gaferneira.notificapp.features.notificationdetail.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.DryRunBadge
import dev.gaferneira.notificapp.core.ui.components.StatusPill
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.mapping.ui
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExecutionWithDetails
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExtractedFieldDisplay
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.TriggeredActionDisplay
import dev.gaferneira.notificapp.util.timeAgo
import java.util.Date

/**
 * One stored rule execution: rule name (tappable to open the rule unless it was deleted), dry-run
 * and redaction notes, the extracted fields and the actions with their outcomes.
 */
@Composable
internal fun ExecutionCard(
    details: ExecutionWithDetails,
    onOpenRule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = details.ruleName?.let { stringResource(R.string.notification_detail_open_rule_label, it) }
    TonalCard(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .then(
                if (openLabel != null) {
                    Modifier.clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpenRule)
                } else {
                    Modifier
                },
            ),
    ) {
        ExecutionHeader(details = details, isTappable = openLabel != null)
        ExecutionNotes(details)
        if (details.extractedFields.isNotEmpty()) {
            SectionLabel(R.string.notification_detail_extracted_data)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                details.extractedFields.forEach { ExtractedFieldRow(it) }
            }
        }
        if (details.triggeredActions.isNotEmpty()) {
            SectionLabel(R.string.notification_detail_actions)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details.triggeredActions.forEach { ActionRow(it, wasDryRun = details.execution.wasDryRun) }
            }
        }
    }
}

@Composable
private fun ExecutionHeader(details: ExecutionWithDetails, isTappable: Boolean) {
    val ruleName = details.ruleName
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            if (ruleName != null) {
                Text(
                    text = ruleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = stringResource(R.string.notification_detail_deleted_rule),
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = remember(details.execution.createdAt, locale) {
                    Date(details.execution.createdAt).timeAgo(locale = locale)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isTappable) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExecutionNotes(details: ExecutionWithDetails) {
    if (!details.execution.wasDryRun && !details.wasRedactionSource) return
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (details.execution.wasDryRun) {
            DryRunBadge()
            Text(
                text = stringResource(R.string.notification_detail_dry_run_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (details.wasRedactionSource) {
            StatusPill(
                icon = Icons.Default.VisibilityOff,
                text = stringResource(R.string.notification_detail_removed_content_note),
            )
        }
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** Name and neutral type chip on top (wrapping), value below so long values and large fonts stay readable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtractedFieldRow(field: ExtractedFieldDisplay, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = field.fieldName ?: stringResource(R.string.notification_detail_removed_field),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontStyle = if (field.isFieldDeleted) FontStyle.Italic else null,
            )
            if (!field.isFieldDeleted) {
                TypeChip(stringResource(field.fieldType.labelRes()))
            }
        }
        SelectionContainer {
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TypeChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(action: TriggeredActionDisplay, wasDryRun: Boolean, modifier: Modifier = Modifier) {
    val ui = action.type?.ui()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ui != null) {
                Icon(
                    imageVector = ui.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(ui?.labelRes ?: R.string.notification_detail_removed_action),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (ui == null) FontStyle.Italic else null,
            )
        }
        // A dry run never executes anything, so a missing outcome is expected, not "unknown".
        if (action.outcome != null || !wasDryRun) {
            OutcomeLabel(action.outcome)
        }
    }
}

/** Icon plus text, so the outcome never depends on color alone. */
@Composable
private fun OutcomeLabel(outcome: ActionOutcome?, modifier: Modifier = Modifier) {
    val color = when (outcome) {
        ActionOutcome.SUCCESS -> MaterialTheme.colorScheme.primary
        ActionOutcome.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = outcome.icon(),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(outcome.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
