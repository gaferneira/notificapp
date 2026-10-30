package dev.gaferneira.notificapp.features.databrowser.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.EmptyStateMessage
import dev.gaferneira.notificapp.core.ui.components.FilterEmptyState
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.navigation.navOptions
import dev.gaferneira.notificapp.core.ui.theme.LocalAppTokens
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.LocalIoDispatcher
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataSort
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserUiState
import dev.gaferneira.notificapp.features.databrowser.contract.DataListContent
import dev.gaferneira.notificapp.features.databrowser.contract.activeFilterCount
import dev.gaferneira.notificapp.features.databrowser.contract.dataListContent
import dev.gaferneira.notificapp.features.databrowser.contract.isNarrowed
import dev.gaferneira.notificapp.features.databrowser.viewmodel.DataBrowserViewModel
import dev.gaferneira.notificapp.features.databrowser.viewmodel.DataBrowserVisitArgs
import dev.gaferneira.notificapp.features.databrowser.viewmodel.UNDO_WINDOW_MS
import dev.gaferneira.notificapp.util.formatNotificationTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date
import java.util.UUID

/**
 * Data Browser Screen: a pushed drill-down (no bottom nav) with paginated browse/filter/search of
 * extracted rule data, CSV/JSON export via the share sheet, swipe-to-delete with Undo, and a bulk
 * delete (overflow menu) behind a mandatory confirmation. [initialRuleId] pre-filters to one rule and
 * [initialSince] to data from that instant on (both removable chips).
 */
@Composable
fun DataBrowserScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    navigateBack: () -> Unit,
    initialRuleId: String? = null,
    initialSince: Long? = null,
    viewModel: DataBrowserViewModel = hiltViewModel<DataBrowserViewModel, DataBrowserViewModel.Factory> {
        it.create(DataBrowserVisitArgs(initialRuleId, initialSince))
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ioDispatcher = LocalIoDispatcher.current

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is DataBrowserEffect.ShowError -> snackbarHostState.showSnackbar(effect.message.asString(context))
            is DataBrowserEffect.ShowBulkDeleted -> snackbarHostState.showSnackbar(
                context.resources.getQuantityString(R.plurals.data_bulk_deleted, effect.count, effect.count),
            )
            // Launched so a pending Undo snackbar never blocks the effect collector.
            is DataBrowserEffect.ShowUndoDelete -> coroutineScope.launch {
                showUndoSnackbar(context, snackbarHostState, effect.valueId, viewModel::onEvent)
            }
            is DataBrowserEffect.RequestExportSink -> {
                coroutineScope.launch {
                    exportAndShare(context, ioDispatcher, viewModel::exportTo, effect.format)
                }
            }
        }
    }

    DataBrowserScreenContent(
        uiState = uiState,
        rows = rows,
        onEvent = viewModel::onEvent,
        navigateTo = navigateTo,
        navigateBack = navigateBack,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Shows the Undo snackbar for a pending delete. Undo restores the row and a dismissal (swipe or a newer
 * snackbar) makes the delete final. The timeout and the commit belong to the ViewModel: this scope is
 * cancelled on rotation, so cancellation (or the [UNDO_WINDOW_MS] visual timeout) sends nothing.
 */
private suspend fun showUndoSnackbar(
    context: Context,
    snackbarHostState: SnackbarHostState,
    valueId: String,
    onEvent: (DataBrowserEvent) -> Unit,
) {
    snackbarHostState.currentSnackbarData?.dismiss()
    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
        snackbarHostState.showSnackbar(
            message = context.getString(R.string.data_undo_deleted),
            actionLabel = context.getString(R.string.data_undo_action),
            duration = SnackbarDuration.Indefinite,
        )
    }
    when (result) {
        SnackbarResult.ActionPerformed -> onEvent(DataBrowserEvent.OnUndoDelete(valueId))
        SnackbarResult.Dismissed -> onEvent(DataBrowserEvent.OnDeleteCommitted(valueId))
        null -> Unit // Timed out: the ViewModel's own timer commits the delete.
    }
}

/**
 * Writes the export to a temp cache file via [exportTo] (a real `OutputStream` sink, so the
 * stream-batched repository write never holds the full result set in memory), then renames it into
 * place and launches the share sheet - mirroring `RulesScreen.shareRuleJson`'s file-ownership
 * split, since only this UI layer can create an Android file/Uri.
 */
