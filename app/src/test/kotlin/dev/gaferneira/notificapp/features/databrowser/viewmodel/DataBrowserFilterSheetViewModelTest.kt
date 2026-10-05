package dev.gaferneira.notificapp.features.databrowser.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataSort
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiEvent
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataBrowserFilterSheetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val applied = DataBrowserFilter(
        ruleIds = listOf("r1"),
        packageNames = listOf("com.a"),
        searchQuery = "invoice",
        sort = DataSort.RULE_ASC,
    )

    @Test
    fun `Init hydrates the draft from the applied filter`() {
        val viewModel = DataBrowserFilterSheetViewModel()

        viewModel.onEvent(UiEvent.Init(applied))

        viewModel.uiState.value.draft shouldBe applied
    }

    @Test
    fun `a second Init does not wipe unapplied edits`() {
        val viewModel = DataBrowserFilterSheetViewModel()
        viewModel.onEvent(UiEvent.Init(applied))
        viewModel.onEvent(UiEvent.OnRuleToggle("r2"))

        viewModel.onEvent(UiEvent.Init(applied))

        viewModel.uiState.value.draft.ruleIds shouldBe listOf("r1", "r2")
    }

    @Test
    fun `toggling a rule and an app adds then removes them`() {
        val viewModel = DataBrowserFilterSheetViewModel()
        viewModel.onEvent(UiEvent.Init(DataBrowserFilter()))

        viewModel.onEvent(UiEvent.OnRuleToggle("r1"))
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.draft shouldBe DataBrowserFilter(ruleIds = listOf("r1"), packageNames = listOf("com.a"))

        viewModel.onEvent(UiEvent.OnRuleToggle("r1"))
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.draft shouldBe DataBrowserFilter()
    }

    @Test
    fun `setting and clearing the date range edits both bounds`() {
        val viewModel = DataBrowserFilterSheetViewModel()
        viewModel.onEvent(UiEvent.Init(DataBrowserFilter()))

        viewModel.onEvent(UiEvent.OnDateRangeChange(dateFrom = 100L, dateTo = 200L))
        viewModel.uiState.value.draft.dateFrom shouldBe 100L
        viewModel.uiState.value.draft.dateTo shouldBe 200L

        viewModel.onEvent(UiEvent.OnDateRangeClear)
        viewModel.uiState.value.draft.dateFrom shouldBe null
        viewModel.uiState.value.draft.dateTo shouldBe null
    }

    @Test
    fun `ClearAll resets rule, app and date but keeps search and sort`() {
        val viewModel = DataBrowserFilterSheetViewModel()
        viewModel.onEvent(UiEvent.Init(applied.copy(dateFrom = 1L, dateTo = 2L)))

        viewModel.onEvent(UiEvent.OnClearAll)

        viewModel.uiState.value.draft shouldBe DataBrowserFilter(searchQuery = "invoice", sort = DataSort.RULE_ASC)
    }

    @Test
    fun `Apply emits the draft`() = runTest(testDispatcher) {
        val viewModel = DataBrowserFilterSheetViewModel()
        viewModel.onEvent(UiEvent.Init(applied))
        viewModel.onEvent(UiEvent.OnAppToggle("com.b"))

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnApply)
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe UiEffect.ApplyFilter(applied.copy(packageNames = listOf("com.a", "com.b")))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss emits only Dismiss and never an ApplyFilter`() = runTest(testDispatcher) {
        val viewModel = DataBrowserFilterSheetViewModel()
        viewModel.onEvent(UiEvent.Init(applied))
        viewModel.onEvent(UiEvent.OnRuleToggle("r2"))

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnDismiss)
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe UiEffect.Dismiss
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the draft is re-hydrated on the next opening after Apply`() {
        val viewModel = DataBrowserFilterSheetViewModel()
        viewModel.onEvent(UiEvent.Init(applied))
        viewModel.onEvent(UiEvent.OnApply)

        viewModel.onEvent(UiEvent.Init(DataBrowserFilter()))

        viewModel.uiState.value.draft shouldBe DataBrowserFilter()
    }
}
