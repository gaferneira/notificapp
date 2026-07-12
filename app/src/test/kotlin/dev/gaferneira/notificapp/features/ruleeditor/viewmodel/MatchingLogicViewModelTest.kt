package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract.UiState
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchingLogicViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MatchingLogicViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MatchingLogicViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class InitForEditTests {

        @Test
        fun `hydrates state from the given condition and switches to EDIT mode`() {
            val condition = RuleCondition(
                id = "cond-1",
                condition = MatchingCondition.TITLE,
                operator = MatchingOperator.EQUALS,
                value = "Payment received",
            )

            viewModel.onEvent(UiEvent.InitForEdit(condition))

            val state = viewModel.uiState.value
            state.mode shouldBe UiState.Mode.EDIT
            state.matchingCondition shouldBe MatchingCondition.TITLE
            state.matchingOperator shouldBe MatchingOperator.EQUALS
            state.matchingValue shouldBe "Payment received"
            state.validationError shouldBe null
        }

        @Test
        fun `falls back to default condition, operator, and empty value when the fields are null`() {
            val condition = RuleCondition(id = "cond-1", condition = null, operator = null, value = null)

            viewModel.onEvent(UiEvent.InitForEdit(condition))

            val state = viewModel.uiState.value
            state.matchingCondition shouldBe MatchingCondition.TEXT_CONTENT
            state.matchingOperator shouldBe MatchingOperator.CONTAINS
            state.matchingValue shouldBe ""
        }
    }

    @Nested
    inner class FieldUpdateTests {

        @Test
        fun `updating the matching condition replaces it in state`() {
            viewModel.onEvent(UiEvent.OnMatchingConditionChange(MatchingCondition.APP_NAME))

            viewModel.uiState.value.matchingCondition shouldBe MatchingCondition.APP_NAME
        }

        @Test
        fun `updating the matching operator replaces it in state`() {
            viewModel.onEvent(UiEvent.OnMatchingOperatorChange(MatchingOperator.REGEX_MATCH))

            viewModel.uiState.value.matchingOperator shouldBe MatchingOperator.REGEX_MATCH
        }

        @Test
        fun `updating the matching value replaces it and clears any validation error`() {
            viewModel.onEvent(UiEvent.OnMatchingValueChange(""))
            viewModel.onEvent(UiEvent.OnConfirm)
            viewModel.uiState.value.validationError shouldBe "Please enter a value to match"

            viewModel.onEvent(UiEvent.OnMatchingValueChange("purchase"))

            val state = viewModel.uiState.value
            state.matchingValue shouldBe "purchase"
            state.validationError shouldBe null
        }

        @Test
        fun `OnClearError resets only the validation error`() {
            viewModel.onEvent(UiEvent.OnConfirm)
            viewModel.uiState.value.validationError shouldBe "Please enter a value to match"

            viewModel.onEvent(UiEvent.OnClearError)

            viewModel.uiState.value.validationError shouldBe null
        }
    }

    @Nested
    inner class ConfirmTests {

        @Test
        fun `confirm with a blank value sets a validation error and emits ShowError without dismissing`() = runTest(testDispatcher) {
            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe UiEffect.ShowError("Please enter a value to match")
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.uiState.value.validationError shouldBe "Please enter a value to match"
        }

        @Test
        fun `confirm in ADD mode emits ConditionCreated with a fresh id, then Dismiss`() = runTest(testDispatcher) {
            viewModel.onEvent(UiEvent.OnMatchingValueChange("purchase"))

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                val created = awaitItem()
                created.shouldBeInstanceOf<UiEffect.ConditionCreated>().condition.value shouldBe "purchase"
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `confirm in EDIT mode emits ConditionUpdated with the original id, then Dismiss`() = runTest(testDispatcher) {
            val original = RuleCondition(
                id = "cond-1",
                condition = MatchingCondition.TITLE,
                operator = MatchingOperator.EQUALS,
                value = "old value",
            )
            viewModel.onEvent(UiEvent.InitForEdit(original))
            viewModel.onEvent(UiEvent.OnMatchingValueChange("new value"))

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                val updated = awaitItem().shouldBeInstanceOf<UiEffect.ConditionUpdated>()
                updated.conditionId shouldBe "cond-1"
                updated.condition.value shouldBe "new value"
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class DismissTests {

        @Test
        fun `dismiss resets state to defaults and emits Dismiss`() = runTest(testDispatcher) {
            viewModel.onEvent(UiEvent.OnMatchingValueChange("purchase"))

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnDismiss)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.uiState.value shouldBe UiState()
        }

        @Test
        fun `dismiss after editing clears the tracked editing id, so a later confirm creates instead of updates`() = runTest(testDispatcher) {
            val original = RuleCondition(id = "cond-1", condition = MatchingCondition.TITLE, operator = MatchingOperator.EQUALS, value = "old")
            viewModel.onEvent(UiEvent.InitForEdit(original))

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnDismiss)
                testDispatcher.scheduler.advanceUntilIdle()
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onEvent(UiEvent.OnMatchingValueChange("fresh"))
            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem().shouldBeInstanceOf<UiEffect.ConditionCreated>()
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
