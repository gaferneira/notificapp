package dev.gaferneira.notificapp.core.rulesharing.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of the `RuleAction` domain model. [type] is deliberately a raw string, not
 * the domain `ActionType` enum: an unrecognized action type (e.g. a rule exported from a newer
 * app version with an action this version doesn't have) must not fail the whole import -
 * `RuleWireMapper` drops just that action and reports it as skipped.
 */
@Serializable
data class ActionDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("isEnabled") val isEnabled: Boolean = true,
    @SerialName("config") val config: Map<String, String> = emptyMap(),
)
