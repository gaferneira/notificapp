package dev.gaferneira.notificapp.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import java.time.format.TextStyle

/**
 * Localized, human-readable labels for rule conditions. Shared by the rule editor and the rule
 * details screen; resolved at the UI layer so the domain layer stays free of Android resources.
 */
@Composable
internal fun MatchingCondition.displayName(): String = stringResource(
    when (this) {
        MatchingCondition.TEXT_CONTENT -> R.string.condition_field_text
        MatchingCondition.TITLE -> R.string.condition_field_title
        MatchingCondition.APP_NAME -> R.string.condition_field_app_name
        MatchingCondition.PACKAGE_NAME -> R.string.condition_field_package_name
        MatchingCondition.RAW_CONTENT -> R.string.condition_field_raw_content
    },
)

@Composable
internal fun MatchingOperator.displayName(): String = stringResource(
    when (this) {
        MatchingOperator.CONTAINS -> R.string.condition_operator_contains
        MatchingOperator.STARTS_WITH -> R.string.condition_operator_starts_with
        MatchingOperator.ENDS_WITH -> R.string.condition_operator_ends_with
        MatchingOperator.EQUALS -> R.string.condition_operator_equals
        MatchingOperator.REGEX_MATCH -> R.string.condition_operator_regex_match
        MatchingOperator.NOT_CONTAINS -> R.string.condition_operator_not_contains
    },
)

@Composable
internal fun ConditionCombinator.displayName(): String = stringResource(
    when (this) {
        ConditionCombinator.ALL -> R.string.condition_combinator_all
        ConditionCombinator.ANY -> R.string.condition_combinator_any
    },
)

/** One-line summary of a condition, in the app's current locale (day names follow it too). */
@Composable
internal fun RuleCondition.displayText(): String = when (this) {
    is RuleCondition.ContentMatchCondition -> stringResource(
        R.string.condition_content_match,
        condition.displayName(),
        operator.displayName(),
        value,
    )
    is RuleCondition.DayOfWeekCondition -> if (days.isEmpty()) {
        stringResource(R.string.condition_days_none)
    } else {
        val locale = LocalConfiguration.current.locales[0]
        days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.FULL_STANDALONE, locale) }
    }
    is RuleCondition.TimeRangeCondition -> stringResource(
        R.string.condition_time_range,
        start.hour,
        start.minute,
        end.hour,
        end.minute,
    )
    is RuleCondition.Group -> pluralStringResource(
        R.plurals.condition_group_summary,
        children.size,
        children.size,
        combinator.displayName(),
    )
}
