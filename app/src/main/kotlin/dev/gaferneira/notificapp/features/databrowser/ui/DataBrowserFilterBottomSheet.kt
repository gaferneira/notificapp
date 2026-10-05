package dev.gaferneira.notificapp.features.databrowser.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.gaferneira.notificapp.core.ui.components.FilterSectionTitle
import dev.gaferneira.notificapp.core.ui.components.FilterSheetFooter
import dev.gaferneira.notificapp.core.ui.components.SelectableFilterChip
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiState
import dev.gaferneira.notificapp.features.databrowser.contract.FilterOption
import dev.gaferneira.notificapp.features.databrowser.contract.activeFilterCount
import dev.gaferneira.notificapp.features.databrowser.viewmodel.DataBrowserFilterSheetViewModel
import dev.gaferneira.notificapp.features.databrowser.viewmodel.DataDateRange
import java.time.ZoneId

private val DatePickerHeight = 480.dp

/**
 * Filter bottom sheet for the Data screen: rules (only those that extract data), apps and a date
 * range. Edits go to an unapplied draft in [DataBrowserFilterSheetViewModel]; the parent only
 * receives a filter on Apply. There is no live result count and no field-type control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBrowserFilterBottomSheet(
    currentFilter: DataBrowserFilter,
    ruleOptions: List<FilterOption>,
    appOptions: List<FilterOption>,
    onFilterApplied: (DataBrowserFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataBrowserFilterSheetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(currentFilter) { viewModel.onEvent(UiEvent.Init(currentFilter)) }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.ApplyFilter -> onFilterApplied(effect.filter)
            UiEffect.Dismiss -> onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(UiEvent.OnDismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        DataBrowserFilterSheetContent(
            uiState = uiState,
            ruleOptions = ruleOptions,
            appOptions = appOptions,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
internal fun DataBrowserFilterSheetContent(
    uiState: UiState,
    ruleOptions: List<FilterOption>,
    appOptions: List<FilterOption>,
    onEvent: (UiEvent) -> Unit,
) {
    val draft = uiState.draft
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.data_filter_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            RulesSection(draft = draft, ruleOptions = ruleOptions, onEvent = onEvent)
            AppsSection(draft = draft, appOptions = appOptions, onEvent = onEvent)
            DateRangeSection(draft = draft, onEvent = onEvent)
        }
        FilterSheetFooter(
            clearEnabled = draft.activeFilterCount() > 0,
            onClearAll = { onEvent(UiEvent.OnClearAll) },
            applyLabel = stringResource(R.string.data_filter_apply),
            onApply = { onEvent(UiEvent.OnApply) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesSection(draft: DataBrowserFilter, ruleOptions: List<FilterOption>, onEvent: (UiEvent) -> Unit) {
    val unknownRule = stringResource(R.string.data_filter_unknown_rule)
    // A selected rule that no longer extracts stays listed so the user can still remove it.
    val stale = draft.ruleIds.filter { id -> ruleOptions.none { it.id == id } }.map { FilterOption(it, unknownRule) }
    val options = ruleOptions + stale
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterSectionTitle(stringResource(R.string.data_filter_section_rules))
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.data_filter_rules_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    SelectableFilterChip(
                        selected = option.id in draft.ruleIds,
                        onClick = { onEvent(UiEvent.OnRuleToggle(option.id)) },
                        label = option.label,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsSection(draft: DataBrowserFilter, appOptions: List<FilterOption>, onEvent: (UiEvent) -> Unit) {
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }
    val selected = draft.packageNames.map { pkg ->
        AppPickerOption(pkg, appOptions.firstOrNull { it.id == pkg }?.label ?: pkg)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterSectionTitle(stringResource(R.string.data_filter_section_apps))
        AppFilterSection(
            selectedApps = selected,
            emptyText = stringResource(R.string.data_filter_apps_any),
            onRemoveApp = { onEvent(UiEvent.OnAppToggle(it)) },
            onChooseApps = { showAppPicker = true },
        )
    }

    if (showAppPicker) {
        val visible = appOptions
            .filter { appSearchQuery.isBlank() || it.label.contains(appSearchQuery.trim(), ignoreCase = true) }
            .map { AppPickerOption(it.id, it.label) }
        AppPickerSheet(
            groups = listOf(AppPickerGroup(stringResource(R.string.data_filter_apps_group), visible)),
            selectedPackages = draft.packageNames.toSet(),
            searchQuery = appSearchQuery,
            onSearchQueryChange = { appSearchQuery = it },
            onToggleApp = { onEvent(UiEvent.OnAppToggle(it)) },
            onDismiss = {
                showAppPicker = false
                appSearchQuery = ""
            },
        )
    }
}

@Composable
private fun DateRangeSection(draft: DataBrowserFilter, onEvent: (UiEvent) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val hasRange = draft.dateFrom != null || draft.dateTo != null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterSectionTitle(stringResource(R.string.data_filter_section_date))
        Text(
            text = if (hasRange) dateRangeLabel(draft.dateFrom, draft.dateTo) else stringResource(R.string.data_filter_date_any),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.data_filter_date_choose))
            }
            if (hasRange) {
                TextButton(onClick = { onEvent(UiEvent.OnDateRangeClear) }) {
                    Text(stringResource(R.string.data_filter_date_clear))
                }
            }
        }
    }

    if (showPicker) {
        DateRangePickerDialog(
            dateFrom = draft.dateFrom,
            dateTo = draft.dateTo,
            onConfirm = { from, to ->
                onEvent(UiEvent.OnDateRangeChange(from, to))
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    dateFrom: Long?,
    dateTo: Long?,
    onConfirm: (dateFrom: Long, dateTo: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val (initialStart, initialEnd) = remember { DataDateRange.toPickerMillis(dateFrom, dateTo, zone) }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart,
        initialSelectedEndDateMillis = initialEnd,
    )
    val start = state.selectedStartDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = {
                    if (start != null) {
                        val bounds = DataDateRange.toFilterBounds(start, state.selectedEndDateMillis, zone)
                        onConfirm(bounds.dateFrom, bounds.dateTo)
                    }
                },
            ) {
                Text(stringResource(R.string.rules_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rules_cancel))
            }
        },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(DatePickerHeight))
    }
}

// region Previews

private val previewRules = listOf(FilterOption("r1", "ICA Purchase"), FilterOption("r2", "Bank transfer"))
private val previewApps = listOf(FilterOption("com.ica", "ICA"), FilterOption("com.bank", "Bank"))

@Preview(showBackground = true)
@Composable
private fun DataBrowserFilterSheetPreview() {
    NotificappTheme(dynamicColor = false) {
        DataBrowserFilterSheetContent(
            uiState = UiState(
                draft = DataBrowserFilter(
                    ruleIds = listOf("r1"),
                    packageNames = listOf("com.ica"),
                    dateFrom = 1_772_323_200_000L,
                    dateTo = 1_772_582_399_999L,
                ),
            ),
            ruleOptions = previewRules,
            appOptions = previewApps,
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f, heightDp = 640)
@Composable
private fun DataBrowserFilterSheetLargeFontDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        DataBrowserFilterSheetContent(
            uiState = UiState(),
            ruleOptions = previewRules,
            appOptions = previewApps,
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "No extracting rules")
@Composable
private fun DataBrowserFilterSheetEmptyPreview() {
    NotificappTheme(dynamicColor = false) {
        DataBrowserFilterSheetContent(uiState = UiState(), ruleOptions = emptyList(), appOptions = emptyList(), onEvent = {})
    }
}

// endregion
