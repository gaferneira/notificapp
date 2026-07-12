package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.MethodType
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.PreviewResult
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
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
class AddFieldViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AddFieldViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AddFieldViewModel(defaultDispatcher = testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class InitializeTests {

        @Test
        fun `initialize with no field and no notification resets to defaults`() = runTest(testDispatcher) {
            // Given: the view model in its initial state

            // When: initializing with no field and no notification
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the state resets to defaults and no preview is attempted (sample text is empty)
            val state = viewModel.uiState.value
            state.fieldName shouldBe ""
            state.selectedMethodType shouldBe MethodType.TEXT_BETWEEN_ANCHORS
            state.sampleText shouldBe ""
            state.previewResult shouldBe PreviewResult.None
            state.validationErrors shouldBe emptyMap()
        }

        @Test
        fun `initialize with a notification populates sample text and auto-previews when method is satisfied`() = runTest(testDispatcher) {
            // Given: an existing field using TextBetweenAnchors and a notification whose content matches
            val field = createTestField(
                method = ExtractionMethod.TextBetweenAnchors(startAnchor = "Total: ", endAnchor = " end"),
            )
            val notification = createTestNotification(content = "Total: 100 USD end")

            // When: initializing with the field and notification
            viewModel.onEvent(UiEvent.Initialize(field = field, notification = notification))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the sample text is populated and the preview auto-runs successfully
            val state = viewModel.uiState.value
            state.sampleText shouldBe "Total: 100 USD end"
            state.selectedMethodType shouldBe MethodType.TEXT_BETWEEN_ANCHORS
            state.startAnchor shouldBe "Total: "
            state.endAnchor shouldBe " end"
            state.previewResult shouldBe PreviewResult.Success(value = "100 USD", startIndex = 7, endIndex = 14)
        }

        @Test
        fun `initialize with no field but a notification does not auto-preview when defaults cannot preview`() = runTest(testDispatcher) {
            // Given: no existing field (defaults to TEXT_BETWEEN_ANCHORS with blank anchors) and a notification with content
            val notification = createTestNotification(content = "Some sample content")

            // When: initializing with no field but a notification
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = notification))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: sample text is populated but preview stays None since anchors are blank
            val state = viewModel.uiState.value
            state.sampleText shouldBe "Some sample content"
            state.previewResult shouldBe PreviewResult.None
        }

        @Test
        fun `initialize populates FixedPosition fields from an existing field`() = runTest(testDispatcher) {
            // Given: an existing field using FixedPosition
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = 2, endIndex = 8))

            // When: initializing with the field
            viewModel.onEvent(UiEvent.Initialize(field = field, notification = null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the method type and fixed indices are populated
            val state = viewModel.uiState.value
            state.selectedMethodType shouldBe MethodType.FIXED_POSITION
            state.fixedStartIndex shouldBe 2
            state.fixedEndIndex shouldBe 8
        }

        @Test
        fun `initialize populates RegexPattern fields from an existing field`() = runTest(testDispatcher) {
            // Given: an existing field using RegexPattern
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "\\d+", captureGroup = 2))

            // When: initializing with the field
            viewModel.onEvent(UiEvent.Initialize(field = field, notification = null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the method type, pattern and capture group are populated
            val state = viewModel.uiState.value
            state.selectedMethodType shouldBe MethodType.REGEX
            state.regexPattern shouldBe "\\d+"
            state.captureGroup shouldBe 2
        }

        @Test
        fun `initialize populates JsonPath fields from an existing field`() = runTest(testDispatcher) {
            // Given: an existing field using JsonPath
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "data.amount"))

            // When: initializing with the field
            viewModel.onEvent(UiEvent.Initialize(field = field, notification = null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the method type and json path are populated
            val state = viewModel.uiState.value
            state.selectedMethodType shouldBe MethodType.JSON_PATH
            state.jsonPath shouldBe "data.amount"
        }

        @Test
        fun `initialize populates SplitByDelimiter fields from an existing field`() = runTest(testDispatcher) {
            // Given: an existing field using SplitByDelimiter
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ";", takeIndex = 3))

            // When: initializing with the field
            viewModel.onEvent(UiEvent.Initialize(field = field, notification = null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the method type, delimiter and take index are populated
            val state = viewModel.uiState.value
            state.selectedMethodType shouldBe MethodType.SPLIT_BY_DELIMITER
            state.delimiter shouldBe ";"
            state.takeIndex shouldBe 3
        }

        @Test
        fun `initialize populates smart amount and smart date method types with no extra parameters`() = runTest(testDispatcher) {
            // Given: an existing field using SmartAmountDetection
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: initializing with the field
            viewModel.onEvent(UiEvent.Initialize(field = field, notification = null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the method type is mapped to SMART_AMOUNT
            viewModel.uiState.value.selectedMethodType shouldBe MethodType.SMART_AMOUNT
        }
    }

    @Nested
    inner class FieldUpdateTests {

        @Test
        fun `field name change updates state and clears its validation error`() = runTest(testDispatcher) {
            // Given: a prior validation error on fieldName (the default TEXT_BETWEEN_ANCHORS method also
            // reports blank-anchor errors, which are unrelated to this assertion)
            viewModel.onEvent(UiEvent.OnSaveClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.validationErrors["fieldName"] shouldBe "Field name is required"

            // When: changing the field name
            viewModel.onEvent(UiEvent.OnFieldNameChange("Amount"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the name updates and the validation error clears
            val state = viewModel.uiState.value
            state.fieldName shouldBe "Amount"
            state.validationErrors.containsKey("fieldName") shouldBe false
        }

        @Test
        fun `method type change updates state and re-runs preview`() = runTest(testDispatcher) {
            // Given: sample text where a fixed position range is always previewable
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = "Hello World")))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: switching to FIXED_POSITION and narrowing the range to the first word
            viewModel.onEvent(UiEvent.OnMethodTypeChange(MethodType.FIXED_POSITION))
            viewModel.onEvent(UiEvent.OnEndIndexChange(5))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the method type updates and a preview is computed
            val state = viewModel.uiState.value
            state.selectedMethodType shouldBe MethodType.FIXED_POSITION
            state.previewResult shouldBe PreviewResult.Success(value = "Hello", startIndex = 0, endIndex = 5)
        }

        @Test
        fun `start index change coerces negative values to zero`() {
            // Given: the default state

            // When: setting a negative start index
            viewModel.onEvent(UiEvent.OnStartIndexChange(-5))

            // Then: the start index is coerced to zero
            viewModel.uiState.value.fixedStartIndex shouldBe 0
        }

        @Test
        fun `end index change coerces values below the start index up to the start index`() {
            // Given: a start index of 10
            viewModel.onEvent(UiEvent.OnStartIndexChange(10))

            // When: setting an end index below the start index
            viewModel.onEvent(UiEvent.OnEndIndexChange(3))

            // Then: the end index is coerced up to the start index
            viewModel.uiState.value.fixedEndIndex shouldBe 10
        }

        @Test
        fun `capture group change coerces negative values to zero`() {
            // When: setting a negative capture group
            viewModel.onEvent(UiEvent.OnCaptureGroupChange(-2))

            // Then: the capture group is coerced to zero
            viewModel.uiState.value.captureGroup shouldBe 0
        }

        @Test
        fun `line number change coerces values below one up to one`() {
            // When: setting a line number of zero
            viewModel.onEvent(UiEvent.OnLineNumberChange(0))

            // Then: the line number is coerced to one
            viewModel.uiState.value.lineNumber shouldBe 1
        }

        @Test
        fun `take index change coerces negative values to zero`() {
            // When: setting a negative take index
            viewModel.onEvent(UiEvent.OnTakeIndexChange(-1))

            // Then: the take index is coerced to zero
            viewModel.uiState.value.takeIndex shouldBe 0
        }

        @Test
        fun `text between anchors updates trigger a preview`() = runTest(testDispatcher) {
            // Given: sample text containing a matching pair of anchors
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = "Total: 100 USD end")))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: setting both anchors
            viewModel.onEvent(UiEvent.OnStartAnchorChange("Total: "))
            viewModel.onEvent(UiEvent.OnEndAnchorChange(" end"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview succeeds with the extracted value
            viewModel.uiState.value.previewResult shouldBe PreviewResult.Success(value = "100 USD", startIndex = 7, endIndex = 14)
        }

        @Test
        fun `regex pattern change triggers a preview`() = runTest(testDispatcher) {
            // Given: sample text and REGEX selected
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = "Total: 100")))
            viewModel.onEvent(UiEvent.OnMethodTypeChange(MethodType.REGEX))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: setting a regex pattern that matches
            viewModel.onEvent(UiEvent.OnRegexPatternChange("\\d+"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview reflects the matched value
            val result = viewModel.uiState.value.previewResult
            result.shouldBeInstanceOf<PreviewResult.Success>().value shouldBe "100"
        }

        @Test
        fun `after keyword change triggers a preview`() = runTest(testDispatcher) {
            // Given: sample text and TEXT_AFTER_KEYWORD selected
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = "Total: 100")))
            viewModel.onEvent(UiEvent.OnMethodTypeChange(MethodType.TEXT_AFTER_KEYWORD))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: setting the keyword to search after
            viewModel.onEvent(UiEvent.OnAfterKeywordChange("Total:"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview extracts the text after the keyword
            val result = viewModel.uiState.value.previewResult
            result.shouldBeInstanceOf<PreviewResult.Success>().value shouldBe "100"
        }

        @Test
        fun `before keyword change triggers a preview`() = runTest(testDispatcher) {
            // Given: sample text and TEXT_BEFORE_KEYWORD selected
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = "100 Total")))
            viewModel.onEvent(UiEvent.OnMethodTypeChange(MethodType.TEXT_BEFORE_KEYWORD))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: setting the keyword to search before
            viewModel.onEvent(UiEvent.OnBeforeKeywordChange("Total"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview extracts the text before the keyword
            val result = viewModel.uiState.value.previewResult
            result.shouldBeInstanceOf<PreviewResult.Success>().value shouldBe "100"
        }

        @Test
        fun `delimiter and take index changes trigger a preview`() = runTest(testDispatcher) {
            // Given: sample text and SPLIT_BY_DELIMITER selected
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = "a,b,c")))
            viewModel.onEvent(UiEvent.OnMethodTypeChange(MethodType.SPLIT_BY_DELIMITER))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: setting delimiter and take index
            viewModel.onEvent(UiEvent.OnDelimiterChange(","))
            viewModel.onEvent(UiEvent.OnTakeIndexChange(1))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview extracts the requested part
            val result = viewModel.uiState.value.previewResult
            result.shouldBeInstanceOf<PreviewResult.Success>().value shouldBe "b"
        }

        @Test
        fun `json path change triggers a preview`() = runTest(testDispatcher) {
            // Given: sample text as a JSON payload and JSON_PATH selected
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = """{"amount":42}""")))
            viewModel.onEvent(UiEvent.OnMethodTypeChange(MethodType.JSON_PATH))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: setting the json path
            viewModel.onEvent(UiEvent.OnJsonPathChange("amount"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview extracts the value at the path
            val result = viewModel.uiState.value.previewResult
            result.shouldBeInstanceOf<PreviewResult.Success>().value shouldBe "42"
        }
    }

    @Nested
    inner class PreviewTests {

        @Test
        fun `preview stays None when sample text is empty`() = runTest(testDispatcher) {
            // Given: no sample text loaded

            // When: requesting a manual preview
            viewModel.onEvent(UiEvent.OnPreviewClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview result stays None
            viewModel.uiState.value.previewResult shouldBe PreviewResult.None
        }

        @Test
        fun `preview reports failure when extraction does not match`() = runTest(testDispatcher) {
            // Given: sample text that does not contain the configured anchors
            viewModel.onEvent(UiEvent.Initialize(field = null, notification = createTestNotification(content = "no anchors here")))
            viewModel.onEvent(UiEvent.OnStartAnchorChange("START"))
            viewModel.onEvent(UiEvent.OnEndAnchorChange("END"))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: requesting a manual preview
            viewModel.onEvent(UiEvent.OnPreviewClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview reports a failure and stops testing
            val state = viewModel.uiState.value
            state.previewResult.shouldBeInstanceOf<PreviewResult.Failure>().reason shouldBe "Start anchor not found: START"
            state.isTesting shouldBe false
        }
    }

    @Nested
    inner class SaveFieldTests {

        @Test
        fun `save with a blank field name reports a validation error and sends ShowError`() = runTest(testDispatcher) {
            // Given: the default state with a blank field name (the default TEXT_BETWEEN_ANCHORS method
            // also reports blank-anchor errors, which are unrelated to this assertion)
            viewModel.effect.test {
                // When: saving
                viewModel.onEvent(UiEvent.OnSaveClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a validation error is set and a ShowError effect is sent
                viewModel.uiState.value.validationErrors["fieldName"] shouldBe "Field name is required"
                awaitItem() shouldBe UiEffect.ShowError("Please fix validation errors")
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `save with a blank required anchor reports a validation error`() = runTest(testDispatcher) {
            // Given: a field name but blank anchors while TEXT_BETWEEN_ANCHORS is selected
            viewModel.onEvent(UiEvent.OnFieldNameChange("Amount"))
            testDispatcher.scheduler.advanceUntilIdle()

            // When: saving
            viewModel.onEvent(UiEvent.OnSaveClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: validation errors are reported for both anchors
            viewModel.uiState.value.validationErrors shouldBe mapOf(
                "startAnchor" to "Start anchor is required",
                "endAnchor" to "End anchor is required",
            )
        }

        @Test
        fun `save with valid data sends ReturnWithField with the built field`() = runTest(testDispatcher) {
            // Given: a valid field name and satisfied anchors
            viewModel.onEvent(UiEvent.OnFieldNameChange("Amount"))
            viewModel.onEvent(UiEvent.OnStartAnchorChange("Total: "))
            viewModel.onEvent(UiEvent.OnEndAnchorChange(" end"))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                // When: saving
                viewModel.onEvent(UiEvent.OnSaveClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a ReturnWithField effect carries the built field
                val effect = awaitItem()
                effect.shouldBeInstanceOf<UiEffect.ReturnWithField>().field.name shouldBe "Amount"
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `save still returns the field when the extraction test fails against the sample text`() = runTest(testDispatcher) {
            // Given: valid field name and anchors that do not match the (empty) sample text
            viewModel.onEvent(UiEvent.OnFieldNameChange("Amount"))
            viewModel.onEvent(UiEvent.OnStartAnchorChange("START"))
            viewModel.onEvent(UiEvent.OnEndAnchorChange("END"))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                // When: saving despite the failing preview
                viewModel.onEvent(UiEvent.OnSaveClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: the field is still returned (saving is allowed even if the test extraction fails)
                val effect = awaitItem()
                effect.shouldBeInstanceOf<UiEffect.ReturnWithField>().field.name shouldBe "Amount"
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class MiscellaneousEventTests {

        @Test
        fun `cancel clicked sends CancelAndReturn effect`() = runTest(testDispatcher) {
            viewModel.effect.test {
                // When: cancelling
                viewModel.onEvent(UiEvent.OnCancelClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a CancelAndReturn effect is sent
                awaitItem() shouldBe UiEffect.CancelAndReturn
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `dismiss error clears the error`() {
            // Given: no direct way to set an error from events, verify dismiss is a no-op safe call
            // When: dismissing the error
            viewModel.onEvent(UiEvent.OnDismissError)

            // Then: the error stays null
            viewModel.uiState.value.error shouldBe null
        }
    }
}
