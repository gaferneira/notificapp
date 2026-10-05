package dev.gaferneira.notificapp.features.rules.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import dev.gaferneira.notificapp.core.ui.components.SelectableFilterChip
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.AppOption
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.CategoryOption
import dev.gaferneira.notificapp.features.rules.viewmodel.FilterBottomSheetViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * "Filter & sort" bottom sheet for the Rules screen.
 *
 * The body scrolls and the Clear all / Show N rules footer is pinned, so both stay reachable at any
 * font scale. Edits go to an unapplied draft in [FilterBottomSheetViewModel]; the parent only
 * receives a filter on Apply.
 *
 * @param allRules Complete list of rules, used to derive options, counts and the live match count
 * @param currentFilter Currently applied filter (hydrates the draft once per opening)
 * @param searchQuery Rules screen search text, so the live count matches what the list will show
 * @param onFilterApplied Called when the user applies the draft
 * @param onDismiss Called when the sheet should be dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesFilterBottomSheet(
    allRules: ImmutableList<Rule>,
    currentFilter: RuleFilter,
    onFilterApplied: (RuleFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    viewModel: FilterBottomSheetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Re-sent when rules change; the ViewModel only hydrates the draft on the first Init per
    // opening, so unapplied edits survive.
    LaunchedEffect(allRules, currentFilter, searchQuery) {
        viewModel.onEvent(RulesFilterContract.UiEvent.Init(allRules, currentFilter, searchQuery))
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is RulesFilterContract.UiEffect.ApplyFilter -> onFilterApplied(effect.filter)
            is RulesFilterContract.UiEffect.Dismiss -> onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(RulesFilterContract.UiEvent.OnDismiss) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        FilterBottomSheetContent(uiState = uiState, onEvent = viewModel::onEvent)
    }
}

@Composable
internal fun FilterBottomSheetContent(
    uiState: RulesFilterContract.UiState,
    onEvent: (RulesFilterContract.UiEvent) -> Unit,
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
                text = stringResource(R.string.filter_rules_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            StatusSection(status = uiState.draft.status, onEvent = onEvent)
            if (uiState.categories.isNotEmpty() || uiState.uncategorizedCount > 0) {
                CategorySection(uiState = uiState, onEvent = onEvent)
            }
            if (uiState.hasAppOptions) {
                AppsSection(uiState = uiState, onEvent = onEvent, onChooseApps = { showAppPicker = true })
            }
            if (!uiState.hasOptions) {
                Text(
                    text = stringResource(R.string.no_filters_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SortSection(sortBy = uiState.draft.sortBy, onEvent = onEvent)
        }
        FilterFooter(uiState = uiState, onEvent = onEvent)
    }

    if (showAppPicker) {
        AppPickerSheet(
            groups = uiState.toPickerGroups(),
            selectedPackages = uiState.draft.selectedApps,
            searchQuery = uiState.appSearchQuery,
            onSearchQueryChange = { onEvent(RulesFilterContract.UiEvent.OnAppSearchChange(it)) },
            onToggleApp = { onEvent(RulesFilterContract.UiEvent.OnAppToggle(it)) },
            onDismiss = {
                showAppPicker = false
                onEvent(RulesFilterContract.UiEvent.OnAppSearchChange(""))
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun StatusSection(
    status: RuleFilter.Status,
    onEvent: (RulesFilterContract.UiEvent) -> Unit,
) {
    val options = listOf(
        RuleFilter.Status.ALL to R.string.status_all,
        RuleFilter.Status.ENABLED to R.string.status_enabled,
        RuleFilter.Status.DISABLED to R.string.status_disabled,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.rules_filter_section_status))
        // Three equal segments cannot hold large-font labels; fall back to a radio list.
        if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = status == value,
                                role = Role.RadioButton,
                                onClick = { onEvent(RulesFilterContract.UiEvent.OnStatusChange(value)) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = status == value, onClick = null)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = status == value,
                        onClick = { onEvent(RulesFilterContract.UiEvent.OnStatusChange(value)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(stringResource(label), maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySection(
    uiState: RulesFilterContract.UiState,
    onEvent: (RulesFilterContract.UiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.rules_filter_section_categories))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.categories.forEach { category ->
                SelectableFilterChip(
                    selected = category.name in uiState.draft.selectedCategories,
                    onClick = { onEvent(RulesFilterContract.UiEvent.OnCategoryToggle(category.name)) },
                    label = "${category.name} · ${category.ruleCount}",
                )
            }
            if (uiState.uncategorizedCount > 0 || uiState.draft.includeUncategorized) {
                SelectableFilterChip(
                    selected = uiState.draft.includeUncategorized,
                    onClick = { onEvent(RulesFilterContract.UiEvent.OnUncategorizedToggle) },
                    label = "${stringResource(R.string.rules_group_uncategorized)} · ${uiState.uncategorizedCount}",
                )
            }
        }
    }
}

@Composable
private fun AppsSection(
    uiState: RulesFilterContract.UiState,
    onEvent: (RulesFilterContract.UiEvent) -> Unit,
    onChooseApps: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.rules_filter_section_apps))
        AppFilterSection(
            selectedApps = uiState.selectedApps.map { AppPickerOption(it.packageName, it.name) },
            emptyText = stringResource(R.string.rules_filter_apps_any),
            onRemoveApp = { onEvent(RulesFilterContract.UiEvent.OnAppToggle(it)) },
            onChooseApps = onChooseApps,
        )
        if (uiState.draft.selectedApps.isNotEmpty()) {
            IncludeGlobalRulesRow(
                checked = uiState.draft.includeGlobalRules,
                onCheckedChange = { onEvent(RulesFilterContract.UiEvent.OnIncludeGlobalRulesChange(it)) },
            )
        }
    }
}

@Composable
private fun IncludeGlobalRulesRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.rules_filter_include_global),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.rules_filter_include_global_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Sort lives in its own compact section below the filters. Sort is not a filter: it is not counted
 * as active and "Clear all" does not touch it; pick the default option to go back to it.
 */
@Composable
private fun SortSection(
    sortBy: RuleFilter.SortBy,
    onEvent: (RulesFilterContract.UiEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sortLabel = stringResource(sortBy.labelRes())
    val title = stringResource(R.string.sort_by)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(title)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { expanded = true }
                    .semantics {
                        contentDescription = "$title, $sortLabel"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = sortLabel, style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RuleFilter.SortBy.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            onEvent(RulesFilterContract.UiEvent.OnSortChange(option))
                            expanded = false
                        },
                        leadingIcon = if (option == sortBy) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterFooter(
    uiState: RulesFilterContract.UiState,
    onEvent: (RulesFilterContract.UiEvent) -> Unit,
) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onEvent(RulesFilterContract.UiEvent.OnClearAll) },
                modifier = Modifier.weight(1f),
                enabled = uiState.hasActiveFilters,
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.clear_all))
            }
            Button(
                onClick = { onEvent(RulesFilterContract.UiEvent.OnApply) },
                modifier = Modifier.weight(1f),
            ) {
                Text(pluralStringResource(R.plurals.rules_filter_show_rules, uiState.matchCount, uiState.matchCount))
            }
        }
    }
}

