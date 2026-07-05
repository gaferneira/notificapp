package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Mapper functions for converting between RuleAction domain models and RuleActionEntity database models.
 */
object RuleActionMapper {

    /**
     * Convert a RuleAction domain model to a RuleActionEntity.
     *
     * @param domain The domain model
     * @param ruleId The parent rule ID for the foreign key
     * @return The database entity
     */
    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(domain: RuleAction, ruleId: String): RuleActionEntity = RuleActionEntity(
        id = domain.id,
        ruleId = ruleId,
        type = domain.type.name,
        isEnabled = domain.isEnabled,
        config = json.encodeToString(domain.config),
    )

    /**
     * Convert a RuleActionEntity to a RuleAction domain model.
     *
     * @param entity The database entity
     * @return The domain model
     */
    fun toDomain(entity: RuleActionEntity): RuleAction = RuleAction(
        id = entity.id,
        type = ActionType.valueOf(entity.type),
        isEnabled = entity.isEnabled,
        config = try {
            json.decodeFromString<Map<String, String>>(entity.config)
        } catch (e: SerializationException) {
            Timber.d(e, "Failed to deserialize action config for ${entity.id}, using empty map")
            emptyMap()
        },
    )

    /**
     * Convert a list of RuleAction domain models to entities.
     *
     * @param domains The domain models
     * @param ruleId The parent rule ID for all actions
     * @return The database entities
     */
    fun toEntityList(domains: List<RuleAction>, ruleId: String): List<RuleActionEntity> = domains.map { toEntity(it, ruleId) }

    /**
     * Convert a list of RuleActionEntity to domain models.
     *
     * @param entities The database entities
     * @return The domain models
     */
    fun toDomainList(entities: List<RuleActionEntity>): List<RuleAction> = entities.map { toDomain(it) }
}
