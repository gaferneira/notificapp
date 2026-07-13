package dev.gaferneira.notificapp.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Domain model representing a rule trigger (matching condition).
 *
 * Sealed interface so a rule can be gated on heterogeneous condition families - notification
 * content, day-of-week, time-range, and (in the future) device state - each with its own shape.
 * Not `@Serializable`: see [Rule]. Serialization to storage/wire is delegated to the DTO layer in
 * `core/rulesharing/dto/ConditionDto.kt`.
 */
@Immutable
sealed interface RuleCondition {
    val id: String

    /**
     * Matches based on a notification-derived string value (title, content, app name, ...).
     * Carries today's pre-existing behavior, renamed from the previous flat `RuleCondition`.
     */
    @Immutable
    data class ContentMatchCondition(
        override val id: String,
        val condition: MatchingCondition,
        val operator: MatchingOperator,
        val value: String,
    ) : RuleCondition

    /**
     * Matches when the current day of week is a member of [days]. An empty [days] set matches no
     * day (fail-closed, not "match every day").
     */
    @Immutable
    data class DayOfWeekCondition(
        override val id: String,
        val days: Set<DayOfWeek>,
    ) : RuleCondition

    /**
     * Matches when the current time falls within `[start, end]` inclusive. When `start` is after
     * `end`, the range wraps across midnight. When `start == end`, matches only that exact instant.
     */
    @Immutable
    data class TimeRangeCondition(
        override val id: String,
        val start: LocalTime,
        val end: LocalTime,
    ) : RuleCondition
}

/**
 * Returns a copy of this condition with a new [id], preserving every other field. A sealed
 * interface has no generated `copy()` of its own (only its data class members do), so callers
 * that need to re-id a condition without caring which subtype it is (e.g. import identity
 * regeneration) use this instead of a `when` at every call site.
 */
fun RuleCondition.withId(id: String): RuleCondition = when (this) {
    is RuleCondition.ContentMatchCondition -> copy(id = id)
    is RuleCondition.DayOfWeekCondition -> copy(id = id)
    is RuleCondition.TimeRangeCondition -> copy(id = id)
}

/**
 * What notification property to match against.
 */
@Serializable
enum class MatchingCondition {
    @SerialName("text_content")
    TEXT_CONTENT,

    @SerialName("title")
    TITLE,

    @SerialName("app_name")
    APP_NAME,

    @SerialName("package_name")
    PACKAGE_NAME,

    @SerialName("raw_content")
    RAW_CONTENT,
}

/**
 * How to perform the match.
 */
@Serializable
enum class MatchingOperator {
    @SerialName("contains")
    CONTAINS,

    @SerialName("starts_with")
    STARTS_WITH,

    @SerialName("ends_with")
    ENDS_WITH,

    @SerialName("equals")
    EQUALS,

    @SerialName("regex_match")
    REGEX_MATCH,

    @SerialName("not_contains")
    NOT_CONTAINS,
}
