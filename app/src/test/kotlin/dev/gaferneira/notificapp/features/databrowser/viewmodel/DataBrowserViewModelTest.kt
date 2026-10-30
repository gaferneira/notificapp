package dev.gaferneira.notificapp.features.databrowser.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.PagingData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.cash.turbine.test
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataSort
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataFilterChip
import dev.gaferneira.notificapp.features.databrowser.contract.FilterOption
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.fakes.FakeDataBrowserRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeNotificationRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class DataBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeDataBrowserRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeDataBrowserRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        ruleRepository: FakeRuleRepository = FakeRuleRepository(),
        notificationRepository: FakeNotificationRepository = FakeNotificationRepository(),
        initialRuleId: String? = null,
        initialSince: Long? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): DataBrowserViewModel = DataBrowserViewModel(
        visitArgs = DataBrowserVisitArgs(initialRuleId, initialSince),
        savedStateHandle = savedStateHandle,
        dataBrowserRepository = repository,
        ruleRepository = ruleRepository,
        notificationRepository = notificationRepository,
        ioDispatcher = testDispatcher,
    )

    private val extractField = createTestField(method = RuleField.ExtractionMethod.SmartAmountDetection)

    @Nested
    inner class PagingTests {

        @Test
        fun `filter change recreates the pager via flatMapLatest with the new filter`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val collectJob = launch { viewModel.rows.collect { } }
            testDispatcher.scheduler.advanceUntilIdle()

            val newFilter = DataBrowserFilter(ruleIds = listOf("rule-1"))
            viewModel.onEvent(DataBrowserEvent.OnFilterChange(newFilter))
            testDispatcher.scheduler.advanceUntilIdle()

            collectJob.cancel()
            repository.browseCalls shouldBe listOf(DataBrowserFilter(), newFilter)
        }

        @Test
        fun `a filter with a non-blank search query uses searchPaged instead of browsePaged`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val collectJob = launch { viewModel.rows.collect { } }
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(DataBrowserEvent.OnSearchQueryChange("invoice"))
            testDispatcher.scheduler.advanceUntilIdle()

            collectJob.cancel()
            repository.searchCalls shouldBe listOf(DataBrowserFilter(searchQuery = "invoice"))
        }
    }

    @Nested
    inner class ClearFiltersTests {

        @Test
        fun `OnClearFilters clears rule, app, date and search but keeps the sort`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(
                DataBrowserEvent.OnFilterChange(
                    DataBrowserFilter(
                        ruleIds = listOf("r1"),
                        packageNames = listOf("com.a"),
                        dateFrom = 10L,
                        dateTo = 20L,
                        searchQuery = "invoice",
                        sort = DataSort.RULE_ASC,
                    ),
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(DataBrowserEvent.OnClearFilters)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe DataBrowserFilter(sort = DataSort.RULE_ASC)
        }

        @Test
        fun `OnClearFilters with the default sort resets to a default filter`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(DataBrowserEvent.OnSearchQueryChange("x"))

            viewModel.onEvent(DataBrowserEvent.OnClearFilters)

            viewModel.uiState.value.filter shouldBe DataBrowserFilter()
        }
    }

    @Nested
    inner class SortTests {

        @Test
        fun `OnSortChange updates the sort and requeries with it`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val collectJob = launch { viewModel.rows.collect { } }
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(DataBrowserEvent.OnSortChange(DataSort.RULE_ASC))
            testDispatcher.scheduler.advanceUntilIdle()

            collectJob.cancel()
            viewModel.uiState.value.filter shouldBe DataBrowserFilter(sort = DataSort.RULE_ASC)
            repository.browseCalls.last() shouldBe DataBrowserFilter(sort = DataSort.RULE_ASC)
        }
    }

    @Nested
    inner class OptionsTests {

        @Test
        fun `ruleOptions lists only rules with at least one Extract data field`() = runTest(testDispatcher) {
            val rules = FakeRuleRepository(
                listOf(
                    createTestRule(id = "A", name = "Rule A", actions = listOf(createTestAction(type = ActionType.SAVE_DATA, fields = listOf(extractField)))),
                    createTestRule(id = "B", name = "Rule B", actions = listOf(createTestAction(type = ActionType.DISMISS_NOTIFICATION))),
                    createTestRule(id = "C", name = "Rule C", actions = listOf(createTestAction(type = ActionType.SAVE_DATA, fields = emptyList()))),
                    createTestRule(
                        id = "D",
                        name = "Rule D",
                        actions = listOf(createTestAction(type = ActionType.SAVE_DATA, isEnabled = false, fields = listOf(extractField))),
                    ),
                ),
            )

            val viewModel = createViewModel(ruleRepository = rules)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.ruleOptions shouldBe listOf(FilterOption("A", "Rule A"))
        }

        @Test
        fun `ruleOptions follow rule changes and are sorted by name`() = runTest(testDispatcher) {
            val action = createTestAction(type = ActionType.SAVE_DATA, fields = listOf(extractField))
            val rules = FakeRuleRepository(listOf(createTestRule(id = "z", name = "Zeta", actions = listOf(action))))
            val viewModel = createViewModel(ruleRepository = rules)
            testDispatcher.scheduler.advanceUntilIdle()

            rules.saveRule(createTestRule(id = "a", name = "alpha", actions = listOf(action)))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.ruleOptions shouldBe listOf(FilterOption("a", "alpha"), FilterOption("z", "Zeta"))
        }

        @Test
        fun `appOptions come from the apps with notifications`() = runTest(testDispatcher) {
            val notifications = FakeNotificationRepository(
                listOf(
                    createTestNotification(id = "1", packageName = "com.b", appName = "Bank"),
                    createTestNotification(id = "2", packageName = "com.a", appName = "Alpha"),
                ),
            )

            val viewModel = createViewModel(notificationRepository = notifications)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.appOptions shouldBe listOf(FilterOption("com.a", "Alpha"), FilterOption("com.b", "Bank"))
        }
    }

    @Nested
    inner class RemoveChipTests {

        private val applied = DataBrowserFilter(
            ruleIds = listOf("r1"),
            packageNames = listOf("com.a", "com.b"),
            dateFrom = 10L,
            dateTo = 20L,
            sort = DataSort.APP_ASC,
        )

        @Test
        fun `OnRemoveFilterChip removes only that value`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(DataBrowserEvent.OnFilterChange(applied))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(DataBrowserEvent.OnRemoveFilterChip(DataFilterChip.App("com.a")))
            testDispatcher.scheduler.advanceUntilIdle()

            val expected = applied.copy(packageNames = listOf("com.b"))
            viewModel.uiState.value.filter shouldBe expected
        }

        @Test
        fun `removing the date chip keeps rules and apps`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(DataBrowserEvent.OnFilterChange(applied))

            viewModel.onEvent(DataBrowserEvent.OnRemoveFilterChip(DataFilterChip.DateRange))

            viewModel.uiState.value.filter shouldBe applied.copy(dateFrom = null, dateTo = null)
        }
    }

    @Nested
    inner class BulkDeleteTests {

        @Test
        fun `OnBulkDeleteClick previews and sets the affected count`() = runTest(testDispatcher) {
            repository.previewDeleteResult = Result.success(listOf("id-1", "id-2", "id-3"))
            val viewModel = createViewModel()

            viewModel.onEvent(DataBrowserEvent.OnBulkDeleteClick)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.pendingDeleteCount shouldBe 3
        }

        @Test
        fun `confirming bulk delete deletes exactly the previewed id list, never re-resolving the filter`() = runTest(testDispatcher) {
            val previewedIds = listOf("id-1", "id-2", "id-3")
            repository.previewDeleteResult = Result.success(previewedIds)
            val viewModel = createViewModel()

            viewModel.onEvent(DataBrowserEvent.OnBulkDeleteClick)
            testDispatcher.scheduler.advanceUntilIdle()

            // Simulate new data arriving between preview and confirmation - a subsequent preview
            // would now resolve a different (larger) set, but confirm must NOT re-resolve.
            repository.previewDeleteResult = Result.success(previewedIds + "id-4-arrived-late")

            viewModel.onEvent(DataBrowserEvent.OnConfirmBulkDelete)
            testDispatcher.scheduler.advanceUntilIdle()

            repository.deleteByIdsCalls shouldBe listOf(previewedIds)
            viewModel.uiState.value.pendingDeleteCount shouldBe null
        }

        @Test
        fun `cancelling bulk delete clears the pending count without deleting`() = runTest(testDispatcher) {
            repository.previewDeleteResult = Result.success(listOf("id-1"))
            val viewModel = createViewModel()

            viewModel.onEvent(DataBrowserEvent.OnBulkDeleteClick)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(DataBrowserEvent.OnCancelBulkDelete)

            viewModel.uiState.value.pendingDeleteCount shouldBe null
            repository.deleteByIdsCalls shouldBe emptyList()
        }
    }

    @Nested
    inner class ExportTests {

        @Test
        fun `OnExportClick emits RequestExportSink for the chosen format`() = runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.effect.test {
                viewModel.onEvent(DataBrowserEvent.OnExportClick(ExportFormat.CSV))
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe DataBrowserEffect.RequestExportSink(ExportFormat.CSV)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `exportTo writes to the given sink via the repository and reports success`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val sink = ByteArrayOutputStream()

            val result = viewModel.exportTo(sink, ExportFormat.CSV)

            result.isSuccess shouldBe true
            repository.exportCalls shouldBe listOf(DataBrowserFilter() to ExportFormat.CSV)
        }

        @Test
        fun `exportTo surfaces a repository failure`() = runTest(testDispatcher) {
            repository.exportResult = Result.failure(RuntimeException("disk full"))
            val viewModel = createViewModel()
            val sink = ByteArrayOutputStream()

            val result = viewModel.exportTo(sink, ExportFormat.JSON)

            result.isFailure shouldBe true
        }
    }

    @Nested
    inner class ExportCancellationTests {

        @Test
        fun `cancelling an in-flight export resets isExporting`() = runTest(testDispatcher) {
            repository.exportGate = CompletableDeferred()
            val viewModel = createViewModel()
            val export = launch { viewModel.exportTo(ByteArrayOutputStream(), ExportFormat.CSV) }
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.isExporting shouldBe true

            export.cancel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isExporting shouldBe false
        }
    }

    @Nested
    inner class InitialRuleTests {

        private val windowStart = 1_773_532_800_000L

        @Test
        fun `a route rule id seeds a removable rule filter`() = runTest(testDispatcher) {
            val viewModel = createViewModel(initialRuleId = "rule-9")
            val collectJob = launch { viewModel.rows.collect { } }
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe DataBrowserFilter(ruleIds = listOf("rule-9"))
            repository.browseCalls shouldBe listOf(DataBrowserFilter(ruleIds = listOf("rule-9")))

            viewModel.onEvent(DataBrowserEvent.OnRemoveFilterChip(DataFilterChip.Rule("rule-9")))
            viewModel.uiState.value.filter shouldBe DataBrowserFilter()
            collectJob.cancel()
        }

        @Test
        fun `without a route rule id the filter starts empty`() = runTest(testDispatcher) {
            createViewModel().uiState.value.filter shouldBe DataBrowserFilter()
        }

        @Test
        fun `a route since seeds an open-ended date filter in the first state and the first query`() = runTest(testDispatcher) {
            // Given / When
            val viewModel = createViewModel(initialRuleId = "rule-9", initialSince = windowStart)
            val collectJob = launch { viewModel.rows.collect { } }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val seeded = DataBrowserFilter(ruleIds = listOf("rule-9"), dateFrom = windowStart, dateTo = null)
            viewModel.uiState.value.filter shouldBe seeded
            repository.browseCalls shouldBe listOf(seeded)
            collectJob.cancel()
        }

        @Test
        fun `the seeded since is a removable date chip that keeps the rule`() = runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel(initialRuleId = "rule-9", initialSince = windowStart)

            // When
            viewModel.onEvent(DataBrowserEvent.OnRemoveFilterChip(DataFilterChip.DateRange))

            // Then
            viewModel.uiState.value.filter shouldBe DataBrowserFilter(ruleIds = listOf("rule-9"))
        }

        @Test
        fun `chips removed by the user stay removed when the ViewModel is restored`() = runTest(testDispatcher) {
            // Given a visit seeded with a rule and a since, then both chips removed
            val savedStateHandle = SavedStateHandle()
            val viewModel = createViewModel(initialRuleId = "rule-9", initialSince = windowStart, savedStateHandle = savedStateHandle)
            viewModel.onEvent(DataBrowserEvent.OnRemoveFilterChip(DataFilterChip.Rule("rule-9")))
            viewModel.onEvent(DataBrowserEvent.OnRemoveFilterChip(DataFilterChip.DateRange))

            // When process death restores a new instance with the same route args and saved state
            val restored = createViewModel(initialRuleId = "rule-9", initialSince = windowStart, savedStateHandle = savedStateHandle)

            // Then nothing comes back
            restored.uiState.value.filter shouldBe DataBrowserFilter()
        }

        @Test
        fun `an untouched seeded filter is kept when the ViewModel is restored`() = runTest(testDispatcher) {
            val savedStateHandle = SavedStateHandle()
            createViewModel(initialRuleId = "rule-9", initialSince = windowStart, savedStateHandle = savedStateHandle)

            val restored = createViewModel(initialRuleId = "rule-9", initialSince = windowStart, savedStateHandle = savedStateHandle)

            restored.uiState.value.filter shouldBe DataBrowserFilter(ruleIds = listOf("rule-9"), dateFrom = windowStart)
        }

        @Test
        fun `the seeded since only lives in that visit`() = runTest(testDispatcher) {
            // Given a visit seeded from Insights
            createViewModel(initialSince = windowStart)

            // When the browser is opened again without a since
            val nextVisit = createViewModel()

            // Then nothing carried over
            nextVisit.uiState.value.filter shouldBe DataBrowserFilter()
        }
    }

    @Nested
    inner class RowDeleteTests {

        private fun row(id: String) = DataBrowserRow(
            valueId = id,
            executionId = "e-$id",
            notificationId = "n-$id",
            ruleName = "Rule",
            packageName = "com.a",
            appName = "A",
            fieldName = "Amount",
            fieldType = RuleField.FieldType.NUMBER,
            valueText = null,
            valueNumber = 1.0,
            valueDate = null,
            notificationTitle = null,
            notificationContent = null,
            createdAt = 0L,
        )

        private val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<DataBrowserRow>() {
                override fun areItemsTheSame(oldItem: DataBrowserRow, newItem: DataBrowserRow) = oldItem.valueId == newItem.valueId
                override fun areContentsTheSame(oldItem: DataBrowserRow, newItem: DataBrowserRow) = oldItem == newItem
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) = Unit
                override fun onRemoved(position: Int, count: Int) = Unit
                override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
                override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
            },
            mainDispatcher = testDispatcher,
            workerDispatcher = testDispatcher,
        )

        private fun visibleIds() = differ.snapshot().items.map { it.valueId }

        private fun TestScope.startCollecting(viewModel: DataBrowserViewModel): Job {
            repository.pagedRows = PagingData.from(listOf(row("a"), row("b")))
            val job = launch { viewModel.rows.collectLatest { differ.submitData(it) } }
            testDispatcher.scheduler.advanceUntilIdle()
            return job
        }

        @Test
        fun `deleting a row hides it and offers undo without deleting yet`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = startCollecting(viewModel)
            visibleIds() shouldBe listOf("a", "b")

            viewModel.effect.test {
                viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
                testDispatcher.scheduler.runCurrent()

                awaitItem() shouldBe DataBrowserEffect.ShowUndoDelete("a")
                cancelAndIgnoreRemainingEvents()
            }
            visibleIds() shouldBe listOf("b")
            repository.deleteByIdCalls shouldBe emptyList()
            job.cancel()
        }

        @Test
        fun `committing the pending delete deletes the row once`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = startCollecting(viewModel)

            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
            viewModel.onEvent(DataBrowserEvent.OnDeleteCommitted("a"))
            viewModel.onEvent(DataBrowserEvent.OnDeleteCommitted("a"))
            testDispatcher.scheduler.advanceUntilIdle()

            repository.deleteByIdCalls shouldBe listOf("a")
            visibleIds() shouldBe listOf("b")
            job.cancel()
        }

        @Test
        fun `undo restores the row without calling the repository`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = startCollecting(viewModel)

            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
            viewModel.onEvent(DataBrowserEvent.OnUndoDelete("a"))
            viewModel.onEvent(DataBrowserEvent.OnDeleteCommitted("a"))
            testDispatcher.scheduler.advanceUntilIdle()

            visibleIds() shouldBe listOf("a", "b")
            repository.deleteByIdCalls shouldBe emptyList()
            job.cancel()
        }

        @Test
        fun `a second delete commits the first one`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = startCollecting(viewModel)

            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("b"))
            testDispatcher.scheduler.runCurrent()

            repository.deleteByIdCalls shouldBe listOf("a")
            visibleIds() shouldBe emptyList()
            job.cancel()
        }

        @Test
        fun `a failed commit unhides the row and reports the error`() = runTest(testDispatcher) {
            repository.deleteByIdResult = Result.failure(RuntimeException("db"))
            val viewModel = createViewModel()
            val job = startCollecting(viewModel)

            viewModel.effect.test {
                viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
                viewModel.onEvent(DataBrowserEvent.OnDeleteCommitted("a"))
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe DataBrowserEffect.ShowUndoDelete("a")
                awaitItem() shouldBe DataBrowserEffect.ShowError(UiText.StringResource(R.string.data_error_delete))
                cancelAndIgnoreRemainingEvents()
            }
            visibleIds() shouldBe listOf("a", "b")
            job.cancel()
        }

        @Test
        fun `the pending delete commits by itself once the undo window elapses`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = startCollecting(viewModel)

            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
            testDispatcher.scheduler.advanceTimeBy(UNDO_WINDOW_MS - 1)
            testDispatcher.scheduler.runCurrent()
            repository.deleteByIdCalls shouldBe emptyList()

            testDispatcher.scheduler.advanceTimeBy(1)
            testDispatcher.scheduler.runCurrent()

            repository.deleteByIdCalls shouldBe listOf("a")
            job.cancel()
        }

        @Test
        fun `undo stops the timer so the row is never deleted`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = startCollecting(viewModel)

            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
            viewModel.onEvent(DataBrowserEvent.OnUndoDelete("a"))
            testDispatcher.scheduler.advanceTimeBy(UNDO_WINDOW_MS * 2)
            testDispatcher.scheduler.runCurrent()

            repository.deleteByIdCalls shouldBe emptyList()
            visibleIds() shouldBe listOf("a", "b")
            job.cancel()
        }

        @Test
        fun `onCleared commits a still-pending delete`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))
            testDispatcher.scheduler.runCurrent()

            DataBrowserViewModel::class.java.getDeclaredMethod("onCleared")
                .apply { isAccessible = true }
                .invoke(viewModel)
            testDispatcher.scheduler.advanceUntilIdle()

            repository.deleteByIdCalls shouldBe listOf("a")
        }

        @Test
        fun `exporting first makes the pending delete final so the hidden row is not exported`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))

            viewModel.exportTo(ByteArrayOutputStream(), ExportFormat.CSV)

            repository.deleteByIdCalls shouldBe listOf("a")
            repository.exportCalls.size shouldBe 1
        }

        @Test
        fun `previewing a bulk delete first makes the pending delete final`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(DataBrowserEvent.OnDeleteRowClick("a"))

            viewModel.onEvent(DataBrowserEvent.OnBulkDeleteClick)
            testDispatcher.scheduler.advanceUntilIdle()

            repository.deleteByIdCalls shouldBe listOf("a")
        }
    }
}
