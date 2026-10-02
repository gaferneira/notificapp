package dev.gaferneira.notificapp.features.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplateInfo
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import kotlinx.collections.immutable.ImmutableList

private const val STEP_COUNT = 3
private val STEP_ICON_SIZE = 24.dp
private val STEP_GAP = 12.dp
private val CONNECTOR_WIDTH = 2.dp

/**
 * First-run checklist: notification access, monitored apps, first rule. Steps 1-2 are derived from
 * [MonitoringStatus] (no persisted state) and collapse into one-line rows; step 3 is the active
 * step and hosts the starter templates. It replaces the monitoring banner while no rule exists.
 *
 * Step 3 is deliberately not gated on steps 1-2: people can explore templates before granting access.
 */
@Composable
internal fun GetStartedSection(
    monitoring: MonitoringStatus,
    templates: ImmutableList<RuleTemplateInfo>,
    onEnableAccess: () -> Unit,
    onManageApps: () -> Unit,
    onCreateFromTemplate: (RuleTemplateInfo) -> Unit,
    onCreateFromScratch: () -> Unit,
    onSeeMoreTemplates: () -> Unit,
) {
    val hasApps = monitoring.monitoredAppCount > 0
    val completedSteps = listOf(monitoring.isListenerEnabled, hasApps).count { it }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChecklistHeader(completedSteps = completedSteps)

        TonalCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChecklistStepRow(
                    done = monitoring.isListenerEnabled,
                    title = stringResource(
                        if (monitoring.isListenerEnabled) R.string.home_checklist_access_done else R.string.home_checklist_access_todo,
                    ),
                    actionLabel = if (monitoring.isListenerEnabled) null else stringResource(R.string.home_checklist_access_action),
                    onClick = if (monitoring.isListenerEnabled) null else onEnableAccess,
                )
                ChecklistStepRow(
                    done = hasApps,
                    title = if (hasApps) {
                        pluralStringResource(R.plurals.home_banner_apps_monitored, monitoring.monitoredAppCount, monitoring.monitoredAppCount)
                    } else {
                        stringResource(R.string.home_checklist_apps_todo)
                    },
                    actionLabel = if (hasApps) null else stringResource(R.string.home_checklist_apps_action),
                    onClick = onManageApps,
                )
                ChecklistStepRow(
                    done = false,
                    current = true,
                    title = stringResource(R.string.home_checklist_rule_title),
                    actionLabel = null,
                    onClick = null,
                )
                CurrentStepContent(
                    templates = templates,
                    onCreateFromTemplate = onCreateFromTemplate,
                    onCreateFromScratch = onCreateFromScratch,
                    onSeeMoreTemplates = onSeeMoreTemplates,
                )
            }
        }
    }
}

/** Expanded body of the current step, indented under its icon and joined to it by a vertical connector. */
@Composable
private fun CurrentStepContent(
    templates: ImmutableList<RuleTemplateInfo>,
    onCreateFromTemplate: (RuleTemplateInfo) -> Unit,
    onCreateFromScratch: () -> Unit,
    onSeeMoreTemplates: () -> Unit,
) {
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .drawBehind {
                val x = STEP_ICON_SIZE.toPx() / 2
                drawLine(connectorColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = CONNECTOR_WIDTH.toPx())
            }
            .padding(start = STEP_ICON_SIZE + STEP_GAP, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_starter_rules_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StarterTemplates(
            templates = templates,
            onCreateFromTemplate = onCreateFromTemplate,
            onCreateFromScratch = onCreateFromScratch,
            onSeeMoreTemplates = onSeeMoreTemplates,
            cardStyle = NotificappStyles.innerPanelStyle,
            cardShape = MaterialTheme.shapes.medium,
        )
    }
}

@Composable
private fun ChecklistHeader(completedSteps: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionEyebrow(text = stringResource(R.string.home_checklist_title))
            Text(
                text = stringResource(R.string.home_checklist_progress, completedSteps, STEP_COUNT),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { completedSteps / STEP_COUNT.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * One-line checklist row. Tappable when [onClick] is set; shows [actionLabel] or a chevron as the
 * affordance. [current] marks the active step with the primary color and a bolder title.
 */
@Composable
private fun ChecklistStepRow(
    done: Boolean,
    title: String,
    actionLabel: String?,
    onClick: (() -> Unit)?,
    current: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(STEP_GAP),
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = when {
                done -> MaterialTheme.colorScheme.secondary
                current -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(STEP_ICON_SIZE),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.Bold else null,
            modifier = Modifier.weight(1f),
        )
        when {
            actionLabel != null -> Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )

            onClick != null -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