private suspend fun exportAndShare(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    exportTo: suspend (OutputStream, ExportFormat) -> Result<Unit>,
    format: ExportFormat,
) {
    val exportsDir = File(context.cacheDir, "data-exports").apply { mkdirs() }
    val extension = if (format == ExportFormat.CSV) "csv" else "json"
    val exportId = UUID.randomUUID()
    val tempFile = File(exportsDir, "export-$exportId.tmp")
    val finalFile = File(exportsDir, "extracted-data-$exportId.$extension")

    val result = withContext(ioDispatcher) {
        val writeResult = FileOutputStream(tempFile).use { stream -> exportTo(stream, format) }
        if (writeResult.isSuccess) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        } else {
            tempFile.delete()
        }
        writeResult
    }

    if (result.isSuccess) {
        val mimeType = if (format == ExportFormat.CSV) "text/csv" else "application/json"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", finalFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.data_export_chooser_title)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataBrowserScreenContent(
    uiState: DataBrowserUiState,
    rows: LazyPagingItems<DataBrowserRow>,
    onEvent: (DataBrowserEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    navigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DataBrowserTopBar(
                isExporting = uiState.isExporting,
                activeFilterCount = uiState.filter.activeFilterCount(),
                currentSort = uiState.filter.sort,
                onBack = navigateBack,
                onFilterClick = { showFilterSheet = true },
                onEvent = onEvent,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            SearchField(
                query = uiState.filter.searchQuery,
                onQueryChange = { onEvent(DataBrowserEvent.OnSearchQueryChange(it)) },
            )

            DataBrowserActiveFilterChips(
                filter = uiState.filter,
                ruleOptions = uiState.ruleOptions,
                appOptions = uiState.appOptions,
                onRemove = { onEvent(DataBrowserEvent.OnRemoveFilterChip(it)) },
            )

            DataRowList(
                rows = rows,
                filter = uiState.filter,
                onRowClick = { navigateTo(Screen.NotificationDetails(it.notificationId), null) },
                onDeleteRow = { onEvent(DataBrowserEvent.OnDeleteRowClick(it)) },
                onClearFilters = { onEvent(DataBrowserEvent.OnClearFilters) },
                onGoToRules = { navigateTo(Screen.Rules, navOptions { clearStack() }) },
            )
        }
    }

    if (showFilterSheet) {
        FilterSheetHost(uiState = uiState, onEvent = onEvent, onClose = { showFilterSheet = false })
    }

    uiState.pendingDeleteCount?.let { count ->
        BulkDeleteConfirmDialog(
            count = count,
            isFiltered = uiState.filter.isNarrowed(),
            onConfirm = { onEvent(DataBrowserEvent.OnConfirmBulkDelete) },
            onDismiss = { onEvent(DataBrowserEvent.OnCancelBulkDelete) },
        )
    }
}

/** Hosts the filter sheet; applying a draft forwards it to the ViewModel and closes the sheet. */
@Composable
private fun FilterSheetHost(uiState: DataBrowserUiState, onEvent: (DataBrowserEvent) -> Unit, onClose: () -> Unit) {
    DataBrowserFilterBottomSheet(
        currentFilter = uiState.filter,
        ruleOptions = uiState.ruleOptions,
        appOptions = uiState.appOptions,
        onFilterApplied = {
            onEvent(DataBrowserEvent.OnFilterChange(it))
            onClose()
        },
        onDismiss = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataBrowserTopBar(
    isExporting: Boolean,
    activeFilterCount: Int,
    currentSort: DataSort,
    onBack: () -> Unit,
    onFilterClick: () -> Unit,
    onEvent: (DataBrowserEvent) -> Unit,
) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.data_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.data_back_cd))
            }
        },
        actions = {
            FilterAction(activeFilterCount = activeFilterCount, onClick = onFilterClick)
            ExportAction(isExporting = isExporting, onExport = { onEvent(DataBrowserEvent.OnExportClick(it)) })
            OverflowAction(
                currentSort = currentSort,
                onSort = { onEvent(DataBrowserEvent.OnSortChange(it)) },
                onBulkDelete = { onEvent(DataBrowserEvent.OnBulkDeleteClick) },
            )
        },
    )
}

