package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.messaging.AppMessenger
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.model.getThrottleResetAt
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.RuleTemplateRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.InitArgs
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.createTestTimeRangeCondition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var ruleRepository: RuleRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var selectedAppRepository: SelectedAppRepository
    private lateinit var ruleTemplateRepository: RuleTemplateRepository
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var appMessenger: AppMessenger
    private lateinit var enabledAppsFlow: MutableStateFlow<List<SelectedApp>>
    private lateinit var viewModel: RuleEditorViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ruleRepository = mockk()
        notificationRepository = mockk()
        selectedAppRepository = mockk()
        navigationHandler = mockk()
        ruleTemplateRepository = mockk()
        appMessenger = AppMessenger()

        enabledAppsFlow = MutableStateFlow<List<SelectedApp>>(emptyList())
        every { selectedAppRepository.observeEnabledApps() } returns enabledAppsFlow
        coEvery { navigationHandler.goBack() } just Runs

        viewModel = createViewModel()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): RuleEditorViewModel = RuleEditorViewModel(
        ruleRepository = ruleRepository,
        notificationRepository = notificationRepository,
        selectedAppRepository = selectedAppRepository,
        ruleTemplateRepository = ruleTemplateRepository,
        ruleEngine = RuleEngine(),
        navigationHandler = navigationHandler,
        appMessenger = appMessenger,
        savedStateHandle = savedStateHandle,
        defaultDispatcher = testDispatcher,
    ).also { testDispatcher.scheduler.advanceUntilIdle() }

    private fun RuleEditorViewModel.initialize(
        ruleId: String? = null,
        notificationId: String? = null,
        templateAssetFileName: String? = null,
    ) {
        onEvent(UiEvent.Initialize(InitArgs(ruleId, notificationId, templateAssetFileName)))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun stubRule(rule: dev.gaferneira.notificapp.domain.model.Rule) {
        coEvery { ruleRepository.getRule(rule.id) } returns Result.success(rule)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class EnabledAppsObservationTests {

        @Test
        fun `enabled apps from the repository are mapped into state`() = runTest(testDispatcher) {
            // Given: the repository emits a new list of enabled apps
            enabledAppsFlow.value = listOf(SelectedApp(packageName = "com.a", appName = "App A"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the state reflects the mapped AppInfo list
            viewModel.uiState.value.enabledApps shouldBe listOf(AppInfo("com.a", "App A"))
        }
    }

    @Nested
    inner class LoadRuleTests {

        @Test
        fun `initializing without any argument yields a clean blank rule and loads nothing`() = runTest(testDispatcher) {
            // When: initializing with no args
            viewModel.initialize()

            // Then: no repository call happens and the blank rule is not dirty
            coVerify(exactly = 0) { ruleRepository.getRule(any()) }
            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.loadError shouldBe null
            state.hasUnsavedChanges shouldBe false
        }

        @Test
        fun `loading an existing rule populates the ui model and shows optional fields`() = runTest(testDispatcher) {
            // Given: a stored rule with a category and description
            val rule = createTestRule(id = "rule-1", description = "desc", category = "Food")
            stubRule(rule)

            // When: initializing with its id
            viewModel.initialize(ruleId = "rule-1")

            // Then: the ui model is populated, optional fields are shown, and the snapshot matches
            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.rule shouldBe RuleUiModel.fromDomain(rule)
            state.initialRule shouldBe state.rule
            state.showCategory shouldBe true
            state.showDescription shouldBe true
        }

        @Test
        fun `loading a rule that is not found shows a non-retryable load error`() = runTest(testDispatcher) {
            // Given: the repository returns no rule for the id
            coEvery { ruleRepository.getRule("missing") } returns Result.success(null)

            // When: initializing
            viewModel.initialize(ruleId = "missing")

            // Then: a blocking load error without retry is set
            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.loadError!!.message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_editor_error_not_found
            state.loadError.canRetry shouldBe false
        }

        @Test
        fun `loading a rule that fails shows a retryable load error and retry recovers`() = runTest(testDispatcher) {
            // Given: the first lookup fails, the second succeeds
            val rule = createTestRule(id = "rule-1")
            coEvery { ruleRepository.getRule("rule-1") } returnsMany listOf(
                Result.failure(RuntimeException("db error")),
                Result.success(rule),
            )

            // When: initializing, then retrying
            viewModel.initialize(ruleId = "rule-1")
            val failed = viewModel.uiState.value
            failed.loadError!!.message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_editor_error_load
            failed.loadError.canRetry shouldBe true

            viewModel.onEvent(UiEvent.OnRetryLoadClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the rule is loaded and the error cleared
            val state = viewModel.uiState.value
            state.loadError shouldBe null
            state.rule shouldBe RuleUiModel.fromDomain(rule)
        }

        @Test
        fun `a second initialize with the same args does not overwrite edits`() = runTest(testDispatcher) {
            // Given: a loaded rule that the user then renames
            val rule = createTestRule(id = "rule-1", name = "Original")
            stubRule(rule)
            viewModel.initialize(ruleId = "rule-1")
            viewModel.onEvent(UiEvent.OnNameChange("Edited"))

            // When: the screen re-sends the same initialize (recomposition / rotation)
            viewModel.initialize(ruleId = "rule-1")

            // Then: the edit survives and the repository was hit only once
            viewModel.uiState.value.rule.name shouldBe "Edited"
            coVerify(exactly = 1) { ruleRepository.getRule("rule-1") }
        }

        @Test
        fun `loading a template prefills an unsaved rule and is not dirty`() = runTest(testDispatcher) {
            // Given: a valid template asset
            val template = dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec.encode(createTestRule(id = "tpl", name = "Template"))
            coEvery { ruleTemplateRepository.getTemplateText("tpl.json") } returns Result.success(template)

            // When: initializing with the template
            viewModel.initialize(templateAssetFileName = "tpl.json")

            // Then: the editor is in create mode with the template content, untouched
            val state = viewModel.uiState.value
            state.rule.id shouldBe null
            state.rule.name shouldBe "Template"
            state.hasUnsavedChanges shouldBe false
        }

        @Test
        fun `a missing template asset shows a specific non-retryable error`() = runTest(testDispatcher) {
            // Given: the asset cannot be read
            coEvery { ruleTemplateRepository.getTemplateText("gone.json") } returns Result.failure(RuntimeException("no asset"))

            // When: initializing
            viewModel.initialize(templateAssetFileName = "gone.json")

            // Then: the error is the "template not available" one
            val error = viewModel.uiState.value.loadError!!
            error.message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_editor_error_template_missing
            error.canRetry shouldBe false
        }

        @Test
        fun `a malformed template shows the invalid template error`() = runTest(testDispatcher) {
            // Given: the asset is not valid rule JSON
            coEvery { ruleTemplateRepository.getTemplateText("bad.json") } returns Result.success("not json")

            // When: initializing
            viewModel.initialize(templateAssetFileName = "bad.json")

            // Then: the invalid-template error is shown
            viewModel.uiState.value.loadError!!.message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe
                R.string.rule_editor_error_template_invalid
        }
    }

    @Nested
    inner class LoadSampleNotificationTests {

        @Test
        fun `a sample notification for a new rule sets the target app and the result is not dirty`() = runTest(testDispatcher) {
            // Given: a sample notification
            val notification = createTestNotification(packageName = "com.a", appName = "App A")
            coEvery { notificationRepository.getNotification(notification.id) } returns Result.success(notification)

            // When: initializing a new rule from it
            viewModel.initialize(notificationId = notification.id)

            // Then: the sample is stored, the app preselected, and the prefill is the clean baseline
            val state = viewModel.uiState.value
            state.sampleNotification shouldBe notification
            state.rule.targetApps shouldBe listOf(notification.app)
            state.rule.triggers shouldBe emptyList()
            state.hasUnsavedChanges shouldBe false
        }

        @Test
        fun `a sample notification for an existing rule preserves target apps and triggers`() = runTest(testDispatcher) {
            // Given: an existing rule with a trigger and a target app
            val existingTrigger = createTestCondition(id = "cond-1")
            val existingTargetApp = AppInfo("com.existing", "Existing App")
            stubRule(createTestRule(id = "rule-1", conditions = listOf(existingTrigger), targetApps = listOf(existingTargetApp)))
            val notification = createTestNotification(packageName = "com.b", appName = "App B")
            coEvery { notificationRepository.getNotification(notification.id) } returns Result.success(notification)

            // When: initializing with both
            viewModel.initialize(ruleId = "rule-1", notificationId = notification.id)

            // Then: the sample is stored but target apps and triggers are preserved
            val state = viewModel.uiState.value
            state.sampleNotification shouldBe notification
            state.rule.targetApps shouldBe listOf(existingTargetApp)
            state.rule.triggers shouldBe listOf(existingTrigger)
        }

        @Test
        fun `a sample notification that is not found leaves the sample empty`() = runTest(testDispatcher) {
            coEvery { notificationRepository.getNotification("missing") } returns Result.success(null)

            viewModel.initialize(notificationId = "missing")

            viewModel.uiState.value.sampleNotification shouldBe null
        }

        @Test
        fun `a sample notification that fails does not block the editor`() = runTest(testDispatcher) {
            coEvery { notificationRepository.getNotification("boom") } returns Result.failure(RuntimeException("io error"))

            viewModel.initialize(notificationId = "boom")

            val state = viewModel.uiState.value
            state.sampleNotification shouldBe null
            state.loadError shouldBe null
        }
    }

    @Nested
    inner class DirtyCheckTests {

        private fun loadExisting() {
            stubRule(
                createTestRule(
                    id = "rule-1",
                    name = "Rule",
                    description = "desc",
                    category = "Cat",
                    conditions = listOf(createTestCondition(id = "c1")),
                    actions = listOf(createTestAction(id = "a1", type = ActionType.DISMISS_NOTIFICATION)),
                ),
            )
            viewModel.initialize(ruleId = "rule-1")
        }

        private fun dirty() = viewModel.uiState.value.hasUnsavedChanges

        @Test
        fun `an untouched existing rule is clean`() {
            loadExisting()
            dirty() shouldBe false
        }

        @Test
        fun `an untouched blank new rule is clean`() {
            viewModel.initialize()
            dirty() shouldBe false
        }

        @Test
        fun `editing the name makes it dirty and reverting makes it clean again`() {
            loadExisting()
            viewModel.onEvent(UiEvent.OnNameChange("Other"))
            dirty() shouldBe true
            viewModel.onEvent(UiEvent.OnNameChange("Rule"))
            dirty() shouldBe false
        }

        @Test
        fun `editing description category and toggles makes it dirty`() {
            loadExisting()
            viewModel.onEvent(UiEvent.OnDescriptionChange("changed"))
            dirty() shouldBe true
            viewModel.onEvent(UiEvent.OnDescriptionChange("desc"))
            viewModel.onEvent(UiEvent.OnCategoryChange("Other"))
            dirty() shouldBe true
            viewModel.onEvent(UiEvent.OnCategoryChange("Cat"))
            dirty() shouldBe false
            viewModel.onEvent(UiEvent.OnDryRunToggle(!viewModel.uiState.value.rule.isDryRun))
            dirty() shouldBe true
            viewModel.onEvent(UiEvent.OnDryRunToggle(!viewModel.uiState.value.rule.isDryRun))
            viewModel.onEvent(UiEvent.OnDeleteRawContentToggle(true))
            dirty() shouldBe true
        }

        @Test
        fun `changing apps scope mode and condition logic makes it dirty`() {
            loadExisting()
            viewModel.onEvent(UiEvent.OnAppsSelected(kotlinx.collections.immutable.persistentListOf(AppInfo("com.a", "A"))))
            dirty() shouldBe true
            viewModel.onEvent(UiEvent.OnAppsSelected(kotlinx.collections.immutable.persistentListOf()))
            dirty() shouldBe false
            viewModel.onEvent(UiEvent.OnConditionLogicChanged(dev.gaferneira.notificapp.domain.model.ConditionCombinator.ANY))
            dirty() shouldBe true
        }

        @Test
        fun `adding or removing conditions and actions makes it dirty`() {
            loadExisting()
            viewModel.onEvent(UiEvent.OnRemoveConditionClicked("c1"))
            dirty() shouldBe true
            viewModel.onEvent(UiEvent.OnConditionSaved(createTestCondition(id = "c1")))
            dirty() shouldBe false
            viewModel.onEvent(UiEvent.OnToggleActionClicked("a1", false))
            dirty() shouldBe true
            viewModel.onEvent(UiEvent.OnToggleActionClicked("a1", true))
            dirty() shouldBe false
            viewModel.onEvent(UiEvent.OnRemoveActionClicked("a1"))
            dirty() shouldBe true
        }

        @Test
        fun `back with unsaved changes asks for confirmation instead of leaving`() = runTest(testDispatcher) {
            loadExisting()
            viewModel.onEvent(UiEvent.OnNameChange("Other"))

            viewModel.onEvent(UiEvent.OnBackClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.showUnsavedChangesDialog shouldBe true
            coVerify(exactly = 0) { navigationHandler.goBack() }
        }

        @Test
        fun `confirming discard leaves and keeping dismisses the dialog`() = runTest(testDispatcher) {
            loadExisting()
            viewModel.onEvent(UiEvent.OnNameChange("Other"))
            viewModel.onEvent(UiEvent.OnBackClicked)

            viewModel.onEvent(UiEvent.OnDiscardDismissed)
            viewModel.uiState.value.showUnsavedChangesDialog shouldBe false
            coVerify(exactly = 0) { navigationHandler.goBack() }

            viewModel.onEvent(UiEvent.OnBackClicked)
            viewModel.onEvent(UiEvent.OnDiscardConfirmed)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.showUnsavedChangesDialog shouldBe false
            coVerify(exactly = 1) { navigationHandler.goBack() }
        }

        @Test
        fun `back from step two returns to step one even with unsaved changes`() = runTest(testDispatcher) {
            loadExisting()
            viewModel.onEvent(UiEvent.OnNameChange("Other"))
            viewModel.onEvent(UiEvent.OnContinueClicked)

            viewModel.onEvent(UiEvent.OnBackClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.currentStep shouldBe 1
            viewModel.uiState.value.showUnsavedChangesDialog shouldBe false
            coVerify(exactly = 0) { navigationHandler.goBack() }
        }
    }

    @Nested
    inner class SavedStateTests {

        @Test
        fun `edits are persisted to saved state and restored without reloading`() = runTest(testDispatcher) {
            // Given: an existing rule edited by the user, with the step advanced
            val handle = SavedStateHandle()
            val first = createViewModel(handle)
            stubRule(createTestRule(id = "rule-1", name = "Original"))
            first.initialize(ruleId = "rule-1")
            first.onEvent(UiEvent.OnNameChange("Edited"))
            first.onEvent(UiEvent.OnContinueClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: the process dies and a new ViewModel is created from the same saved state
            val restored = createViewModel(handle)
            restored.initialize(ruleId = "rule-1")

            // Then: the draft, baseline and step are restored, still dirty, and no reload happened
            val state = restored.uiState.value
            state.rule.id shouldBe "rule-1"
            state.rule.name shouldBe "Edited"
            state.initialRule.name shouldBe "Original"
            state.currentStep shouldBe 2
            state.hasUnsavedChanges shouldBe true
            coVerify(exactly = 1) { ruleRepository.getRule("rule-1") }
        }

        @Test
        fun `a restored new rule keeps a null id and a blank name`() = runTest(testDispatcher) {
            val handle = SavedStateHandle()
            val first = createViewModel(handle)
            first.initialize()
            first.onEvent(UiEvent.OnConditionSaved(createTestCondition(id = "c1")))
            testDispatcher.scheduler.advanceUntilIdle()

            val restored = createViewModel(handle)

            restored.uiState.value.rule.id shouldBe null
            restored.uiState.value.rule.name shouldBe ""
            restored.uiState.value.rule.triggers.size shouldBe 1
            restored.uiState.value.hasUnsavedChanges shouldBe true
        }

        @Test
        fun `nothing is persisted before the initial load completes`() = runTest(testDispatcher) {
            val handle = SavedStateHandle()
            val vm = createViewModel(handle)
            vm.onEvent(UiEvent.OnNameChange("typed too early"))
            testDispatcher.scheduler.advanceUntilIdle()

            handle.contains("rule_editor_draft") shouldBe false
        }

        @Test
        fun `a restored draft still re-fetches the sample notification`() = runTest(testDispatcher) {
            val handle = SavedStateHandle()
            val first = createViewModel(handle)
            first.initialize()
            first.onEvent(UiEvent.OnNameChange("Draft"))
            testDispatcher.scheduler.advanceUntilIdle()
            val notification = createTestNotification()
            coEvery { notificationRepository.getNotification(notification.id) } returns Result.success(notification)

            val restored = createViewModel(handle)
            restored.initialize(notificationId = notification.id)

            restored.uiState.value.sampleNotification shouldBe notification
            restored.uiState.value.rule.name shouldBe "Draft"
        }

        @Test
        fun `a corrupt saved state falls back to a normal load`() = runTest(testDispatcher) {
            val handle = SavedStateHandle(mapOf("rule_editor_draft" to "garbage", "rule_editor_initial" to "garbage"))
            stubRule(createTestRule(id = "rule-1", name = "Stored"))

            val vm = createViewModel(handle)
            vm.initialize(ruleId = "rule-1")

            vm.uiState.value.rule.name shouldBe "Stored"
        }
    }

    @Nested
    inner class StepNavigationTests {

        @Test
        fun `continue clicked navigates to step two`() {
            // When: continue is clicked
            viewModel.onEvent(UiEvent.OnContinueClicked)

            // Then: the current step becomes 2
            viewModel.uiState.value.currentStep shouldBe 2
        }

        @Test
        fun `back to logic clicked navigates to step one`() {
            // Given: currently on step 2
            viewModel.onEvent(UiEvent.OnContinueClicked)

            // When: navigating back to logic
            viewModel.onEvent(UiEvent.OnBackToLogicClicked)

            // Then: the current step becomes 1
            viewModel.uiState.value.currentStep shouldBe 1
        }
    }

    @Nested
    inner class NameDescriptionCategoryTests {

        @Test
        fun `name change updates the rule name and clears the name validation error`() {
            // Given: a prior validation error for the name field, triggered by an empty save
            viewModel.onEvent(UiEvent.OnSaveClicked)
            viewModel.uiState.value.validationErrors.containsKey("name") shouldBe true

            // When: changing the name
            viewModel.onEvent(UiEvent.OnNameChange("My Rule"))

            // Then: the name updates and the validation error clears
            val state = viewModel.uiState.value
            state.rule.name shouldBe "My Rule"
            state.validationErrors.containsKey("name") shouldBe false
        }

        @Test
        fun `add description clicked reveals the description field`() {
            // When: clicking add description
            viewModel.onEvent(UiEvent.OnAddDescriptionClicked)

            // Then: the description field becomes visible
            viewModel.uiState.value.showDescription shouldBe true
        }

        @Test
        fun `description change updates the rule description`() {
            // When: changing the description
            viewModel.onEvent(UiEvent.OnDescriptionChange("A description"))

            // Then: the rule description updates
            viewModel.uiState.value.rule.description shouldBe "A description"
        }

        @Test
        fun `add category clicked reveals the category field`() {
            // When: clicking add category
            viewModel.onEvent(UiEvent.OnAddCategoryClicked)

            // Then: the category field becomes visible
            viewModel.uiState.value.showCategory shouldBe true
        }

        @Test
        fun `category change updates the rule category`() {
            // When: changing the category
            viewModel.onEvent(UiEvent.OnCategoryChange("Finance"))

            // Then: the rule category updates
            viewModel.uiState.value.rule.category shouldBe "Finance"
        }

        @Test
        fun `dry run toggle updates the rule's dry-run flag`() {
            // Given: dry-run is on by default for new rules
            viewModel.uiState.value.rule.isDryRun shouldBe true

            // When: disabling dry-run
            viewModel.onEvent(UiEvent.OnDryRunToggle(false))

            // Then: the rule's dry-run flag is disabled
            viewModel.uiState.value.rule.isDryRun shouldBe false

            // When: enabling it again
            viewModel.onEvent(UiEvent.OnDryRunToggle(true))

            // Then: the flag is set
            viewModel.uiState.value.rule.isDryRun shouldBe true
        }
    }

    @Nested
    inner class ConditionsTests {

        @Test
        fun `add condition clicked shows the matching logic sheet for a new condition`() {
            // When: clicking add condition
            viewModel.onEvent(UiEvent.OnAddConditionClicked)

            // Then: the sheet is visible with no condition being edited
            val state = viewModel.uiState.value
            state.isMatchingLogicSheetVisible shouldBe true
            state.editingConditionId shouldBe null
        }

        @Test
        fun `remove condition clicked removes the matching condition`() {
            // Given: a saved condition
            viewModel.onEvent(UiEvent.OnConditionSaved(createTestCondition(id = "cond-1")))

            // When: removing it
            viewModel.onEvent(UiEvent.OnRemoveConditionClicked("cond-1"))

            // Then: the condition list is empty
            viewModel.uiState.value.rule.triggers shouldBe emptyList()
        }

        @Test
        fun `condition item clicked opens the sheet for editing an existing condition`() {
            // Given: a saved condition
            viewModel.onEvent(UiEvent.OnConditionSaved(createTestCondition(id = "cond-1")))

            // When: clicking the condition item
            viewModel.onEvent(UiEvent.OnConditionItemClicked("cond-1"))

            // Then: the sheet opens in editing mode
            val state = viewModel.uiState.value
            state.editingConditionId shouldBe "cond-1"
            state.isMatchingLogicSheetVisible shouldBe true
        }

        @Test
        fun `condition item clicked with an unknown id does nothing`() {
            // When: clicking a condition id that does not exist
            viewModel.onEvent(UiEvent.OnConditionItemClicked("unknown"))

            // Then: no sheet opens and no editing id is set
            val state = viewModel.uiState.value
            state.editingConditionId shouldBe null
            state.isMatchingLogicSheetVisible shouldBe false
        }

        @Test
        fun `condition saved with no editing id appends a new condition and closes the sheet`() {
            // Given: the matching logic sheet is open for a new condition
            viewModel.onEvent(UiEvent.OnAddConditionClicked)
            val condition = createTestCondition(id = "cond-1")

            // When: saving the condition
            viewModel.onEvent(UiEvent.OnConditionSaved(condition))

            // Then: the condition is appended and the sheet closes
            val state = viewModel.uiState.value
            state.rule.triggers shouldBe listOf(condition)
            state.isMatchingLogicSheetVisible shouldBe false
        }

        @Test
        fun `condition saved while editing updates the existing condition and clears editing state`() {
            // Given: an existing condition being edited
            val original = createTestCondition(id = "cond-1", value = "old")
            viewModel.onEvent(UiEvent.OnConditionSaved(original))
            viewModel.onEvent(UiEvent.OnConditionItemClicked("cond-1"))

            val updated = createTestCondition(id = "cond-1", value = "new")

            // When: saving the updated condition
            viewModel.onEvent(UiEvent.OnConditionSaved(updated))

            // Then: the condition is replaced and editing state clears
            val state = viewModel.uiState.value
            state.rule.triggers shouldBe listOf(updated)
            state.editingConditionId shouldBe null
            state.isMatchingLogicSheetVisible shouldBe false
        }
    }

    @Nested
    inner class AppsTests {

        @Test
        fun `apps clicked shows the app sheet`() {
            // When: clicking apps
            viewModel.onEvent(UiEvent.OnAppsClicked)

            // Then: the app sheet becomes visible
            viewModel.uiState.value.isAppSheetVisible shouldBe true
        }

        @Test
        fun `apps selected updates the target apps and hides the sheet`() {
            // Given: the app sheet is open
            viewModel.onEvent(UiEvent.OnAppsClicked)
            val apps = persistentListOf(AppInfo("com.a", "App A"))

            // When: selecting apps
            viewModel.onEvent(UiEvent.OnAppsSelected(apps))

            // Then: the target apps update and the sheet closes
            val state = viewModel.uiState.value
            state.rule.targetApps shouldBe apps
            state.isAppSheetVisible shouldBe false
        }

        @Test
        fun `app scope mode changed updates isIncludeMode`() {
            // Given: a rule with a selected app, defaulting to include mode
            viewModel.onEvent(UiEvent.OnAppsSelected(persistentListOf(AppInfo("com.a", "App A"))))
            viewModel.uiState.value.rule.isIncludeMode shouldBe true

            // When: switching to exclude mode
            viewModel.onEvent(UiEvent.OnAppScopeModeChanged(false))

            // Then: the rule is now in exclude mode
            viewModel.uiState.value.rule.isIncludeMode shouldBe false
        }

        @Test
        fun `app scope mode toggle is ignored when no apps are selected`() {
            // Given: a rule with no target apps
            viewModel.uiState.value.rule.targetApps shouldBe emptyList()

            // When: attempting to change scope mode
            viewModel.onEvent(UiEvent.OnAppScopeModeChanged(false))

            // Then: the mode stays at its default (include) because there is nothing to include/exclude
            viewModel.uiState.value.rule.isIncludeMode shouldBe true
        }
    }

    @Nested
    inner class ActionsTests {

        @Test
        fun `add action clicked shows the type picker`() {
            // When: clicking add action
            viewModel.onEvent(UiEvent.OnAddActionClicked)

            // Then: the type-picker dialog becomes visible
            viewModel.uiState.value.isActionTypePickerVisible shouldBe true
        }

        @Test
        fun `selecting a config type opens the action sheet seeded with that type`() {
            // Given: the picker is open
            viewModel.onEvent(UiEvent.OnAddActionClicked)

            // When: selecting a config action type
            viewModel.onEvent(UiEvent.OnActionTypeSelected(ActionType.CREATE_ALARM))

            // Then: the picker closes and the action sheet opens seeded with that type
            val state = viewModel.uiState.value
            state.isActionTypePickerVisible shouldBe false
            state.isActionSheetVisible shouldBe true
            state.pendingActionType shouldBe ActionType.CREATE_ALARM
            state.editingActionId shouldBe null
        }

        @Test
        fun `selecting Dismiss adds it directly without opening a sheet`() {
            // Given: the picker is open
            viewModel.onEvent(UiEvent.OnAddActionClicked)

            // When: selecting Dismiss (no configuration)
            viewModel.onEvent(UiEvent.OnActionTypeSelected(ActionType.DISMISS_NOTIFICATION))

            // Then: the dismiss action is added directly and no config sheet opens
            val state = viewModel.uiState.value
            state.rule.actions.map { it.type } shouldBe listOf(ActionType.DISMISS_NOTIFICATION)
            state.isActionSheetVisible shouldBe false
            state.pendingActionType shouldBe null
        }

        @Test
        fun `editing a Dismiss action is a no-op (nothing to configure)`() {
            // Given: an existing dismiss action
            viewModel.onEvent(UiEvent.OnActionTypeSelected(ActionType.DISMISS_NOTIFICATION))
            val dismissId = viewModel.uiState.value.rule.actions.single().id

            // When: tapping it to edit
            viewModel.onEvent(UiEvent.OnEditActionClicked(dismissId))

            // Then: no sheet opens
            val state = viewModel.uiState.value
            state.isActionSheetVisible shouldBe false
        }

        @Test
        fun `selecting Extract data opens the extract sheet without creating the action`() {
            // Given: the picker is open
            viewModel.onEvent(UiEvent.OnAddActionClicked)

            // When: selecting Extract data
            viewModel.onEvent(UiEvent.OnActionTypeSelected(ActionType.SAVE_DATA))

            // Then: the sheet opens seeded for a new action, but no SAVE_DATA action is created yet
            // (commit-on-confirm: the action is only added when the draft is confirmed)
            val state = viewModel.uiState.value
            state.rule.actions shouldBe emptyList()
            state.isActionSheetVisible shouldBe true
            state.pendingActionType shouldBe ActionType.SAVE_DATA
            state.editingActionId shouldBe null
        }

        @Test
        fun `editing a config action opens the sheet with the editing id set`() {
            // Given: an existing config action
            viewModel.onEvent(UiEvent.OnActionTypeSelected(ActionType.CREATE_ALARM))
            viewModel.onEvent(UiEvent.OnActionSaved(createTestAction(id = "alarm-1", type = ActionType.CREATE_ALARM)))

            // When: editing it
            viewModel.onEvent(UiEvent.OnEditActionClicked("alarm-1"))

            // Then: the sheet opens with the editing id set
            val state = viewModel.uiState.value
            state.editingActionId shouldBe "alarm-1"
            state.isActionSheetVisible shouldBe true
        }

        @Test
        fun `editing an Extract data action opens the extract sheet in edit mode`() {
            // Given: an existing SAVE_DATA action
            viewModel.onEvent(UiEvent.OnExtractDataCommitted(persistentListOf(createTestField(method = ExtractionMethod.SmartAmountDetection))))
            val saveId = viewModel.uiState.value.rule.actions.single { it.type == ActionType.SAVE_DATA }.id

            // When: editing it
            viewModel.onEvent(UiEvent.OnEditActionClicked(saveId))

            // Then: the Extract-data sheet opens with the editing id set so the sheet shows "Update"
            val state = viewModel.uiState.value
            state.isActionSheetVisible shouldBe true
            state.editingActionId shouldBe saveId
        }

        @Test
        fun `editing an unknown action id is a no-op`() {
            // When: editing an id that isn't on the rule
            viewModel.onEvent(UiEvent.OnEditActionClicked("nope"))

            // Then: nothing opens
            val state = viewModel.uiState.value
            state.isActionSheetVisible shouldBe false
        }

        @Test
        fun `removing a config action removes it without confirmation`() {
            // Given: a saved config action
            viewModel.onEvent(UiEvent.OnActionSaved(createTestAction(id = "alarm-1", type = ActionType.CREATE_ALARM)))

            // When: removing it
            viewModel.onEvent(UiEvent.OnRemoveActionClicked("alarm-1"))

            // Then: the action list is empty and no confirmation was requested
            val state = viewModel.uiState.value
            state.rule.actions shouldBe emptyList()
            state.pendingExtractDataRemovalId shouldBe null
        }

        @Test
        fun `action saved with no editing id appends a new action and closes the sheet`() {
            // Given: the action sheet is open for a new config action
            viewModel.onEvent(UiEvent.OnActionTypeSelected(ActionType.CREATE_ALARM))
            val action = createTestAction(id = "alarm-1", type = ActionType.CREATE_ALARM)

            // When: saving the action
            viewModel.onEvent(UiEvent.OnActionSaved(action))

            // Then: the action is appended and the sheet closes
            val state = viewModel.uiState.value
            state.rule.actions shouldBe listOf(action)
            state.isActionSheetVisible shouldBe false
            state.pendingActionType shouldBe null
        }

        @Test
        fun `action saved while editing updates the existing action and clears editing state`() {
            // Given: an existing config action being edited
            val original = createTestAction(id = "alarm-1", type = ActionType.CREATE_ALARM, isEnabled = true)
            viewModel.onEvent(UiEvent.OnActionSaved(original))
            viewModel.onEvent(UiEvent.OnEditActionClicked("alarm-1"))

            val updated = createTestAction(id = "alarm-1", type = ActionType.CREATE_ALARM, isEnabled = false)

            // When: saving the updated action
            viewModel.onEvent(UiEvent.OnActionSaved(updated))

            // Then: the action is replaced and editing state clears
            val state = viewModel.uiState.value
            state.rule.actions shouldBe listOf(updated)
            state.editingActionId shouldBe null
            state.isActionSheetVisible shouldBe false
        }

        @Test
        fun `toggling an action flips its enabled state`() {
            // Given: an enabled config action
            viewModel.onEvent(UiEvent.OnActionSaved(createTestAction(id = "alarm-1", type = ActionType.CREATE_ALARM, isEnabled = true)))

            // When: toggling it off
            viewModel.onEvent(UiEvent.OnToggleActionClicked("alarm-1", enabled = false))

            // Then: the action is disabled
            viewModel.uiState.value.rule.actions.single().isEnabled shouldBe false
        }

        @Test
        fun `re-enabling a throttled snooze action stamps a fresh reset watermark`() {
            // Given: a disabled throttle snooze action with a stale reset watermark
            val staleAction = RuleAction.createThrottleSnooze(
                id = "snooze-1",
                windowMinutes = 10,
                resetAt = 1_000L,
                isEnabled = false,
            )
            viewModel.onEvent(UiEvent.OnActionSaved(staleAction))

            // When: re-enabling it
            viewModel.onEvent(UiEvent.OnToggleActionClicked("snooze-1", enabled = true))

            // Then: it is enabled again and the watermark moved forward (no longer the stale value)
            val toggled = viewModel.uiState.value.rule.actions.single()
            toggled.isEnabled shouldBe true
            toggled.getThrottleResetAt() shouldNotBe 1_000L
        }

        @Test
        fun `disabling a throttled snooze action does not touch the reset watermark`() {
            // Given: an enabled throttle snooze action
            val action = RuleAction.createThrottleSnooze(id = "snooze-1", windowMinutes = 10, resetAt = 1_000L)
            viewModel.onEvent(UiEvent.OnActionSaved(action))

            // When: disabling it
            viewModel.onEvent(UiEvent.OnToggleActionClicked("snooze-1", enabled = false))

            // Then: the watermark is unchanged - only a re-enable stamps a fresh one
            val toggled = viewModel.uiState.value.rule.actions.single()
            toggled.isEnabled shouldBe false
            toggled.getThrottleResetAt() shouldBe 1_000L
        }

        @Test
        fun `removing Extract data with fields asks for confirmation before clearing`() {
            // Given: an Extract-data action committed with a field
            val field = createTestField(method = ExtractionMethod.RegexPattern("\\d+"))
            viewModel.onEvent(UiEvent.OnExtractDataCommitted(persistentListOf(field)))
            val saveId = viewModel.uiState.value.rule.actions.single { it.type == ActionType.SAVE_DATA }.id

            // When: removing the Extract-data action
            viewModel.onEvent(UiEvent.OnRemoveActionClicked(saveId))

            // Then: removal is pending confirmation; nothing is removed yet
            val pending = viewModel.uiState.value
            pending.pendingExtractDataRemovalId shouldBe saveId
            pending.rule.actions.map { it.type } shouldBe listOf(ActionType.SAVE_DATA)
            pending.rule.fields.size shouldBe 1

            // When: confirming
            viewModel.onEvent(UiEvent.OnConfirmExtractDataRemoval)

            // Then: the action and its fields are cleared
            val confirmed = viewModel.uiState.value
            confirmed.rule.actions shouldBe emptyList()
            confirmed.rule.fields shouldBe emptyList()
            confirmed.pendingExtractDataRemovalId shouldBe null
        }

        @Test
        fun `removing empty Extract data clears without confirmation`() {
            // Given: a legacy Extract-data action with no fields (e.g. loaded from an older rule)
            viewModel.onEvent(UiEvent.OnActionSaved(createTestAction(id = "save-1", type = ActionType.SAVE_DATA)))

            // When: removing it
            viewModel.onEvent(UiEvent.OnRemoveActionClicked("save-1"))

            // Then: removed immediately, no confirmation
            val state = viewModel.uiState.value
            state.rule.actions shouldBe emptyList()
            state.pendingExtractDataRemovalId shouldBe null
        }
    }

    @Nested
    inner class ExtractDataCommitTests {

        @Test
        fun `committing extract data adds a SAVE_DATA action and sets the fields`() {
            // Given: a rule with no actions
            val fields = persistentListOf(
                createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection),
                createTestField(id = "f2", method = ExtractionMethod.SmartDateDetection),
            )

            // When: committing the extract-data draft
            viewModel.onEvent(UiEvent.OnExtractDataCommitted(fields))

            // Then: a single SAVE_DATA action is added and the rule's fields are set
            val state = viewModel.uiState.value
            state.rule.actions.map { it.type } shouldBe listOf(ActionType.SAVE_DATA)
            state.rule.fields shouldBe fields
            state.isActionSheetVisible shouldBe false
            state.editingActionId shouldBe null
            state.pendingActionType shouldBe null
        }

        @Test
        fun `committing extract data again replaces the fields without duplicating the action`() {
            // Given: an already-committed extract-data action
            viewModel.onEvent(UiEvent.OnExtractDataCommitted(persistentListOf(createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection))))
            val saveId = viewModel.uiState.value.rule.actions.single().id

            // When: committing a different set of fields (edit flow)
            val updated = persistentListOf(createTestField(id = "f2", method = ExtractionMethod.SmartDateDetection))
            viewModel.onEvent(UiEvent.OnExtractDataCommitted(updated))

            // Then: the same single SAVE_DATA action remains and the fields are replaced
            val state = viewModel.uiState.value
            state.rule.actions.map { it.id } shouldBe listOf(saveId)
            state.rule.fields shouldBe updated
        }
    }

    @Nested
    inner class TestAgainstHistoryTests {

        @Test
        fun `test against history with no captured notifications yields zero matches`() = runTest(testDispatcher) {
            // Given: no notifications have ever been captured
            coEvery { notificationRepository.getNotificationsForBacktest(any(), any(), any()) } returns Result.success(emptyList())

            // When: testing against history
            viewModel.onEvent(UiEvent.OnTestAgainstHistoryClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: results are empty and nothing was tested
            val state = viewModel.uiState.value
            state.isBacktesting shouldBe false
            state.backtestResults shouldBe emptyList()
            state.backtestTestedCount shouldBe 0
        }

        @Test
        fun `test against history matches notifications whose conditions match and extracts fields`() = runTest(testDispatcher) {
            // Given: a draft rule matching content containing "Total" with an extraction field
            val condition = createTestCondition(value = "Total")
            viewModel.onEvent(UiEvent.OnConditionSaved(condition))
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Total: "))
            viewModel.onEvent(UiEvent.OnExtractDataCommitted(persistentListOf(field)))

            val matching = createTestNotification(id = "n1", content = "Total: 100", rawContent = "Total: 100")
            val nonMatching = createTestNotification(id = "n2", content = "no match here", rawContent = "no match here")
            coEvery { notificationRepository.getNotificationsForBacktest(any(), any(), any()) } returns Result.success(listOf(matching, nonMatching))

            // When: testing against history
            viewModel.onEvent(UiEvent.OnTestAgainstHistoryClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: only the matching notification is returned, with its field extracted
            val state = viewModel.uiState.value
            state.backtestTestedCount shouldBe 2
            state.backtestResults?.map { it.notification.id } shouldBe listOf("n1")
            state.backtestResults?.first()?.extractedData?.get(field.id) shouldBe "100"
        }

        @Test
        fun `test against history filters candidates to the rule's target apps`() = runTest(testDispatcher) {
            // Given: a draft rule that always matches, scoped to a single target app
            viewModel.onEvent(UiEvent.OnConditionSaved(createTestCondition()))
            viewModel.onEvent(UiEvent.OnAppsSelected(persistentListOf(AppInfo("com.a", "App A"))))

            val fromTargetApp = createTestNotification(id = "n1", packageName = "com.a")
            coEvery {
                notificationRepository.getNotificationsForBacktest(listOf("com.a"), true, any())
            } returns Result.success(listOf(fromTargetApp))

            // When: testing against history
            viewModel.onEvent(UiEvent.OnTestAgainstHistoryClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: only the notification from the target app is tested and matched
            val state = viewModel.uiState.value
            state.backtestTestedCount shouldBe 1
            state.backtestResults?.map { it.notification.id } shouldBe listOf("n1")
        }

        @Test
        fun `test against history passes exclude mode to the repository`() = runTest(testDispatcher) {
            // Given: a draft rule in exclude mode with one excluded app
            viewModel.onEvent(UiEvent.OnConditionSaved(createTestCondition()))
            viewModel.onEvent(UiEvent.OnAppsSelected(persistentListOf(AppInfo("com.a", "App A"))))
            viewModel.onEvent(UiEvent.OnAppScopeModeChanged(false))

            val fromOtherApp = createTestNotification(id = "n1", packageName = "com.b")
            coEvery {
                notificationRepository.getNotificationsForBacktest(listOf("com.a"), false, any())
            } returns Result.success(listOf(fromOtherApp))

            // When: testing against history
            viewModel.onEvent(UiEvent.OnTestAgainstHistoryClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the repository is called with exclude mode and the candidate is tested
            val state = viewModel.uiState.value
            state.backtestTestedCount shouldBe 1
            state.backtestResults?.map { it.notification.id } shouldBe listOf("n1")
        }

        @Test
        fun `test against history evaluates each notification against its own captured timestamp, not wall-clock now`() = runTest(testDispatcher) {
            // Given: a draft rule gated on a TimeRangeCondition that only matches 09:00-10:00,
            // and two notifications captured at different times of day
            viewModel.onEvent(UiEvent.OnConditionSaved(createTestTimeRangeCondition(start = LocalTime.of(9, 0), end = LocalTime.of(10, 0))))

            val zone = ZoneId.systemDefault()
            val capturedInsideWindow = LocalDateTime.of(2026, 7, 6, 9, 30).atZone(zone).toInstant().toEpochMilli()
            val capturedOutsideWindow = LocalDateTime.of(2026, 7, 6, 15, 0).atZone(zone).toInstant().toEpochMilli()
            val insideWindow = createTestNotification(id = "n1", timestamp = capturedInsideWindow)
            val outsideWindow = createTestNotification(id = "n2", timestamp = capturedOutsideWindow)
            coEvery { notificationRepository.getNotificationsForBacktest(any(), any(), any()) } returns Result.success(listOf(insideWindow, outsideWindow))

            // When: testing against history
            viewModel.onEvent(UiEvent.OnTestAgainstHistoryClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: only the notification captured inside the time window matches - each
            // candidate was evaluated against its own timestamp, not a shared wall-clock instant
            val state = viewModel.uiState.value
            state.backtestTestedCount shouldBe 2
            state.backtestResults?.map { it.notification.id } shouldBe listOf("n1")
        }

        @Test
        fun `test against history failure surfaces an error and stops the loading state`() = runTest(testDispatcher) {
            // Given: the notification repository fails
            coEvery {
                notificationRepository.getNotificationsForBacktest(any(), any(), any())
            } returns Result.failure(IllegalStateException("db error"))

            viewModel.effect.test {
                // When: testing against history
                viewModel.onEvent(UiEvent.OnTestAgainstHistoryClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a ShowError effect is sent and the loading state clears
                awaitItem().shouldBeInstanceOf<UiEffect.ShowError>().message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe
                    R.string.rule_editor_error_backtest
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.uiState.value.isBacktesting shouldBe false
        }

        @Test
        fun `dismiss backtest results clears them`() = runTest(testDispatcher) {
            // Given: a completed backtest run
            coEvery { notificationRepository.getNotificationsForBacktest(any(), any(), any()) } returns Result.success(emptyList())
            viewModel.onEvent(UiEvent.OnTestAgainstHistoryClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // When: dismissing the results
            viewModel.onEvent(UiEvent.OnDismissBacktestResults)

            // Then: results are cleared
            viewModel.uiState.value.backtestResults shouldBe null
        }
    }

    @Nested
    inner class SaveRuleTests {

        @Test
        fun `save with a blank name sets a validation error and sends ShowError without calling the repository`() = runTest(testDispatcher) {
            viewModel.effect.test {
                // When: saving with the default blank name
                viewModel.onEvent(UiEvent.OnSaveClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a validation error is set, a ShowError effect is sent, and no repository call happens
                viewModel.uiState.value.validationErrors["name"].shouldBeInstanceOf<UiText.StringResource>().id shouldBe
                    R.string.rule_editor_error_name_required
                awaitItem().shouldBeInstanceOf<UiEffect.ShowError>().message.shouldBeInstanceOf<UiText.StringResource>().id shouldBe
                    R.string.rule_editor_error_enter_name
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { ruleRepository.saveRule(any()) }
        }

        @Test
        fun `saving a new rule calls saveRule, posts a success message, and navigates back`() = runTest(testDispatcher) {
            // Given: a valid rule name and a repository that succeeds
            viewModel.onEvent(UiEvent.OnNameChange("My Rule"))
            coEvery { ruleRepository.saveRule(any()) } returns Result.success(Unit)

            appMessenger.messages.test {
                // When: saving
                viewModel.onEvent(UiEvent.OnSaveClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a new rule is saved, the success message is delivered app-wide, and navigation goes back
                coVerify(exactly = 1) { ruleRepository.saveRule(any()) }
                coVerify(exactly = 0) { ruleRepository.updateRule(any()) }
                awaitItem().shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_editor_saved
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { navigationHandler.goBack() }
            val state = viewModel.uiState.value
            state.isSaving shouldBe false
            state.hasUnsavedChanges shouldBe false
        }

        @Test
        fun `a second save tap while saving is ignored`() = runTest(testDispatcher) {
            viewModel.onEvent(UiEvent.OnNameChange("My Rule"))
            coEvery { ruleRepository.saveRule(any()) } returns Result.success(Unit)

            viewModel.onEvent(UiEvent.OnSaveClicked)
            viewModel.onEvent(UiEvent.OnSaveClicked)
            viewModel.uiState.value.isSaving shouldBe true
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { ruleRepository.saveRule(any()) }
        }

        @Test
        fun `saving an existing rule calls updateRule instead of saveRule`() = runTest(testDispatcher) {
            // Given: an existing rule loaded from the repository
            val rule = createTestRule(id = "rule-1")
            coEvery { ruleRepository.getRule("rule-1") } returns Result.success(rule)
            viewModel.initialize(ruleId = "rule-1")
            coEvery { ruleRepository.updateRule(any()) } returns Result.success(Unit)

            // When: saving
            viewModel.onEvent(UiEvent.OnSaveClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: updateRule is called instead of saveRule
            coVerify(exactly = 1) { ruleRepository.updateRule(any()) }
            coVerify(exactly = 0) { ruleRepository.saveRule(any()) }
        }

        @Test
        fun `saving that fails sets a dismissible error and does not navigate back`() = runTest(testDispatcher) {
            // Given: a valid rule name and a repository that fails
            viewModel.onEvent(UiEvent.OnNameChange("My Rule"))
            coEvery { ruleRepository.saveRule(any()) } returns Result.failure(RuntimeException("write failed"))

            // When: saving
            viewModel.onEvent(UiEvent.OnSaveClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: saving stops, a localized error is set, the draft stays dirty, and the error can be dismissed
            val state = viewModel.uiState.value
            state.isSaving shouldBe false
            state.error.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_editor_error_save
            coVerify(exactly = 0) { navigationHandler.goBack() }

            viewModel.onEvent(UiEvent.OnDismissError)
            viewModel.uiState.value.error shouldBe null
        }
    }

    @Nested
    inner class DeleteRuleTests {

        @Test
        fun `delete clicked shows the delete confirmation dialog`() {
            // When: clicking delete
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            // Then: the confirmation dialog is shown
            viewModel.uiState.value.showDeleteConfirmation shouldBe true
        }

        @Test
        fun `delete dismissed hides the delete confirmation dialog`() {
            // Given: the confirmation dialog is showing
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            // When: dismissing it
            viewModel.onEvent(UiEvent.OnDeleteDismissed)

            // Then: the confirmation dialog is hidden
            viewModel.uiState.value.showDeleteConfirmation shouldBe false
        }

        @Test
        fun `delete confirmed with no rule id does not call the repository`() = runTest(testDispatcher) {
            // Given: a rule with no id (never saved)
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            // When: confirming delete
            viewModel.onEvent(UiEvent.OnDeleteConfirmed)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: no delete call happens
            coVerify(exactly = 0) { ruleRepository.deleteRule(any()) }
        }

        @Test
        fun `delete confirmed for an existing rule deletes it, posts a message, and navigates back`() = runTest(testDispatcher) {
            // Given: an existing rule loaded from the repository
            stubRule(createTestRule(id = "rule-1"))
            viewModel.initialize(ruleId = "rule-1")
            coEvery { ruleRepository.deleteRule("rule-1") } returns Result.success(Unit)
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            appMessenger.messages.test {
                // When: confirming delete
                viewModel.onEvent(UiEvent.OnDeleteConfirmed)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: the dialog closes and the success message is delivered app-wide
                val state = viewModel.uiState.value
                state.isSaving shouldBe false
                state.showDeleteConfirmation shouldBe false
                awaitItem().shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_editor_deleted
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { navigationHandler.goBack() }
        }

        @Test
        fun `delete confirmed that fails sets an error and does not navigate back`() = runTest(testDispatcher) {
            // Given: an existing rule whose deletion fails
            stubRule(createTestRule(id = "rule-1"))
            viewModel.initialize(ruleId = "rule-1")
            coEvery { ruleRepository.deleteRule("rule-1") } returns Result.failure(RuntimeException("delete failed"))
            viewModel.onEvent(UiEvent.OnDeleteClicked)

            // When: confirming delete
            viewModel.onEvent(UiEvent.OnDeleteConfirmed)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: a localized error is set and the editor stays open
            val state = viewModel.uiState.value
            state.isSaving shouldBe false
            state.error.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.rule_editor_error_delete
            coVerify(exactly = 0) { navigationHandler.goBack() }
        }
    }

    @Nested
    inner class BackAndDismissTests {

        @Test
        fun `back clicked navigates back`() = runTest(testDispatcher) {
            // When: clicking back
            viewModel.onEvent(UiEvent.OnBackClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: navigation goes back
            coVerify(exactly = 1) { navigationHandler.goBack() }
        }

        @Test
        fun `dismiss error keeps validation errors`() {
            // Given: a blank-name save attempt that produced a validation error
            viewModel.onEvent(UiEvent.OnSaveClicked)

            // When: dismissing the error
            viewModel.onEvent(UiEvent.OnDismissError)

            // Then: the error is clear and the inline validation error remains
            viewModel.uiState.value.error shouldBe null
            viewModel.uiState.value.validationErrors.containsKey("name") shouldBe true
        }

        @Test
        fun `dismiss sheet hides all bottom sheets and clears editing ids`() {
            // Given: multiple sheets open with editing ids set
            viewModel.onEvent(UiEvent.OnConditionItemClicked("cond-1"))
            viewModel.onEvent(UiEvent.OnEditActionClicked("action-1"))
            viewModel.onEvent(UiEvent.OnAppsClicked)

            // When: dismissing the sheet
            viewModel.onEvent(UiEvent.OnDismissSheet)

            // Then: every sheet visibility flag and editing id resets
            val state = viewModel.uiState.value
            state.isActionSheetVisible shouldBe false
            state.isMatchingLogicSheetVisible shouldBe false
            state.isAppSheetVisible shouldBe false
            state.editingConditionId shouldBe null
            state.editingActionId shouldBe null
        }
    }
}
