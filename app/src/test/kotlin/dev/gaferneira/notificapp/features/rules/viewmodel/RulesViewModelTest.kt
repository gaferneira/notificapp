package dev.gaferneira.notificapp.features.rules.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var ruleRepository: RuleRepository
    private lateinit var viewModel: RulesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ruleRepository = mockk()
        every { ruleRepository.observeAllRules() } returns MutableStateFlow(emptyList())
        viewModel = RulesViewModel(ruleRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class ExportTests {

        @Test
        fun `export sends a ShareRule effect with the rule's encoded JSON`() = runTest(testDispatcher) {
            // Given: an existing rule
            val rule = createTestRule(id = "rule-1", name = "Bank payment")
            coEvery { ruleRepository.getRule("rule-1") } returns Result.success(rule)

            viewModel.effect.test {
                // When: exporting it
                viewModel.onEvent(RulesEvent.OnExportRuleClick("rule-1"))
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a ShareRule effect carries the rule's name and encoded JSON
                awaitItem() shouldBe RulesEffect.ShareRule(ruleName = "Bank payment", json = RuleJsonCodec.encode(rule))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `export of a missing rule sends ShowError`() = runTest(testDispatcher) {
            // Given: no rule with this id exists
            coEvery { ruleRepository.getRule("missing") } returns Result.success(null)

            viewModel.effect.test {
                // When: exporting it
                viewModel.onEvent(RulesEvent.OnExportRuleClick("missing"))
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: an error effect is sent
                awaitItem() shouldBe RulesEffect.ShowError("Rule not found")
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
            // version doesn't recognize (e.g. exported from a newer version)
            val rule = createTestRule(
                id = "rule-1",
                name = "Bank payment",
                actions = listOf(createTestAction(type = ActionType.SAVE_DATA)),
            )
            val json = RuleJsonCodec.encode(rule).replace("\"save_data\"", "\"send_webhook\"")

            // When: the text is received
            viewModel.onEvent(RulesEvent.OnRuleTextReceived(json))

            // Then: the preview still succeeds and reports the skipped action
            val state = viewModel.uiState.value
            state.importPreview.shouldNotBeNull()
            state.importSkippedActions shouldBe listOf("send_webhook")
        }

        @Test
        fun `receiving malformed rule text sets an import error and no preview`() {
            // When: receiving garbage input
            viewModel.onEvent(RulesEvent.OnRuleTextReceived("not json at all"))

            // Then: an error is set and there is no preview to confirm
            val state = viewModel.uiState.value
            state.importPreview shouldBe null
            state.importError.shouldNotBeNull()
        }

        @Test
        fun `confirming import saves the preview rule and clears it`() = runTest(testDispatcher) {
            // Given: a decoded import preview
            val rule = createTestRule(id = "rule-1", name = "Bank payment")
            viewModel.onEvent(RulesEvent.OnRuleTextReceived(RuleJsonCodec.encode(rule)))
            val preview = viewModel.uiState.value.importPreview
            preview.shouldNotBeNull()
            coEvery { ruleRepository.saveRule(preview) } returns Result.success(Unit)

            // When: confirming the import
            viewModel.onEvent(RulesEvent.OnImportConfirmed)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview rule is saved and the preview clears
            viewModel.uiState.value.importPreview shouldBe null
            coVerify(exactly = 1) { ruleRepository.saveRule(preview) }
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
            coVerify(exactly = 0) { ruleRepository.saveRule(any()) }
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
}
