package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.local.dao.ActionTypeRow
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.dao.WebhookDeliveryDao
import dev.gaferneira.notificapp.core.data.local.mapper.RuleExecutionMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.CountBucket
import dev.gaferneira.notificapp.domain.model.FieldTotal
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.model.OutcomeCounts
import dev.gaferneira.notificapp.domain.model.RuleActivity
import dev.gaferneira.notificapp.domain.model.RuleRef
import dev.gaferneira.notificapp.domain.model.TrendPoint
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookHealth
import dev.gaferneira.notificapp.domain.repository.InsightsRepository
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Implementation of [InsightsRepository].
 *
 * Composes reactive DAO queries (Room re-emits when any observed table changes) plus the webhook
 * list into one [InsightsSummary] per emission. All DAO queries already exclude dry runs. The
 * period/quiet-rule cutoffs are resolved from [CurrentTimeProvider] once per collection, so a
 * long-lived collector keeps its original window until the period is re-observed.
 * Storage errors are mapped to `Failure` (ADR 006) and emitted as `Result.failure`.
 */
@Suppress("LongParameterList") // Hilt-injected composition of five DAOs, one repository, clock, dispatcher
internal class InsightsRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
    private val ruleExecutionDao: RuleExecutionDao,
    private val extractedFieldValueDao: ExtractedFieldValueDao,
    private val ruleDao: RuleDao,
    private val webhookRepository: WebhookRepository,
    private val webhookDeliveryDao: WebhookDeliveryDao,
    private val currentTimeProvider: CurrentTimeProvider,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : InsightsRepository {

    override fun observeSummary(period: InsightsPeriod): Flow<Result<InsightsSummary>> = flow {
        emitAll(summaryFlow(period))
    }
        .conflate() // a burst of rule_executions writes re-triggers the whole combine; keep only the latest summary
        .map { Result.success(it) }
        .catch { e ->
            Timber.e(e, "Failed to observe insights summary")
            emit(e.toFailureResult())
        }
        .flowOn(ioDispatcher)

    private fun summaryFlow(period: InsightsPeriod): Flow<InsightsSummary> {
        // Assumes the device zone matches SQLite's 'localtime' used by the daily-count query
        // (RuleExecutionDao.observeDailyCountsSince); the zero-fill below and that bucketing must agree.
        val zone = ZoneId.systemDefault()
        val nowMillis = currentTimeProvider.nowEpochMillis()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val firstDay = today.minusDays((period.days - 1).toLong())
        val since = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val quietSince = nowMillis - QUIET_WINDOW_MILLIS

        return combine(
            overviewFlow(since),
            breakdownFlow(since, quietSince),
            actionStatsFlow(since),
            webhookHealthFlow(),
        ) { overview, breakdown, actions, webhooks ->
            InsightsSummary(
                period = period,
                windowStart = since,
                capturedCount = overview.captured,
                handledCount = overview.handled,
                actionsRunCount = actions.runCount,
                actionsFailedCount = actions.failedCount,
                valuesExtractedCount = overview.values,
                trend = zeroFill(breakdown.daily, firstDay, period.days).toImmutableList(),
                fieldTotals = breakdown.fieldTotals.toImmutableList(),
                topRules = breakdown.topRules.toImmutableList(),
                quietRules = breakdown.quietRules.toImmutableList(),
                actionOutcomes = actions.byType.toImmutableMap(),
                webhooks = webhooks.toImmutableList(),
                topApps = breakdown.topApps.toImmutableList(),
                hasAnyRules = overview.ruleCount > 0,
            )
        }
    }

    private fun overviewFlow(since: Long): Flow<Overview> = combine(
        notificationDao.observeCountSince(since),
        notificationDao.observeProcessedCountSince(since),
        extractedFieldValueDao.observeCountSince(since),
        ruleDao.observeCount(),
    ) { captured, handled, values, rules -> Overview(captured, handled, values, rules) }

    private fun breakdownFlow(since: Long, quietSince: Long): Flow<Breakdown> = combine(
        ruleExecutionDao.observeDailyCountsSince(since),
        extractedFieldValueDao.observeNumericTotalsSince(since),
        ruleExecutionDao.observeTopRulesSince(since, TOP_LIMIT),
        ruleDao.observeQuietRulesSince(quietSince),
        notificationDao.observeTopAppsSince(since, TOP_LIMIT),
    ) { daily, totals, top, quiet, apps -> Breakdown(daily, totals, top, quiet, apps) }

    private fun actionStatsFlow(since: Long): Flow<ActionStats> = combine(
        ruleExecutionDao.observeActionOutcomesSince(since),
        ruleDao.observeActionTypes(),
    ) { rawOutcomes, actionTypes -> aggregateActionOutcomes(rawOutcomes, actionTypes) }

    private fun webhookHealthFlow(): Flow<List<WebhookHealth>> = combine(
        webhookRepository.observeWebhooks(),
        webhookDeliveryDao.observeUnresolvedCounts(),
    ) { webhooks, pending ->
        val pendingByWebhook = pending.associate { it.webhookId to it.count }
        webhooks.map { it.toHealth(pendingByWebhook[it.id] ?: 0) }
    }

    private fun Webhook.toHealth(pending: Int) = WebhookHealth(
        webhookId = id,
        name = name,
        lastStatus = lastDeliveryStatus,
        lastDeliveryAt = lastDeliveryAt,
        pendingRetries = pending,
    )

    private fun zeroFill(daily: List<TrendPoint>, firstDay: LocalDate, days: Int): List<TrendPoint> {
        val countsByDate = daily.associate { it.date to it.count }
        return (0 until days).map { offset ->
            val date = firstDay.plusDays(offset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            TrendPoint(date = date, count = countsByDate[date] ?: 0)
        }
    }

    private data class Overview(val captured: Int, val handled: Int, val values: Int, val ruleCount: Int)

    private data class Breakdown(
        val daily: List<TrendPoint>,
        val fieldTotals: List<FieldTotal>,
        val topRules: List<RuleActivity>,
        val quietRules: List<RuleRef>,
        val topApps: List<CountBucket>,
    )

    private companion object {
        const val TOP_LIMIT = 5
        const val QUIET_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

/** Aggregated action outcomes: totals plus per-[ActionType] counts. */
internal data class ActionStats(
    val runCount: Int,
    val failedCount: Int,
    val byType: Map<ActionType, OutcomeCounts>,
)

/**
 * Folds raw `action_outcomes` JSON blobs into [ActionStats]. `SUCCESS` and `FAILED` count as "run";
 * `SKIPPED` and `SUPPRESSED` both land in [OutcomeCounts.skipped]. Outcomes whose action no longer
 * exists (rule edited/deleted) still count toward the totals but cannot be attributed to a type.
 */
internal fun aggregateActionOutcomes(rawOutcomes: List<String>, actionTypes: List<ActionTypeRow>): ActionStats {
    val typeById = actionTypes.mapNotNull { row ->
        runCatching { ActionType.valueOf(row.type) }.getOrNull()?.let { row.id to it }
    }.toMap()
    val byType = mutableMapOf<ActionType, OutcomeCounts>()
    var runCount = 0
    var failedCount = 0
    rawOutcomes.forEach { raw ->
        RuleExecutionMapper.decodeActionOutcomes(raw).forEach { (actionId, outcome) ->
            if (outcome == ActionOutcome.SUCCESS || outcome == ActionOutcome.FAILED) runCount++
            if (outcome == ActionOutcome.FAILED) failedCount++
            typeById[actionId]?.let { type -> byType[type] = (byType[type] ?: OutcomeCounts()).withOutcome(outcome) }
        }
    }
    return ActionStats(runCount, failedCount, byType.toSortedMap())
}

private fun OutcomeCounts.withOutcome(outcome: ActionOutcome): OutcomeCounts = when (outcome) {
    ActionOutcome.SUCCESS -> copy(success = success + 1)
    ActionOutcome.FAILED -> copy(failed = failed + 1)
    ActionOutcome.SKIPPED, ActionOutcome.SUPPRESSED -> copy(skipped = skipped + 1)
}
