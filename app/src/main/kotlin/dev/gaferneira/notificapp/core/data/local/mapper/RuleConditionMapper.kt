package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition

/**
 * Mapper functions for converting between RuleCondition domain models and RuleConditionEntity database models.
 */
internal object RuleConditionMapper {

    /**
     * Convert a RuleCondition domain model to a RuleConditionEntity.
     *
     * @param domain The domain model
     * @param ruleId The parent rule ID for the foreign key
     * @return The database entity
     */
    fun toEntity(domain: RuleCondition, ruleId: String): RuleConditionEntity = RuleConditionEntity(
        id = domain.id,
        ruleId = ruleId,
        condition = domain.condition?.name,
        operator = domain.operator?.name,
        value = domain.value,
    )

    /**
     * Convert a RuleConditionEntity to a RuleCondition domain model.
     *
     * @param entity The database entity
     * @return The domain model
     */
    fun toDomain(entity: RuleConditionEntity): RuleCondition = RuleCondition(
        id = entity.id,
        condition = entity.condition?.let { MatchingCondition.valueOf(it) },
        operator = entity.operator?.let { MatchingOperator.valueOf(it) },
        value = entity.value,
    )

    /**
     * Convert a list of RuleCondition domain models to entities.
     *
     * @param domains The domain models
     * @param ruleId The parent rule ID for all conditions
     * @return The database entities
     */
    fun toEntityList(domains: List<RuleCondition>, ruleId: String): List<RuleConditionEntity> = domains.map { toEntity(it, ruleId) }

    /**
     * Convert a list of RuleConditionEntity to domain models.
     *
     * @param entities The database entities
     * @return The domain models
     */
    fun toDomainList(entities: List<RuleConditionEntity>): List<RuleCondition> = entities.map { toDomain(it) }
}
