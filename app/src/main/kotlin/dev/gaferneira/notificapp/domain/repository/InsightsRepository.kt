package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import kotlinx.coroutines.flow.Flow

/** Read-only aggregation surface backing the Insights dashboard. */
interface InsightsRepository {

    /**
     * Continuously observes the dashboard aggregates for [period]. Each emission is a full
     * snapshot; storage failures are emitted as `Result.failure` carrying a mapped `Failure`
     * (ADR 006) and the flow then completes.
     */
    fun observeSummary(period: InsightsPeriod): Flow<Result<InsightsSummary>>
}
