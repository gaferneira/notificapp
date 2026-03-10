package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.domain.model.RuleField
import kotlinx.serialization.json.Json

/**
 * Mapper functions for converting between RuleField domain models and RuleFieldEntity database models.
 */
object RuleFieldMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "classType"
    }

    /**
     * Convert a RuleField domain model to a RuleFieldEntity.
     *
     * @param domain The domain model
     * @param ruleId The parent rule ID for the foreign key
     * @return The database entity
     */
    fun toEntity(domain: RuleField, ruleId: String): RuleFieldEntity = RuleFieldEntity(
        id = domain.id,
        ruleId = ruleId,
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
     * @param ruleId The parent rule ID for all fields
     * @return The database entities
     */
    fun toEntityList(domains: List<RuleField>, ruleId: String): List<RuleFieldEntity> = domains.map { toEntity(it, ruleId) }

    /**
     * Convert a list of RuleFieldEntity to domain models.
     *
     * @param entities The database entities
     * @return The domain models
     */
    fun toDomainList(entities: List<RuleFieldEntity>): List<RuleField> = entities.map { toDomain(it) }
}
