package dev.gaferneira.notificapp.features.inbox.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract
import dev.gaferneira.notificapp.features.inbox.viewmodel.InboxFilterBottomSheetViewModel

/**
 * Bottom sheet for filtering inbox notifications.
 * Allows filtering by source app and processed status.
 *
 * @param currentSelectedApps Currently selected app package names
 * @param currentStatusFilter Current processed status filter
 * @param onFilterApplied Called when user applies new filters with the selected apps and status
 * @param onDismiss Called when the sheet should be dismissed
 * @param modifier Modifier for the bottom sheet
 * @param viewModel The ViewModel for this bottom sheet (injected by default)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxFilterBottomSheet(
    currentSelectedApps: List<String>,
    onFilterApplied: (List<String>, InboxFilterContract.Status) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentStatusFilter: InboxFilterContract.Status = InboxFilterContract.Status.ALL,
    viewModel: InboxFilterBottomSheetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Initialize the ViewModel with current filter state
    LaunchedEffect(currentSelectedApps, currentStatusFilter) {
        viewModel.initialize(currentSelectedApps, currentStatusFilter)
    }

    // Collect effects and handle them internally
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is InboxFilterContract.UiEffect.ApplyFilter -> {
                onFilterApplied(effect.selectedApps, effect.statusFilter)
            }
            is InboxFilterContract.UiEffect.Dismiss -> {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(InboxFilterContract.UiEvent.OnDismiss) },
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
    uiState: InboxFilterContract.UiState,
    onEvent: (InboxFilterContract.UiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Title
        Text(
            text = stringResource(R.string.filter_inbox_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
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
                selected = uiState.statusFilter == InboxFilterContract.Status.ALL,
                onClick = {
                    onEvent(InboxFilterContract.UiEvent.OnStatusChange(InboxFilterContract.Status.ALL))
                },
                label = { Text(stringResource(R.string.status_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = uiState.statusFilter == InboxFilterContract.Status.PROCESSED,
                onClick = {
                    onEvent(InboxFilterContract.UiEvent.OnStatusChange(InboxFilterContract.Status.PROCESSED))
                },
                label = { Text(stringResource(R.string.status_processed)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
                ),
            )
            FilterChip(
                selected = uiState.statusFilter == InboxFilterContract.Status.UNPROCESSED,
                onClick = {
                    onEvent(InboxFilterContract.UiEvent.OnStatusChange(InboxFilterContract.Status.UNPROCESSED))
                },
                label = { Text(stringResource(R.string.status_unprocessed)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.error,
                    selectedLabelColor = MaterialTheme.colorScheme.onError,
                ),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                            onEvent(InboxFilterContract.UiEvent.OnAppToggle(app.packageName))
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
        if (uiState.availableApps.isEmpty()) {
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
                onClick = { onEvent(InboxFilterContract.UiEvent.OnClearAll) },
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
                onClick = { onEvent(InboxFilterContract.UiEvent.OnApply) },
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

@Preview
@Composable
private fun InboxFilterBottomSheetPreview() {
    NotificappTheme {
        FilterBottomSheetContent(
            uiState = InboxFilterContract.UiState(
                availableApps = listOf(
                    AppInfo("com.ica", "ICA", null),
                    AppInfo("com.klarna.app", "Klarna", null),
                    AppInfo("se.postnord.mobile", "PostNord", null),
                    AppInfo("com.whatsapp", "WhatsApp", null),
                ),
                selectedApps = setOf("com.ica"),
                hasActiveFilters = true,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun InboxFilterBottomSheetEmptyPreview() {
    NotificappTheme {
        FilterBottomSheetContent(
            uiState = InboxFilterContract.UiState(
                availableApps = emptyList(),
            ),
            onEvent = {},
        )
    }
}
