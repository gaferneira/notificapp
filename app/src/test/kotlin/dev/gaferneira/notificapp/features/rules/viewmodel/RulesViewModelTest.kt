package dev.gaferneira.notificapp.features.rules.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleImportFailure
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.preferences.RulesFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.RulesSort
import dev.gaferneira.notificapp.domain.model.preferences.RulesStatusFilter
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RuleFilterChip
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeUserPreferencesRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var preferences: FakeUserPreferencesRepository
    private lateinit var viewModel: RulesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ruleRepository = FakeRuleRepository()
        preferences = FakeUserPreferencesRepository()
        viewModel = RulesViewModel(ruleRepository, preferences)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class NavigationTests {

        @Test
        fun `clicking a rule sends a NavigateToRuleDetails effect`() = runTest(testDispatcher) {
            viewModel.effect.test {
                // When: tapping a rule row
                viewModel.onEvent(RulesEvent.OnRuleClick("rule-1"))
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: it opens the read-only details screen, not the editor
                awaitItem() shouldBe RulesEffect.NavigateToRuleDetails("rule-1")
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `adding a rule still sends a NavigateToRuleEditor effect with no id`() = runTest(testDispatcher) {
            viewModel.effect.test {
                // When: tapping the add action
                viewModel.onEvent(RulesEvent.OnAddRuleClick)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then
                awaitItem() shouldBe RulesEffect.NavigateToRuleEditor()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class ImportTests {

        @Test
        fun `receiving valid rule text sets a preview with fresh identity and dry-run forced on`() {
            // Given: a rule exported while active and not in dry-run
            val rule = createTestRule(id = "rule-1", name = "Bank payment", isDryRun = false, isActive = false)
            val json = RuleJsonCodec.encode(rule)

            // When: the text is received (from a picked file or clipboard)
            viewModel.onEvent(RulesEvent.OnRuleTextReceived(json))

            // Then: the preview is forced into dry-run + active, with a freshly generated id
            val preview = viewModel.uiState.value.importPreview
            preview.shouldNotBeNull()
            preview.name shouldBe "Bank payment"
            preview.isDryRun shouldBe true
            preview.isActive shouldBe true
            (preview.id != "rule-1") shouldBe true
            viewModel.uiState.value.importError shouldBe null
        }

        @Test
        fun `receiving rule text with an unrecognized action surfaces it as skipped`() {
            // Given: a rule encoded normally, then tampered to carry an action type this app
            // version doesn't recognize (e.g. exported from a newer version) - a placeholder
            // token, not a real `ActionType` (see webhook-delivery's addition of `send_webhook`
            // as a genuinely recognized type)
            val rule = createTestRule(
                id = "rule-1",
                name = "Bank payment",
                actions = listOf(createTestAction(type = ActionType.SAVE_DATA)),
            )
            val json = RuleJsonCodec.encode(rule).replace("\"save_data\"", "\"some_future_action_type\"")

            // When: the text is received
            viewModel.onEvent(RulesEvent.OnRuleTextReceived(json))

            // Then: the preview still succeeds and reports the skipped action
            val state = viewModel.uiState.value
            state.importPreview.shouldNotBeNull()
            state.importSkippedActions shouldBe listOf("some_future_action_type")
        }

        @Test
        fun `receiving malformed rule text sets an import error and no preview`() {
            // When: receiving garbage input
            viewModel.onEvent(RulesEvent.OnRuleTextReceived("not json at all"))

            // Then: an error is set and there is no preview to confirm
            val state = viewModel.uiState.value
            state.importPreview shouldBe null
            state.importError.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rules_import_invalid
        }

        @Test
        fun `a rule exported from a newer schema version sets a localized import error`() {
            // Given: an envelope from a future schema version
            val json = RuleJsonCodec.encode(createTestRule(name = "Bank payment"))
                .replace("\"schemaVersion\": 1", "\"schemaVersion\": 999")

            // When: the text is received
            viewModel.onEvent(RulesEvent.OnRuleTextReceived(json))

            // Then: the error is a string resource, not the codec's English message
            viewModel.uiState.value.importError.shouldBeInstanceOf<UiText.StringResource>().id shouldBe
                R.string.rules_import_error_newer_version
        }

        @Test
        fun `codec failures map to their localized messages`() {
            RuleImportFailure.MissingName().toImportErrorText()
                .shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rules_import_error_missing_name
            RuleImportFailure.UnknownValue("operator", "fuzzy").toImportErrorText()
                .shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rules_import_error_unknown_value
            RuleImportFailure.NestedTooDeeply(5).toImportErrorText()
                .shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rules_import_error_too_deep
            IllegalStateException("boom").toImportErrorText()
                .shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rules_import_invalid
        }

        @Test
        fun `confirming import persists the preview and it appears in the observed stream`() = runTest(testDispatcher) {
            // Given: a decoded import preview
            val rule = createTestRule(id = "rule-1", name = "Bank payment")
            viewModel.onEvent(RulesEvent.OnRuleTextReceived(RuleJsonCodec.encode(rule)))
            val preview = viewModel.uiState.value.importPreview
            preview.shouldNotBeNull()

            // When: confirming the import
            viewModel.onEvent(RulesEvent.OnImportConfirmed)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview clears and the rule is persisted, visible via the observed stream
            viewModel.uiState.value.importPreview shouldBe null
            ruleRepository.currentRules().single().name shouldBe "Bank payment"
        }

        @Test
        fun `cancelling import clears the preview without saving`() {
            // Given: a decoded import preview
            val rule = createTestRule(id = "rule-1", name = "Bank payment")
            viewModel.onEvent(RulesEvent.OnRuleTextReceived(RuleJsonCodec.encode(rule)))

            // When: cancelling
            viewModel.onEvent(RulesEvent.OnImportCancelled)

            // Then: the preview clears and nothing is saved
            viewModel.uiState.value.importPreview shouldBe null
            ruleRepository.currentRules() shouldBe emptyList()
        }

        @Test
        fun `dismissing the import error clears it`() {
            // Given: a failed decode
            viewModel.onEvent(RulesEvent.OnRuleTextReceived("not json"))
            viewModel.uiState.value.importError.shouldNotBeNull()

            // When: dismissing the error
            viewModel.onEvent(RulesEvent.OnDismissImportError)

            // Then: it clears
            viewModel.uiState.value.importError shouldBe null
        }
    }

    @Nested
    inner class AppFilterTests {

        private val excludeRule = createTestRule(
            id = "rule-1",
            isIncludeMode = false,
            targetApps = listOf(AppInfo("com.b", "App B")),
        )

        @Test
        fun `an exclude-mode rule that omits the app is hidden by default`() = runTest(testDispatcher) {
            // Given: an exclude-mode rule that omits com.a (it would run for com.a but never mentions it)
            ruleRepository.saveRule(excludeRule)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: filtering by com.a without the global-rules toggle
            viewModel.onEvent(RulesEvent.OnFilterChange(RuleFilter(selectedApps = setOf("com.a"))))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            viewModel.uiState.value.rules.getDataOrThrow() shouldBe persistentListOf()
        }

        @Test
        fun `an exclude-mode rule that omits the app is surfaced when include-global is on`() = runTest(testDispatcher) {
            ruleRepository.saveRule(excludeRule)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(
                RulesEvent.OnFilterChange(RuleFilter(selectedApps = setOf("com.a"), includeGlobalRules = true)),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.rules.getDataOrThrow() shouldBe persistentListOf(excludeRule)
        }

        @Test
        fun `an exclude-mode rule that lists the app is surfaced because it mentions it`() = runTest(testDispatcher) {
            ruleRepository.saveRule(excludeRule)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(RulesEvent.OnFilterChange(RuleFilter(selectedApps = setOf("com.b"))))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.rules.getDataOrThrow() shouldBe persistentListOf(excludeRule)
        }
    }

    @Nested
    inner class ActiveFilterChipTests {

        @Test
        fun `removing a chip removes only that filter and keeps the rest and the sort`() = runTest(testDispatcher) {
            // Given
            val filter = RuleFilter(
                status = RuleFilter.Status.ENABLED,
                selectedCategories = setOf("Finance"),
                selectedApps = setOf("com.a"),
                sortBy = RuleFilter.SortBy.NAME_ASC,
            )
            viewModel.onEvent(RulesEvent.OnFilterChange(filter))
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.onEvent(RulesEvent.OnRemoveFilter(RuleFilterChip.Category("Finance")))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            viewModel.uiState.value.filter shouldBe filter.copy(selectedCategories = emptySet())
        }

        @Test
        fun `removing the last app chip also turns include-global off`() = runTest(testDispatcher) {
            viewModel.onEvent(
                RulesEvent.OnFilterChange(RuleFilter(selectedApps = setOf("com.a"), includeGlobalRules = true)),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(RulesEvent.OnRemoveFilter(RuleFilterChip.App("com.a")))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe RuleFilter()
        }

        @Test
        fun `clearing filters keeps the sort order`() = runTest(testDispatcher) {
            viewModel.onEvent(
                RulesEvent.OnFilterChange(
                    RuleFilter(status = RuleFilter.Status.DISABLED, sortBy = RuleFilter.SortBy.STATUS),
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(RulesEvent.OnClearFilters)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe RuleFilter(sortBy = RuleFilter.SortBy.STATUS)
        }
    }

    @Nested
    inner class FilterPersistenceTests {

        @Test
        fun `the applied filter is saved to preferences`() = runTest(testDispatcher) {
            // When
            viewModel.onEvent(
                RulesEvent.OnFilterChange(
                    RuleFilter(
                        status = RuleFilter.Status.ENABLED,
                        selectedCategories = setOf("Finance"),
                        includeUncategorized = true,
                        selectedApps = setOf("com.a"),
                        includeGlobalRules = true,
                        sortBy = RuleFilter.SortBy.NAME_DESC,
                    ),
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            preferences.rulesFilters() shouldBe RulesFilterSettings(
                status = RulesStatusFilter.ENABLED,
                selectedCategories = listOf("Finance"),
                includeUncategorized = true,
                selectedApps = listOf("com.a"),
                includeGlobalRules = true,
                sortBy = RulesSort.NAME_DESC,
            )
        }

        @Test
        fun `removing a chip saves the new filter`() = runTest(testDispatcher) {
            viewModel.onEvent(RulesEvent.OnFilterChange(RuleFilter(status = RuleFilter.Status.DISABLED)))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(RulesEvent.OnRemoveFilter(RuleFilterChip.Status(RuleFilter.Status.DISABLED)))
            testDispatcher.scheduler.advanceUntilIdle()

            preferences.rulesFilters() shouldBe RulesFilterSettings()
        }

        @Test
        fun `a saved filter is restored on start`() = runTest(testDispatcher) {
            // Given: preferences from a previous process
            val saved = RulesFilterSettings(
                status = RulesStatusFilter.DISABLED,
                selectedCategories = listOf("Finance"),
                selectedApps = listOf("com.a"),
                includeGlobalRules = true,
                sortBy = RulesSort.UPDATED_RECENT,
            )

            // When: a new ViewModel starts
            val restored = RulesViewModel(ruleRepository, FakeUserPreferencesRepository(UserPreferences(rulesFilterSettings = saved)))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            restored.uiState.value.filter shouldBe RuleFilter(
                status = RuleFilter.Status.DISABLED,
                selectedCategories = setOf("Finance"),
                selectedApps = setOf("com.a"),
                includeGlobalRules = true,
                sortBy = RuleFilter.SortBy.UPDATED_RECENT,
            )
        }
    }

    @Nested
    inner class SearchAndClearTests {

        @Test
        fun `search matches name, description and category`() = runTest(testDispatcher) {
            // Given
            ruleRepository.saveRule(createTestRule(id = "a", name = "Alpha", description = "bank alerts", category = "Misc"))
            ruleRepository.saveRule(createTestRule(id = "b", name = "Beta", description = "x", category = "Finance"))
            ruleRepository.saveRule(createTestRule(id = "c", name = "Gamma", description = "x", category = "Misc"))
            testDispatcher.scheduler.advanceUntilIdle()

            // When / Then: description match
            viewModel.onEvent(RulesEvent.OnSearchQueryChange("bank"))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.rules.getDataOrThrow().map { it.id } shouldBe listOf("a")

            // When / Then: category match
            viewModel.onEvent(RulesEvent.OnSearchQueryChange("finance"))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.rules.getDataOrThrow().map { it.id } shouldBe listOf("b")
        }

        @Test
        fun `clearing filters resets search and filter but keeps all rules`() = runTest(testDispatcher) {
            // Given: a rule hidden by both a search query and a status filter
            ruleRepository.saveRule(createTestRule(id = "a", name = "Alpha", isActive = true))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(RulesEvent.OnSearchQueryChange("zzz"))
            viewModel.onEvent(RulesEvent.OnFilterChange(RuleFilter(status = RuleFilter.Status.DISABLED)))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.rules.getDataOrThrow() shouldBe persistentListOf()
            viewModel.uiState.value.allRules.size shouldBe 1

            // When
            viewModel.onEvent(RulesEvent.OnClearFilters)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            state.searchQuery shouldBe ""
            state.filter shouldBe RuleFilter()
            state.rules.getDataOrThrow().map { it.id } shouldBe listOf("a")
        }
    }

    @Nested
    inner class LoadErrorTests {

        @Test
        fun `a failing rules stream surfaces an error state and retry recovers`() = runTest(testDispatcher) {
            // Given: a repository whose stream fails
            val repository = mockk<RuleRepository>()
            every { repository.observeAllRules() } returns flow { throw IllegalStateException("boom") }
            val vm = RulesViewModel(repository, FakeUserPreferencesRepository())
            testDispatcher.scheduler.advanceUntilIdle()
            vm.uiState.value.rules.shouldBeInstanceOf<Resource.Error<*>>()

            // When: the stream recovers and the user retries
            every { repository.observeAllRules() } returns flowOf(listOf(createTestRule(id = "a")))
            vm.onEvent(RulesEvent.LoadRules)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            vm.uiState.value.rules.getDataOrThrow().map { it.id } shouldBe listOf("a")
        }
    }
}
