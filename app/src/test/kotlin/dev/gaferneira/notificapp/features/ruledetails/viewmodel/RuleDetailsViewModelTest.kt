package dev.gaferneira.notificapp.features.ruledetails.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleStats
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiEffect
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleExecutionRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleRepository
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleDetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var executionRepository: FakeRuleExecutionRepository
    private lateinit var viewModel: RuleDetailsViewModel

    private val rule = createTestRule(id = "rule-1", name = "Bank payment", isActive = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ruleRepository = FakeRuleRepository(initial = listOf(rule))
        executionRepository = FakeRuleExecutionRepository()
        navigationHandler = mockk()
        coEvery { navigationHandler.goBack() } returns Unit
        coEvery { navigationHandler.navigate(any()) } returns Unit
        viewModel = RuleDetailsViewModel(ruleRepository, executionRepository, navigationHandler)
        viewModel.setRuleId("rule-1")
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class LoadTests {

        @Test
        fun `observing an existing rule populates state and clears loading`() {
            // Then: the rule is shown
            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.rule shouldBe rule
            state.error shouldBe null
        }

        @Test
        fun `state reflects edits made to the rule while the screen is open`() = runTest(testDispatcher) {
            // Given: the screen showing the rule

            // When: the rule is updated elsewhere (e.g. saved from the editor)
            ruleRepository.updateRule(rule.copy(name = "Renamed"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the new data is shown without reloading
            viewModel.uiState.value.rule?.name shouldBe "Renamed"
        }

        @Test
        fun `a failing observation sets a load error`() = runTest(testDispatcher) {
            // Given: a repository whose stream throws
            val failing = mockk<RuleRepository>()
            coEvery { failing.observeRule(any()) } returns flow { throw IllegalStateException("boom") }
            val vm = RuleDetailsViewModel(failing, executionRepository, navigationHandler)

            // When: observing
            vm.setRuleId("rule-1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the error state is exposed
            val state = vm.uiState.value
            state.isLoading shouldBe false
            state.rule shouldBe null
            state.error.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_details_error_load
        }
    }

    @Nested
    inner class NavigationTests {

        @Test
        fun `edit navigates to the rule editor for this rule`() {
            // When
            viewModel.onEvent(UiEvent.OnEditClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { navigationHandler.navigate(Routes.ruleEditor(ruleId = "rule-1")) }
        }

        @Test
        fun `back pops the screen`() {
            // When
            viewModel.onEvent(UiEvent.OnBackClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { navigationHandler.goBack() }
        }
    }

    @Nested
    inner class ToggleTests {

        @Test
        fun `toggling flips the rule's active state`() {
            // When
            viewModel.onEvent(UiEvent.OnToggleActiveClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the observed rule is now inactive
            viewModel.uiState.value.rule?.isActive shouldBe false
        }
    }

    @Nested
    inner class ShareTests {

        @Test
        fun `share sends a ShareRule effect with the rule's encoded JSON`() = runTest(testDispatcher) {
            viewModel.effect.test {
                // When
                viewModel.onEvent(UiEvent.OnShareClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then
                awaitItem() shouldBe UiEffect.ShareRule(ruleName = "Bank payment", json = RuleJsonCodec.encode(rule))
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class DeleteTests {

        @Test
        fun `delete click shows the confirmation dialog without deleting`() {
            // When
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            // Then
            viewModel.uiState.value.showDeleteConfirmation shouldBe true
            ruleRepository.currentRules() shouldBe listOf(rule)
        }

        @Test
        fun `dismissing the confirmation hides it and keeps the rule`() {
            // Given
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            // When
            viewModel.onEvent(UiEvent.OnDeleteDismissed)

            // Then
            viewModel.uiState.value.showDeleteConfirmation shouldBe false
            ruleRepository.currentRules() shouldBe listOf(rule)
        }

        @Test
        fun `confirming deletes the rule and pops exactly once`() {
            // Given
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            // When
            viewModel.onEvent(UiEvent.OnDeleteConfirmed)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: deleted, dialog closed, and the observer's null emission did not pop a second time
            ruleRepository.currentRules() shouldBe emptyList()
            viewModel.uiState.value.showDeleteConfirmation shouldBe false
            coVerify(exactly = 1) { navigationHandler.goBack() }
        }

        @Test
        fun `a failed delete keeps the screen and sends an error message`() = runTest(testDispatcher) {
            // Given: a repository whose delete fails
            val failing = mockk<RuleRepository>()
            coEvery { failing.observeRule(any()) } returns FakeRuleRepository(listOf(rule)).observeRule("rule-1")
            coEvery { failing.deleteRule(any()) } returns Result.failure(IllegalStateException("db"))
            val vm = RuleDetailsViewModel(failing, executionRepository, navigationHandler)
            vm.setRuleId("rule-1")
            testDispatcher.scheduler.advanceUntilIdle()

            vm.effect.test {
                // When
                vm.onEvent(UiEvent.OnDeleteConfirmed)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then
                val effect = awaitItem().shouldBeInstanceOf<UiEffect.ShowMessage>()
                effect.message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_details_error_delete
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { navigationHandler.goBack() }
        }
    }

    @Nested
    inner class ExternalDeletionTests {

        @Test
        fun `when the rule is deleted elsewhere the screen pops itself once`() = runTest(testDispatcher) {
            // Given: the screen showing the rule

            // When: the rule is deleted from the editor stacked above this screen
            ruleRepository.deleteRule("rule-1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { navigationHandler.goBack() }
        }

        @Test
        fun `opening a rule id that does not exist pops the screen`() = runTest(testDispatcher) {
            // Given: a fresh ViewModel
            val vm = RuleDetailsViewModel(ruleRepository, executionRepository, navigationHandler)

            // When: it is pointed at a missing rule
            vm.setRuleId("missing")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { navigationHandler.goBack() }
        }
    }

    @Nested
    inner class GoLiveTests {

        private val dryRunRule = createTestRule(id = "rule-1", name = "Bank payment", isDryRun = true)

        private fun dryRunViewModel(repository: RuleRepository = ruleRepository): RuleDetailsViewModel {
            val vm = RuleDetailsViewModel(repository, executionRepository, navigationHandler)
            vm.setRuleId("rule-1")
            testDispatcher.scheduler.advanceUntilIdle()
            return vm
        }

        @Test
        fun `state exposes the dry-run flag so the banner shows only for dry-run rules`() {
            // Given: a live rule (default setup) and a dry-run rule
            val liveState = viewModel.uiState.value
            ruleRepository = FakeRuleRepository(initial = listOf(dryRunRule))
            val dryRunState = dryRunViewModel().uiState.value

            // Then
            liveState.rule?.isDryRun shouldBe false
            dryRunState.rule?.isDryRun shouldBe true
        }

        @Test
        fun `go live click opens the dialog without saving`() {
            // Given
            ruleRepository = FakeRuleRepository(initial = listOf(dryRunRule))
            val vm = dryRunViewModel()

            // When
            vm.onEvent(UiEvent.OnGoLiveClicked)

            // Then
            vm.uiState.value.showGoLiveConfirmation shouldBe true
            ruleRepository.currentRules() shouldBe listOf(dryRunRule)
        }

        @Test
        fun `dismissing the dialog closes it and keeps the rule in dry run`() {
            // Given
            ruleRepository = FakeRuleRepository(initial = listOf(dryRunRule))
            val vm = dryRunViewModel()
            vm.onEvent(UiEvent.OnGoLiveClicked)

            // When
            vm.onEvent(UiEvent.OnGoLiveDismissed)

            // Then
            vm.uiState.value.showGoLiveConfirmation shouldBe false
            ruleRepository.currentRules() shouldBe listOf(dryRunRule)
        }

        @Test
        fun `confirming saves the rule as live, refreshes updatedAt and emits success`() = runTest(testDispatcher) {
            // Given
            ruleRepository = FakeRuleRepository(initial = listOf(dryRunRule))
            val vm = dryRunViewModel()
            vm.onEvent(UiEvent.OnGoLiveClicked)

            vm.effect.test {
                // When
                vm.onEvent(UiEvent.OnGoLiveConfirmed)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then
                val effect = awaitItem().shouldBeInstanceOf<UiEffect.ShowMessage>()
                effect.message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_details_go_live_success
                cancelAndIgnoreRemainingEvents()
            }
            val state = vm.uiState.value
            state.showGoLiveConfirmation shouldBe false
            state.isGoingLive shouldBe false
            // The observed rule is now live, so the banner disappears
            state.rule?.isDryRun shouldBe false
            state.rule?.updatedAt?.shouldBeGreaterThan(dryRunRule.updatedAt)
        }

        @Test
        fun `a failed save keeps the rule in dry run, closes the dialog and sends an error`() = runTest(testDispatcher) {
            // Given: a repository whose update fails
            val failing = mockk<RuleRepository>()
            coEvery { failing.observeRule(any()) } returns FakeRuleRepository(listOf(dryRunRule)).observeRule("rule-1")
            coEvery { failing.updateRule(any()) } returns Result.failure(IllegalStateException("db"))
            val vm = dryRunViewModel(failing)
            vm.onEvent(UiEvent.OnGoLiveClicked)

            vm.effect.test {
                // When
                vm.onEvent(UiEvent.OnGoLiveConfirmed)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then
                val effect = awaitItem().shouldBeInstanceOf<UiEffect.ShowMessage>()
                effect.message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_details_error_go_live
                cancelAndIgnoreRemainingEvents()
            }
            val state = vm.uiState.value
            state.showGoLiveConfirmation shouldBe false
            state.isGoingLive shouldBe false
            state.rule?.isDryRun shouldBe true
        }

        @Test
        fun `confirming for a rule that is already live does nothing`() {
            // Given: the default live rule
            // When
            viewModel.onEvent(UiEvent.OnGoLiveConfirmed)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            ruleRepository.currentRules() shouldBe listOf(rule)
        }
    }

    @Nested
    inner class StatsTests {

        private fun execution(id: String, createdAt: Long, wasDryRun: Boolean) = RuleExecution(
            id = id,
            notificationId = "n-$id",
            ruleId = "rule-1",
            extractedData = emptyMap(),
            triggeredActions = emptyList(),
            wasDryRun = wasDryRun,
            createdAt = createdAt,
        )

        @Test
        fun `stats are loaded alongside the rule`() = runTest(testDispatcher) {
            // Given: three recorded matches, two of them in test mode
            val now = 100L * DAY_MS
            val repo = FakeRuleExecutionRepository().apply { this.now = now }
            repo.saveExecution(execution("e1", now - DAY_MS, wasDryRun = true), emptyList())
            repo.saveExecution(execution("e2", now - 20 * DAY_MS, wasDryRun = true), emptyList())
            repo.saveExecution(execution("e3", now - 40 * DAY_MS, wasDryRun = false), emptyList())

            // When: the screen opens
            val vm = RuleDetailsViewModel(ruleRepository, repo, navigationHandler)
            vm.setRuleId("rule-1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: aggregates are exposed in state
            vm.uiState.value.stats shouldBe RuleStats(
                totalMatches = 3,
                matchesLast7Days = 1,
                matchesLast30Days = 2,
                liveMatches = 1,
                testModeMatches = 2,
                lastTriggeredAt = now - DAY_MS,
            )
        }

        @Test
        fun `a rule that never matched exposes empty stats`() {
            // Given/When: no executions (default setup)
            // Then: stats are loaded and zeroed, so the UI can show its empty state
            viewModel.uiState.value.stats shouldBe RuleStats()
        }

        @Test
        fun `stats update live when a new match is recorded`() = runTest(testDispatcher) {
            // When: a match is recorded while the screen is open
            executionRepository.saveExecution(execution("e1", createdAt = 5L, wasDryRun = false), emptyList())
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            viewModel.uiState.value.stats?.totalMatches shouldBe 1
        }

        @Test
        fun `a stats failure hides the section without breaking the screen`() {
            // Given: stats loading fails
            val repo = FakeRuleExecutionRepository().apply { statsError = IllegalStateException("db") }

            // When: the screen opens
            val vm = RuleDetailsViewModel(ruleRepository, repo, navigationHandler)
            vm.setRuleId("rule-1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the rule is shown, no error state, and there are no stats
            val state = vm.uiState.value
            state.rule shouldBe rule
            state.isLoading shouldBe false
            state.error shouldBe null
            state.stats shouldBe null
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
