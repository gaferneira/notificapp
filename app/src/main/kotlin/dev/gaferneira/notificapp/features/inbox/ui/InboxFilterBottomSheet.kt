package dev.gaferneira.notificapp.features.inbox.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.AppFilterSection
import dev.gaferneira.notificapp.core.ui.components.AppPickerGroup
import dev.gaferneira.notificapp.core.ui.components.AppPickerOption
import dev.gaferneira.notificapp.core.ui.components.AppPickerSheet
import dev.gaferneira.notificapp.core.ui.components.ChoiceOption
import dev.gaferneira.notificapp.core.ui.components.FilterSectionTitle
import dev.gaferneira.notificapp.core.ui.components.FilterSheetFooter
import dev.gaferneira.notificapp.core.ui.components.SegmentedChoiceGroup
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilter
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract.AppOption
import dev.gaferneira.notificapp.features.inbox.viewmodel.InboxFilterBottomSheetViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

/**
 * Filter bottom sheet for the Inbox.
 *
 * The body scrolls and the Clear all / Show N notifications footer is pinned, so both stay
 * reachable at any font scale. Edits go to an unapplied draft in
 * [InboxFilterBottomSheetViewModel]; the parent only receives a filter on Apply.
 *
 * @param currentFilter Currently applied filter (hydrates the draft once per opening)
 * @param onFilterApplied Called when the user applies the draft
 * @param onDismiss Called when the sheet should be dismissed
 * @param searchQuery Inbox search text, so the live count matches what the list will show
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxFilterBottomSheet(
    currentFilter: InboxFilter,
    onFilterApplied: (InboxFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    viewModel: InboxFilterBottomSheetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The ViewModel only hydrates the draft on the first Init per opening, so unapplied edits
    // survive a re-sent Init.
    LaunchedEffect(currentFilter, searchQuery) {
        viewModel.onEvent(InboxFilterContract.UiEvent.Init(currentFilter, searchQuery))
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is InboxFilterContract.UiEffect.ApplyFilter -> onFilterApplied(effect.filter)
            is InboxFilterContract.UiEffect.Dismiss -> onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(InboxFilterContract.UiEvent.OnDismiss) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        InboxFilterSheetContent(uiState = uiState, onEvent = viewModel::onEvent)
    }
}

@Composable
internal fun InboxFilterSheetContent(
    uiState: InboxFilterContract.UiState,
    onEvent: (InboxFilterContract.UiEvent) -> Unit,
) {
    var showAppPicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.filter_inbox_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            StatusSection(status = uiState.draft.status, onEvent = onEvent)
            if (uiState.hasAppOptions) {
                AppsSection(uiState = uiState, onEvent = onEvent, onChooseApps = { showAppPicker = true })
            } else {
                Text(
                    text = stringResource(R.string.inbox_filter_no_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        InboxFilterFooter(uiState = uiState, onEvent = onEvent)
    }

    if (showAppPicker) {
        AppPickerSheet(
            groups = uiState.toPickerGroups(),
            selectedPackages = uiState.draft.selectedApps,
            searchQuery = uiState.appSearchQuery,
            onSearchQueryChange = { onEvent(InboxFilterContract.UiEvent.OnAppSearchChange(it)) },
            onToggleApp = { onEvent(InboxFilterContract.UiEvent.OnAppToggle(it)) },
            onDismiss = {
                showAppPicker = false
                onEvent(InboxFilterContract.UiEvent.OnAppSearchChange(""))
            },
        )
    }
}

@Composable
private fun StatusSection(
    status: Status,
    onEvent: (InboxFilterContract.UiEvent) -> Unit,
) {
    val options = listOf(
        ChoiceOption(Status.ALL, stringResource(R.string.status_all)),
        ChoiceOption(Status.PROCESSED, stringResource(R.string.status_processed)),
        ChoiceOption(Status.UNPROCESSED, stringResource(R.string.status_unprocessed)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterSectionTitle(stringResource(R.string.inbox_filter_section_status))
        SegmentedChoiceGroup(
            options = options,
            selected = status,
            onSelect = { onEvent(InboxFilterContract.UiEvent.OnStatusChange(it)) },
        )
    }
}

@Composable
private fun AppsSection(
    uiState: InboxFilterContract.UiState,
    onEvent: (InboxFilterContract.UiEvent) -> Unit,
    onChooseApps: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterSectionTitle(stringResource(R.string.inbox_filter_section_apps))
        AppFilterSection(
            selectedApps = uiState.selectedApps.map { AppPickerOption(it.packageName, it.name) },
            emptyText = stringResource(R.string.inbox_filter_apps_any),
            onRemoveApp = { onEvent(InboxFilterContract.UiEvent.OnAppToggle(it)) },
            onChooseApps = onChooseApps,
        )
    }
}

@Composable
private fun InboxFilterFooter(
    uiState: InboxFilterContract.UiState,
    onEvent: (InboxFilterContract.UiEvent) -> Unit,
) {
    FilterSheetFooter(
        clearEnabled = uiState.hasActiveFilters,
        onClearAll = { onEvent(InboxFilterContract.UiEvent.OnClearAll) },
        applyLabel = pluralStringResource(R.plurals.inbox_filter_show_notifications, uiState.matchCount, uiState.matchCount),
        onApply = { onEvent(InboxFilterContract.UiEvent.OnApply) },
    )
}

@Composable
private fun InboxFilterContract.UiState.toPickerGroups(): List<AppPickerGroup> = listOf(
    AppPickerGroup(stringResource(R.string.inbox_filter_apps_group_with), withNotificationsApps.map { it.toPickerOption() }),
    AppPickerGroup(stringResource(R.string.inbox_filter_apps_group_without), withoutNotificationsApps.map { it.toPickerOption() }),
)

@Composable
private fun AppOption.toPickerOption() = AppPickerOption(
    packageName = packageName,
    name = name,
    supportingText = pluralStringResource(R.plurals.inbox_filter_notification_count, notificationCount, notificationCount),
)

// region Previews

private val previewApps = persistentListOf(
    AppOption("com.ica", "ICA", 42),
    AppOption("com.klarna.app", "Klarna", 7),
    AppOption("se.postnord.mobile", "PostNord", 1),
)

@Preview(showBackground = true)
@Composable
private fun InboxFilterSheetPreview() {
    NotificappTheme(dynamicColor = false) {
        InboxFilterSheetContent(
            uiState = InboxFilterContract.UiState(
                draft = InboxFilter(selectedApps = setOf("com.ica"), status = Status.UNPROCESSED),
                withNotificationsApps = previewApps,
                selectedApps = persistentListOf(previewApps.first()),
                hasAppOptions = true,
                matchCount = 12,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f, heightDp = 640)
@Composable
private fun InboxFilterSheetLargeFontDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        InboxFilterSheetContent(
            uiState = InboxFilterContract.UiState(
                withNotificationsApps = previewApps,
                hasAppOptions = true,
                matchCount = 50,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "Many apps, long names")
@Composable
private fun InboxFilterSheetManyAppsPreview() {
    val many = List(12) { AppOption("com.app$it", "A rather long application display name number $it", it) }
    NotificappTheme(dynamicColor = false) {
        InboxFilterSheetContent(
            uiState = InboxFilterContract.UiState(
                draft = InboxFilter(selectedApps = many.map { it.packageName }.toSet()),
                withNotificationsApps = many.filter { it.notificationCount > 0 }.toImmutableList(),
                selectedApps = many.toImmutableList(),
                hasAppOptions = true,
                matchCount = 66,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun InboxFilterSheetEmptyPreview() {
    NotificappTheme(dynamicColor = false) {
        InboxFilterSheetContent(uiState = InboxFilterContract.UiState(), onEvent = {})
    }
}

// endregion
