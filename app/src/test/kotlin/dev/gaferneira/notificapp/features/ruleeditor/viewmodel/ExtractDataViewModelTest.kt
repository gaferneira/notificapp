package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestField
import io.kotest.matchers.shouldBe
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
class ExtractDataViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: ExtractDataViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ExtractDataViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class InitTests {

        @Test
        fun `init seeds the draft fields and the editing flag`() {
            // Given: two existing fields for an edit flow
            val fields = listOf(
                createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection),
                createTestField(id = "f2", method = ExtractionMethod.SmartDateDetection),
            )

            // When: initializing for editing
            viewModel.onEvent(UiEvent.Init(initialFields = fields, isEditingAction = true, sampleText = null))

            // Then: the draft is seeded and the editing flag is set
            val state = viewModel.uiState.value
            state.fields shouldBe fields
            state.isEditingAction shouldBe true
        }

        @Test
        fun `init with no fields starts an empty draft that cannot be confirmed`() {
            // When: initializing for a new action with no fields
            viewModel.onEvent(UiEvent.Init(initialFields = emptyList(), isEditingAction = false, sampleText = "text"))

            // Then: the draft is empty and confirm is disabled
            val state = viewModel.uiState.value
            state.fields shouldBe emptyList()
            state.isEditingAction shouldBe false
            state.canConfirm shouldBe false
        }
    }

    @Nested
    inner class FieldSheetTests {

        @Test
        fun `add field clicked opens the nested sheet with no editing id`() {
            // Given: an existing editing id from a prior edit
            viewModel.onEvent(UiEvent.OnEditFieldClicked("f1"))

            // When: clicking add field
            viewModel.onEvent(UiEvent.OnAddFieldClicked)

            // Then: the nested sheet is visible with the editing id cleared
            val state = viewModel.uiState.value
            state.isFieldSheetVisible shouldBe true
            state.editingFieldId shouldBe null
        }

        @Test
        fun `edit field clicked opens the nested sheet with the editing id set`() {
            // When: clicking edit on a field
            viewModel.onEvent(UiEvent.OnEditFieldClicked("f1"))

            // Then: the nested sheet opens with the editing id set
            val state = viewModel.uiState.value
            state.isFieldSheetVisible shouldBe true
            state.editingFieldId shouldBe "f1"
        }

        @Test
        fun `field saved with no editing id appends to the draft and closes the nested sheet`() {
            // Given: the nested sheet is open for a new field
            viewModel.onEvent(UiEvent.OnAddFieldClicked)
            val field = createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection)

            // When: saving the field
            viewModel.onEvent(UiEvent.OnFieldSaved(field))

            // Then: the field is appended and the nested sheet closes
            val state = viewModel.uiState.value
            state.fields shouldBe listOf(field)
            state.isFieldSheetVisible shouldBe false
            state.editingFieldId shouldBe null
        }

        @Test
        fun `field saved while editing replaces the existing draft field`() {
            // Given: an existing draft field being edited
            val original = createTestField(id = "f1", name = "Old", method = ExtractionMethod.SmartAmountDetection)
            viewModel.onEvent(UiEvent.Init(initialFields = listOf(original), isEditingAction = true, sampleText = null))
            viewModel.onEvent(UiEvent.OnEditFieldClicked("f1"))

            val updated = createTestField(id = "f1", name = "New", method = ExtractionMethod.SmartAmountDetection)

            // When: saving the updated field
            viewModel.onEvent(UiEvent.OnFieldSaved(updated))

            // Then: the field is replaced and the nested sheet closes
            val state = viewModel.uiState.value
            state.fields shouldBe listOf(updated)
            state.isFieldSheetVisible shouldBe false
        }

        @Test
        fun `remove field clicked removes it from the draft`() {
            // Given: a draft with one field
            val field = createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection)
            viewModel.onEvent(UiEvent.Init(initialFields = listOf(field), isEditingAction = false, sampleText = null))

            // When: removing it
            viewModel.onEvent(UiEvent.OnRemoveFieldClicked("f1"))

            // Then: the draft is empty
            viewModel.uiState.value.fields shouldBe emptyList()
        }
    }

    @Nested
    inner class AutoGenerateTests {

        @Test
        fun `auto generate with no sample text sends a ShowError effect`() = runTest(testDispatcher) {
            // Given: init without sample text
            viewModel.onEvent(UiEvent.Init(initialFields = emptyList(), isEditingAction = false, sampleText = null))

            viewModel.effect.test {
                // When: auto-generating
                viewModel.onEvent(UiEvent.OnAutoGenerate)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a ShowError effect is sent and the draft stays empty
                awaitItem() shouldBe UiEffect.ShowError("No notification text available to analyze")
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.uiState.value.fields shouldBe emptyList()
        }

        @Test
        fun `auto generate with no numbers in the text sends a ShowError effect`() = runTest(testDispatcher) {
            // Given: sample text with no digits
            viewModel.onEvent(UiEvent.Init(initialFields = emptyList(), isEditingAction = false, sampleText = "no numbers here"))

            viewModel.effect.test {
                // When: auto-generating
                viewModel.onEvent(UiEvent.OnAutoGenerate)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: a ShowError effect is sent
                awaitItem() shouldBe UiEffect.ShowError("No numbers found in the notification")
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `auto generate with a single number populates one Amount field`() {
            // Given: sample text with a single number
            viewModel.onEvent(UiEvent.Init(initialFields = emptyList(), isEditingAction = false, sampleText = "Total: 153,50 kr"))

            // When: auto-generating
            viewModel.onEvent(UiEvent.OnAutoGenerate)

            // Then: a single "Amount" field is in the draft
            viewModel.uiState.value.fields.map { it.name } shouldBe listOf("Amount")
        }

        @Test
        fun `auto generate with multiple numbers populates indexed Amount fields`() {
            // Given: sample text with two numbers
            viewModel.onEvent(UiEvent.Init(initialFields = emptyList(), isEditingAction = false, sampleText = "153,50 kr and 20,00 kr"))

            // When: auto-generating
            viewModel.onEvent(UiEvent.OnAutoGenerate)

            // Then: fields are named "Amount 1" and "Amount 2"
            viewModel.uiState.value.fields.map { it.name } shouldBe listOf("Amount 1", "Amount 2")
        }

        @Test
        fun `auto generate replaces any existing draft fields`() {
            // Given: a draft that already has a field and sample text with one number
            val existing = createTestField(id = "f1", name = "Old", method = ExtractionMethod.SmartAmountDetection)
            viewModel.onEvent(UiEvent.Init(initialFields = listOf(existing), isEditingAction = true, sampleText = "Total: 10"))

            // When: auto-generating
            viewModel.onEvent(UiEvent.OnAutoGenerate)

            // Then: the existing field is replaced by the generated one
            viewModel.uiState.value.fields.map { it.name } shouldBe listOf("Amount")
        }
    }

    @Nested
    inner class ConfirmDismissTests {

        @Test
        fun `canConfirm is false when the draft is empty and true once a field exists`() {
            // Given: an empty draft
            viewModel.onEvent(UiEvent.Init(initialFields = emptyList(), isEditingAction = false, sampleText = null))
            viewModel.uiState.value.canConfirm shouldBe false

            // When: a field is added
            viewModel.onEvent(UiEvent.OnFieldSaved(createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection)))

            // Then: confirm becomes allowed
            viewModel.uiState.value.canConfirm shouldBe true
        }

        @Test
        fun `confirm emits Committed with the draft fields then Dismiss`() = runTest(testDispatcher) {
            // Given: a draft with a field
            val field = createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection)
            viewModel.onEvent(UiEvent.Init(initialFields = listOf(field), isEditingAction = false, sampleText = null))

            viewModel.effect.test {
                // When: confirming
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: Committed(fields) is emitted, followed by Dismiss
                awaitItem() shouldBe UiEffect.Committed(listOf(field))
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `confirm with an empty draft emits nothing`() = runTest(testDispatcher) {
            // Given: an empty draft
            viewModel.onEvent(UiEvent.Init(initialFields = emptyList(), isEditingAction = false, sampleText = null))

            viewModel.effect.test {
                // When: confirming
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: no effect is emitted
                expectNoEvents()
            }
        }

        @Test
        fun `dismiss discards the draft and emits Dismiss without Committed`() = runTest(testDispatcher) {
            // Given: a draft with a field
            val field = createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection)
            viewModel.onEvent(UiEvent.Init(initialFields = listOf(field), isEditingAction = true, sampleText = null))

            viewModel.effect.test {
                // When: dismissing
                viewModel.onEvent(UiEvent.OnDismiss)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: only Dismiss is emitted and the draft is reset
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
            val state = viewModel.uiState.value
            state.fields shouldBe emptyList()
            state.isEditingAction shouldBe false
        }
    }
}
