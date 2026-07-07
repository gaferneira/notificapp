package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import java.util.UUID

/**
 * Mapper functions for converting between Rule domain models and RuleEntity database models.
 */
object RuleMapper {

    /**
     * Convert a RuleEntity to an Rule domain model.
     *
     * @param entity The database entity
     * @param fieldEntities List of field entities for this rule
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
        targetApps = if (entity.isGlobal) null else targetApps,
        conditions = RuleConditionMapper.toDomainList(conditionEntities),
        fields = RuleFieldMapper.toDomainList(fieldEntities),
        actions = normalizeActions(
            RuleActionMapper.toDomainList(actionEntities),
            RuleFieldMapper.toDomainList(fieldEntities),
        ),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    /**
     * Back-compat normalization: extraction fields and the `SAVE_DATA` action used to be
     * decoupled, so a stored rule can have fields with no `SAVE_DATA` action. Synthesize an enabled
     * one so its field values keep being persisted after Extract-data gating is introduced. A rule
     * that already has a (possibly disabled) `SAVE_DATA` action reflects a deliberate post-change
     * state and is left untouched.
     */
    @androidx.annotation.VisibleForTesting
    internal fun normalizeActions(actions: List<RuleAction>, fields: List<RuleField>): List<RuleAction> {
        val hasSaveDataAction = actions.any { it.type == ActionType.SAVE_DATA }
        return if (fields.isNotEmpty() && !hasSaveDataAction) {
            actions + RuleAction(id = UUID.randomUUID().toString(), type = ActionType.SAVE_DATA)
        } else {
            actions
        }
    }

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
        isGlobal = domain.targetApps == null,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
    )

    /**
     * Convert RuleField domain models to entities for a specific rule.
     *
     * @param fields The domain models
     * @param ruleId The parent rule ID
     * @return The database entities
     */
    fun fieldsToEntityList(fields: List<RuleField>, ruleId: String): List<RuleFieldEntity> = RuleFieldMapper.toEntityList(fields, ruleId)

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
