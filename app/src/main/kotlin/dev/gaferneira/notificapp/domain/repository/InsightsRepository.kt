package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.ActionBreakdownRow
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/** Read-only aggregation surface backing the Insights dashboard. */
interface InsightsRepository {

    /**
     * Continuously observes the dashboard aggregates for [period]. Each emission is a full
     * snapshot; storage failures are emitted as `Result.failure` carrying a mapped `Failure`
     * (ADR 006) and the flow then completes.
     */
    fun observeSummary(period: InsightsPeriod): Flow<Result<InsightsSummary>>

    /**
     * Continuously observes which rules produced action outcomes in [period], one row per
     * (action type, rule), those with the most failures first. [type] limits the rows to one action
     * type; null covers all of them. Failures are emitted like [observeSummary].
     */
    fun observeActionBreakdown(type: ActionType?, period: InsightsPeriod): Flow<Result<ImmutableList<ActionBreakdownRow>>>
}
