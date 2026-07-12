package dev.gaferneira.notificapp.features.rules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract
import dev.gaferneira.notificapp.features.rules.viewmodel.FilterBottomSheetViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Bottom sheet for advanced rule filtering.
 * Allows filtering by category and target app.
 *
 * @param allRules Complete list of rules to extract available filter options
 * @param currentFilter Current filter configuration
 * @param onFilterApplied Called when user applies new filters
 * @param onDismiss Called when the sheet should be dismissed
 * @param modifier Modifier for the bottom sheet
 * @param viewModel The ViewModel for this bottom sheet (injected by default)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesFilterBottomSheet(
    allRules: ImmutableList<Rule>,
    currentFilter: RuleFilter,
    onFilterApplied: (RuleFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilterBottomSheetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Initialize the ViewModel with current data
    LaunchedEffect(allRules, currentFilter) {
        viewModel.onEvent(RulesFilterContract.UiEvent.Init(allRules, currentFilter))
    }

    // Collect effects and handle them internally
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is RulesFilterContract.UiEffect.ApplyFilter -> {
                onFilterApplied(effect.filter)
            }
            is RulesFilterContract.UiEffect.Dismiss -> {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(RulesFilterContract.UiEvent.OnDismiss) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        FilterBottomSheetContent(
            uiState = uiState,
            onEvent = viewModel::onEvent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBottomSheetContent(
    uiState: RulesFilterContract.UiState,
    onEvent: (RulesFilterContract.UiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Title
        Text(
            text = stringResource(R.string.filter_rules_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(12.dp))

        SortDropdown(
            selectedSort = uiState.sortBy,
            onSortSelected = { sortBy ->
                onEvent(RulesFilterContract.UiEvent.OnSortChange(sortBy))
            },
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Status Section
        Text(
            text = stringResource(R.string.filter_by_status),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = uiState.statusFilter == RuleFilter.Status.ALL,
                onClick = {
                    onEvent(RulesFilterContract.UiEvent.OnStatusChange(RuleFilter.Status.ALL))
                },
                label = { Text(stringResource(R.string.status_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = uiState.statusFilter == RuleFilter.Status.ENABLED,
                onClick = {
                    onEvent(RulesFilterContract.UiEvent.OnStatusChange(RuleFilter.Status.ENABLED))
                },
                label = { Text(stringResource(R.string.status_enabled)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = uiState.statusFilter == RuleFilter.Status.DISABLED,
                onClick = {
                    onEvent(RulesFilterContract.UiEvent.OnStatusChange(RuleFilter.Status.DISABLED))
                },
                label = { Text(stringResource(R.string.status_disabled)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Category Section
        if (uiState.availableCategories.isNotEmpty()) {
            Text(
                text = stringResource(R.string.filter_by_category),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.availableCategories.forEach { category ->
                    val isSelected = uiState.selectedCategories.contains(category)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onEvent(RulesFilterContract.UiEvent.OnCategoryToggle(category))
                        },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // App Section
        if (uiState.availableApps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.filter_by_app),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.availableApps.forEach { app ->
                    val isSelected = uiState.selectedApps.contains(app.packageName)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onEvent(RulesFilterContract.UiEvent.OnAppToggle(app.packageName))
                        },
                        label = { Text(app.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Empty state
        if (uiState.availableCategories.isEmpty() && uiState.availableApps.isEmpty()) {
            Text(
                text = stringResource(R.string.no_filters_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp),
            )
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.apply))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    selectedSort: RuleFilter.SortBy,
    onSortSelected: (RuleFilter.SortBy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val sortOptions = listOf(
        RuleFilter.SortBy.CATEGORY_ASC to R.string.sort_category,
        RuleFilter.SortBy.NAME_ASC to R.string.sort_name_asc,
        RuleFilter.SortBy.NAME_DESC to R.string.sort_name_desc,
        RuleFilter.SortBy.CREATED_NEWEST to R.string.sort_created_newest,
        RuleFilter.SortBy.CREATED_OLDEST to R.string.sort_created_oldest,
        RuleFilter.SortBy.UPDATED_RECENT to R.string.sort_updated_recent,
        RuleFilter.SortBy.STATUS to R.string.sort_status,
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = stringResource(
                sortOptions.find { it.first == selectedSort }?.second ?: R.string.sort_category,
            ),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.sort_by)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sortOptions.forEach { (sortBy, stringRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(stringRes)) },
                    onClick = {
                        onSortSelected(sortBy)
                        expanded = false
                    },
                    leadingIcon = if (selectedSort == sortBy) {
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

@Preview
@Composable
private fun FilterBottomSheetPreview() {
    NotificappTheme {
        FilterBottomSheetContent(
            uiState = RulesFilterContract.UiState(
                availableCategories = listOf("Finance", "Deliveries", "Shopping", "Health"),
                availableApps = persistentListOf(
                    AppInfo("com.ica", "ICA", null),
                    AppInfo("com.klarna.app", "Klarna", null),
                    AppInfo("se.postnord.mobile", "PostNord", null),
                ),
                selectedCategories = setOf("Finance"),
                selectedApps = setOf("com.ica"),
                hasActiveFilters = true,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun FilterBottomSheetEmptyPreview() {
    NotificappTheme {
        FilterBottomSheetContent(
            uiState = RulesFilterContract.UiState(
                availableCategories = emptyList(),
                availableApps = persistentListOf(),
            ),
            onEvent = {},
        )
    }
}
