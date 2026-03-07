package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule

/**
 * Mapper functions for converting between Rule domain models and RuleEntity database models.
 */
object RuleMapper {

    /**
     * Convert a RuleEntity to an Rule domain model.
     *
     * @param entity The database entity
     * @param targetApps List of package names for target apps (null if global rule)
     * @return The domain model
     */
    fun toDomain(entity: RuleEntity, targetApps: List<AppInfo>? = null): Rule = Rule(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        category = entity.category,
        isActive = entity.isActive,
        targetApps = if (entity.isGlobal) null else targetApps,
        conditions = entity.triggers,
        fields = entity.ruleFields,
        actions = entity.actions,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /**
     * Convert an Rule domain model to a RuleEntity.
     *
     * Note: Target apps are stored separately in rule_target_apps table.
     *
     * @param domain The domain model
     * @return The database entity
     */
    fun toEntity(domain: Rule): RuleEntity = RuleEntity(
        id = domain.id,
        name = domain.name,
        description = domain.description,
        category = domain.category,
        area = null, // Not yet in domain model
        isActive = domain.isActive,
        isGlobal = domain.targetApps == null,
        ruleFields = domain.fields,
        triggers = domain.conditions,
        actions = domain.actions,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
    )
}