/** Share icon opening the CSV/JSON export menu; shows a spinner while an export is running. */
@Composable
private fun ExportAction(isExporting: Boolean, onExport: (ExportFormat) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showMenu = true }, enabled = !isExporting) {
        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp))
        } else {
            Icon(imageVector = Icons.Default.Share, contentDescription = stringResource(R.string.data_export_cd))
        }
    }
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.data_export_csv)) },
            onClick = {
                showMenu = false
                onExport(ExportFormat.CSV)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.data_export_json)) },
            onClick = {
                showMenu = false
                onExport(ExportFormat.JSON)
            },
        )
    }
}

/** Overflow (⋮) menu holding the sort options (current one checked) and the destructive bulk delete. */
@Composable
private fun OverflowAction(currentSort: DataSort, onSort: (DataSort) -> Unit, onBulkDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showMenu = true }) {
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.data_more_cd))
    }
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.data_sort_menu_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
            onClick = {},
            enabled = false,
        )
        DataSort.entries.forEach { sort ->
            val isSelected = sort == currentSort
            DropdownMenuItem(
                text = { Text(stringResource(sort.labelRes())) },
                leadingIcon = {
                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null)
                },
                modifier = Modifier.semantics { selected = isSelected },
                onClick = {
                    showMenu = false
                    onSort(sort)
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.data_delete_filtered_menu),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            onClick = {
                showMenu = false
                onBulkDelete()
            },
        )
    }
}

/** Filter button with a badge showing how many filter dimensions are active. */
@Composable
private fun FilterAction(activeFilterCount: Int, onClick: () -> Unit) {
    BadgedBox(
        badge = {
            if (activeFilterCount > 0) {
                Badge { Text(activeFilterCount.toString(), style = MaterialTheme.typography.labelSmall) }
            }
        },
    ) {
        val activeDescription = if (activeFilterCount > 0) {
            pluralStringResource(R.plurals.rules_filter_active_state, activeFilterCount, activeFilterCount)
        } else {
            null
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.semantics { activeDescription?.let { stateDescription = it } },
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = stringResource(R.string.data_filter_cd),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun DataSort.labelRes(): Int = when (this) {
    DataSort.DATE_DESC -> R.string.data_sort_date_desc
    DataSort.DATE_ASC -> R.string.data_sort_date_asc
    DataSort.RULE_ASC -> R.string.data_sort_rule_asc
    DataSort.RULE_DESC -> R.string.data_sort_rule_desc
    DataSort.APP_ASC -> R.string.data_sort_app_asc
    DataSort.APP_DESC -> R.string.data_sort_app_desc
    DataSort.FIELD_ASC -> R.string.data_sort_field_asc
    DataSort.FIELD_DESC -> R.string.data_sort_field_desc
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val container = LocalAppTokens.current.cardContainer
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.data_search_hint)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.data_search_clear_cd))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun DataRowList(
    rows: LazyPagingItems<DataBrowserRow>,
    filter: DataBrowserFilter,
    onRowClick: (DataBrowserRow) -> Unit,
    onDeleteRow: (String) -> Unit,
    onClearFilters: () -> Unit,
    onGoToRules: () -> Unit,
) {
    val refresh = rows.loadState.refresh
    when (dataListContent(refresh, rows.itemCount, filter)) {
        DataListContent.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        DataListContent.Error -> {
            (refresh as? LoadState.Error)?.error?.let { Timber.e(it, "Failed to load data rows") }
            DataRowListError(onRetry = { rows.retry() })
        }
        DataListContent.FilterEmpty -> FilterEmptyState(onClearFilters = onClearFilters)
        DataListContent.NoData -> NoDataState(onGoToRules = onGoToRules)
        DataListContent.Rows -> DataRowListContent(rows = rows, onRowClick = onRowClick, onDeleteRow = onDeleteRow)
    }
}

@Composable
private fun DataRowListError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.data_list_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.data_list_error_unknown),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.data_list_error_retry))
        }
    }
}

