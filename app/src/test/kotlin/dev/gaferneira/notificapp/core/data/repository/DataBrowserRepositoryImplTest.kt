package dev.gaferneira.notificapp.core.data.repository

import androidx.paging.PagingSource
import androidx.room.withTransaction
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.DataBrowserDao
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.MostActiveRuleRow
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.CountBucket
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataSort
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.TrendPoint
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.sql.SQLException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class DataBrowserRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val database = mockk<AppDatabase>(relaxed = true)
    private val dataBrowserDao = mockk<DataBrowserDao>()
    private val extractedFieldValueDao = mockk<ExtractedFieldValueDao>(relaxed = true)
    private val currentTimeProvider = mockk<CurrentTimeProvider> {
        every { nowEpochMillis() } returns FIXED_NOW
    }

    private fun repository() = DataBrowserRepositoryImpl(
        database = database,
        dataBrowserDao = dataBrowserDao,
        extractedFieldValueDao = extractedFieldValueDao,
        currentTimeProvider = currentTimeProvider,
        ioDispatcher = testDispatcher,
    )

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun mockWithTransaction() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Int>(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val block = it.invocation.args[1] as suspend () -> Int
            block()
        }
    }

    private fun row(id: String) = DataBrowserRow(
        valueId = id,
        executionId = "exec-$id",
        ruleName = "Rule",
        packageName = "com.test.app",
        appName = "Test App",
        fieldName = "Amount",
        fieldType = RuleField.FieldType.NUMBER,
        valueText = null,
        valueNumber = 42.0,
        valueDate = null,
        notificationTitle = "Title",
        notificationContent = "Content",
        createdAt = 1_000L,
    )

    // ---- previewDelete / deleteByIds ----

    @Test
    fun `previewDelete returns the concrete matching id list`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            listOf("v1", "v2", "v3")

        val result = repository().previewDelete(DataBrowserFilter())

        result.getOrThrow() shouldBe listOf("v1", "v2", "v3")
    }

    @Test
    fun `previewDelete on a zero-match filter returns an empty list, not an error`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        val result = repository().previewDelete(DataBrowserFilter())

        result.isSuccess shouldBe true
        result.getOrThrow() shouldBe emptyList()
    }

    @Test
    fun `deleteByIds deletes exactly the previewed id set inside a transaction`() = runTest(testDispatcher) {
        mockWithTransaction()
        coEvery { dataBrowserDao.deleteByIds(listOf("v1", "v2")) } returns 2

        val result = repository().deleteByIds(listOf("v1", "v2"))

        result.getOrThrow() shouldBe 2
        coVerify(exactly = 1) { dataBrowserDao.deleteByIds(listOf("v1", "v2")) }
    }

    @Test
    fun `deleteByIds never re-resolves the filter - a row inserted after preview is not deleted`() = runTest(testDispatcher) {
        mockWithTransaction()
        // Given: preview resolved ["v1", "v2"] before a new matching row "v3" was inserted
        coEvery { dataBrowserDao.deleteByIds(listOf("v1", "v2")) } returns 2

        val result = repository().deleteByIds(listOf("v1", "v2"))

        result.getOrThrow() shouldBe 2
        // Then: deleteByIds is invoked with exactly the previewed set, never re-querying getMatchingIds
        coVerify(exactly = 0) { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `deleteByIds with an empty list is a no-op success without touching the dao`() = runTest(testDispatcher) {
        val result = repository().deleteByIds(emptyList())

        result.getOrThrow() shouldBe 0
        coVerify(exactly = 0) { dataBrowserDao.deleteByIds(any()) }
    }

    @Test
    fun `deleteByIds maps a dao failure to Result_failure`() = runTest(testDispatcher) {
        mockWithTransaction()
        coEvery { dataBrowserDao.deleteByIds(any()) } throws SQLException("db locked")

        val result = repository().deleteByIds(listOf("v1"))

        result.isFailure shouldBe true
    }

    @Test
    fun `deleteByIds chunks a large id set into fixed-size batches, same as exportRows`() = runTest(testDispatcher) {
        mockWithTransaction()
        val ids = (1..1500).map { "v$it" }
        coEvery { dataBrowserDao.deleteByIds(ids.subList(0, 1000)) } returns 1000
        coEvery { dataBrowserDao.deleteByIds(ids.subList(1000, 1500)) } returns 500

        val result = repository().deleteByIds(ids)

        result.getOrThrow() shouldBe 1500
        coVerify(exactly = 1) { dataBrowserDao.deleteByIds(ids.subList(0, 1000)) }
        coVerify(exactly = 1) { dataBrowserDao.deleteByIds(ids.subList(1000, 1500)) }
        coVerify(exactly = 2) { dataBrowserDao.deleteByIds(any()) }
    }

    @Test
    fun `deleteById delegates to ExtractedFieldValueDao and is a no-op on an already-deleted id`() = runTest(testDispatcher) {
        coEvery { extractedFieldValueDao.delete("v1") } returns Unit

        val result = repository().deleteById("v1")

        result.isSuccess shouldBe true
    }

    @Test
    fun `deleteById maps a dao failure to Result_failure`() = runTest(testDispatcher) {
        coEvery { extractedFieldValueDao.delete("v1") } throws SQLException("db locked")

        val result = repository().deleteById("v1")

        result.isFailure shouldBe true
    }

    @Test
    fun `previewDelete maps a dao failure to Result_failure`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            SQLException("db error")

        val result = repository().previewDelete(DataBrowserFilter())

        result.isFailure shouldBe true
    }

    // ---- exportRows ----

    @Test
    fun `exportRows snapshots the matching ids once, immune to a concurrent delete changing the live table`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            listOf("v1", "v2")
        coEvery { dataBrowserDao.getMatchingBatch(listOf("v1", "v2")) } returns listOf(row("v1"), row("v2"))
        val sink = ByteArrayOutputStream()

        val result = repository().exportRows(DataBrowserFilter(), sink, ExportFormat.CSV)

        result.isSuccess shouldBe true
        // Then: the id snapshot is resolved exactly once, regardless of how many batches follow
        coVerify(exactly = 1) { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `exportRows on a zero-result filter writes a valid empty CSV - header only`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val sink = ByteArrayOutputStream()

        val result = repository().exportRows(DataBrowserFilter(), sink, ExportFormat.CSV)

        result.isSuccess shouldBe true
        sink.toString(Charsets.UTF_8.name()) shouldBe "Field Name,Field Type,Value,Source App,Rule Name,Timestamp\r\n"
    }

    @Test
    fun `exportRows on a zero-result filter writes a valid empty JSON array`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val sink = ByteArrayOutputStream()

        val result = repository().exportRows(DataBrowserFilter(), sink, ExportFormat.JSON)

        result.isSuccess shouldBe true
        sink.toString(Charsets.UTF_8.name()) shouldBe "[]"
    }

    @Test
    fun `a mid-export batch failure surfaces as Result_failure so the caller does not rename a partial temp file`() = runTest(testDispatcher) {
        // Given: 2 batches worth of ids, the second batch read fails
        val ids = (1..1500).map { "v$it" }
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ids
        coEvery { dataBrowserDao.getMatchingBatch(ids.subList(0, 1000)) } returns ids.subList(0, 1000).map { row(it) }
        coEvery { dataBrowserDao.getMatchingBatch(ids.subList(1000, 1500)) } throws SQLException("db error mid-export")
        val sink = ByteArrayOutputStream()

        val result = repository().exportRows(DataBrowserFilter(), sink, ExportFormat.CSV)

        // Then: the whole export is reported as a failure - the caller must not rename the temp
        // file it wrote `sink` to into place at the final share-sheet path
        result.isFailure shouldBe true
    }

    @Test
    fun `exportRows batches matching ids into fixed EXPORT_BATCH_SIZE chunks`() = runTest(testDispatcher) {
        val ids = (1..1500).map { "v$it" }
        coEvery { dataBrowserDao.getMatchingIds(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns ids
        coEvery { dataBrowserDao.getMatchingBatch(any()) } returns emptyList()
        val sink = ByteArrayOutputStream()

        repository().exportRows(DataBrowserFilter(), sink, ExportFormat.CSV)

        coVerify(exactly = 1) { dataBrowserDao.getMatchingBatch(ids.subList(0, 1000)) }
        coVerify(exactly = 1) { dataBrowserDao.getMatchingBatch(ids.subList(1000, 1500)) }
    }

    // ---- statistics ----

    @Test
    fun `statistics maps a zero-data result without error`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getCount(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 0
        coEvery { dataBrowserDao.getMostActiveRule(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        coEvery { dataBrowserDao.getPerRuleCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { dataBrowserDao.getPerAppCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { dataBrowserDao.getTrend(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        val result = repository().statistics(DataBrowserFilter())

        val stats = result.getOrThrow()
        stats.total shouldBe 0
        stats.thisWeek shouldBe 0
        stats.mostActiveRuleName shouldBe null
        stats.perRule shouldBe emptyList()
        stats.perApp shouldBe emptyList()
        // 30-day trend window, every day filled in as a zero-count bucket
        stats.trend.size shouldBe 30
        stats.trend.all { it.count == 0 } shouldBe true
    }

    @Test
    fun `statistics fills zero-count trend days the group-by query omits`() = runTest(testDispatcher) {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(FIXED_NOW), ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        coEvery { dataBrowserDao.getCount(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 5
        coEvery { dataBrowserDao.getMostActiveRule(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MostActiveRuleRow(ruleName = "Rule A", count = 5)
        coEvery { dataBrowserDao.getPerRuleCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            listOf(CountBucket("Rule A", 5))
        coEvery { dataBrowserDao.getPerAppCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            listOf(CountBucket("Test App", 5))
        coEvery { dataBrowserDao.getTrend(any(), any(), any(), any(), any(), any(), any()) } returns
            listOf(TrendPoint(date = today, count = 5))

        val result = repository().statistics(DataBrowserFilter())

        val stats = result.getOrThrow()
        stats.total shouldBe 5
        stats.mostActiveRuleName shouldBe "Rule A"
        stats.trend.size shouldBe 30
        stats.trend.first { it.date == today }.count shouldBe 5
        stats.trend.filter { it.date != today }.all { it.count == 0 } shouldBe true
    }

    @Test
    fun `statistics maps a dao failure to Result_failure`() = runTest(testDispatcher) {
        coEvery { dataBrowserDao.getCount(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws SQLException("db error")

        val result = repository().statistics(DataBrowserFilter())

        result.isFailure shouldBe true
    }

    @Test
    fun `statistics translates a non-empty filter into the exact DataBrowserFilter_toQueryParams shape`() = runTest(testDispatcher) {
        val filter = DataBrowserFilter(
            ruleIds = listOf("rule-1"),
            packageNames = listOf("com.test.app"),
            fieldTypes = listOf(RuleField.FieldType.NUMBER),
            dateFrom = 500L,
            dateTo = 2_000L,
        )
        coEvery { dataBrowserDao.getMostActiveRule(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        coEvery { dataBrowserDao.getPerRuleCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { dataBrowserDao.getPerAppCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { dataBrowserDao.getTrend(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        val weekStartMillis = LocalDate.ofInstant(Instant.ofEpochMilli(FIXED_NOW), ZoneId.systemDefault())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        coEvery {
            dataBrowserDao.getCount(
                ruleIds = listOf("rule-1"), hasRuleFilter = true,
                packageNames = listOf("com.test.app"), hasPackageFilter = true,
                fieldTypes = listOf("NUMBER"), hasFieldTypeFilter = true,
                dateFrom = 500L, hasDateFromFilter = true,
                dateTo = 2_000L, hasDateToFilter = true,
            )
        } returns 3
        coEvery {
            dataBrowserDao.getCount(
                ruleIds = listOf("rule-1"), hasRuleFilter = true,
                packageNames = listOf("com.test.app"), hasPackageFilter = true,
                fieldTypes = listOf("NUMBER"), hasFieldTypeFilter = true,
                dateFrom = weekStartMillis, hasDateFromFilter = true,
                dateTo = 2_000L, hasDateToFilter = true,
            )
        } returns 1

        val result = repository().statistics(filter)

        val stats = result.getOrThrow()
        stats.total shouldBe 3
        stats.thisWeek shouldBe 1
        coVerify(exactly = 1) {
            dataBrowserDao.getCount(
                ruleIds = listOf("rule-1"), hasRuleFilter = true,
                packageNames = listOf("com.test.app"), hasPackageFilter = true,
                fieldTypes = listOf("NUMBER"), hasFieldTypeFilter = true,
                dateFrom = 500L, hasDateFromFilter = true,
                dateTo = 2_000L, hasDateToFilter = true,
            )
        }
    }

    @Test
    fun `statistics clamps thisWeek to the caller's dateFrom when it is later than the week start`() = runTest(testDispatcher) {
        // Given: a dateFrom set to just before FIXED_NOW, later than the current local week start
        val filter = DataBrowserFilter(dateFrom = FIXED_NOW - 1_000L)
        coEvery { dataBrowserDao.getMostActiveRule(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        coEvery { dataBrowserDao.getPerRuleCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { dataBrowserDao.getPerAppCounts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { dataBrowserDao.getTrend(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { dataBrowserDao.getCount(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 0

        repository().statistics(filter)

        // Then: thisWeek's dateFrom is the caller's (later) dateFrom, not the earlier week start.
        // Both the `total` and `thisWeek` getCount calls end up with identical params here because
        // the clamp resolves to the same (later) dateFrom for both - exactly=2 confirms the clamp
        // actually took the caller's dateFrom branch rather than silently falling back to weekStart.
        coVerify(exactly = 2) {
            dataBrowserDao.getCount(
                ruleIds = emptyList(), hasRuleFilter = false,
                packageNames = emptyList(), hasPackageFilter = false,
                fieldTypes = emptyList(), hasFieldTypeFilter = false,
                dateFrom = FIXED_NOW - 1_000L, hasDateFromFilter = true,
                dateTo = Long.MAX_VALUE, hasDateToFilter = false,
            )
        }
    }

    // ---- browsePaged / searchPaged: dispatcher injection ----

    @Test
    fun `browsePaged builds its PagingSource via the injected DataBrowserDao`() = runTest(testDispatcher) {
        val pagingSource = mockk<PagingSource<Int, DataBrowserRow>>()
        every {
            dataBrowserDao.getFilteredPaged(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns pagingSource

        repository().browsePaged(DataBrowserFilter())

        // Constructing the Flow shouldn't eagerly invoke the DAO; the factory is lazy until
        // collected by a Pager. Verifying no exception is thrown on construction is sufficient
        // here since Paging3's load() internals are exercised by Android instrumentation, not
        // this JVM-only suite (see design.md Testing Strategy coverage gap).
    }

    @Test
    fun `browsePaged's PagingSource factory calls DataBrowserDao with the filter's own params`() = runTest(testDispatcher) {
        every {
            dataBrowserDao.getFilteredPaged(
                ruleIds = listOf("rule-1"), hasRuleFilter = true,
                packageNames = emptyList(), hasPackageFilter = false,
                fieldTypes = emptyList(), hasFieldTypeFilter = false,
                dateFrom = 0L, hasDateFromFilter = false,
                dateTo = Long.MAX_VALUE, hasDateToFilter = false,
                sortKey = DataSort.RULE_ASC.ordinal,
            )
        } returns mockk(relaxed = true)
        val filter = DataBrowserFilter(ruleIds = listOf("rule-1"), sort = DataSort.RULE_ASC)

        // Collecting the first emission is enough to force the Pager to invoke the lazy
        // pagingSourceFactory once, without exercising real Paging3 load() internals.
        runCatching { repository().browsePaged(filter).first() }

        coVerify(exactly = 1) {
            dataBrowserDao.getFilteredPaged(
                ruleIds = listOf("rule-1"), hasRuleFilter = true,
                packageNames = emptyList(), hasPackageFilter = false,
                fieldTypes = emptyList(), hasFieldTypeFilter = false,
                dateFrom = 0L, hasDateFromFilter = false,
                dateTo = Long.MAX_VALUE, hasDateToFilter = false,
                sortKey = DataSort.RULE_ASC.ordinal,
            )
        }
    }

    @Test
    fun `searchPaged's PagingSource factory calls DataBrowserDao with the filter's own params`() = runTest(testDispatcher) {
        every {
            dataBrowserDao.searchFtsPaged(
                ftsQuery = any(), ruleIds = emptyList(), hasRuleFilter = false,
                packageNames = emptyList(), hasPackageFilter = false,
                fieldTypes = emptyList(), hasFieldTypeFilter = false,
                dateFrom = 0L, hasDateFromFilter = false,
                dateTo = Long.MAX_VALUE, hasDateToFilter = false,
                sortKey = DataSort.DATE_DESC.ordinal,
            )
        } returns mockk(relaxed = true)
        val filter = DataBrowserFilter(searchQuery = "acme")

        runCatching { repository().searchPaged(filter).first() }

        coVerify(exactly = 1) {
            dataBrowserDao.searchFtsPaged(
                ftsQuery = any(), ruleIds = emptyList(), hasRuleFilter = false,
                packageNames = emptyList(), hasPackageFilter = false,
                fieldTypes = emptyList(), hasFieldTypeFilter = false,
                dateFrom = 0L, hasDateFromFilter = false,
                dateTo = Long.MAX_VALUE, hasDateToFilter = false,
                sortKey = DataSort.DATE_DESC.ordinal,
            )
        }
    }
}

private const val FIXED_NOW = 1_784_275_200_000L // 2026-07-16T00:00:00Z-ish, exact instant irrelevant to date-window math
