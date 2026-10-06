package dev.gaferneira.notificapp.features.insights.contract

import androidx.compose.runtime.Immutable
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter

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

sealed interface InsightsEvent {
    data class OnPeriodChange(val period: InsightsPeriod) : InsightsEvent
    data object OnRetry : InsightsEvent
    data object OnCapturedClick : InsightsEvent
    data object OnHandledClick : InsightsEvent
    data object OnValuesExtractedClick : InsightsEvent
    data class OnFieldTotalClick(val ruleId: String) : InsightsEvent
    data class OnRuleClick(val ruleId: String) : InsightsEvent
    data object OnWebhooksClick : InsightsEvent
    data object OnBrowseDataClick : InsightsEvent
    data object OnGoToRulesClick : InsightsEvent
}

sealed interface InsightsEffect {
    /**
     * [initialStatus] overrides the saved inbox filter and [since] (inclusive epoch millis) bounds the
     * list, both for that visit only.
     */
    data class NavigateToInbox(val initialStatus: NotificationStatusFilter? = null, val since: Long? = null) : InsightsEffect

    /** [ruleId] limits the browser to one rule's data and [since] (inclusive epoch millis) to that window; null = no limit. */
    data class NavigateToDataBrowser(val ruleId: String? = null, val since: Long? = null) : InsightsEffect
    data class NavigateToRuleDetails(val ruleId: String) : InsightsEffect
    data object NavigateToWebhooks : InsightsEffect
    data object NavigateToRules : InsightsEffect

    /** A refresh failed while data is on screen: keep the dashboard and surface a transient message. */
    data class ShowError(val message: UiText) : InsightsEffect
}
