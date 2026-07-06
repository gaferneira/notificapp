package dev.gaferneira.notificapp.core.rulesharing.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of the `RuleCondition` domain model. [condition] and [operator] cross the
 * wire as raw strings rather than the domain enums, so `RuleWireMapper` controls exactly how an
 * unrecognized value is handled (currently: fail the import - a rule that can't evaluate its
 * conditions is meaningless) instead of kotlinx's default enum-decoding behavior.
 */
@Serializable
data class ConditionDto(
    @SerialName("id") val id: String,
    @SerialName("condition") val condition: String? = null,
    @SerialName("operator") val operator: String? = null,
    @SerialName("value") val value: String? = null,
)
