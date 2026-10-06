package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.data.local.dao.ActionTypeRow
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.dao.WebhookDeliveryDao
import dev.gaferneira.notificapp.core.data.local.dao.WebhookPendingCount
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.CountBucket
import dev.gaferneira.notificapp.domain.model.FieldTotal
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.model.OutcomeCounts
import dev.gaferneira.notificapp.domain.model.RuleActivity
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleRef
import dev.gaferneira.notificapp.domain.model.TrendPoint
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.testutil.fakes.FakeWebhookRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsRepositoryImplTest {

    private val notificationDao = mockk<NotificationDao>()
    private val ruleExecutionDao = mockk<RuleExecutionDao>()
    private val extractedFieldValueDao = mockk<ExtractedFieldValueDao>()
    private val ruleDao = mockk<RuleDao>()
    private val webhookRepository = FakeWebhookRepository()
    private val pendingCounts = MutableStateFlow<List<WebhookPendingCount>>(emptyList())
    private val deliveryDao = mockk<WebhookDeliveryDao> {
        every { observeUnresolvedCounts() } returns pendingCounts
    }
    private val currentTimeProvider = mockk<CurrentTimeProvider> {
        every { nowEpochMillis() } returns NOW
    }

    private fun TestScope.repository() = InsightsRepositoryImpl(
        notificationDao = notificationDao,
        ruleExecutionDao = ruleExecutionDao,
        extractedFieldValueDao = extractedFieldValueDao,
        ruleDao = ruleDao,
        webhookRepository = webhookRepository,
        webhookDeliveryDao = deliveryDao,
        currentTimeProvider = currentTimeProvider,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Suppress("LongParameterList") // test stub builder: one named, defaulted knob per DAO stream
    private fun stubDaos(
        captured: Int = 0,
        handled: Int = 0,
        values: Int = 0,
        ruleCount: Int = 1,
        daily: List<TrendPoint> = emptyList(),
        totals: List<FieldTotal> = emptyList(),
        topRules: List<RuleActivity> = emptyList(),
        quiet: List<RuleRef> = emptyList(),
        topApps: List<CountBucket> = emptyList(),
        outcomes: List<String> = emptyList(),
        actionTypes: List<ActionTypeRow> = emptyList(),
        dailyFlow: Flow<List<TrendPoint>> = flowOf(daily),
    ) {
        every { notificationDao.observeCountSince(any()) } returns flowOf(captured)
        every { notificationDao.observeProcessedCountSince(any()) } returns flowOf(handled)
        every { extractedFieldValueDao.observeCountSince(any()) } returns flowOf(values)
        every { ruleDao.observeCount() } returns flowOf(ruleCount)
        every { ruleExecutionDao.observeDailyCountsSince(any()) } returns dailyFlow
        every { extractedFieldValueDao.observeNumericTotalsSince(any()) } returns flowOf(totals)
        every { ruleExecutionDao.observeTopRulesSince(any(), any()) } returns flowOf(topRules)
        every { ruleDao.observeQuietRulesSince(any()) } returns flowOf(quiet)
        every { notificationDao.observeTopAppsSince(any(), any()) } returns flowOf(topApps)
        every { ruleExecutionDao.observeActionOutcomesSince(any()) } returns flowOf(outcomes)
        every { ruleDao.observeActionTypes() } returns flowOf(actionTypes)
    }

    private suspend fun TestScope.summary(period: InsightsPeriod = InsightsPeriod.DAYS_7): InsightsSummary = repository().observeSummary(period).first().getOrThrow()

    @Test
    fun `given sparse daily counts, when observing 7 days, then trend is zero-filled oldest first`() = runTest {
        stubDaos(daily = listOf(TrendPoint("2026-03-13", 4)))

        val trend = summary().trend

        trend.map { it.date } shouldBe (9..15).map { "2026-03-%02d".format(it) }
        trend.map { it.count } shouldBe listOf(0, 0, 0, 0, 4, 0, 0)
    }

    @Test
    fun `given the 30 day period, when observing, then trend spans 30 days and since is the first local midnight`() = runTest {
        stubDaos()
        val sinceSlot = mutableListOf<Long>()
        every { ruleExecutionDao.observeDailyCountsSince(capture(sinceSlot)) } returns flowOf(emptyList())

        val trend = summary(InsightsPeriod.DAYS_30).trend

        trend.size shouldBe 30
        trend.first().date shouldBe "2026-02-14"
        trend.last().date shouldBe "2026-03-15"
        sinceSlot.single() shouldBe LocalDate.of(2026, 2, 14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun `given counts from the DAOs, when observing, then overview and empty-state flag map through`() = runTest {
        stubDaos(captured = 12, handled = 5, values = 7, ruleCount = 0)

        val result = summary()

        result.capturedCount shouldBe 12
        result.handledCount shouldBe 5
        result.valuesExtractedCount shouldBe 7
        result.hasAnyRules shouldBe false
    }

    @Test
    fun `given the 7 day period, when observing, then windowStart is the bound every overview count uses`() = runTest {
        stubDaos()
        val bounds = mutableListOf<Long>()
        every { notificationDao.observeCountSince(capture(bounds)) } returns flowOf(0)
        every { notificationDao.observeProcessedCountSince(capture(bounds)) } returns flowOf(0)
        every { extractedFieldValueDao.observeCountSince(capture(bounds)) } returns flowOf(0)
        every { extractedFieldValueDao.observeNumericTotalsSince(capture(bounds)) } returns flowOf(emptyList())

        val result = summary()

        result.windowStart shouldBe LocalDate.of(2026, 3, 9).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        bounds shouldBe List(4) { result.windowStart }
    }

    @Test
    fun `given processed notifications, when observing, then handled comes from the processed count, not executions`() = runTest {
        stubDaos(handled = 3)
        every { ruleExecutionDao.observeExecutionCountSince(any()) } returns flowOf(99)

        val result = summary()

        result.handledCount shouldBe 3
        verify(exactly = 0) { ruleExecutionDao.observeExecutionCountSince(any()) }
    }

    @Test
    fun `given mixed action outcomes, when observing, then they aggregate per type with skipped and suppressed merged`() = runTest {
        stubDaos(
            outcomes = listOf(
                """{"a1":"SUCCESS","a2":"FAILED"}""",
                """{"a1":"FAILED"}""",
                """{"a1":"SKIPPED","gone":"SUCCESS"}""",
                """{"a1":"SUPPRESSED"}""",
                "not json",
            ),
            actionTypes = listOf(
                ActionTypeRow("a1", ActionType.SEND_WEBHOOK.name),
                ActionTypeRow("a2", ActionType.DISMISS_NOTIFICATION.name),
                ActionTypeRow("a3", "REMOVED_TYPE"),
            ),
        )

        val result = summary()

        result.actionOutcomes shouldBe mapOf(
            ActionType.DISMISS_NOTIFICATION to OutcomeCounts(failed = 1),
            ActionType.SEND_WEBHOOK to OutcomeCounts(success = 1, failed = 1, skipped = 2),
        )
        // run = SUCCESS + FAILED across all outcomes (including the orphaned "gone" action)
        result.actionsRunCount shouldBe 4
        result.actionsFailedCount shouldBe 2
    }

    @Test
    fun `given top rules and apps, when observing, then the DAOs are capped at 5 and results pass through`() = runTest {
        val rules = (1..5).map { RuleActivity("r$it", "Rule $it", 10 - it) }
        val apps = (1..5).map { CountBucket("App $it", 10 - it) }
        stubDaos(topRules = rules, topApps = apps)

        val result = summary()

        result.topRules shouldBe rules
        result.topApps shouldBe apps
        verify { ruleExecutionDao.observeTopRulesSince(any(), 5) }
        verify { notificationDao.observeTopAppsSince(any(), 5) }
    }

    @Test
    fun `given each period, when observing, then quiet rules always look back 30 days`() = runTest {
        stubDaos()
        val quietSince = mutableListOf<Long>()
        every { ruleDao.observeQuietRulesSince(capture(quietSince)) } returns flowOf(listOf(RuleRef("q", "Quiet")))

        val sevenDays = summary(InsightsPeriod.DAYS_7)
        summary(InsightsPeriod.DAYS_30)

        sevenDays.quietRules shouldBe listOf(RuleRef("q", "Quiet"))
        quietSince shouldBe listOf(NOW - THIRTY_DAYS_MILLIS, NOW - THIRTY_DAYS_MILLIS)
    }

    @Test
    fun `given numeric field totals, when observing, then they pass through unchanged`() = runTest {
        val totals = listOf(FieldTotal("r1", "ICA", "f1", "Amount", RuleField.FieldType.CURRENCY, 2340.0, 12))
        stubDaos(totals = totals)

        summary().fieldTotals shouldBe totals
    }

    @Test
    fun `given unresolved deliveries, when observing, then pending retries come from the per-webhook counts`() = runTest {
        stubDaos()
        webhookRepository.setWebhooks(
            listOf(
                Webhook(id = "w1", name = "Hook 1", url = "https://a.example", lastDeliveryStatus = WebhookDeliveryStatus.UNREACHABLE, lastDeliveryAt = 99L),
                Webhook(id = "w2", name = "Hook 2", url = "https://b.example", lastDeliveryStatus = WebhookDeliveryStatus.DELIVERED, lastDeliveryAt = 50L),
            ),
        )
        pendingCounts.value = listOf(WebhookPendingCount("w1", 2))

        val webhooks = summary().webhooks

        webhooks.map { it.webhookId to it.pendingRetries } shouldBe listOf("w1" to 2, "w2" to 0)
        webhooks.first().lastStatus shouldBe WebhookDeliveryStatus.UNREACHABLE
        webhooks.first().lastDeliveryAt shouldBe 99L
    }

    @Test
    fun `given the delivery queue changes, when observing, then pending retries refresh live`() = runTest {
        stubDaos()
        webhookRepository.setWebhooks(listOf(Webhook(id = "w1", name = "Hook 1", url = "https://a.example")))
        val emissions = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().observeSummary(InsightsPeriod.DAYS_7).collect { emissions += it.getOrThrow().webhooks.single().pendingRetries }
        }

        pendingCounts.value = listOf(WebhookPendingCount("w1", 3))
        job.cancel()

        emissions.first() shouldBe 0
        emissions.last() shouldBe 3
    }

    @Test
    fun `given a failing DAO flow, when observing, then a mapped Failure result is emitted`() = runTest {
        stubDaos(dailyFlow = flow { throw IllegalStateException("db exploded") })

        val result = repository().observeSummary(InsightsPeriod.DAYS_7).first()

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<Failure.ApplicationException>()
    }

    private companion object {
        val NOW: Long = ZonedDateTime.of(2026, 3, 15, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
