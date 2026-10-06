package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.repository.InsightsRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

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
            trend = persistentListOf(),
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
