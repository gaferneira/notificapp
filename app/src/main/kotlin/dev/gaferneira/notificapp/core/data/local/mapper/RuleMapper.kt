package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import kotlinx.collections.immutable.toImmutableList

/**
 * Mapper functions for converting between Rule domain models and RuleEntity database models.
 */
internal object RuleMapper {

    /**
     * Convert a RuleEntity to an Rule domain model.
     *
     * @param entity The database entity
     * @param fieldEntities Field entities owned by this rule's `SAVE_DATA` action (keyed on `action_id`)
     * @param conditionEntities List of condition entities for this rule
     * @param actionEntities List of action entities for this rule
     * @param targetApps List of package names for target apps (null if global rule)
     * @return The domain model
     */
    fun toDomain(
        entity: RuleEntity,
        fieldEntities: List<RuleFieldEntity>,
        conditionEntities: List<RuleConditionEntity>,
        actionEntities: List<RuleActionEntity>,
        targetApps: List<AppInfo>? = null,
    ): Rule = Rule(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        category = entity.category?.takeIf { it.isNotBlank() },
        isActive = entity.isActive,
        isDryRun = entity.isDryRun,
        targetApps = targetApps?.toImmutableList()?.takeIf { it.isNotEmpty() },
        isIncludeMode = entity.isIncludeMode,
        conditions = RuleConditionMapper.toDomainList(conditionEntities).toImmutableList(),
        actions = RuleActionMapper.toDomainList(actionEntities, fieldEntities).toImmutableList(),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /**
     * Convert an Rule domain model to a RuleEntity.
     *
     * Note: Target apps, fields, conditions, and actions are stored separately.
     *
     * @param domain The domain model
     * @return The database entity
     */
    fun toEntity(domain: Rule): RuleEntity = RuleEntity(
        id = domain.id,
        name = domain.name,
        description = domain.description,
        category = domain.category?.takeIf { it.isNotBlank() },
        isActive = domain.isActive,
        isDryRun = domain.isDryRun,
        isIncludeMode = domain.isIncludeMode,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
    )

    /**
     * Derive field entities from the rule's actions. Only the `SAVE_DATA` action carries fields
     * (an invariant enforced by the domain model), so this simply flattens whatever fields each
     * action holds, keying each field entity to its owning action's id.
     *
     * @param actions The rule's domain actions
     * @return The database entities, keyed to their owning action
     */
    fun fieldsToEntityList(actions: List<RuleAction>): List<RuleFieldEntity> = actions.flatMap { action -> RuleFieldMapper.toEntityList(action.fields, action.id) }

    /**
     * Convert RuleCondition domain models to entities for a specific rule.
     *
     * @param conditions The domain models
     * @param ruleId The parent rule ID
     * @return The database entities
     */
    fun conditionsToEntityList(conditions: List<RuleCondition>, ruleId: String): List<RuleConditionEntity> = RuleConditionMapper.toEntityList(conditions, ruleId)

    /**
     * Convert RuleAction domain models to entities for a specific rule.
     *
     * @param actions The domain models
     * @param ruleId The parent rule ID
     * @return The database entities
     */
    fun actionsToEntityList(actions: List<RuleAction>, ruleId: String): List<RuleActionEntity> = RuleActionMapper.toEntityList(actions, ruleId)
}
