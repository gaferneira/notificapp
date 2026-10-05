package dev.gaferneira.notificapp.features.databrowser.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.DismissibleChip
import dev.gaferneira.notificapp.core.ui.components.DismissibleChipsRow
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.features.databrowser.contract.DataFilterChip
import dev.gaferneira.notificapp.features.databrowser.contract.FilterOption
import dev.gaferneira.notificapp.features.databrowser.contract.activeChips
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Dismissible chips for the active Data filters (each rule, each app, the date range) shown under
 * the search field. Tapping a chip removes that single value. Renders nothing when no filter is
 * active.
 *
 * Stale selections (a rule or app missing from the options) fall back to "Unknown rule" or the
 * package name and stay removable.
 */
@Composable
fun DataBrowserActiveFilterChips(
    filter: DataBrowserFilter,
    ruleOptions: List<FilterOption>,
    appOptions: List<FilterOption>,
    onRemove: (DataFilterChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownRule = stringResource(R.string.data_filter_unknown_rule)
    val dateLabel = dateRangeLabel(filter.dateFrom, filter.dateTo)
    val chips = filter.activeChips().map { chip ->
        DismissibleChip(
            value = chip,
            label = when (chip) {
                is DataFilterChip.Rule -> ruleOptions.firstOrNull { it.id == chip.id }?.label ?: unknownRule
                is DataFilterChip.App -> appOptions.firstOrNull { it.id == chip.packageName }?.label ?: chip.packageName
                DataFilterChip.DateRange -> dateLabel
            },
            appPackageName = (chip as? DataFilterChip.App)?.packageName,
        )
    }
    DismissibleChipsRow(chips = chips, onRemove = onRemove, modifier = modifier)
}

/** Localized "start – end" label of the date range, or a single date when both bounds fall on one day. */
@Composable
internal fun dateRangeLabel(dateFrom: Long?, dateTo: Long?): String {
    val zone = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    fun Long.format(): String = formatter.format(Instant.ofEpochMilli(this).atZone(zone).toLocalDate())
    val from = (dateFrom ?: dateTo)?.format()
    val to = (dateTo ?: dateFrom)?.format()
    return when {
        from == null || to == null -> ""
        from == to -> from
        else -> stringResource(R.string.data_filter_date_range_chip, from, to)
    }
}

private val previewFilter = DataBrowserFilter(
    ruleIds = listOf("r1", "gone"),
    packageNames = listOf("com.ica"),
    dateFrom = 1_772_323_200_000L,
    dateTo = 1_772_582_399_999L,
)
private val previewRules = listOf(FilterOption("r1", "ICA Purchase"))
private val previewApps = listOf(FilterOption("com.ica", "ICA"))

@Preview(showBackground = true)
@Composable
private fun DataBrowserActiveFilterChipsPreview() {
    NotificappTheme(dynamicColor = false) {
        DataBrowserActiveFilterChips(previewFilter, previewRules, previewApps, onRemove = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun DataBrowserActiveFilterChipsDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        DataBrowserActiveFilterChips(previewFilter, previewRules, previewApps, onRemove = {})
    }
}
