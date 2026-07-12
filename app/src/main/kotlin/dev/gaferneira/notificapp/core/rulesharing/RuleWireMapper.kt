package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.core.rulesharing.dto.ActionDto
import dev.gaferneira.notificapp.core.rulesharing.dto.AppInfoDto
import dev.gaferneira.notificapp.core.rulesharing.dto.ConditionDto
import dev.gaferneira.notificapp.core.rulesharing.dto.ExtractionMethodDto
import dev.gaferneira.notificapp.core.rulesharing.dto.FieldDto
import dev.gaferneira.notificapp.core.rulesharing.dto.RULE_EXPORT_SCHEMA_VERSION
import dev.gaferneira.notificapp.core.rulesharing.dto.RuleDto
import dev.gaferneira.notificapp.core.rulesharing.dto.RuleExportDto
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

private val wireEnumJson = Json { ignoreUnknownKeys = true }

private fun <T> T.toWireString(serializer: KSerializer<T>): String = wireEnumJson.encodeToJsonElement(serializer, this).jsonPrimitive.content

private fun <T> String.fromWireStringOrNull(serializer: KSerializer<T>): T? = try {
    wireEnumJson.decodeFromJsonElement(serializer, JsonPrimitive(this))
} catch (e: SerializationException) {
    Timber.d(e, "Unrecognized wire value: \"$this\"")
    null
}

private fun <T> String.fromWireStringStrict(serializer: KSerializer<T>, label: String): T = fromWireStringOrNull(serializer)
    ?: throw IllegalArgumentException("Unknown $label: \"$this\". This rule may require a newer version of Notificapp.")

/**
 * Maps a domain [Rule] to its wire representation, ready to be encoded as JSON.
 */
