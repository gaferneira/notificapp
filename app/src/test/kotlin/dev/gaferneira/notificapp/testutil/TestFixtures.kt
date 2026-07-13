package dev.gaferneira.notificapp.testutil

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import kotlinx.collections.immutable.toImmutableList
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Test fixture builders for domain models used across pure-Kotlin unit tests.
 *
 * Every builder ships with sensible defaults so call sites only override the
 * fields relevant to the scenario under test.
 */

fun createTestNotification(
    id: String = "test-id",
    packageName: String = "com.test.app",
    appName: String = "Test App",
    title: String? = "Test Title",
    content: String? = "Test Content",
    rawContent: String = "Test Title\nTest Content",
    timestamp: Long = 0L,
    isProcessed: Boolean = false,
    appliedRulesCount: Int = 0,
    sbnKey: String? = null,
): Notification = Notification(
    id = id,
    packageName = packageName,
    appName = appName,
    title = title,
    content = content,
    rawContent = rawContent,
    timestamp = timestamp,
    isProcessed = isProcessed,
    appliedRulesCount = appliedRulesCount,
    sbnKey = sbnKey,
)

fun createTestCondition(
    id: String = "test-condition-id",
    condition: MatchingCondition = MatchingCondition.TEXT_CONTENT,
    operator: MatchingOperator = MatchingOperator.CONTAINS,
    value: String = "",
): RuleCondition.ContentMatchCondition = RuleCondition.ContentMatchCondition(
    id = id,
    condition = condition,
    operator = operator,
    value = value,
)

fun createTestDayOfWeekCondition(
    id: String = "test-day-of-week-condition-id",
    days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
): RuleCondition.DayOfWeekCondition = RuleCondition.DayOfWeekCondition(id = id, days = days)

fun createTestTimeRangeCondition(
    id: String = "test-time-range-condition-id",
    start: LocalTime = LocalTime.of(9, 0),
    end: LocalTime = LocalTime.of(17, 0),
): RuleCondition.TimeRangeCondition = RuleCondition.TimeRangeCondition(id = id, start = start, end = end)

fun createTestField(
    id: String = "test-field-id",
    name: String = "Test Field",
    fieldType: RuleField.FieldType = RuleField.FieldType.STRING,
    method: RuleField.ExtractionMethod,
    isRequired: Boolean = false,
): RuleField = RuleField(
    id = id,
    name = name,
    fieldType = fieldType,
    method = method,
    isRequired = isRequired,
)

fun createTestAction(
    id: String = "test-action-id",
    type: ActionType = ActionType.SAVE_DATA,
    isEnabled: Boolean = true,
    config: Map<String, String> = emptyMap(),
    fields: List<RuleField> = emptyList(),
): RuleAction = RuleAction(
    id = id,
    type = type,
    isEnabled = isEnabled,
    config = config,
    fields = fields.toImmutableList(),
)

@Suppress("LongParameterList")
fun createTestRule(
    id: String = "test-rule-id",
    name: String = "Test Rule",
    description: String? = null,
    category: String? = null,
    isActive: Boolean = true,
    isDryRun: Boolean = false,
    targetApps: List<AppInfo>? = null,
    conditions: List<RuleCondition> = emptyList(),
    actions: List<RuleAction> = emptyList(),
    createdAt: Long = 0L,
    updatedAt: Long = 0L,
): Rule = Rule(
    id = id,
    name = name,
    description = description,
    category = category,
    isActive = isActive,
    isDryRun = isDryRun,
    targetApps = targetApps?.toImmutableList(),
    conditions = conditions.toImmutableList(),
    actions = actions.toImmutableList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