@Composable
private fun RulesFilterContract.UiState.toPickerGroups(): List<AppPickerGroup> = listOf(
    AppPickerGroup(stringResource(R.string.rules_filter_apps_group_used), usedApps.map { it.toPickerOption() }),
    AppPickerGroup(stringResource(R.string.rules_filter_apps_group_other), otherApps.map { it.toPickerOption() }),
)

@Composable
private fun AppOption.toPickerOption() = AppPickerOption(
    packageName = packageName,
    name = name,
    supportingText = pluralStringResource(R.plurals.rules_filter_rule_count, ruleCount, ruleCount),
)

private fun RuleFilter.SortBy.labelRes(): Int = when (this) {
    RuleFilter.SortBy.CATEGORY_ASC -> R.string.sort_category
    RuleFilter.SortBy.NAME_ASC -> R.string.sort_name_asc
    RuleFilter.SortBy.NAME_DESC -> R.string.sort_name_desc
    RuleFilter.SortBy.CREATED_NEWEST -> R.string.sort_created_newest
    RuleFilter.SortBy.CREATED_OLDEST -> R.string.sort_created_oldest
    RuleFilter.SortBy.UPDATED_RECENT -> R.string.sort_updated_recent
    RuleFilter.SortBy.STATUS -> R.string.sort_status
}

private const val LARGE_FONT_SCALE = 1.3f

// region Previews

private val previewUsedApps = persistentListOf(
    AppOption("com.ica", "ICA", 3),
    AppOption("com.klarna.app", "Klarna", 2),
    AppOption("se.postnord.mobile", "PostNord", 1),
)

private fun previewState(
    draft: RuleFilter = RuleFilter(),
    selectedApps: List<AppOption> = emptyList(),
) = RulesFilterContract.UiState(
    draft = draft,
    categories = persistentListOf(
        CategoryOption("Deliveries", 1),
        CategoryOption("Finance", 4),
        CategoryOption("Shopping", 2),
    ),
    uncategorizedCount = 2,
    usedApps = previewUsedApps,
    otherApps = persistentListOf(AppOption("com.chat", "Chat", 0)),
    selectedApps = selectedApps.toImmutableList(),
    matchCount = 12,
)

@Preview(showBackground = true)
@Composable
private fun FilterBottomSheetPreview() {
    NotificappTheme(dynamicColor = false) {
        FilterBottomSheetContent(
            uiState = previewState(
                draft = RuleFilter(
                    status = RuleFilter.Status.ENABLED,
                    selectedCategories = setOf("Finance"),
                    selectedApps = setOf("com.ica"),
                    includeGlobalRules = true,
                ),
                selectedApps = listOf(previewUsedApps.first()),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f, heightDp = 640)
@Composable
private fun FilterBottomSheetLargeFontDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        FilterBottomSheetContent(uiState = previewState(), onEvent = {})
    }
}

@Preview(showBackground = true, name = "Many apps, long names")
@Composable
private fun FilterBottomSheetManyAppsPreview() {
    val many = List(12) { AppOption("com.app$it", "A rather long application display name number $it", it) }
    NotificappTheme(dynamicColor = false) {
        FilterBottomSheetContent(
            uiState = previewState(
                draft = RuleFilter(selectedApps = many.map { it.packageName }.toSet()),
                selectedApps = many,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun FilterBottomSheetEmptyPreview() {
    NotificappTheme(dynamicColor = false) {
        FilterBottomSheetContent(uiState = RulesFilterContract.UiState(), onEvent = {})
    }
}

// endregion
