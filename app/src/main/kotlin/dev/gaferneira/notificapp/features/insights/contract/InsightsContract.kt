package dev.gaferneira.notificapp.features.insights.contract

import androidx.compose.runtime.Immutable
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.ActionBreakdownRow
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.model.TrendMetric
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class InsightsUiState(
    val period: InsightsPeriod = InsightsPeriod.DAYS_7,
    /**
     * Latest snapshot; kept while a period switch or retry is loading, so it may belong to an older
     * period than [period] (see [InsightsSummary.period]). Null until the first success.
     */
    val summary: InsightsSummary? = null,
    val isLoading: Boolean = true,
    /** Full-screen error; only set while there is no [summary] to show (later failures use [InsightsEffect.ShowError]). */
    val error: UiText? = null,
    /** Metric the Activity chart shows; switching is client-side, every series is in [summary]. */
    val chartMetric: TrendMetric = TrendMetric.NOTIFICATIONS,
    /** The per-rule action sheet; null while it is closed. */
    val actionBreakdown: ActionBreakdownUiState? = null,
) {
    /** True when there is nothing to show at all: no rules and no data in the period. */
    val isGlobalEmpty: Boolean
        get() = summary?.let {
            !it.hasAnyRules &&
                it.capturedCount == 0 &&
                it.handledCount == 0 &&
                it.actionsRunCount == 0 &&
                it.valuesExtractedCount == 0
        } ?: false
}

/** Loaded-on-demand content of the action breakdown sheet. [type] null means every action type. */
@Immutable
data class ActionBreakdownUiState(
    val type: ActionType?,
    val isLoading: Boolean = true,
    val rows: ImmutableList<ActionBreakdownRow> = persistentListOf(),
)

sealed interface InsightsEvent {
    data class OnPeriodChange(val period: InsightsPeriod) : InsightsEvent
    data object OnRetry : InsightsEvent
    data class OnChartMetricChange(val metric: TrendMetric) : InsightsEvent
    data object OnCapturedClick : InsightsEvent
    data object OnHandledClick : InsightsEvent
    data object OnValuesExtractedClick : InsightsEvent
    data class OnFieldTotalClick(val ruleId: String) : InsightsEvent
    data class OnRuleClick(val ruleId: String) : InsightsEvent
    data class OnTopAppClick(val packageName: String) : InsightsEvent
    data class OnActionClick(val type: ActionType) : InsightsEvent

    /** The "Actions run" tile: the breakdown for every action type, most failures first. */
    data object OnActionsRunTileClick : InsightsEvent
    data object OnActionBreakdownDismiss : InsightsEvent

    /** A rule row inside the breakdown sheet: closes it and opens the rule. */
    data class OnActionBreakdownRuleClick(val ruleId: String) : InsightsEvent
    data object OnWebhooksClick : InsightsEvent
    data object OnBrowseDataClick : InsightsEvent
    data object OnGoToRulesClick : InsightsEvent
}

sealed interface InsightsEffect {
    /**
     * [initialStatus] overrides the saved inbox filter and [since] (inclusive epoch millis) bounds the
     * list and [app] (package name) limits it to one source app, all for that visit only.
     */
    data class NavigateToInbox(
        val initialStatus: NotificationStatusFilter? = null,
        val since: Long? = null,
        val app: String? = null,
    ) : InsightsEffect

    /** [ruleId] limits the browser to one rule's data and [since] (inclusive epoch millis) to that window; null = no limit. */
    data class NavigateToDataBrowser(val ruleId: String? = null, val since: Long? = null) : InsightsEffect
    data class NavigateToRuleDetails(val ruleId: String) : InsightsEffect
    data object NavigateToWebhooks : InsightsEffect
    data object NavigateToRules : InsightsEffect

    /** A refresh failed while data is on screen: keep the dashboard and surface a transient message. */
    data class ShowError(val message: UiText) : InsightsEffect
}
