package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionWithValues
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.RuleExecution
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Mapper functions for converting between RuleExecution domain models and RuleExecutionEntity database models.
 *
 * Decoding policy: an unknown [ActionOutcome] name inside `action_outcomes` drops only that entry
 * (logged); malformed JSON yields an empty map. A bad outcome never discards the whole execution.
 */
internal object RuleExecutionMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Decode a stored `action_outcomes` column; unknown outcome names are skipped, malformed JSON yields an empty map. */
    fun decodeActionOutcomes(raw: String): Map<String, ActionOutcome> = runCatching {
        json.decodeFromString<Map<String, String>>(raw).mapNotNull { (actionId, name) ->
            val outcome = ActionOutcome.entries.firstOrNull { it.name == name }
            if (outcome == null) Timber.e("Skipping unknown action outcome '$name' for action $actionId")
            outcome?.let { actionId to it }
        }.toMap()
    }.getOrElse { e ->
        Timber.e(e, "Failed to decode action outcomes")
        emptyMap()
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
        ruleName = domain.ruleName,
        triggeredActions = json.encodeToString(domain.triggeredActions),
        actionOutcomes = domain.actionOutcomes.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
        wasDryRun = domain.wasDryRun,
        createdAt = domain.createdAt,
    )

    /**
     * Convert a RuleExecutionEntity to a RuleExecution domain model.
     *
     * @param entity The database entity
     * @param values The execution's extracted field values (empty when the caller does not need them)
     * @return The domain model
     */
    fun toDomain(
        entity: RuleExecutionEntity,
        values: List<ExtractedFieldValueEntity> = emptyList(),
    ): RuleExecution = RuleExecution(
        id = entity.id,
        notificationId = entity.notificationId,
        ruleId = entity.ruleId,
        ruleName = entity.ruleName,
        extractedValues = ExtractedFieldValueMapper.toDomainList(values),
        triggeredActions = json.decodeFromString(entity.triggeredActions),
        actionOutcomes = entity.actionOutcomes?.let(::decodeActionOutcomes) ?: emptyMap(),
        wasDryRun = entity.wasDryRun,
        createdAt = entity.createdAt,
    )

    /**
     * Convert executions loaded with their values (see `RuleExecutionDao.observeExecutionsForNotification`)
     * to domain models, skipping rows that fail to decode like [toDomainList].
     */
    fun withValuesToDomainList(rows: List<RuleExecutionWithValues>): List<RuleExecution> = rows.mapNotNull { row ->
        runCatching { toDomain(row.execution, row.values) }
            .onFailure { e -> Timber.e(e, "Failed to decode rule execution ${row.execution.id}") }
            .getOrNull()
    }

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
