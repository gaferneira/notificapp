package dev.gaferneira.notificapp.features.rules.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.AppOption
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.CategoryOption
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.UiEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var monitoredAppsFlow: MutableStateFlow<List<SelectedApp>>
    private lateinit var viewModel: FilterBottomSheetViewModel

    private val bank = AppInfo("com.b", "bank")
    private val alpha = AppInfo("com.a", "Alpha")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        monitoredAppsFlow = MutableStateFlow(emptyList())
        val selectedAppRepository = mockk<SelectedAppRepository> {
            every { observeEnabledApps() } returns monitoredAppsFlow
        }
        viewModel = FilterBottomSheetViewModel(selectedAppRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun init(rules: List<Rule>, filter: RuleFilter = RuleFilter(), searchQuery: String = "") {
        viewModel.onEvent(UiEvent.Init(rules.toImmutableList(), filter, searchQuery))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `categories are sorted case-insensitively with rule counts`() {
        init(
            listOf(
                createTestRule(id = "1", category = "finance"),
                createTestRule(id = "2", category = "Finance"),
                createTestRule(id = "3", category = "Deliveries"),
                createTestRule(id = "4", category = "Deliveries"),
                createTestRule(id = "5", category = null),
            ),
        )

        state.categories shouldBe listOf(
            CategoryOption("Deliveries", 2),
            CategoryOption("finance", 1),
            CategoryOption("Finance", 1),
        )
        state.uncategorizedCount shouldBe 1
    }

    @Test
    fun `used apps count the rules that mention them in include or exclude mode`() {
        init(
            listOf(
                createTestRule(id = "1", targetApps = listOf(alpha, bank)),
                createTestRule(id = "2", isIncludeMode = false, targetApps = listOf(alpha)),
                createTestRule(id = "3", targetApps = null),
            ),
        )

        state.usedApps shouldBe listOf(AppOption("com.a", "Alpha", 2), AppOption("com.b", "bank", 1))
        state.otherApps shouldBe emptyList()
    }

    @Test
    fun `monitored apps without rules go to the other group with zero rules`() {
        monitoredAppsFlow.value = listOf(SelectedApp("com.a", "Alpha"), SelectedApp("com.c", "Chat"))
        init(listOf(createTestRule(targetApps = listOf(alpha))))

        state.usedApps shouldBe listOf(AppOption("com.a", "Alpha", 1))
        state.otherApps shouldBe listOf(AppOption("com.c", "Chat", 0))
    }

    @Test
    fun `app lists are sorted case-insensitively`() {
        monitoredAppsFlow.value = listOf(SelectedApp("com.z", "zeta"), SelectedApp("com.y", "Yankee"), SelectedApp("com.x", "xray"))
        init(emptyList())

        state.otherApps.map { it.name } shouldBe listOf("xray", "Yankee", "zeta")
    }

    @Test
    fun `app search is case-insensitive over the name and keeps selected apps visible`() {
        monitoredAppsFlow.value = listOf(SelectedApp("com.c", "Chat"))
        init(
            listOf(createTestRule(targetApps = listOf(alpha, bank))),
            filter = RuleFilter(selectedApps = setOf("com.b")),
        )

        viewModel.onEvent(UiEvent.OnAppSearchChange("ALP"))

        state.usedApps.map { it.name } shouldBe listOf("Alpha")
        state.otherApps shouldBe emptyList()
        state.selectedApps.map { it.name } shouldBe listOf("bank")
    }

    @Test
    fun `init hydrates the draft from the applied filter`() {
        val filter = RuleFilter(
            status = RuleFilter.Status.ENABLED,
            selectedCategories = setOf("Finance"),
            selectedApps = setOf("com.a"),
            includeGlobalRules = true,
            sortBy = RuleFilter.SortBy.STATUS,
        )
        init(listOf(createTestRule(category = "Finance", targetApps = listOf(alpha))), filter)

        state.draft shouldBe filter
        state.hasActiveFilters shouldBe true
    }

    @Test
    fun `sort alone does not make the draft an active filter`() {
        init(emptyList())

        viewModel.onEvent(UiEvent.OnSortChange(RuleFilter.SortBy.NAME_ASC))

        state.draft.sortBy shouldBe RuleFilter.SortBy.NAME_ASC
        state.hasActiveFilters shouldBe false
    }

    @Test
    fun `hasActiveFilters is derived from the draft`() {
        init(emptyList())

        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        state.hasActiveFilters shouldBe true
        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        state.hasActiveFilters shouldBe false

        viewModel.onEvent(UiEvent.OnUncategorizedToggle)
        state.hasActiveFilters shouldBe true
        viewModel.onEvent(UiEvent.OnUncategorizedToggle)

        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.DISABLED))
        state.hasActiveFilters shouldBe true
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ALL))

        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        state.hasActiveFilters shouldBe true
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        state.hasActiveFilters shouldBe false
    }

    @Test
    fun `stale categories are dropped on hydration but stale apps stay selected and visible`() {
        init(
            rules = listOf(createTestRule(category = "Finance")),
            filter = RuleFilter(
                selectedCategories = setOf("Finance", "Gone"),
                includeUncategorized = true,
                selectedApps = setOf("com.gone"),
                includeGlobalRules = true,
            ),
        )

        state.draft.selectedCategories shouldBe setOf("Finance")
        // No rule is uncategorized any more
        state.draft.includeUncategorized shouldBe false
        state.draft.selectedApps shouldBe setOf("com.gone")
        state.selectedApps shouldBe listOf(AppOption("com.gone", "com.gone", 0))
        state.otherApps shouldBe listOf(AppOption("com.gone", "com.gone", 0))
    }

    @Test
    fun `re-init with refreshed rules does not wipe unapplied edits`() {
        val rule = createTestRule(id = "1", category = "Finance", targetApps = listOf(alpha))
        init(listOf(rule))
        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))

        // Rules refresh while the sheet is open (same applied filter is re-sent)
        init(listOf(rule, createTestRule(id = "2", category = "Finance")), RuleFilter())

        state.draft.selectedCategories shouldBe setOf("Finance")
        state.draft.status shouldBe RuleFilter.Status.ENABLED
        state.categories shouldBe listOf(CategoryOption("Finance", 2))
    }

    @Test
    fun `a new sheet opening after dismiss hydrates the draft again`() = runTest(testDispatcher) {
        init(emptyList())
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))
        viewModel.onEvent(UiEvent.OnDismiss)
        testDispatcher.scheduler.advanceUntilIdle()

        init(emptyList(), RuleFilter(status = RuleFilter.Status.DISABLED))

        state.draft.status shouldBe RuleFilter.Status.DISABLED
    }

    @Test
    fun `match count follows the draft and the screen search query`() {
        val rules = listOf(
            createTestRule(id = "1", name = "Bank one", category = "Finance", targetApps = listOf(alpha)),
            createTestRule(id = "2", name = "Bank two", category = "Finance", targetApps = null),
            createTestRule(id = "3", name = "Other", category = null, isActive = false),
        )
        init(rules)
        state.matchCount shouldBe 3

        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        state.matchCount shouldBe 2

        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        state.matchCount shouldBe 1

        viewModel.onEvent(UiEvent.OnIncludeGlobalRulesChange(true))
        state.matchCount shouldBe 2

        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.DISABLED))
        state.matchCount shouldBe 0

        init(rules, searchQuery = "two")
        viewModel.onEvent(UiEvent.OnClearAll)
        state.matchCount shouldBe 1
    }

    @Test
    fun `include-global is switched off when the last app is deselected`() {
        init(emptyList())
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.onEvent(UiEvent.OnIncludeGlobalRulesChange(true))
        state.draft.includeGlobalRules shouldBe true

        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))

        state.draft.includeGlobalRules shouldBe false
    }

    @Test
    fun `include-global is ignored while no app is selected`() {
        init(emptyList())

        viewModel.onEvent(UiEvent.OnIncludeGlobalRulesChange(true))

        state.draft.includeGlobalRules shouldBe false
    }

    @Test
    fun `clear all resets filters but keeps the sort`() {
        init(emptyList())
        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))
        viewModel.onEvent(UiEvent.OnSortChange(RuleFilter.SortBy.STATUS))

        viewModel.onEvent(UiEvent.OnClearAll)

        state.draft shouldBe RuleFilter(sortBy = RuleFilter.SortBy.STATUS)
        state.hasActiveFilters shouldBe false
    }

    @Test
    fun `apply emits ApplyFilter carrying the draft`() = runTest(testDispatcher) {
        init(emptyList())
        viewModel.onEvent(UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))
        viewModel.onEvent(UiEvent.OnCategoryToggle("Finance"))
        viewModel.onEvent(UiEvent.OnUncategorizedToggle)

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnApply)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem().shouldBeInstanceOf<UiEffect.ApplyFilter>()
            effect.filter shouldBe RuleFilter(
                status = RuleFilter.Status.ENABLED,
                selectedCategories = setOf("Finance"),
                includeUncategorized = true,
            )
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