/** No extracted data exists at all: explains how Data fills up and links to Rules. */
@Composable
private fun NoDataState(onGoToRules: () -> Unit) {
    EmptyStateMessage(
        title = stringResource(R.string.data_empty_title),
        message = stringResource(R.string.data_empty_explanation),
        actionLabel = stringResource(R.string.data_empty_go_to_rules),
        onAction = onGoToRules,
    )
}

@Composable
private fun DataRowListContent(
    rows: LazyPagingItems<DataBrowserRow>,
    onRowClick: (DataBrowserRow) -> Unit,
    onDeleteRow: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = rows.itemCount, key = rows.itemKey { it.valueId }) { index ->
            val row = rows[index]
            if (row != null) {
                DataRowItem(row = row, onClick = { onRowClick(row) }, onDelete = { onDeleteRow(row.valueId) })
            }
        }
    }
}

/**
 * A row that deletes on end-to-start swipe. Swiping isn't reachable with assistive tech, so the
 * same delete is also exposed as a custom accessibility action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataRowItem(row: DataBrowserRow, onClick: () -> Unit, onDelete: () -> Unit) {
    val deleteLabel = stringResource(R.string.data_row_delete_cd)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(deleteLabel) {
                    onDelete()
                    true
                },
            )
        },
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeDeleteBackground(dismissState) },
    ) {
        DataRowCard(row = row, onClick = onClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteBackground(state: SwipeToDismissBoxState) {
    if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** Field name as a label, the value prominent, then "rule · app · time". Tapping opens the notification. */
@Composable
private fun DataRowCard(row: DataBrowserRow, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val emptyValue = stringResource(R.string.data_row_value_empty)
    val value = remember(row, locale) { formatDataValue(row, locale) ?: emptyValue }
    val time = remember(row.createdAt, locale) { Date(row.createdAt).formatNotificationTime(locale) }
    val meta = stringResource(R.string.data_row_meta, row.ruleName, row.appName, time)

    TonalCard(modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        Text(
            text = row.fieldName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Confirms the bulk delete; the copy says "all data" when no filter is active, else counts the matches. */
@Composable
private fun BulkDeleteConfirmDialog(
    count: Int,
    isFiltered: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(if (isFiltered) R.string.data_delete_filtered_title else R.string.data_delete_all_title)
    val message = when {
        count == 0 && isFiltered -> stringResource(R.string.data_delete_none_filtered)
        count == 0 -> stringResource(R.string.data_delete_none_all)
        isFiltered -> pluralStringResource(R.plurals.data_delete_filtered_message, count, count)
        else -> pluralStringResource(R.plurals.data_delete_all_message, count, count)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (count == 0) R.string.data_delete_ok else R.string.data_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.data_delete_cancel))
            }
        },
    )
}

// region Previews

private fun previewRow() = DataBrowserRow(
    valueId = "1",
    executionId = "e1",
    notificationId = "n1",
    ruleName = "ICA Purchase",
    packageName = "com.ica",
    appName = "ICA",
    fieldName = "Amount",
    fieldType = RuleField.FieldType.CURRENCY,
    valueText = "249.90 kr",
    valueNumber = 249.9,
    valueDate = null,
    notificationTitle = "Receipt",
    notificationContent = "Thanks for shopping",
    createdAt = System.currentTimeMillis(),
)

@Preview(showBackground = true)
@Composable
private fun DataBrowserScreenPreview() {
    NotificappTheme(darkTheme = false, dynamicColor = false) {
        val lazyPagingItems = flowOf(PagingData.from(listOf(previewRow()))).collectAsLazyPagingItems()

        DataBrowserScreenContent(
            uiState = DataBrowserUiState(),
            rows = lazyPagingItems,
            onEvent = {},
            navigateTo = { _, _ -> },
            navigateBack = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DataBrowserScreenPreviewDark() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        val lazyPagingItems = flowOf(PagingData.from(listOf(previewRow()))).collectAsLazyPagingItems()

        DataBrowserScreenContent(
            uiState = DataBrowserUiState(filter = DataBrowserFilter(ruleIds = listOf("r1"))),
            rows = lazyPagingItems,
            onEvent = {},
            navigateTo = { _, _ -> },
            navigateBack = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BulkDeleteConfirmDialogPreviewDark() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        BulkDeleteConfirmDialog(count = 12, isFiltered = false, onConfirm = {}, onDismiss = {})
    }
}

// endregion
