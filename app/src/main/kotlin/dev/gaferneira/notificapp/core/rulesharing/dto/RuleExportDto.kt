package dev.gaferneira.notificapp.core.rulesharing.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Current version of the rule export wire format. Bump this and handle the previous version in
 * `RuleWireMapper`/`RuleJsonCodec.decode` if the shape of [RuleDto] ever needs a breaking change.
 *
 */
const val RULE_EXPORT_SCHEMA_VERSION = 1

/**
 * Versioned envelope for sharing a rule as JSON (export/import, community rule gallery). This DTO
 * layer - not the domain model - is the canonical definition of the wire format: every field is
 * pinned with an explicit [SerialName], so renaming a Kotlin property on the domain side never
 * changes exported JSON. See `docs/rule-format.md`.
 */
@Serializable
data class RuleExportDto(
    @SerialName("schemaVersion") val schemaVersion: Int = RULE_EXPORT_SCHEMA_VERSION,
    @SerialName("rule") val rule: RuleDto,
)

/**
 * Wire representation of the `Rule` domain model.
 */
@Serializable
data class RuleDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("isDryRun") val isDryRun: Boolean = false,
    @SerialName("targetApps") val targetApps: List<AppInfoDto>? = null,
    @SerialName("isIncludeMode") val isIncludeMode: Boolean = true,
    @SerialName("conditionLogic") val conditionLogic: String = "ALL",
    @SerialName("conditions") val conditions: List<ConditionDto> = emptyList(),
    @SerialName("actions") val actions: List<ActionDto> = emptyList(),
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)
