package dev.gaferneira.notificapp.features.inbox.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.DismissibleChip
import dev.gaferneira.notificapp.core.ui.components.DismissibleChipsRow
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilter
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterChip

/**
 * Dismissible chips for the active Inbox filters (status, each app) shown under the search field.
 * Tapping a chip removes that single filter. Renders nothing when no filter is active.
 *
 * @param appNames Display names by package name; unknown packages fall back to the package name
 */
@Composable
fun InboxActiveFilterChips(
    filter: InboxFilter,
    appNames: Map<String, String>,
    onRemove: (InboxFilterChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = filter.activeChips().map { chip ->
        DismissibleChip(
            value = chip,
            label = chip.label(appNames),
            appPackageName = (chip as? InboxFilterChip.App)?.packageName,
        )
    }
    DismissibleChipsRow(chips = chips, onRemove = onRemove, modifier = modifier)
}

@Composable
private fun InboxFilterChip.label(appNames: Map<String, String>): String = when (this) {
    is InboxFilterChip.Status -> stringResource(
        if (status == NotificationStatusFilter.PROCESSED) R.string.status_processed else R.string.status_unprocessed,
    )
    is InboxFilterChip.App -> appNames[packageName] ?: packageName
}

private val previewFilter = InboxFilter(
    selectedApps = setOf("com.ica", "com.klarna"),
    status = NotificationStatusFilter.UNPROCESSED,
)
private val previewNames = mapOf("com.ica" to "ICA", "com.klarna" to "Klarna")

@Preview(showBackground = true)
@Composable
private fun InboxActiveFilterChipsPreview() {
    NotificappTheme(dynamicColor = false) {
        InboxActiveFilterChips(filter = previewFilter, appNames = previewNames, onRemove = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun InboxActiveFilterChipsDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        InboxActiveFilterChips(filter = previewFilter, appNames = previewNames, onRemove = {})
    }
}