fun Rule.toDto(): RuleExportDto = RuleExportDto(
    schemaVersion = RULE_EXPORT_SCHEMA_VERSION,
    rule = RuleDto(
        id = id,
        name = name,
        description = description,
        category = category,
        isActive = isActive,
        isDryRun = isDryRun,
        targetApps = targetApps?.map { it.toDto() },
        conditions = conditions.map { it.toDto() },
        actions = actions.map { it.toDto() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    ),
)

/**
 * Maps a decoded wire envelope back to a domain [Rule], dropping any actions this app version
 * doesn't recognize. Throws [IllegalArgumentException] if a condition or extraction method is
 * unrecognized - unlike actions, those can't be meaningfully dropped without changing what the
 * rule does, so the whole import fails instead.
 */
fun RuleExportDto.toDomain(): RuleImportResult {
    val mappedActions = rule.actions.map { it to it.toDomainOrNull() }
    val skippedActions = mappedActions.filter { (_, action) -> action == null }.map { (dto, _) -> dto.type }

    val domainRule = Rule(
        id = rule.id,
        name = rule.name,
        description = rule.description,
        category = rule.category,
        isActive = rule.isActive,
        isDryRun = rule.isDryRun,
        targetApps = rule.targetApps?.map { it.toDomain() }?.toImmutableList(),
        conditions = rule.conditions.map { it.toDomain() }.toImmutableList(),
        actions = mappedActions.mapNotNull { (_, action) -> action }.toImmutableList(),
        createdAt = rule.createdAt,
        updatedAt = rule.updatedAt,
    )
    return RuleImportResult(rule = domainRule, skippedActions = skippedActions)
}

private fun AppInfo.toDto(): AppInfoDto = AppInfoDto(packageName = packageName, name = name, category = category)

private fun AppInfoDto.toDomain(): AppInfo = AppInfo(packageName = packageName, name = name, category = category)

private fun RuleCondition.toDto(): ConditionDto = ConditionDto(
    id = id,
    condition = condition?.toWireString(MatchingCondition.serializer()),
    operator = operator?.toWireString(MatchingOperator.serializer()),
    value = value,
)

private fun ConditionDto.toDomain(): RuleCondition = RuleCondition(
    id = id,
    condition = condition?.fromWireStringStrict(MatchingCondition.serializer(), "condition type"),
    operator = operator?.fromWireStringStrict(MatchingOperator.serializer(), "operator"),
    value = value,
)

private fun RuleField.toDto(): FieldDto = FieldDto(
    id = id,
    name = name,
    fieldType = fieldType.toWireString(RuleField.FieldType.serializer()),
    isRequired = isRequired,
    method = method.toDto(),
)

private fun FieldDto.toDomain(): RuleField = RuleField(
    id = id,
    name = name,
    fieldType = fieldType.fromWireStringStrict(RuleField.FieldType.serializer(), "field type"),
    method = method.toDomain(),
    isRequired = isRequired,
)

private fun ExtractionMethod.toDto(): ExtractionMethodDto = when (this) {
    is ExtractionMethod.FixedPosition -> ExtractionMethodDto.FixedPosition(startIndex, endIndex)
    is ExtractionMethod.TextBetweenAnchors -> ExtractionMethodDto.TextBetweenAnchors(startAnchor, endAnchor)
    is ExtractionMethod.RegexPattern -> ExtractionMethodDto.RegexPattern(pattern, captureGroup)
    is ExtractionMethod.TextAfterKeyword -> ExtractionMethodDto.TextAfterKeyword(keyword, maxLength)
    is ExtractionMethod.TextBeforeKeyword -> ExtractionMethodDto.TextBeforeKeyword(keyword)
    is ExtractionMethod.LineExtraction -> ExtractionMethodDto.LineExtraction(lineNumber)
    is ExtractionMethod.SplitByDelimiter -> ExtractionMethodDto.SplitByDelimiter(delimiter, takeIndex)
    is ExtractionMethod.JsonPath -> ExtractionMethodDto.JsonPath(path)
    ExtractionMethod.SmartAmountDetection -> ExtractionMethodDto.SmartAmountDetection
    ExtractionMethod.SmartDateDetection -> ExtractionMethodDto.SmartDateDetection
}

private fun ExtractionMethodDto.toDomain(): ExtractionMethod = when (this) {
    is ExtractionMethodDto.FixedPosition -> ExtractionMethod.FixedPosition(startIndex, endIndex)
    is ExtractionMethodDto.TextBetweenAnchors -> ExtractionMethod.TextBetweenAnchors(startAnchor, endAnchor)
    is ExtractionMethodDto.RegexPattern -> ExtractionMethod.RegexPattern(pattern, captureGroup)
    is ExtractionMethodDto.TextAfterKeyword -> ExtractionMethod.TextAfterKeyword(keyword, maxLength)
    is ExtractionMethodDto.TextBeforeKeyword -> ExtractionMethod.TextBeforeKeyword(keyword)
    is ExtractionMethodDto.LineExtraction -> ExtractionMethod.LineExtraction(lineNumber)
    is ExtractionMethodDto.SplitByDelimiter -> ExtractionMethod.SplitByDelimiter(delimiter, takeIndex)
    is ExtractionMethodDto.JsonPath -> ExtractionMethod.JsonPath(path)
    ExtractionMethodDto.SmartAmountDetection -> ExtractionMethod.SmartAmountDetection
    ExtractionMethodDto.SmartDateDetection -> ExtractionMethod.SmartDateDetection
}

private fun RuleAction.toDto(): ActionDto = ActionDto(
    id = id,
    type = type.toWireString(ActionType.serializer()),
    isEnabled = isEnabled,
    config = config,
    fields = fields.map { it.toDto() },
)

private fun ActionDto.toDomainOrNull(): RuleAction? {
    val actionType = type.fromWireStringOrNull(ActionType.serializer()) ?: return null
    return RuleAction(id = id, type = actionType, isEnabled = isEnabled, config = config, fields = fields.map { it.toDomain() }.toImmutableList())
}
