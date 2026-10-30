package dev.gaferneira.notificapp.features.insights.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.AppIcon
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
import dev.gaferneira.notificapp.domain.model.AppActivity
import dev.gaferneira.notificapp.domain.model.FieldTotal
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.model.OutcomeCounts
import dev.gaferneira.notificapp.domain.model.RuleActivity
import dev.gaferneira.notificapp.domain.model.RuleRef
import dev.gaferneira.notificapp.domain.model.TrendMetric
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.model.WebhookHealth
import dev.gaferneira.notificapp.features.insights.contract.InsightsEvent
import dev.gaferneira.notificapp.features.insights.contract.InsightsUiState
import dev.gaferneira.notificapp.util.timeAgo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import java.text.NumberFormat
import java.util.Date

/** All dashboard sections in display order; each one hides itself when it has nothing to show. */
@Composable
internal fun InsightsContent(uiState: InsightsUiState, summary: InsightsSummary, onEvent: (InsightsEvent) -> Unit) {
    PeriodToggle(selected = uiState.period, onSelect = { onEvent(InsightsEvent.OnPeriodChange(it)) })
    OverviewTiles(summary = summary, onEvent = onEvent)
    ActivitySection(
        summary = summary,
        metric = uiState.chartMetric,
        onMetricChange = { onEvent(InsightsEvent.OnChartMetricChange(it)) },
    )
    TopAppsSection(apps = summary.topApps, onAppClick = { onEvent(InsightsEvent.OnTopAppClick(it)) })
    FieldTotalsSection(totals = summary.fieldTotals, onClick = { onEvent(InsightsEvent.OnFieldTotalClick(it)) })
    RulesSection(
        topRules = summary.topRules,
        quietRules = summary.quietRules,
        onRuleClick = { onEvent(InsightsEvent.OnRuleClick(it)) },
    )
    ActionsSection(outcomes = summary.actionOutcomes, onActionClick = { onEvent(InsightsEvent.OnActionClick(it)) })
    WebhooksSection(webhooks = summary.webhooks, onClick = { onEvent(InsightsEvent.OnWebhooksClick) })
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
                onClick = if (summary.actionsRunCount > 0) {
                    { onEvent(InsightsEvent.OnActionsRunTileClick) }
                } else {
                    null
                },
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

/**
 * The wording that depends on what the chart counts. Kept apart from [ActivitySection] so another
 * metric only needs its own instance.
 */
private data class ChartTexts(
    @StringRes val chipLabel: Int,
    @StringRes val subtitle: Int,
    @StringRes val summaryEmpty: Int,
    @StringRes val description: Int,
    @StringRes val descriptionEmpty: Int,
    @PluralsRes val selection: Int,
)

private val NotificationsChartTexts = ChartTexts(
    chipLabel = R.string.insights_chart_metric_notifications,
    subtitle = R.string.insights_chart_subtitle_notifications,
    summaryEmpty = R.string.insights_chart_summary_empty_notifications,
    description = R.string.insights_chart_description_notifications,
    descriptionEmpty = R.string.insights_chart_description_empty_notifications,
    selection = R.plurals.insights_chart_selection_notifications,
)

private val HandledChartTexts = ChartTexts(
    chipLabel = R.string.insights_chart_metric_handled,
    subtitle = R.string.insights_chart_subtitle_handled,
    summaryEmpty = R.string.insights_chart_summary_empty_handled,
    description = R.string.insights_chart_description_handled,
    descriptionEmpty = R.string.insights_chart_description_empty_handled,
    selection = R.plurals.insights_chart_selection_handled,
)

private val RuleRunsChartTexts = ChartTexts(
    chipLabel = R.string.insights_chart_metric_rule_runs,
    subtitle = R.string.insights_chart_subtitle_rule_runs,
    summaryEmpty = R.string.insights_chart_summary_empty,
    description = R.string.insights_chart_description,
    descriptionEmpty = R.string.insights_chart_description_empty,
    selection = R.plurals.insights_chart_selection_rule_runs,
)

private val ActionsFailedChartTexts = ChartTexts(
    chipLabel = R.string.insights_chart_metric_actions_failed,
    subtitle = R.string.insights_chart_subtitle_actions_failed,
    summaryEmpty = R.string.insights_chart_summary_empty_actions_failed,
    description = R.string.insights_chart_description_actions_failed,
    descriptionEmpty = R.string.insights_chart_description_empty_actions_failed,
    selection = R.plurals.insights_chart_selection_actions_failed,
)

private fun TrendMetric.texts(): ChartTexts = when (this) {
    TrendMetric.NOTIFICATIONS -> NotificationsChartTexts
    TrendMetric.HANDLED -> HandledChartTexts
    TrendMetric.RULE_RUNS -> RuleRunsChartTexts
    TrendMetric.ACTIONS_FAILED -> ActionsFailedChartTexts
}

@Composable
internal fun ActivitySection(
    summary: InsightsSummary,
    metric: TrendMetric,
    onMetricChange: (TrendMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trend = summary.trends[metric]
    if (trend.isNullOrEmpty()) return
    // Labels and chart come from the period the data was computed for, which can lag the toggle while a switch loads.
    val period = summary.period
    val locale = currentLocale()
    val resources = LocalContext.current.resources
    val texts = metric.texts()
    val chart = remember(trend, period, locale) { buildTrendChart(trend, period, locale) }
    val averageFormat = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 } }
    val countFormat = rememberIntegerFormat()
    val description = if (chart.peakDayLabel == null) {
        stringResource(texts.descriptionEmpty, period.days)
    } else {
        stringResource(texts.description, period.days, chart.total, chart.peakCount, chart.peakDayLabel)
    }
    val summaryLine = if (chart.peakDayLabel == null) {
        stringResource(texts.summaryEmpty)
    } else {
        stringResource(
            R.string.insights_chart_summary,
            formatCount(chart.total, countFormat),
            averageFormat.format(chart.average),
            formatCount(chart.peakCount, countFormat),
            chart.peakDayLabel,
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_activity), subtitle = stringResource(texts.subtitle))
        MetricChips(selected = metric, onSelect = onMetricChange)
        TonalCard {
            Text(
                text = summaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            // Keyed on the metric so a bar selected on one series is not carried over to the next.
            key(metric) {
                BarChart(
                    entries = chart.entries,
                    contentDescription = description,
                    maxLabel = if (chart.peakCount > 0) stringResource(R.string.insights_chart_max, formatCount(chart.peakCount, countFormat)) else null,
                    selectionLabel = { entry ->
                        resources.getQuantityString(texts.selection, entry.value, entry.value, entry.description)
                    },
                )
            }
        }
    }
}

