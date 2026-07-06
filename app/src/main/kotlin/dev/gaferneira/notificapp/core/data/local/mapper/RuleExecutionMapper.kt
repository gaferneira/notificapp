package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.RuleExecution
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Mapper functions for converting between RuleExecution domain models and RuleExecutionEntity database models.
 */
object RuleExecutionMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Convert a RuleExecution domain model to a RuleExecutionEntity.
     *
     * @param domain The domain model
     * @return The database entity
     */
    fun toEntity(domain: RuleExecution): RuleExecutionEntity = RuleExecutionEntity(
        id = domain.id,
        notificationId = domain.notificationId,
        ruleId = domain.ruleId,
        extractedData = json.encodeToString(domain.extractedData),
        triggeredActions = json.encodeToString(domain.triggeredActions),
        actionOutcomes = domain.actionOutcomes.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
        wasDryRun = domain.wasDryRun,
        createdAt = domain.createdAt,
    )

    /**
     * Convert a RuleExecutionEntity to a RuleExecution domain model.
     *
     * @param entity The database entity
     * @return The domain model
     */
    fun toDomain(entity: RuleExecutionEntity): RuleExecution = RuleExecution(
        id = entity.id,
        notificationId = entity.notificationId,
        ruleId = entity.ruleId,
        extractedData = json.decodeFromString(entity.extractedData),
        triggeredActions = json.decodeFromString(entity.triggeredActions),
        actionOutcomes = entity.actionOutcomes?.let { json.decodeFromString<Map<String, ActionOutcome>>(it) } ?: emptyMap(),
        wasDryRun = entity.wasDryRun,
        createdAt = entity.createdAt,
    )

    /**
     * Convert a list of RuleExecution domain models to entities.
     *
     * @param domains The domain models
     * @return The database entities
     */
    fun toEntityList(domains: List<RuleExecution>): List<RuleExecutionEntity> = domains.map { toEntity(it) }

    /**
     * Convert a list of RuleExecutionEntity to domain models.
     *
     * Rows that fail to decode (e.g. malformed JSON) are logged and skipped rather than
     * failing the entire list/Flow emission.
     *
     * @param entities The database entities
     * @return The domain models
     */
    fun toDomainList(entities: List<RuleExecutionEntity>): List<RuleExecution> = entities.mapNotNull { entity ->
        runCatching { toDomain(entity) }
            .onFailure { e -> Timber.e(e, "Failed to decode rule execution ${entity.id}") }
            .getOrNull()
    }
}
