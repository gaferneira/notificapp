package dev.gaferneira.notificapp.core.rulesharing.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire *and* storage representation of the `RuleCondition` domain model.
 * `@SerialName`-discriminated sealed hierarchy, mirroring [ExtractionMethodDto]. [ContentMatch]'s
 * [ContentMatch.condition]/[ContentMatch.operator] cross the wire as raw strings rather than the
 * domain enums, so `RuleWireMapper` controls exactly how an unrecognized value is handled
 * (currently: fail the import - a rule that can't evaluate its conditions is meaningless) instead
 * of kotlinx's default enum-decoding behavior.
 */
@Serializable
sealed class ConditionDto {
    abstract val id: String

    @Serializable
    @SerialName("content_match")
    data class ContentMatch(
        @SerialName("id") override val id: String,
        @SerialName("condition") val condition: String,
        @SerialName("operator") val operator: String,
        @SerialName("value") val value: String,
    ) : ConditionDto()

    @Serializable
    @SerialName("day_of_week")
    data class DayOfWeek(
        @SerialName("id") override val id: String,
        /** ISO day names, e.g. `MONDAY`..`SUNDAY`. */
        @SerialName("days") val days: List<String>,
    ) : ConditionDto()

    @Serializable
    @SerialName("time_range")
    data class TimeRange(
        @SerialName("id") override val id: String,
        /** `HH:mm` formatted local time. */
        @SerialName("start") val start: String,
        /** `HH:mm` formatted local time. */
        @SerialName("end") val end: String,
    ) : ConditionDto()
}
