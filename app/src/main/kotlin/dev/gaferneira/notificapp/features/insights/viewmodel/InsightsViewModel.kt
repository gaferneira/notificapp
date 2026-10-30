package dev.gaferneira.notificapp.features.insights.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.TrendMetric
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.domain.repository.InsightsRepository
import dev.gaferneira.notificapp.features.insights.contract.ActionBreakdownUiState
import dev.gaferneira.notificapp.features.insights.contract.InsightsEffect
import dev.gaferneira.notificapp.features.insights.contract.InsightsEvent
import dev.gaferneira.notificapp.features.insights.contract.InsightsUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository,
) : MviViewModel<InsightsUiState, InsightsEvent, InsightsEffect>(InsightsUiState()) {

    private val period = MutableStateFlow(InsightsPeriod.DAYS_7)

    /** Bumped by retry: the repository flow completes after a failure, so recovery means re-collecting. */
    private val reloadCount = MutableStateFlow(0)

    /** Collection feeding the open action sheet; cancelled when the sheet closes or the period changes. */
    private var breakdownJob: Job? = null

    init {
        observeSummary()
    }

    override fun onEvent(event: InsightsEvent) {
        when (event) {
            is InsightsEvent.OnPeriodChange -> changePeriod(event.period)
            InsightsEvent.OnRetry -> retry()
            is InsightsEvent.OnChartMetricChange -> changeChartMetric(event.metric)
            // Tiles and totals are window-bound, so their destinations get the same window start.
            InsightsEvent.OnCapturedClick -> sendEffect(InsightsEffect.NavigateToInbox(NotificationStatusFilter.ALL, windowStart()))
            InsightsEvent.OnHandledClick -> sendEffect(InsightsEffect.NavigateToInbox(NotificationStatusFilter.PROCESSED, windowStart()))
            InsightsEvent.OnValuesExtractedClick -> sendEffect(InsightsEffect.NavigateToDataBrowser(since = windowStart()))
            is InsightsEvent.OnFieldTotalClick -> sendEffect(InsightsEffect.NavigateToDataBrowser(event.ruleId, windowStart()))
            is InsightsEvent.OnTopAppClick -> sendEffect(InsightsEffect.NavigateToInbox(NotificationStatusFilter.ALL, windowStart(), event.packageName))
            is InsightsEvent.OnRuleClick -> sendEffect(InsightsEffect.NavigateToRuleDetails(event.ruleId))
            is InsightsEvent.OnActionClick,
            InsightsEvent.OnActionsRunTileClick,
            InsightsEvent.OnActionBreakdownDismiss,
            is InsightsEvent.OnActionBreakdownRuleClick,
            -> onBreakdownEvent(event)
            InsightsEvent.OnWebhooksClick -> sendEffect(InsightsEffect.NavigateToWebhooks)
            InsightsEvent.OnBrowseDataClick -> sendEffect(InsightsEffect.NavigateToDataBrowser())
            InsightsEvent.OnGoToRulesClick -> sendEffect(InsightsEffect.NavigateToRules)
        }
    }

    private fun onBreakdownEvent(event: InsightsEvent) {
        when (event) {
            is InsightsEvent.OnActionClick -> openActionBreakdown(event.type)
            InsightsEvent.OnActionsRunTileClick -> openActionBreakdown(null)
            InsightsEvent.OnActionBreakdownDismiss -> dismissActionBreakdown()
            is InsightsEvent.OnActionBreakdownRuleClick -> {
                dismissActionBreakdown()
                sendEffect(InsightsEffect.NavigateToRuleDetails(event.ruleId))
            }
            else -> Unit
        }
    }

    /** Start of the window the shown counts use; null only before the first summary (tiles are not shown then). */
    private fun windowStart(): Long? = uiState.value.summary?.windowStart

    private fun changePeriod(newPeriod: InsightsPeriod) {
        if (newPeriod == period.value) return
        dismissActionBreakdown() // its rows belong to the old period
        setState { copy(period = newPeriod, isLoading = true, error = null) }
        period.value = newPeriod
    }

    private fun changeChartMetric(metric: TrendMetric) {
        setState { copy(chartMetric = metric) }
    }

    private fun openActionBreakdown(type: ActionType?) {
        breakdownJob?.cancel()
        setState { copy(actionBreakdown = ActionBreakdownUiState(type = type)) }
        breakdownJob = viewModelScope.launch {
            insightsRepository.observeActionBreakdown(type, period.value).collect { result ->
                result
                    .onSuccess { rows -> setState { copy(actionBreakdown = ActionBreakdownUiState(type, isLoading = false, rows = rows)) } }
                    .onFailure { e ->
                        Timber.e(e, "Failed to load action breakdown")
                        dismissActionBreakdown()
                        sendEffect(InsightsEffect.ShowError(UiText.StringResource(R.string.insights_error_message)))
                    }
            }
        }
    }

    private fun dismissActionBreakdown() {
        breakdownJob?.cancel()
        breakdownJob = null
        setState { copy(actionBreakdown = null) }
    }

    private fun retry() {
        setState { copy(isLoading = true, error = null) }
        reloadCount.value += 1
    }

    /** Keeps an already loaded dashboard and reports through a snackbar; the full error state is only for "nothing to show". */
    private fun handleLoadFailure() {
        val message = UiText.StringResource(R.string.insights_error_message)
        if (uiState.value.summary == null) {
            setState { copy(isLoading = false, error = message) }
        } else {
            setState { copy(isLoading = false, error = null) }
            sendEffect(InsightsEffect.ShowError(message))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSummary() {
        viewModelScope.launch {
            combine(period, reloadCount) { selected, _ -> selected }
                .flatMapLatest { selected -> insightsRepository.observeSummary(selected) }
                .collect { result ->
                    result
                        .onSuccess { summary -> setState { copy(summary = summary, isLoading = false, error = null) } }
                        .onFailure { e ->
                            Timber.e(e, "Failed to load insights")
                            handleLoadFailure()
                        }
                }
        }
    }
}
