package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.PreviewResult
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.fakes.FakeNotificationRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.collections.immutable.persistentListOf
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
    private val notificationRepository = FakeNotificationRepository()

    private lateinit var viewModel: ExtractDataViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ExtractDataViewModel(
            notificationRepository = notificationRepository,
            defaultDispatcher = testDispatcher,
        )
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
            val fields = persistentListOf(
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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = "text"))

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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(original), isEditingAction = true, sampleText = null))
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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = false, sampleText = null))

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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))

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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = "no numbers here"))

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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = "Total: 153,50 kr"))

            // When: auto-generating
            viewModel.onEvent(UiEvent.OnAutoGenerate)

            // Then: a single "Amount" field is in the draft
            viewModel.uiState.value.fields.map { it.name } shouldBe listOf("Amount")
        }

        @Test
        fun `auto generate with multiple numbers populates indexed Amount fields`() {
            // Given: sample text with two numbers
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = "153,50 kr and 20,00 kr"))

            // When: auto-generating
            viewModel.onEvent(UiEvent.OnAutoGenerate)

            // Then: fields are named "Amount 1" and "Amount 2"
            viewModel.uiState.value.fields.map { it.name } shouldBe listOf("Amount 1", "Amount 2")
        }

        @Test
        fun `auto generate replaces any existing draft fields`() {
            // Given: a draft that already has a field and sample text with one number
            val existing = createTestField(id = "f1", name = "Old", method = ExtractionMethod.SmartAmountDetection)
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(existing), isEditingAction = true, sampleText = "Total: 10"))

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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))
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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = false, sampleText = null))

            viewModel.effect.test {
                // When: confirming
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: Committed(fields) is emitted, followed by Dismiss
                awaitItem() shouldBe UiEffect.Committed(persistentListOf(field))
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `confirm with an empty draft emits nothing`() = runTest(testDispatcher) {
            // Given: an empty draft
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))

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
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = true, sampleText = null))

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

    @Nested
    inner class PreviewResultsTests {

        @Test
        fun `init with sample text populates a preview entry per field keyed by field id`() = runTest(testDispatcher) {
            // Given: two fields, one whose regex matches the sample text and one that doesn't
            val matching = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            val nonMatching = createTestField(id = "f2", method = ExtractionMethod.RegexPattern("NOPE"))

            // When: initializing with a non-blank sample text
            viewModel.onEvent(
                UiEvent.Init(
                    initialFields = persistentListOf(matching, nonMatching),
                    isEditingAction = false,
                    sampleText = "Total: 153 kr",
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: previewResults has one entry per field, keyed by field id
            val previews = viewModel.uiState.value.previewResults
            previews.keys shouldBe setOf("f1", "f2")
        }

        @Test
        fun `a field whose method matches the sample text yields a Success preview`() = runTest(testDispatcher) {
            // Given: a field whose regex matches the sample text
            val matching = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))

            // When: initializing with matching sample text
            viewModel.onEvent(
                UiEvent.Init(initialFields = persistentListOf(matching), isEditingAction = false, sampleText = "Total: 153 kr"),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview entry is a Success with the extracted value
            viewModel.uiState.value.previewResults["f1"] shouldBe PreviewResult.Success(value = "153")
        }

        @Test
        fun `a field whose method does not match the sample text yields a Failure preview`() = runTest(testDispatcher) {
            // Given: a field whose regex does not match the sample text
            val nonMatching = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("NOPE"))

            // When: initializing with non-matching sample text
            viewModel.onEvent(
                UiEvent.Init(initialFields = persistentListOf(nonMatching), isEditingAction = false, sampleText = "Total: 153 kr"),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview entry is a Failure, not a missing key or an exception
            viewModel.uiState.value.previewResults["f1"] shouldBe PreviewResult.Failure("Pattern did not match")
        }

        @Test
        fun `field saved as a new field recomputes previews to include the new key`() = runTest(testDispatcher) {
            // Given: init with a sample text and no fields
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = "Total: 153 kr"))
            viewModel.onEvent(UiEvent.OnAddFieldClicked)
            val newField = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))

            // When: saving the new field
            viewModel.onEvent(UiEvent.OnFieldSaved(newField))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: previewResults keyset matches the post-mutation fields keyset
            viewModel.uiState.value.previewResults.keys shouldBe setOf("f1")
        }

        @Test
        fun `field saved while editing recomputes the edited key's preview value`() = runTest(testDispatcher) {
            // Given: an existing field with a non-matching pattern
            val original = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("NOPE"))
            viewModel.onEvent(
                UiEvent.Init(initialFields = persistentListOf(original), isEditingAction = true, sampleText = "Total: 153 kr"),
            )
            viewModel.onEvent(UiEvent.OnEditFieldClicked("f1"))

            // When: replacing it with a field that matches
            val updated = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(UiEvent.OnFieldSaved(updated))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the preview for "f1" updates to a Success
            viewModel.uiState.value.previewResults["f1"] shouldBe PreviewResult.Success(value = "153")
        }

        @Test
        fun `remove field clicked drops the removed field's preview key`() = runTest(testDispatcher) {
            // Given: two fields with previews computed
            val field1 = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            val field2 = createTestField(id = "f2", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(
                UiEvent.Init(initialFields = persistentListOf(field1, field2), isEditingAction = false, sampleText = "Total: 153 kr"),
            )

            // When: removing one field
            viewModel.onEvent(UiEvent.OnRemoveFieldClicked("f1"))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: only the remaining field's key is present
            viewModel.uiState.value.previewResults.keys shouldBe setOf("f2")
        }

        @Test
        fun `auto generate recomputes previews for the newly generated fields`() = runTest(testDispatcher) {
            // Given: init with a two-line sample text (auto-generate targets line 10 for its single field,
            // which exists on line 2 of a multi-line sample containing one number)
            val multiLineSample = (1..9).joinToString("\n") { "filler" } + "\nTotal: 153 kr"
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = multiLineSample))

            // When: auto-generating
            viewModel.onEvent(UiEvent.OnAutoGenerate)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: previewResults has an entry keyed by the generated field's id, with a Success result
            // extracting the target line's content
            val state = viewModel.uiState.value
            val generatedId = state.fields.single().id
            state.previewResults.keys shouldBe setOf(generatedId)
            (state.previewResults.getValue(generatedId) as PreviewResult.Success).value shouldBe "Total: 153 kr"
        }

        @Test
        fun `init with null sample text produces empty previewResults without throwing`() {
            // Given/When: initializing with a field but null sample text
            val field = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = false, sampleText = null))

            // Then: previewResults is empty, no exception is thrown
            viewModel.uiState.value.previewResults shouldBe emptyMap()
        }

        @Test
        fun `init with blank sample text produces empty previewResults without throwing`() {
            // Given/When: initializing with a field but blank sample text
            val field = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = false, sampleText = "   "))

            // Then: previewResults is empty, no exception is thrown
            viewModel.uiState.value.previewResults shouldBe emptyMap()
        }

        @Test
        fun `dismiss resets previewResults to empty along with the rest of state`() = runTest(testDispatcher) {
            // Given: a draft with a computed preview
            val field = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(
                UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = true, sampleText = "Total: 153 kr"),
            )
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.previewResults.keys shouldBe setOf("f1")

            viewModel.effect.test {
                // When: dismissing
                viewModel.onEvent(UiEvent.OnDismiss)
                testDispatcher.scheduler.advanceUntilIdle()
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }

            // Then: previewResults is reset to empty
            viewModel.uiState.value.previewResults shouldBe emptyMap()
        }
    }

    @Nested
    inner class OverrideTests {

        @Test
        fun `selecting a history notification sets the override and collapses the list`() {
            // Given: the sheet is browsing history
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))
            val notification = createTestNotification(id = "n1", content = "Total: 42 kr")

            // When: selecting it
            viewModel.onEvent(UiEvent.OnHistoryNotificationSelected(notification))

            // Then: the override is set and the list is collapsed
            val state = viewModel.uiState.value
            state.overrideNotification shouldBe notification
            state.isBrowsingHistory shouldBe false
        }

        @Test
        fun `selecting an override recomputes previews against its text with no stale results`() = runTest(testDispatcher) {
            // Given: a draft field whose pattern matches the override's text but not the prior (null) sample
            val field = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = false, sampleText = null))
            viewModel.uiState.value.previewResults shouldBe emptyMap()

            val notification = createTestNotification(id = "n1", content = "Total: 153 kr")

            // When: selecting the override
            viewModel.onEvent(UiEvent.OnHistoryNotificationSelected(notification))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: previews reflect the override's text, not stale/empty results
            viewModel.uiState.value.previewResults["f1"] shouldBe PreviewResult.Success(value = "153")
        }

        @Test
        fun `clearing the override resets it to null and previews reset to empty`() {
            // Given: an override is selected in the edit flow (no entry sample)
            val field = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = true, sampleText = null))
            viewModel.onEvent(
                UiEvent.OnHistoryNotificationSelected(createTestNotification(id = "n1", content = "Total: 153 kr")),
            )
            viewModel.uiState.value.overrideNotification shouldNotBe null

            // When: clearing the override
            viewModel.onEvent(UiEvent.OnOverrideCleared)

            // Then: the override is null and previews reset to empty
            val state = viewModel.uiState.value
            state.overrideNotification shouldBe null
            state.previewResults shouldBe emptyMap()
        }

        @Test
        fun `entry-flow sample takes precedence over any override`() = runTest(testDispatcher) {
            // Given: an entry-flow sample was provided on Init
            val field = createTestField(id = "f1", method = ExtractionMethod.RegexPattern("""\d+"""))
            viewModel.onEvent(
                UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = false, sampleText = "Entry: 10 kr"),
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val entryPreview = viewModel.uiState.value.previewResults["f1"]

            // When: selecting a history override with different matching text
            viewModel.onEvent(
                UiEvent.OnHistoryNotificationSelected(createTestNotification(id = "n1", content = "Override: 999 kr")),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: previews still reflect the entry sample, unaffected by the override
            viewModel.uiState.value.previewResults["f1"] shouldBe entryPreview
        }

        @Test
        fun `confirm after selecting an override emits Committed with only the draft fields`() = runTest(testDispatcher) {
            // Given: a draft field and a selected override
            val field = createTestField(id = "f1", method = ExtractionMethod.SmartAmountDetection)
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(field), isEditingAction = false, sampleText = null))
            viewModel.onEvent(
                UiEvent.OnHistoryNotificationSelected(createTestNotification(id = "n1", content = "Total: 153 kr")),
            )

            viewModel.effect.test {
                // When: confirming
                viewModel.onEvent(UiEvent.OnConfirm)
                testDispatcher.scheduler.advanceUntilIdle()

                // Then: Committed carries only the draft fields, no override/notification data
                awaitItem() shouldBe UiEffect.Committed(persistentListOf(field))
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `dismiss clears the override back to null`() = runTest(testDispatcher) {
            // Given: an override was selected
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))
            viewModel.onEvent(
                UiEvent.OnHistoryNotificationSelected(createTestNotification(id = "n1", content = "Total: 153 kr")),
            )

            viewModel.effect.test {
                // When: dismissing
                viewModel.onEvent(UiEvent.OnDismiss)
                testDispatcher.scheduler.advanceUntilIdle()
                awaitItem() shouldBe UiEffect.Dismiss
                cancelAndIgnoreRemainingEvents()
            }

            // Then: the override is cleared
            viewModel.uiState.value.overrideNotification shouldBe null
        }
    }

    @Nested
    inner class HistoryBrowseTests {

        @Test
        fun `opening history sets loading state synchronously then loads results once`() = runTest(testDispatcher) {
            // Given: a bounded history fetch
            val history = listOf(createTestNotification(id = "n1"), createTestNotification(id = "n2"))
            notificationRepository.setNotifications(history)
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))

            // When: opening the history list
            viewModel.onEvent(UiEvent.OnBrowseHistoryOpened(null))

            // Then: loading state is set synchronously, before the fetch completes
            val loadingState = viewModel.uiState.value
            loadingState.isBrowsingHistory shouldBe true
            loadingState.isLoadingHistory shouldBe true

            // When: the fetch completes
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the repository was called exactly once and results land in state
            notificationRepository.backtestCallCount shouldBe 1
            val loadedState = viewModel.uiState.value
            loadedState.isLoadingHistory shouldBe false
            loadedState.historyResults shouldBe history
        }

        @Test
        fun `empty history results in an empty list with loading false`() = runTest(testDispatcher) {
            // Given: the repository returns zero notifications (fake starts empty)
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))

            // When: opening the history list and letting the fetch complete
            viewModel.onEvent(UiEvent.OnBrowseHistoryOpened(null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: results are empty and loading is false (distinct from mid-flight loading)
            val state = viewModel.uiState.value
            state.historyResults shouldBe emptyList()
            state.isLoadingHistory shouldBe false
        }

        @Test
        fun `a failed fetch clears loading and results without propagating the exception`() = runTest(testDispatcher) {
            // Given: the repository fetch fails
            notificationRepository.backtestError = IllegalStateException("boom")
            viewModel.onEvent(UiEvent.Init(initialFields = persistentListOf(), isEditingAction = false, sampleText = null))

            // When: opening the history list and letting the fetch complete
            viewModel.onEvent(UiEvent.OnBrowseHistoryOpened(null))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: no exception propagates, and state resolves to the empty/not-loading resting state
            val state = viewModel.uiState.value
            state.isLoadingHistory shouldBe false
            state.historyResults shouldBe emptyList()
        }
    }
}
