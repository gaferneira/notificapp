package dev.gaferneira.notificapp.features.rules.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.UiEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
class FilterBottomSheetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: FilterBottomSheetViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FilterBottomSheetViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init derives sorted distinct categories and apps from the given rules`() = runTest(testDispatcher) {
        viewModel.onEvent(
            UiEvent.Init(
                allRules = listOf(
                    createTestRule(category = "Finance", targetApps = listOf(AppInfo("com.b", "Bank"))),
                    createTestRule(category = "Finance", targetApps = listOf(AppInfo("com.a", "Alpha"))),
                    createTestRule(category = "Deliveries", targetApps = null),
                ),
                currentFilter = RuleFilter(),
            ),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        state.availableCategories shouldBe listOf("Deliveries", "Finance")
        state.availableApps.map { it.name } shouldBe listOf("Alpha", "Bank")
    }

    @Test
    fun `init hydrates the selected filter fields and hasActiveFilters from the given filter`() = runTest(testDispatcher) {
        val filter = RuleFilter(
            status = RuleFilter.Status.ENABLED,
            selectedCategories = setOf("Finance"),
            selectedApps = setOf("com.a"),
            sortBy = RuleFilter.SortBy.STATUS,
        )

        viewModel.onEvent(UiEvent.Init(allRules = emptyList(), currentFilter = filter))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        state.statusFilter shouldBe RuleFilter.Status.ENABLED
        state.selectedCategories shouldBe setOf("Finance")
        state.selectedApps shouldBe setOf("com.a")
        state.sortBy shouldBe RuleFilter.SortBy.STATUS
        state.hasActiveFilters shouldBe true
    }

    @Test
    fun `toggling a category flips hasActiveFilters on and back off`() {
        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        viewModel.uiState.value.hasActiveFilters shouldBe true
        viewModel.uiState.value.selectedCategories shouldBe setOf("Finance")

        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        viewModel.uiState.value.hasActiveFilters shouldBe false
        viewModel.uiState.value.selectedCategories shouldBe emptySet()
    }

    @Test
    fun `toggling an app flips hasActiveFilters on and back off`() {
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.hasActiveFilters shouldBe true

        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `changing status away from ALL sets hasActiveFilters`() {
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))
        viewModel.uiState.value.hasActiveFilters shouldBe true

        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ALL))
        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `changing sort away from the default sets hasActiveFilters`() {
        viewModel.onEvent(UiEvent.OnSortChange(RuleFilter.SortBy.STATUS))
        viewModel.uiState.value.hasActiveFilters shouldBe true

        viewModel.onEvent(UiEvent.OnSortChange(RuleFilter.SortBy.CATEGORY_ASC))
        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `clear all resets every filter field and hasActiveFilters`() {
        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))
        viewModel.onEvent(UiEvent.OnSortChange(RuleFilter.SortBy.STATUS))

        viewModel.onEvent(UiEvent.OnClearAll)

        val state = viewModel.uiState.value
        state.selectedCategories shouldBe emptySet()
        state.selectedApps shouldBe emptySet()
        state.statusFilter shouldBe RuleFilter.Status.ALL
        state.sortBy shouldBe RuleFilter.SortBy.CATEGORY_ASC
        state.hasActiveFilters shouldBe false
    }

    @Test
    fun `apply emits ApplyFilter carrying the current selection`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))
        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnApply)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem().shouldBeInstanceOf<UiEffect.ApplyFilter>()
            effect.filter.status shouldBe RuleFilter.Status.ENABLED
            effect.filter.selectedCategories shouldBe setOf("Finance")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss emits Dismiss`() = runTest(testDispatcher) {
        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnDismiss)
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe UiEffect.Dismiss
            cancelAndIgnoreRemainingEvents()
        }
    }
}
