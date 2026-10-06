package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.core.rulesharing.dto.ExtractionMethodDto
import dev.gaferneira.notificapp.core.rulesharing.toDomain
import dev.gaferneira.notificapp.core.rulesharing.toDto
import dev.gaferneira.notificapp.domain.model.RuleField
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Mapper functions for converting between RuleField domain models and RuleFieldEntity database models.
 *
 * The extraction method is persisted as an [ExtractionMethodDto] payload (the same hierarchy the
 * wire format uses), discriminated by its `type` property - mirroring how
 * [RuleConditionMapper] stores conditions. The domain model itself is never serialized.
 */
internal object RuleFieldMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Convert a RuleField domain model to a RuleFieldEntity.
     *
     * @param domain The domain model
     * @param actionId The owning `SAVE_DATA` action ID for the foreign key
     * @return The database entity
     */
    fun toEntity(domain: RuleField, actionId: String): RuleFieldEntity = RuleFieldEntity(
        id = domain.id,
        actionId = actionId,
        name = domain.name,
        fieldType = domain.fieldType.name,
        methodConfig = json.encodeToString(ExtractionMethodDto.serializer(), domain.method.toDto()),
        isRequired = domain.isRequired,
    )

    /**
     * Convert a RuleFieldEntity to a RuleField domain model.
     *
     * @param entity The database entity
     * @return The domain model
     * @throws kotlinx.serialization.SerializationException if the stored method payload is unknown or malformed
     */
    fun toDomain(entity: RuleFieldEntity): RuleField = RuleField(
        id = entity.id,
        name = entity.name,
        fieldType = RuleField.FieldType.valueOf(entity.fieldType),
        method = json.decodeFromString(ExtractionMethodDto.serializer(), entity.methodConfig).toDomain(),
        isRequired = entity.isRequired,
    )

    /**
     * Convert a list of RuleField domain models to entities.
     *
     * @param domains The domain models
     * @param actionId The owning `SAVE_DATA` action ID for all fields
     * @return The database entities
     */
    fun toEntityList(domains: List<RuleField>, actionId: String): List<RuleFieldEntity> = domains.map { toEntity(it, actionId) }

    /**
     * Convert a list of RuleFieldEntity to domain models.
     *
     * Rows whose method payload cannot be decoded (e.g. a method type written by a newer build) are
     * logged and skipped, so one unreadable field never prevents the rule from loading.
     *
     * @param entities The database entities
     * @return The domain models
     */
    fun toDomainList(entities: List<RuleFieldEntity>): List<RuleField> = entities.mapNotNull { entity ->
        runCatching { toDomain(entity) }
            .onFailure { e -> Timber.e(e, "Skipping rule field ${entity.id}: undecodable extraction method") }
            .getOrNull()
    }
}
