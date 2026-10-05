package dev.gaferneira.notificapp.features.databrowser.viewmodel

import androidx.paging.PagingData
import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataSort
import dev.gaferneira.notificapp.domain.model.DataStatistics
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.repository.DataBrowserRepository
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataFilterChip
import dev.gaferneira.notificapp.features.databrowser.contract.FilterOption
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.fakes.FakeNotificationRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

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
    ): DataBrowserViewModel = DataBrowserViewModel(
        dataBrowserRepository = repository,
        ruleRepository = ruleRepository,
        notificationRepository = notificationRepository,
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
            repository.statisticsCalls.clear()

            viewModel.onEvent(DataBrowserEvent.OnClearFilters)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe DataBrowserFilter(sort = DataSort.RULE_ASC)
            repository.statisticsCalls shouldBe listOf(DataBrowserFilter(sort = DataSort.RULE_ASC))
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
        fun `OnRemoveFilterChip removes only that value and reloads the stats`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(DataBrowserEvent.OnFilterChange(applied))
            testDispatcher.scheduler.advanceUntilIdle()
            repository.statisticsCalls.clear()

            viewModel.onEvent(DataBrowserEvent.OnRemoveFilterChip(DataFilterChip.App("com.a")))
            testDispatcher.scheduler.advanceUntilIdle()

            val expected = applied.copy(packageNames = listOf("com.b"))
            viewModel.uiState.value.filter shouldBe expected
            repository.statisticsCalls shouldBe listOf(expected)
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
    inner class StatisticsTests {

        @Test
        fun `stats load on init`() = runTest(testDispatcher) {
            val stats = DataStatistics(total = 5, thisWeek = 2, mostActiveRuleName = "Rule A", perRule = emptyList(), perApp = emptyList(), trend = emptyList())
            repository.statisticsResult = Result.success(stats)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.stats shouldBe stats
            viewModel.uiState.value.isStatsLoading shouldBe false
        }

        @Test
        fun `OnRefreshStats reloads statistics for the current filter`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            repository.statisticsCalls.clear()

            viewModel.onEvent(DataBrowserEvent.OnRefreshStats)
            testDispatcher.scheduler.advanceUntilIdle()

            repository.statisticsCalls shouldBe listOf(DataBrowserFilter())
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
}

/**
 * Deterministic in-memory [DataBrowserRepository] fake for VM tests. The paginated methods return
 * an empty [PagingData] by default while tracking every call's filter (faking Paging3's real
 * pipeline isn't worth the complexity for VM-level tests, mirroring `FakeNotificationRepository`).
 */
private class FakeDataBrowserRepository : DataBrowserRepository {

    val browseCalls = mutableListOf<DataBrowserFilter>()
    val searchCalls = mutableListOf<DataBrowserFilter>()
    val statisticsCalls = mutableListOf<DataBrowserFilter>()
    val deleteByIdsCalls = mutableListOf<List<String>>()
    val exportCalls = mutableListOf<Pair<DataBrowserFilter, ExportFormat>>()

    var statisticsResult: Result<DataStatistics> = Result.success(
        DataStatistics(total = 0, thisWeek = 0, mostActiveRuleName = null, perRule = emptyList(), perApp = emptyList(), trend = emptyList()),
    )
    var previewDeleteResult: Result<List<String>> = Result.success(emptyList())
    var deleteByIdsResult: Result<Int> = Result.success(0)
    var exportResult: Result<Unit> = Result.success(Unit)

    override fun browsePaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>> {
        browseCalls += filter
        return flowOf(PagingData.empty())
    }

    override fun searchPaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>> {
        searchCalls += filter
        return flowOf(PagingData.empty())
    }

    override suspend fun statistics(filter: DataBrowserFilter): Result<DataStatistics> {
        statisticsCalls += filter
        return statisticsResult
    }

    override suspend fun exportRows(filter: DataBrowserFilter, sink: OutputStream, format: ExportFormat): Result<Unit> {
        exportCalls += filter to format
        return exportResult
    }

    override suspend fun previewDelete(filter: DataBrowserFilter): Result<List<String>> = previewDeleteResult

    override suspend fun deleteById(valueId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteByIds(ids: List<String>): Result<Int> {
        deleteByIdsCalls += ids
        return deleteByIdsResult
    }
}
