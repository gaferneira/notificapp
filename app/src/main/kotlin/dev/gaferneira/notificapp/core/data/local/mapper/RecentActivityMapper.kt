package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.dao.RecentActivityRow
import dev.gaferneira.notificapp.domain.model.RecentActivity

/**
 * Maps [RecentActivityRow] (the [dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao]
 * join projection) to the domain [RecentActivity] model.
 */
internal object RecentActivityMapper {
    fun toDomain(row: RecentActivityRow): RecentActivity = RecentActivity(
        executionId = row.executionId,
        ruleId = row.ruleId,
        ruleName = row.ruleName,
        notificationId = row.notificationId,
        notificationTitle = row.notificationTitle,
        notificationContent = row.notificationContent,
        packageName = row.packageName,
        appName = row.appName,
        executedAt = row.executedAt,
    )
}
