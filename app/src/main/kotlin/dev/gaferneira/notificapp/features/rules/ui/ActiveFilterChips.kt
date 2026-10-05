package dev.gaferneira.notificapp.features.rules.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.DismissibleChip
import dev.gaferneira.notificapp.core.ui.components.DismissibleChipsRow
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RuleFilterChip

/**
 * Horizontally scrollable row of dismissible chips, one per active filter dimension entry
 * (status, each category, uncategorized, each app, the "rules for all apps" note). Tapping a chip
 * removes that single filter. Renders nothing when no filter is active.
 *
 * @param appNames Display names by package name; unknown packages fall back to the package name
 */
@Composable
fun ActiveFilterChips(
    filter: RuleFilter,
    appNames: Map<String, String>,
    onRemove: (RuleFilterChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = filter.activeChips().map { chip ->
        DismissibleChip(
            value = chip,
            label = chip.label(appNames),
            appPackageName = (chip as? RuleFilterChip.App)?.packageName,
        )
    }
    DismissibleChipsRow(chips = chips, onRemove = onRemove, modifier = modifier)
}

@Composable
private fun RuleFilterChip.label(appNames: Map<String, String>): String = when (this) {
    is RuleFilterChip.Status -> stringResource(
        if (status == RuleFilter.Status.ENABLED) R.string.status_enabled else R.string.status_disabled,
    )
    is RuleFilterChip.Category -> name
    RuleFilterChip.Uncategorized -> stringResource(R.string.rules_group_uncategorized)
    is RuleFilterChip.App -> appNames[packageName] ?: packageName
    RuleFilterChip.GlobalRules -> stringResource(R.string.rules_filter_chip_all_apps)
}

private val previewFilter = RuleFilter(
    status = RuleFilter.Status.ENABLED,
    selectedCategories = setOf("Finance", "Deliveries"),
    includeUncategorized = true,
    selectedApps = setOf("com.ica", "com.klarna"),
    includeGlobalRules = true,
)
private val previewNames = mapOf("com.ica" to "ICA", "com.klarna" to "Klarna")

@Preview(showBackground = true)
@Composable
private fun ActiveFilterChipsPreview() {
    NotificappTheme(dynamicColor = false) {
        ActiveFilterChips(filter = previewFilter, appNames = previewNames, onRemove = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun ActiveFilterChipsDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        ActiveFilterChips(filter = previewFilter, appNames = previewNames, onRemove = {})
    }
}