/** One chip per [TrendMetric]; scrolls sideways because four labels can overflow narrow screens or large fonts. */
@Composable
private fun MetricChips(selected: TrendMetric, onSelect: (TrendMetric) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrendMetric.entries.forEach { metric ->
            FilterChip(
                selected = metric == selected,
                onClick = { onSelect(metric) },
                label = { Text(stringResource(metric.texts().chipLabel), maxLines = 1) },
            )
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
internal fun ActionsSection(
    outcomes: ImmutableMap<ActionType, OutcomeCounts>,
    onActionClick: (ActionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (outcomes.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_actions))
        TonalCard {
            outcomes.entries.forEachIndexed { index, (type, counts) ->
                if (index > 0) HorizontalDivider()
                val typeUi = type.ui()
                InsightsRow(
                    title = stringResource(typeUi.labelRes),
                    annotatedSubtitle = outcomeCountsText(counts),
                    icon = typeUi.icon,
                    onClick = { onActionClick(type) },
                )
            }
        }
    }
}

/** Only the non-zero outcome counts, joined with a dot; the failed count is highlighted in the error color. */
@Composable
internal fun outcomeCountsText(counts: OutcomeCounts, prefix: String? = null): AnnotatedString {
    val errorColor = MaterialTheme.colorScheme.error
    val succeeded = if (counts.success > 0) pluralStringResource(R.plurals.insights_outcome_succeeded, counts.success, counts.success) else null
    val failed = if (counts.failed > 0) pluralStringResource(R.plurals.insights_tile_actions_failed, counts.failed, counts.failed) else null
    val skipped = if (counts.skipped > 0) pluralStringResource(R.plurals.insights_outcome_skipped, counts.skipped, counts.skipped) else null
    return buildAnnotatedString {
        val parts = listOfNotNull(prefix, succeeded, failed, skipped)
        parts.forEachIndexed { index, part ->
            if (index > 0) append(" · ")
            if (part == failed) {
                withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.SemiBold)) { append(part) }
            } else {
                append(part)
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
internal fun TopAppsSection(apps: ImmutableList<AppActivity>, onAppClick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) return
    val countFormat = rememberIntegerFormat()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = stringResource(R.string.insights_section_top_apps))
        TonalCard {
            apps.forEachIndexed { index, app ->
                if (index > 0) HorizontalDivider()
                InsightsRow(
                    title = app.appName,
                    trailing = formatCount(app.count, countFormat),
                    leading = { AppIcon(packageName = app.packageName, appName = app.appName, size = 32.dp) },
                    onClick = { onAppClick(app.packageName) },
                )
            }
        }
    }
}

/** A title (+ optional subtitle, plain or [annotatedSubtitle]) row with an optional leading icon (or custom [leading] content) and trailing value; clickable when [onClick] is set. */
@Composable
internal fun InsightsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    annotatedSubtitle: AnnotatedString? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
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
        if (leading != null) {
            leading()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val supporting = annotatedSubtitle ?: subtitle?.let { AnnotatedString(it) }
            if (supporting != null) {
                Text(
                    text = supporting,
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
