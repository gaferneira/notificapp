package dev.gaferneira.notificapp.features.insights.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.BarChart
import dev.gaferneira.notificapp.core.ui.components.SectionHeader
import dev.gaferneira.notificapp.core.ui.components.StatTile
import dev.gaferneira.notificapp.core.ui.components.StatTileTone
import dev.gaferneira.notificapp.core.ui.components.StatusPill
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.mapping.ui
import dev.gaferneira.notificapp.core.ui.text.currentLocale
import dev.gaferneira.notificapp.core.ui.text.formatCount
import dev.gaferneira.notificapp.core.ui.text.rememberIntegerFormat
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.CountBucket
import dev.gaferneira.notificapp.domain.model.FieldTotal
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.model.OutcomeCounts
import dev.gaferneira.notificapp.domain.model.RuleActivity
import dev.gaferneira.notificapp.domain.model.RuleRef
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.model.WebhookHealth
import dev.gaferneira.notificapp.features.insights.contract.InsightsEvent
import dev.gaferneira.notificapp.features.insights.contract.InsightsUiState
import dev.gaferneira.notificapp.util.timeAgo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import java.util.Date

/** All dashboard sections in display order; each one hides itself when it has nothing to show. */
@Composable
internal fun InsightsContent(uiState: InsightsUiState, summary: InsightsSummary, onEvent: (InsightsEvent) -> Unit) {
    PeriodToggle(selected = uiState.period, onSelect = { onEvent(InsightsEvent.OnPeriodChange(it)) })
    OverviewTiles(summary = summary, onEvent = onEvent)
    ActivitySection(summary = summary)
    FieldTotalsSection(totals = summary.fieldTotals, onClick = { onEvent(InsightsEvent.OnFieldTotalClick(it)) })
    RulesSection(
        topRules = summary.topRules,
        quietRules = summary.quietRules,
        onRuleClick = { onEvent(InsightsEvent.OnRuleClick(it)) },
    )
    ActionsSection(outcomes = summary.actionOutcomes)
    WebhooksSection(webhooks = summary.webhooks, onClick = { onEvent(InsightsEvent.OnWebhooksClick) })
    TopAppsSection(apps = summary.topApps)
    Button(
        onClick = { onEvent(InsightsEvent.OnBrowseDataClick) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.insights_browse_data))
    }
}

