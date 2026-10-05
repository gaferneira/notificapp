@file:Suppress("UnusedPrivateMember") // previews use a multi-preview annotation detekt cannot see through

package dev.gaferneira.notificapp.features.ruledetails.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleStats
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/** Light, dark and 2x font scale, to check summary wrapping and collapsible headers in each. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Font 2x", showBackground = true, fontScale = 2f)
private annotation class RuleDetailsThemes

private fun content(rule: Rule, stats: RuleStats? = null) = UiState(rule = rule, isLoading = false, stats = stats)

private fun condition(id: String, value: String) = RuleCondition.ContentMatchCondition(
    id = id,
    condition = MatchingCondition.TEXT_CONTENT,
    operator = MatchingOperator.CONTAINS,
    value = value,
)

private fun shortRule() = Rule(
    id = "short",
    name = "Dismiss promotions",
    description = null,
    targetApps = persistentListOf(AppInfo("com.shop", "Shop")),
    conditions = persistentListOf(condition("c1", "sale")),
    actions = persistentListOf(RuleAction.createFlashAlert(id = "a1")),
)

private fun longRule() = shortRule().copy(
    id = "long",
    name = "Long rule",
    conditionLogic = ConditionCombinator.ANY,
    conditions = (1..5).map { condition("c$it", "keyword $it") }.toPersistentList(),
    actions = (1..4).map { RuleAction.createFlashAlert(id = "a$it") }.toPersistentList(),
)

@Composable
private fun PreviewHost(rule: Rule, stats: RuleStats? = null) {
    NotificappTheme(dynamicColor = false) {
        RuleDetailsScreenContent(uiState = content(rule, stats), onEvent = {})
    }
}

@RuleDetailsThemes
@Composable
private fun ShortRulePreview() = PreviewHost(shortRule())

@RuleDetailsThemes
@Composable
private fun LongRuleCollapsedPreview() = PreviewHost(longRule())

@RuleDetailsThemes
@Composable
private fun DryRunRulePreview() = PreviewHost(
    rule = shortRule().copy(isDryRun = true),
    stats = RuleStats(totalMatches = 3, matchesLast7Days = 3, matchesLast30Days = 3, testModeMatches = 3, lastTriggeredAt = 0L),
)

@RuleDetailsThemes
@Composable
private fun DisabledActionPreview() = PreviewHost(
    shortRule().copy(
        actions = persistentListOf(
            RuleAction.createFlashAlert(id = "a1"),
            RuleAction.createFlashAlert(id = "a2", isEnabled = false),
        ),
    ),
)

@RuleDetailsThemes
@Composable
private fun ExcludeModePreview() = PreviewHost(
    shortRule().copy(
        isIncludeMode = false,
        targetApps = persistentListOf(AppInfo("com.social", "Social"), AppInfo("com.game", "Game")),
    ),
)

@RuleDetailsThemes
@Composable
private fun ManyAppsPreview() = PreviewHost(
    shortRule().copy(
        targetApps = (1..9).map { AppInfo("com.app$it", "App number $it") }.toPersistentList(),
    ),
)
