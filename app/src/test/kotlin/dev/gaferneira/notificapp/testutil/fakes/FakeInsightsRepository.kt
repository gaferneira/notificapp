package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.ActionBreakdownRow
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.repository.InsightsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * Deterministic in-memory [InsightsRepository] fake for VM tests. Every period is served from a
 * [MutableStateFlow] of [Result]s, overridable per period via [setSummary]/[setFailure]; periods
 * without an override fall back to [default]. [requestedPeriods] records each observed period in
 * order, so tests can assert period switches.
 */
class FakeInsightsRepository(initial: InsightsSummary = emptySummary()) : InsightsRepository {

    private val default = MutableStateFlow(Result.success(initial))
    private val perPeriod = mutableMapOf<InsightsPeriod, MutableStateFlow<Result<InsightsSummary>>>()

    /** Periods passed to [observeSummary], oldest first. */
    val requestedPeriods = mutableListOf<InsightsPeriod>()

    /** Emit [summary] to observers of [period], or of every non-overridden period when null. */
    fun setSummary(summary: InsightsSummary, period: InsightsPeriod? = null) = flowFor(period).let { it.value = Result.success(summary) }

    /** Emit a failure to observers of [period], or of every non-overridden period when null. */
    fun setFailure(failure: Throwable, period: InsightsPeriod? = null) = flowFor(period).let { it.value = Result.failure(failure) }

    private val breakdown = MutableStateFlow<Result<ImmutableList<ActionBreakdownRow>>>(Result.success(persistentListOf()))

    /** Arguments of every [observeActionBreakdown] call, oldest first. */
    val requestedBreakdowns = mutableListOf<Pair<ActionType?, InsightsPeriod>>()

    /** Number of live collectors of [observeActionBreakdown], to assert cancellation. */
    var activeBreakdownCollectors = 0
        private set

    /** Emit [rows] to the breakdown observers. */
    fun setBreakdown(rows: List<ActionBreakdownRow>) {
        breakdown.value = Result.success(persistentListOf<ActionBreakdownRow>().addAll(rows))
    }

    /** Emit a failure to the breakdown observers. */
    fun setBreakdownFailure(failure: Throwable) {
        breakdown.value = Result.failure(failure)
    }

    override fun observeActionBreakdown(type: ActionType?, period: InsightsPeriod): Flow<Result<ImmutableList<ActionBreakdownRow>>> {
        requestedBreakdowns += type to period
        return breakdown
            .onStart { activeBreakdownCollectors++ }
            .onCompletion { activeBreakdownCollectors-- }
    }

    override fun observeSummary(period: InsightsPeriod): Flow<Result<InsightsSummary>> {
        requestedPeriods += period
        // Stamp the requested period like the real repository does, so period switches are observable in state.
        return (perPeriod[period] ?: default).map { result -> result.map { it.copy(period = period) } }
    }

    private fun flowFor(period: InsightsPeriod?): MutableStateFlow<Result<InsightsSummary>> = if (period == null) {
        default
    } else {
        perPeriod.getOrPut(period) { MutableStateFlow(default.value) }
    }

    companion object {
        /** Fixed window start served by [emptySummary], so tests can assert it reaches navigation effects. */
        const val WINDOW_START = 1_773_532_800_000L

        /** A summary with no data and no rules (the global empty state). */
        fun emptySummary(hasAnyRules: Boolean = false) = InsightsSummary(
            period = InsightsPeriod.DAYS_7,
            windowStart = WINDOW_START,
            capturedCount = 0,
            handledCount = 0,
            actionsRunCount = 0,
            actionsFailedCount = 0,
            valuesExtractedCount = 0,
            trends = persistentMapOf(),
            fieldTotals = persistentListOf(),
            topRules = persistentListOf(),
            quietRules = persistentListOf(),
            actionOutcomes = persistentMapOf(),
            webhooks = persistentListOf(),
            topApps = persistentListOf(),
            hasAnyRules = hasAnyRules,
        )
    }
}