@Composable
internal fun PeriodToggle(selected: InsightsPeriod, onSelect: (InsightsPeriod) -> Unit, modifier: Modifier = Modifier) {
    val options = InsightsPeriod.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    text = stringResource(
                        if (period == InsightsPeriod.DAYS_7) R.string.insights_period_7_days else R.string.insights_period_30_days,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun OverviewTiles(summary: InsightsSummary, onEvent: (InsightsEvent) -> Unit, modifier: Modifier = Modifier) {
    val countFormat = rememberIntegerFormat()
    val failed = summary.actionsFailedCount
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = formatCount(summary.capturedCount, countFormat),
                label = stringResource(R.string.insights_tile_captured),
                icon = Icons.Outlined.Inbox,
                onClick = { onEvent(InsightsEvent.OnCapturedClick) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = formatCount(summary.handledCount, countFormat),
                label = stringResource(R.string.insights_tile_handled),
                icon = Icons.Outlined.NotificationsActive,
                onClick = { onEvent(InsightsEvent.OnHandledClick) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = formatCount(summary.actionsRunCount, countFormat),
                label = stringResource(R.string.insights_tile_actions_run),
                icon = Icons.Outlined.Bolt,
                supportingText = if (failed > 0) pluralStringResource(R.plurals.insights_tile_actions_failed, failed, failed) else null,
                supportingTone = StatTileTone.Error,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = formatCount(summary.valuesExtractedCount, countFormat),
                label = stringResource(R.string.insights_tile_values_extracted),
                icon = Icons.Outlined.DataObject,
                onClick = { onEvent(InsightsEvent.OnValuesExtractedClick) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
internal fun ActivitySection(summary: InsightsSummary, modifier: Modifier = Modifier) {
    if (summary.trend.isEmpty()) return
    // Labels and chart come from the period the data was computed for, which can lag the toggle while a switch loads.
    val period = summary.period
    val locale = currentLocale()
    val chart = remember(summary.trend, period, locale) { buildTrendChart(summary.trend, period, locale) }
    val description = if (chart.peakDayLabel == null) {
        stringResource(R.string.insights_chart_description_empty, period.days)
    } else {
        stringResource(R.string.insights_chart_description, period.days, chart.total, chart.peakCount, chart.peakDayLabel)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_activity))
        TonalCard {
            BarChart(entries = chart.entries, contentDescription = description, highlightLast = true)
        }
    }
}

@Composable
internal fun FieldTotalsSection(totals: ImmutableList<FieldTotal>, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (totals.isEmpty()) return
    val locale = currentLocale()
    val formats = remember(locale) { FieldSumFormats(locale) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_totals))
        TonalCard {
            totals.forEachIndexed { index, total ->
                if (index > 0) HorizontalDivider()
                InsightsRow(
                    title = stringResource(R.string.insights_total_title, total.ruleName, total.fieldName),
                    subtitle = pluralStringResource(R.plurals.insights_values_count, total.count, total.count),
                    trailing = formats.format(total.sum, total.fieldType, total.unit),
                    // Deleted rule (null id): history is still counted but there is nothing to open.
                    onClick = total.ruleId?.let { id -> { onClick(id) } },
                )
            }
        }
    }
}

@Composable
internal fun RulesSection(
    topRules: ImmutableList<RuleActivity>,
    quietRules: ImmutableList<RuleRef>,
    onRuleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (topRules.isEmpty() && quietRules.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_rules))
        TonalCard {
            topRules.forEachIndexed { index, rule ->
                if (index > 0) HorizontalDivider()
                InsightsRow(
                    title = rule.ruleName,
                    trailing = pluralStringResource(R.plurals.insights_rule_runs, rule.runCount, rule.runCount),
                    onClick = rule.ruleId?.let { id -> { onRuleClick(id) } },
                )
            }
            if (quietRules.isNotEmpty()) {
                if (topRules.isNotEmpty()) HorizontalDivider()
                Text(
                    text = stringResource(R.string.insights_quiet_rules_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                quietRules.forEach { rule ->
                    InsightsRow(
                        title = rule.ruleName,
                        subtitle = stringResource(R.string.insights_quiet_rule_hint),
                        onClick = { onRuleClick(rule.ruleId) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActionsSection(outcomes: ImmutableMap<ActionType, OutcomeCounts>, modifier: Modifier = Modifier) {
    if (outcomes.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_actions))
        TonalCard {
            outcomes.entries.forEachIndexed { index, (type, counts) ->
                if (index > 0) HorizontalDivider()
                val typeUi = type.ui()
                InsightsRow(
                    title = stringResource(typeUi.labelRes),
                    subtitle = stringResource(R.string.insights_action_outcomes, counts.success, counts.failed, counts.skipped),
                    icon = typeUi.icon,
                )
            }
        }
    }
}

@Composable
internal fun WebhooksSection(webhooks: ImmutableList<WebhookHealth>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (webhooks.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_webhooks))
        TonalCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
            webhooks.forEachIndexed { index, webhook ->
                if (index > 0) HorizontalDivider()
                WebhookRow(webhook)
            }
        }
    }
}

@Composable
private fun WebhookRow(webhook: WebhookHealth) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = webhook.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (icon, label) = webhookStatusPill(webhook.lastStatus)
            StatusPill(icon = icon, text = label)
            webhook.lastDeliveryAt?.let { at ->
                Text(
                    text = stringResource(R.string.insights_webhook_last_delivery, Date(at).timeAgo()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (webhook.pendingRetries > 0) {
            Text(
                text = pluralStringResource(R.plurals.insights_webhook_pending_retries, webhook.pendingRetries, webhook.pendingRetries),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun webhookStatusPill(status: WebhookDeliveryStatus): Pair<ImageVector, String> = when (status) {
    WebhookDeliveryStatus.DELIVERED -> Icons.Default.CheckCircle to stringResource(R.string.insights_webhook_delivered)
    WebhookDeliveryStatus.CONFIG_ERROR -> Icons.Default.Error to stringResource(R.string.insights_webhook_config_error)
    WebhookDeliveryStatus.UNREACHABLE -> Icons.Default.WarningAmber to stringResource(R.string.insights_webhook_unreachable)
    WebhookDeliveryStatus.UNKNOWN -> Icons.Default.Info to stringResource(R.string.insights_webhook_no_deliveries)
}

@Composable
internal fun TopAppsSection(apps: ImmutableList<CountBucket>, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) return
    val countFormat = rememberIntegerFormat()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_top_apps))
        TonalCard {
            apps.forEachIndexed { index, app ->
                if (index > 0) HorizontalDivider()
                InsightsRow(title = app.label, trailing = formatCount(app.count, countFormat))
            }
        }
    }
}

/** A title (+ optional subtitle) row with an optional leading icon and trailing value; clickable when [onClick] is set. */
@Composable
private fun InsightsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
