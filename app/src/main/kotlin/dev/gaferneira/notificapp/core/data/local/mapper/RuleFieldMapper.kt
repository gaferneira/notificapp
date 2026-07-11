package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.domain.model.RuleField
import kotlinx.serialization.json.Json

/**
 * Mapper functions for converting between RuleField domain models and RuleFieldEntity database models.
 */
internal object RuleFieldMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "classType"
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
        methodType = domain.method.type,
        methodConfig = json.encodeToString(RuleField.ExtractionMethod.serializer(), domain.method),
        isRequired = domain.isRequired,
    )

    /**
     * Convert a RuleFieldEntity to a RuleField domain model.
     *
     * @param entity The database entity
     * @return The domain model
     */
    fun toDomain(entity: RuleFieldEntity): RuleField = RuleField(
        id = entity.id,
        name = entity.name,
        fieldType = RuleField.FieldType.valueOf(entity.fieldType),
        method = json.decodeFromString(RuleField.ExtractionMethod.serializer(), entity.methodConfig),
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
     * @param entities The database entities
     * @return The domain models
     */
    fun toDomainList(entities: List<RuleFieldEntity>): List<RuleField> = entities.map { toDomain(it) }
}
