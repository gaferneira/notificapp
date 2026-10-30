package dev.gaferneira.notificapp.features.insights.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.ActionBreakdownRow
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.OutcomeCounts
import dev.gaferneira.notificapp.domain.model.TrendMetric
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.features.insights.contract.ActionBreakdownUiState
import dev.gaferneira.notificapp.features.insights.contract.InsightsEffect
import dev.gaferneira.notificapp.features.insights.contract.InsightsEvent
import dev.gaferneira.notificapp.testutil.fakes.FakeInsightsRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeInsightsRepository.Companion.WINDOW_START
import dev.gaferneira.notificapp.testutil.fakes.FakeInsightsRepository.Companion.emptySummary
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeInsightsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeInsightsRepository(populated(captured = 10))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun populated(captured: Int) = emptySummary(hasAnyRules = true).copy(capturedCount = captured)

    private fun createViewModel() = InsightsViewModel(repository)

    @Test
    fun `initial load observes DAYS_7 and exposes the summary`() = runTest(testDispatcher) {
        // Given / When
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        repository.requestedPeriods shouldBe listOf(InsightsPeriod.DAYS_7)
        val state = viewModel.uiState.value
        state.period shouldBe InsightsPeriod.DAYS_7
        state.isLoading shouldBe false
        state.error shouldBe null
        state.summary?.capturedCount shouldBe 10
    }

    @Test
    fun `period change requests DAYS_30 and shows its data`() = runTest(testDispatcher) {
        // Given
        repository.setSummary(populated(captured = 99), InsightsPeriod.DAYS_30)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onEvent(InsightsEvent.OnPeriodChange(InsightsPeriod.DAYS_30))
        advanceUntilIdle()

        // Then
        repository.requestedPeriods shouldBe listOf(InsightsPeriod.DAYS_7, InsightsPeriod.DAYS_30)
        viewModel.uiState.value.period shouldBe InsightsPeriod.DAYS_30
        viewModel.uiState.value.summary?.capturedCount shouldBe 99
    }

    @Test
    fun `selecting the current period does not reload`() = runTest(testDispatcher) {
        // Given
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onEvent(InsightsEvent.OnPeriodChange(InsightsPeriod.DAYS_7))
        advanceUntilIdle()

        // Then
        repository.requestedPeriods shouldBe listOf(InsightsPeriod.DAYS_7)
    }

    @Test
    fun `failure sets the error state and stops loading`() = runTest(testDispatcher) {
        // Given
        repository.setFailure(IllegalStateException("db down"))

        // When
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        state.isLoading shouldBe false
        state.summary shouldBe null
        state.error shouldBe UiText.StringResource(R.string.insights_error_message)
    }

    @Test
    fun `retry re-collects and recovers from a failure`() = runTest(testDispatcher) {
        // Given
        repository.setFailure(IllegalStateException("db down"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.setSummary(populated(captured = 7))

        // When
        viewModel.onEvent(InsightsEvent.OnRetry)
        advanceUntilIdle()

        // Then
        repository.requestedPeriods.size shouldBe 2
        val state = viewModel.uiState.value
        state.error shouldBe null
        state.isLoading shouldBe false
        state.summary?.capturedCount shouldBe 7
    }

    @Test
    fun `live updates from the repository replace the summary`() = runTest(testDispatcher) {
        // Given
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        repository.setSummary(populated(captured = 11))
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.summary?.capturedCount shouldBe 11
    }

    @Test
    fun `the handled count is exposed from the summary`() = runTest(testDispatcher) {
        // Given
        repository.setSummary(populated(captured = 10).copy(handledCount = 4))

        // When
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.summary?.handledCount shouldBe 4
    }

    @Test
    fun `only handled notifications are not the global empty state`() = runTest(testDispatcher) {
        // Given
        repository.setSummary(emptySummary(hasAnyRules = false).copy(handledCount = 2))

        // When
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.isGlobalEmpty shouldBe false
    }

    @Test
    fun `window-bound tiles navigate with the window start of the shown summary`() = runTest(testDispatcher) {
        // Given a 30-day summary with its own window start
        val thirtyDayStart = WINDOW_START - 23L * 24 * 60 * 60 * 1000
        repository.setSummary(populated(captured = 1).copy(windowStart = thirtyDayStart), InsightsPeriod.DAYS_30)
        val viewModel = createViewModel()
        viewModel.onEvent(InsightsEvent.OnPeriodChange(InsightsPeriod.DAYS_30))
        advanceUntilIdle()

        viewModel.effect.test {
            // When
            viewModel.onEvent(InsightsEvent.OnHandledClick)
            advanceUntilIdle()

            // Then
            awaitItem() shouldBe InsightsEffect.NavigateToInbox(NotificationStatusFilter.PROCESSED, since = thirtyDayStart)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `click events emit the matching navigation effects`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val expectations = listOf(
            InsightsEvent.OnCapturedClick to InsightsEffect.NavigateToInbox(NotificationStatusFilter.ALL, since = WINDOW_START),
            InsightsEvent.OnHandledClick to InsightsEffect.NavigateToInbox(NotificationStatusFilter.PROCESSED, since = WINDOW_START),
            InsightsEvent.OnValuesExtractedClick to InsightsEffect.NavigateToDataBrowser(since = WINDOW_START),
            InsightsEvent.OnFieldTotalClick("rule-1") to InsightsEffect.NavigateToDataBrowser("rule-1", since = WINDOW_START),
            InsightsEvent.OnTopAppClick("com.bank") to InsightsEffect.NavigateToInbox(NotificationStatusFilter.ALL, since = WINDOW_START, app = "com.bank"),
            InsightsEvent.OnRuleClick("rule-2") to InsightsEffect.NavigateToRuleDetails("rule-2"),
            InsightsEvent.OnWebhooksClick to InsightsEffect.NavigateToWebhooks,
            InsightsEvent.OnBrowseDataClick to InsightsEffect.NavigateToDataBrowser(),
            InsightsEvent.OnGoToRulesClick to InsightsEffect.NavigateToRules,
        )

        viewModel.effect.test {
            expectations.forEach { (event, effect) ->
                // When
                viewModel.onEvent(event)
                advanceUntilIdle()

                // Then
                awaitItem() shouldBe effect
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no rules and no data is the global empty state`() = runTest(testDispatcher) {
        // Given
        repository.setSummary(emptySummary(hasAnyRules = false))

        // When
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.isGlobalEmpty shouldBe true
    }

    @Test
    fun `existing rules or any data is not the global empty state`() = runTest(testDispatcher) {
        // Given a rule exists but nothing happened yet
        repository.setSummary(emptySummary(hasAnyRules = true))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.uiState.value.isGlobalEmpty shouldBe false

        // When data exists without rules (e.g. rules deleted after capturing)
        repository.setSummary(emptySummary(hasAnyRules = false).copy(capturedCount = 3))
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.isGlobalEmpty shouldBe false
    }

    @Test
    fun `summary and its period are kept while a period switch is loading`() = runTest(testDispatcher) {
        // Given a loaded DAYS_7 dashboard
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When the user switches period and the new data has not arrived yet
        viewModel.onEvent(InsightsEvent.OnPeriodChange(InsightsPeriod.DAYS_30))

        // Then the toggle moves but the old snapshot keeps its own period
        val state = viewModel.uiState.value
        state.period shouldBe InsightsPeriod.DAYS_30
        state.isLoading shouldBe true
        state.summary?.period shouldBe InsightsPeriod.DAYS_7
        state.summary?.capturedCount shouldBe 10

        // And once it arrives the snapshot follows the new period
        advanceUntilIdle()
        viewModel.uiState.value.summary?.period shouldBe InsightsPeriod.DAYS_30
        viewModel.uiState.value.isLoading shouldBe false
    }

    @Test
    fun `failure after a successful load keeps the summary and emits the snackbar effect`() = runTest(testDispatcher) {
        // Given a loaded dashboard
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effect.test {
            // When a later emission fails
            repository.setFailure(IllegalStateException("db down"))
            advanceUntilIdle()

            // Then the dashboard stays and the error goes out as a one-off effect
            awaitItem() shouldBe InsightsEffect.ShowError(UiText.StringResource(R.string.insights_error_message))
            cancelAndIgnoreRemainingEvents()
        }
        val state = viewModel.uiState.value
        state.summary?.capturedCount shouldBe 10
        state.error shouldBe null
        state.isLoading shouldBe false
    }

    @Test
    fun `chart metric defaults to notifications and switches without reloading`() = runTest(testDispatcher) {
        // Given
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.uiState.value.chartMetric shouldBe TrendMetric.NOTIFICATIONS

        // When
        viewModel.onEvent(InsightsEvent.OnChartMetricChange(TrendMetric.ACTIONS_FAILED))
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.chartMetric shouldBe TrendMetric.ACTIONS_FAILED
        repository.requestedPeriods shouldBe listOf(InsightsPeriod.DAYS_7)
    }

    @Test
    fun `clicking an action loads its breakdown into the sheet state`() = runTest(testDispatcher) {
        // Given
        val rows = listOf(ActionBreakdownRow(ActionType.SEND_WEBHOOK, "r1", "Alpha", OutcomeCounts(success = 2, failed = 1)))
        repository.setBreakdown(rows)
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onEvent(InsightsEvent.OnActionClick(ActionType.SEND_WEBHOOK))
        advanceUntilIdle()

        // Then
        repository.requestedBreakdowns shouldBe listOf(ActionType.SEND_WEBHOOK to InsightsPeriod.DAYS_7)
        val sheet = viewModel.uiState.value.actionBreakdown
        sheet?.type shouldBe ActionType.SEND_WEBHOOK
        sheet?.isLoading shouldBe false
        sheet?.rows shouldBe rows
    }

    @Test
    fun `the sheet is loading until the breakdown arrives`() = runTest(testDispatcher) {
        // Given
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onEvent(InsightsEvent.OnActionClick(ActionType.SAVE_DATA))

        // Then
        viewModel.uiState.value.actionBreakdown shouldBe ActionBreakdownUiState(type = ActionType.SAVE_DATA, isLoading = true)
    }

    @Test
    fun `the Actions run tile opens the breakdown for every type`() = runTest(testDispatcher) {
        // Given
        val viewModel = createViewModel()
        advanceUntilIdle()

        // When
        viewModel.onEvent(InsightsEvent.OnActionsRunTileClick)
        advanceUntilIdle()

        // Then
        repository.requestedBreakdowns shouldBe listOf(null to InsightsPeriod.DAYS_7)
        viewModel.uiState.value.actionBreakdown?.type shouldBe null
        viewModel.uiState.value.actionBreakdown?.isLoading shouldBe false
    }

    @Test
    fun `dismissing the sheet closes it and stops collecting`() = runTest(testDispatcher) {
        // Given an open sheet
        val viewModel = createViewModel()
        viewModel.onEvent(InsightsEvent.OnActionsRunTileClick)
        advanceUntilIdle()
        repository.activeBreakdownCollectors shouldBe 1

        // When
        viewModel.onEvent(InsightsEvent.OnActionBreakdownDismiss)
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.actionBreakdown shouldBe null
        repository.activeBreakdownCollectors shouldBe 0
    }

    @Test
    fun `tapping a rule in the sheet closes it and opens the rule`() = runTest(testDispatcher) {
        // Given an open sheet
        val viewModel = createViewModel()
        viewModel.onEvent(InsightsEvent.OnActionsRunTileClick)
        advanceUntilIdle()

        viewModel.effect.test {
            // When
            viewModel.onEvent(InsightsEvent.OnActionBreakdownRuleClick("rule-7"))
            advanceUntilIdle()

            // Then
            awaitItem() shouldBe InsightsEffect.NavigateToRuleDetails("rule-7")
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.uiState.value.actionBreakdown shouldBe null
        repository.activeBreakdownCollectors shouldBe 0
    }

    @Test
    fun `changing the period while the sheet is open closes it`() = runTest(testDispatcher) {
        // Given an open sheet
        val viewModel = createViewModel()
        viewModel.onEvent(InsightsEvent.OnActionsRunTileClick)
        advanceUntilIdle()

        // When
        viewModel.onEvent(InsightsEvent.OnPeriodChange(InsightsPeriod.DAYS_30))
        advanceUntilIdle()

        // Then
        viewModel.uiState.value.actionBreakdown shouldBe null
        repository.activeBreakdownCollectors shouldBe 0
    }

    @Test
    fun `a breakdown failure closes the sheet and reports the error`() = runTest(testDispatcher) {
        // Given
        repository.setBreakdownFailure(IllegalStateException("db down"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effect.test {
            // When
            viewModel.onEvent(InsightsEvent.OnActionsRunTileClick)
            advanceUntilIdle()

            // Then
            awaitItem() shouldBe InsightsEffect.ShowError(UiText.StringResource(R.string.insights_error_message))
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.uiState.value.actionBreakdown shouldBe null
    }

    @Test
    fun `loading state is the initial state before the first emission`() {
        val viewModel = createViewModel()

        viewModel.uiState.value.isLoading shouldBe true
        viewModel.uiState.value.summary shouldBe null
    }
}
