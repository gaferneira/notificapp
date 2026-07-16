package dev.gaferneira.notificapp.features.databrowser.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.LocalIoDispatcher
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataSort
import dev.gaferneira.notificapp.domain.model.DataStatistics
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserUiState
import dev.gaferneira.notificapp.features.databrowser.viewmodel.DataBrowserViewModel
import dev.gaferneira.notificapp.util.formatNotificationTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date
import java.util.UUID

/**
 * Data Browser Screen: paginated browse/filter/search of extracted rule data, a plain-text stats
 * header (no chart rendering - see `docs/roadmap.md`), CSV/JSON export via the share sheet, and
 * single/bulk delete with a mandatory bulk-delete confirmation.
 */
@Composable
fun DataBrowserScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: DataBrowserViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ioDispatcher = LocalIoDispatcher.current

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is DataBrowserEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is DataBrowserEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
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
        snackbarHostState = snackbarHostState,
    )
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
        context.startActivity(Intent.createChooser(intent, "Share exported data"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataBrowserScreenContent(
    uiState: DataBrowserUiState,
    rows: LazyPagingItems<DataBrowserRow>,
    onEvent: (DataBrowserEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DataBrowserTopBar(
                isExporting = uiState.isExporting,
                onEvent = onEvent,
            )
        },
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.DATA,
                navigateTo = navigateTo,
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

            StatsHeader(stats = uiState.stats, isLoading = uiState.isStatsLoading)

            Spacer(modifier = Modifier.height(8.dp))

            DataRowList(rows = rows, onDeleteRow = { onEvent(DataBrowserEvent.OnDeleteRowClick(it)) })
        }
    }

    uiState.pendingDeleteCount?.let { count ->
        BulkDeleteConfirmDialog(
            count = count,
            onConfirm = { onEvent(DataBrowserEvent.OnConfirmBulkDelete) },
            onDismiss = { onEvent(DataBrowserEvent.OnCancelBulkDelete) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataBrowserTopBar(
    isExporting: Boolean,
    onEvent: (DataBrowserEvent) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "Data",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = "Sort")
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                DataSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label()) },
                        onClick = {
                            showSortMenu = false
                            onEvent(DataBrowserEvent.OnSortChange(sort))
                        },
                    )
                }
            }

            IconButton(onClick = { onEvent(DataBrowserEvent.OnBulkDeleteClick) }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Bulk delete filtered data")
            }

            IconButton(onClick = { showExportMenu = true }, enabled = !isExporting) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Export data")
                }
            }
            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Export as CSV") },
                    onClick = {
                        showExportMenu = false
                        onEvent(DataBrowserEvent.OnExportClick(ExportFormat.CSV))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Export as JSON") },
                    onClick = {
                        showExportMenu = false
                        onEvent(DataBrowserEvent.OnExportClick(ExportFormat.JSON))
                    },
                )
            }
        },
    )
}

private fun DataSort.label(): String = when (this) {
    DataSort.DATE_DESC -> "Newest first"
    DataSort.DATE_ASC -> "Oldest first"
    DataSort.RULE_ASC -> "Rule (A-Z)"
    DataSort.RULE_DESC -> "Rule (Z-A)"
    DataSort.APP_ASC -> "App (A-Z)"
    DataSort.APP_DESC -> "App (Z-A)"
    DataSort.FIELD_ASC -> "Field (A-Z)"
    DataSort.FIELD_DESC -> "Field (Z-A)"
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        placeholder = { Text("Search extracted data...") },
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
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

/**
 * Plain-text statistics summary - no chart/graph rendering, per the data-statistics spec's
 * "Statistics are computed but not rendered as charts (Phase 1)" requirement.
 */
@Composable
private fun StatsHeader(stats: DataStatistics?, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (isLoading && stats == null) {
                Text(
                    text = "Loading stats...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (stats == null) {
                Text(
                    text = "No data yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val topRule = stats.mostActiveRuleName ?: "—"
                Text(
                    text = "Total: ${stats.total} · This week: ${stats.thisWeek} · Top rule: $topRule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DataRowList(
    rows: LazyPagingItems<DataBrowserRow>,
    onDeleteRow: (String) -> Unit,
) {
    when (val refreshState = rows.loadState.refresh) {
        is LoadState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is LoadState.Error -> {
            DataRowListError(message = refreshState.error.message, onRetry = { rows.retry() })
        }
        is LoadState.NotLoading -> {
            if (rows.itemCount == 0) {
                DataRowListEmpty()
            } else {
                DataRowListContent(rows = rows, onDeleteRow = onDeleteRow)
            }
        }
    }
}

@Composable
private fun DataRowListError(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Error loading data",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message ?: "Unknown error",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun DataRowListEmpty() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No extracted data yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DataRowListContent(
    rows: LazyPagingItems<DataBrowserRow>,
    onDeleteRow: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = rows.itemCount, key = rows.itemKey { it.valueId }) { index ->
            val row = rows[index]
            if (row != null) {
                DataRowCard(row = row, onDelete = { onDeleteRow(row.valueId) })
            }
        }
    }
}

@Composable
private fun DataRowCard(row: DataBrowserRow, onDelete: () -> Unit) {
    val value = row.valueText
        ?: row.valueNumber?.toString()
        ?: row.valueDate?.let { Date(it).formatNotificationTime() }
        ?: "—"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${row.fieldName}: $value",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${row.ruleName} · ${row.appName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BulkDeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete filtered data?") },
        text = {
            Text(
                if (count == 0) {
                    "No entries match the current filters."
                } else {
                    "This will permanently delete $count ${if (count == 1) "entry" else "entries"} matching the current filters."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (count == 0) "OK" else "Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun DataBrowserScreenPreview() {
    NotificappTheme(darkTheme = false, dynamicColor = false) {
        val pagingData = PagingData.from(
            listOf(
                DataBrowserRow(
                    valueId = "1",
                    executionId = "e1",
                    ruleName = "ICA Purchase",
                    packageName = "com.ica",
                    appName = "ICA",
                    fieldName = "Amount",
                    fieldType = RuleField.FieldType.CURRENCY,
                    valueText = "249.90 kr",
                    valueNumber = null,
                    valueDate = null,
                    notificationTitle = "Receipt",
                    notificationContent = "Thanks for shopping",
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
        val lazyPagingItems = flowOf(pagingData).collectAsLazyPagingItems()

        DataBrowserScreenContent(
            uiState = DataBrowserUiState(
                stats = DataStatistics(
                    total = 42,
                    thisWeek = 5,
                    mostActiveRuleName = "ICA Purchase",
                    perRule = emptyList(),
                    perApp = emptyList(),
                    trend = emptyList(),
                ),
            ),
            rows = lazyPagingItems,
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DataBrowserScreenPreviewDark() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        val lazyPagingItems = flowOf(PagingData.empty<DataBrowserRow>()).collectAsLazyPagingItems()

        DataBrowserScreenContent(
            uiState = DataBrowserUiState(),
            rows = lazyPagingItems,
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}

// endregion
