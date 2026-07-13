package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Verifies every [RuleCondition] subtype round-trips through the JSON polymorphic `payload`
 * column without data loss - maps to rule-storage spec's "round-trips through storage" scenarios.
 */
class RuleConditionMapperTest {

    private val ruleId = "rule-1"

    @Test
    fun `content condition round-trips through storage`() {
        // Given: a rule with a ContentMatchCondition
        val condition = RuleCondition.ContentMatchCondition(
            id = "c1",
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Total",
        )

        // When: mapping to entity and back
        val entity = RuleConditionMapper.toEntity(condition, ruleId)
        val loaded = RuleConditionMapper.toDomain(entity)

        // Then: the loaded condition has the same field, operator, and value as before saving
        loaded shouldBe condition
        entity.ruleId shouldBe ruleId
    }

    @Test
    fun `day-of-week condition round-trips through storage`() {
        // Given: a rule with a DayOfWeekCondition
        val condition = RuleCondition.DayOfWeekCondition(id = "c2", days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))

        // When: mapping to entity and back
        val entity = RuleConditionMapper.toEntity(condition, ruleId)
        val loaded = RuleConditionMapper.toDomain(entity)

        // Then: the loaded condition has the same days set as before saving
        loaded shouldBe condition
    }

    @Test
    fun `time-range condition round-trips through storage`() {
        // Given: a rule with a TimeRangeCondition
        val condition = RuleCondition.TimeRangeCondition(id = "c3", start = LocalTime.of(22, 0), end = LocalTime.of(6, 0))

        // When: mapping to entity and back
        val entity = RuleConditionMapper.toEntity(condition, ruleId)
        val loaded = RuleConditionMapper.toDomain(entity)

        // Then: the loaded condition has the same start and end values as before saving
        loaded shouldBe condition
    }

    @Test
    fun `mixed-family condition list round-trips in order`() {
        // Given: a rule with a DayOfWeekCondition, a TimeRangeCondition, and a ContentMatchCondition
        val dayCondition = RuleCondition.DayOfWeekCondition(id = "c1", days = setOf(DayOfWeek.MONDAY))
        val timeCondition = RuleCondition.TimeRangeCondition(id = "c2", start = LocalTime.of(9, 0), end = LocalTime.of(17, 0))
        val contentCondition = RuleCondition.ContentMatchCondition(
            id = "c3",
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.EQUALS,
            value = "Payment received",
        )
        val conditions = listOf(dayCondition, timeCondition, contentCondition)

        // When: mapping the list to entities and back
        val entities = RuleConditionMapper.toEntityList(conditions, ruleId)
        val loaded = RuleConditionMapper.toDomainList(entities)

        // Then: all three conditions are present after loading, with their original types, values, and order preserved
        loaded shouldBe conditions
    }
}
