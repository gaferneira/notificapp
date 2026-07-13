package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.rulesharing.dto.ConditionDto
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Mapper functions for converting between [RuleCondition] domain models and [RuleConditionEntity]
 * database models, via the polymorphic [ConditionDto] payload (ADR 011's 2026-07-12 amendment).
 * The same [ConditionDto] hierarchy is reused by `RuleWireMapper` for the wire format - see that
 * class's documentation for the accepted storage/wire coupling this introduces.
 */
internal object RuleConditionMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Convert a [RuleCondition] domain model to a [RuleConditionEntity].
     *
     * @param domain The domain model
     * @param ruleId The parent rule ID for the foreign key
     * @return The database entity
     */
    fun toEntity(domain: RuleCondition, ruleId: String): RuleConditionEntity = RuleConditionEntity(
        id = domain.id,
        ruleId = ruleId,
        payload = json.encodeToString(ConditionDto.serializer(), domain.toDto()),
    )

    /**
     * Convert a [RuleConditionEntity] to a [RuleCondition] domain model.
     *
     * @param entity The database entity
     * @return The domain model
     */
    fun toDomain(entity: RuleConditionEntity): RuleCondition = json.decodeFromString(ConditionDto.serializer(), entity.payload).toDomain()

    /**
     * Convert a list of [RuleCondition] domain models to entities.
     *
     * @param domains The domain models
     * @param ruleId The parent rule ID for all conditions
     * @return The database entities
     */
    fun toEntityList(domains: List<RuleCondition>, ruleId: String): List<RuleConditionEntity> = domains.map { toEntity(it, ruleId) }

    /**
     * Convert a list of [RuleConditionEntity] to domain models.
     *
     * @param entities The database entities
     * @return The domain models
     */
    fun toDomainList(entities: List<RuleConditionEntity>): List<RuleCondition> = entities.map { toDomain(it) }

    private fun RuleCondition.toDto(): ConditionDto = when (this) {
        is RuleCondition.ContentMatchCondition -> ConditionDto.ContentMatch(
            id = id,
            condition = condition.name,
            operator = operator.name,
            value = value,
        )
        is RuleCondition.DayOfWeekCondition -> ConditionDto.DayOfWeek(
            id = id,
            days = days.map { it.name },
        )
        is RuleCondition.TimeRangeCondition -> ConditionDto.TimeRange(
            id = id,
            start = start.toString(),
            end = end.toString(),
        )
    }

    private fun ConditionDto.toDomain(): RuleCondition = when (this) {
        is ConditionDto.ContentMatch -> RuleCondition.ContentMatchCondition(
            id = id,
            condition = MatchingCondition.valueOf(condition),
            operator = MatchingOperator.valueOf(operator),
            value = value,
        )
        is ConditionDto.DayOfWeek -> RuleCondition.DayOfWeekCondition(
            id = id,
            days = days.map { DayOfWeek.valueOf(it) }.toSet(),
        )
        is ConditionDto.TimeRange -> RuleCondition.TimeRangeCondition(
            id = id,
            start = LocalTime.parse(start),
            end = LocalTime.parse(end),
        )
    }
}
