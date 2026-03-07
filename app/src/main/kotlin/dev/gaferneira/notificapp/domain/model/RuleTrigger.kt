package dev.gaferneira.notificapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain model representing a rule trigger (matching condition).
 *
 * Defines when a rule should be applied based on notification properties.
 */
@Serializable
data class RuleTrigger(
    val id: String,
    val type: TriggerType,
    /** For CONDITION type: what to match against */
    val condition: MatchingCondition? = null,
    /** For CONDITION type: how to match */
    val operator: MatchingOperator? = null,
    /** For CONDITION type: the value to match */
    val value: String? = null,
    /** For APP type: selected app package names */
    val targetApps: List<AppInfo> = emptyList(),
)

/**
 * Type of trigger.
 */
@Serializable
enum class TriggerType {
    @SerialName("condition")
    CONDITION,

    @SerialName("app")
    APP,
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
