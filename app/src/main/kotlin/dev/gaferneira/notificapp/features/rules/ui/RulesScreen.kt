package dev.gaferneira.notificapp.features.rules.ui

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.LocalIoDispatcher
import dev.gaferneira.notificapp.core.ui.utils.getCategoryIcon
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.features.rules.contract.RulesUiState
import dev.gaferneira.notificapp.features.rules.viewmodel.RulesViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
fun RulesScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Handle effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is RulesEffect.NavigateToRuleDetails ->
                navigateTo(Routes.ruleDetails(ruleId = effect.ruleId), null)

            is RulesEffect.NavigateToRuleEditor ->
                navigateTo(Routes.ruleEditor(ruleId = effect.ruleId), null)

            is RulesEffect.ShowError -> snackbarHostState.showSnackbar(effect.message.asString(context))

            is RulesEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message.asString(context))
        }
    }

    // Show filter bottom sheet
    if (showFilterSheet && uiState.allRules.isNotEmpty()) {
        RulesFilterBottomSheet(
            allRules = uiState.allRules,
            currentFilter = uiState.filter,
            onFilterApplied = { filter ->
                viewModel.onEvent(RulesEvent.OnFilterChange(filter))
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    RulesScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        navigateTo = navigateTo,
        onShowFilterSheet = { showFilterSheet = true },
        snackbarHostState = snackbarHostState,
    )
}

/**
 * A single exported rule is a few KB at most; this is a generous cap against a malicious or
 * corrupt "rule" file blowing up memory on import.
 */
private const val MAX_IMPORT_FILE_SIZE_BYTES = 1 * 1024 * 1024

/**
 * Reads up to [maxBytes] from this stream, returning null (without buffering the rest of the
 * stream into memory) if it contains more than that.
 */
private fun InputStream.readUpTo(maxBytes: Int): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(chunk)
        if (read == -1) break
        if (buffer.size() + read > maxBytes) return null
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesTopBar(
    filter: RuleFilter,
    onShowFilterSheet: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportFromTemplates: () -> Unit,
) {
    var showImportMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.rules_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            BadgedBox(
                badge = {
                    if (filter.isActive()) {
                        Badge {
                            Text(
                                filter.activeFilterCount().toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
            ) {
                IconButton(onClick = onShowFilterSheet) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.rules_filter_cd),
                    )
                }
            }

            Box {
                IconButton(onClick = { showImportMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.rules_more_options),
                    )
                }
                RulesImportMenu(
                    expanded = showImportMenu,
                    onDismiss = { showImportMenu = false },
                    onImportFromFile = onImportFromFile,
                    onImportFromClipboard = onImportFromClipboard,
                    onImportFromTemplates = onImportFromTemplates,
                )
            }
        },
    )
}

@Composable
private fun RulesImportMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromClipboard: () -> Unit,
    onImportFromTemplates: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rules_import_from_file)) },
            onClick = {
                onDismiss()
                onImportFromFile()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rules_import_from_clipboard)) },
            onClick = {
                onDismiss()
                onImportFromClipboard()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rules_import_from_templates)) },
            onClick = {
                onDismiss()
                onImportFromTemplates()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RulesScreenContent(
    uiState: RulesUiState,
    onEvent: (RulesEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    onShowFilterSheet: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val ioDispatcher = LocalIoDispatcher.current
    val filePickerLauncher = rememberRuleFilePickerLauncher(onEvent, coroutineScope, ioDispatcher)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RulesTopBar(
                filter = uiState.filter,
                onShowFilterSheet = onShowFilterSheet,
                onImportFromFile = { filePickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onImportFromClipboard = {
                    val text = clipboardManager.getText()?.text.orEmpty()
                    onEvent(RulesEvent.OnRuleTextReceived(text))
                },
                onImportFromTemplates = { navigateTo(Routes.ruleTemplates(), null) },
            )
        },
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.RULES,
                navigateTo = navigateTo,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigateTo(Routes.ruleTemplates(), null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.rules_new_rule)) },
            )
        },
    ) { innerPadding ->
        RulesBody(
            uiState = uiState,
            onEvent = onEvent,
            onBrowseTemplates = { navigateTo(Routes.ruleTemplates(), null) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        )
    }

    RulesImportDialogs(uiState = uiState, onEvent = onEvent)
}

/**
 * Registers the "import from file" [ActivityResultContracts.OpenDocument] launcher and wires its
 * result into [RulesEvent.OnRuleTextReceived], off the main thread.
 */
@Composable
private fun rememberRuleFilePickerLauncher(
    onEvent: (RulesEvent) -> Unit,
    coroutineScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) = run {
    val context = LocalContext.current
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // A null uri means the user cancelled the picker - not an import attempt, so don't
        // surface an error dialog for it.
        if (uri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            val text = withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readUpTo(MAX_IMPORT_FILE_SIZE_BYTES)?.decodeToString()
                    }
                }.getOrNull()
            }
            onEvent(RulesEvent.OnRuleTextReceived(text.orEmpty()))
        }
    }
}

@Composable
private fun RulesBody(
    uiState: RulesUiState,
    onEvent: (RulesEvent) -> Unit,
    onBrowseTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (val rulesResource = uiState.rules) {
            is Resource.Loading -> {
                LoadingState()
            }

            is Resource.Error -> {
                ErrorState(onRetry = { onEvent(RulesEvent.LoadRules) })
            }

            is Resource.Success -> {
                SuccessState(
                    rules = rulesResource.data ?: emptyList(),
                    hasAnyRules = uiState.allRules.isNotEmpty(),
                    searchQuery = uiState.searchQuery,
                    filter = uiState.filter,
                    onEvent = onEvent,
                    onBrowseTemplates = onBrowseTemplates,
                )
            }
        }
    }
}

@Composable
private fun RulesImportDialogs(
    uiState: RulesUiState,
    onEvent: (RulesEvent) -> Unit,
) {
    uiState.importPreview?.let { preview ->
        ImportPreviewDialog(
            rule = preview,
            skippedActionCount = uiState.importSkippedActions.size,
            onConfirm = { onEvent(RulesEvent.OnImportConfirmed) },
            onDismiss = { onEvent(RulesEvent.OnImportCancelled) },
        )
    }

    uiState.importError?.let { error ->
        AlertDialog(
            onDismissRequest = { onEvent(RulesEvent.OnDismissImportError) },
            title = { Text(stringResource(R.string.rules_import_error_title)) },
            text = { Text(error.asString()) },
            confirmButton = {
                TextButton(onClick = { onEvent(RulesEvent.OnDismissImportError) }) {
                    Text(stringResource(R.string.rules_ok))
                }
            },
        )
    }
}

/**
 * Confirmation dialog shown after a rule file/clipboard text decodes successfully, before it's
 * saved. Always mentions dry-run since imported rules start there regardless of the source file.
 */
@Composable
private fun ImportPreviewDialog(
    rule: Rule,
    skippedActionCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_import_title, rule.name)) },
        text = { ImportPreviewContent(rule = rule, skippedActionCount = skippedActionCount) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.rules_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rules_cancel))
            }
        },
    )
}

@Composable
private fun ImportPreviewContent(
    rule: Rule,
    skippedActionCount: Int,
) {
    val appNames = rule.targetApps?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
    val appsSummary = when {
        appNames == null -> stringResource(R.string.rules_import_apps_all)
        rule.isIncludeMode -> stringResource(R.string.rules_import_apps_include, appNames)
        else -> stringResource(R.string.rules_import_apps_exclude, appNames)
    }

    Column {
        rule.description?.let { description ->
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = stringResource(
                R.string.rules_import_summary,
                rule.conditions.size,
                rule.saveDataFields().size,
                rule.actions.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = appsSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (skippedActionCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.rules_import_skipped_actions,
                    skippedActionCount,
                    skippedActionCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.rules_import_dry_run_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.rules_load_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.rules_load_error_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.rules_retry))
        }
    }
}

@Composable
private fun SuccessState(
    rules: List<Rule>,
    hasAnyRules: Boolean,
    searchQuery: String,
    filter: RuleFilter,
    onEvent: (RulesEvent) -> Unit,
    onBrowseTemplates: () -> Unit,
) {
    Column {
        // Nothing to search or filter until the first rule exists.
        if (hasAnyRules) {
            RulesSearchBar(
                searchQuery = searchQuery,
                onSearchChange = { onEvent(RulesEvent.OnSearchQueryChange(it)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            rules.isNotEmpty() -> RulesList(rules = rules, filter = filter, onEvent = onEvent)
            hasAnyRules -> NoResultsState(onClearFilters = { onEvent(RulesEvent.OnClearFilters) })
            else -> EmptyRulesState(onBrowseTemplates = onBrowseTemplates)
        }
    }
}

/** First-run state: the user has no rules at all. */
@Composable
private fun EmptyRulesState(onBrowseTemplates: () -> Unit) {
    EmptyMessage(
        title = stringResource(R.string.rules_empty_title),
        message = stringResource(R.string.rules_empty_message),
        actionLabel = stringResource(R.string.rules_browse_templates),
        onAction = onBrowseTemplates,
    )
}

/** Rules exist, but the current search/filters hide all of them. */
@Composable
private fun NoResultsState(onClearFilters: () -> Unit) {
    EmptyMessage(
        title = stringResource(R.string.rules_no_results_title),
        message = stringResource(R.string.rules_no_results_message),
        actionLabel = stringResource(R.string.rules_clear_filters),
        onAction = onClearFilters,
    )
}

@Composable
private fun EmptyMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun RulesSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.rules_search_placeholder)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.rules_search_clear),
                    )
                }
            }
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

@Composable
private fun RulesList(
    rules: List<Rule>,
    filter: RuleFilter,
    onEvent: (RulesEvent) -> Unit,
) {
    // Determine grouping based on sort option. Keys stay language-neutral; headers are resolved
    // to localized labels at render time.
    val groupedRules = remember(rules, filter.sortBy) {
        when (filter.sortBy) {
            RuleFilter.SortBy.CATEGORY_ASC -> rules.groupBy { it.category.orEmpty() }
            RuleFilter.SortBy.STATUS -> rules.groupBy { it.isActive.toString() }
            else -> mapOf("" to rules) // Flat list - no grouping
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        groupedRules.forEach { (groupKey, groupRules) ->
            // Flat lists use a single "" group and show no header. For category grouping "" is
            // the uncategorized group, which still gets a header.
            if (filter.sortBy == RuleFilter.SortBy.CATEGORY_ASC || groupKey.isNotEmpty()) {
                item(key = "header_$groupKey") {
                    when (filter.sortBy) {
                        RuleFilter.SortBy.CATEGORY_ASC -> CategoryHeader(
                            category = groupKey.ifEmpty { null },
                            ruleCount = groupRules.size,
                        )
                        RuleFilter.SortBy.STATUS -> StatusHeader(
                            isActive = groupKey.toBoolean(),
                            ruleCount = groupRules.size,
                        )
                        else -> { /* No header for flat list */ }
                    }
                }
            }

            items(items = groupRules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onClick = { onEvent(RulesEvent.OnRuleClick(rule.id)) },
                    onToggleActive = { onEvent(RulesEvent.OnRuleToggleActive(rule.id)) },
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: String?,
    ruleCount: Int,
) {
    val categoryIcon = getCategoryIcon(category ?: "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Category icon
        Icon(
            imageVector = categoryIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )

        // Category name
        Text(
            text = category ?: stringResource(R.string.rules_group_uncategorized),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Rule count badge
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Text(
                text = ruleCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun StatusHeader(
    isActive: Boolean,
    ruleCount: Int,
) {
    val statusIcon = if (isActive) {
        Icons.Default.CheckCircle
    } else {
        Icons.Default.DoNotDisturb
    }

    val iconTint = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status icon
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )

        // Status name
        Text(
            text = stringResource(if (isActive) R.string.status_enabled else R.string.status_disabled),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Rule count badge
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Text(
                text = ruleCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun RulesScreenPreview() {
    NotificappTheme(darkTheme = false, dynamicColor = false) {
        RulesScreenContent(
            uiState = RulesUiState(
                rules = Resource.Success(
                    persistentListOf(
                        Rule(
                            id = "1",
                            name = "ICA Purchase",
                            description = "Extract purchase info from ICA",
                            category = "Finance",
                            isActive = true,
                            targetApps = persistentListOf(),
                            actions = persistentListOf(),
                        ),
                        Rule(
                            id = "2",
                            name = "Klarna Payment",
                            description = "Track Klarna payments",
                            category = "Finance",
                            isActive = false,
                            targetApps = persistentListOf(),
                            actions = persistentListOf(),
                        ),
                        Rule(
                            id = "3",
                            name = "PostNord Tracker",
                            description = "Track deliveries",
                            category = "Deliveries",
                            isActive = true,
                            targetApps = persistentListOf(),
                            actions = persistentListOf(),
                        ),
                    ),
                ),
                allRules = persistentListOf(),
                searchQuery = "",
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowFilterSheet = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RulesScreenPreviewDark() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        RulesScreenContent(
            uiState = RulesUiState(
                rules = Resource.Success(
                    persistentListOf(
                        Rule(
                            id = "1",
                            name = "ICA Purchase",
                            description = "Extract purchase info from ICA",
                            category = "Finance",
                            isActive = true,
                            targetApps = persistentListOf(),
                            actions = persistentListOf(),
                        ),
                        Rule(
                            id = "2",
                            name = "Klarna Payment",
                            description = "Track Klarna payments",
                            category = "Finance",
                            isActive = false,
                            targetApps = persistentListOf(),
                            actions = persistentListOf(),
                        ),
                        Rule(
                            id = "3",
                            name = "PostNord Tracker",
                            description = "Track deliveries",
                            category = "Deliveries",
                            isActive = true,
                            targetApps = persistentListOf(),
                            actions = persistentListOf(),
                        ),
                    ),
                ),
                allRules = persistentListOf(),
                searchQuery = "",
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowFilterSheet = {},
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun RulesScreenLoadingPreview() {
    MaterialTheme {
        RulesScreenContent(
            uiState = RulesUiState(
                rules = Resource.Loading(),
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowFilterSheet = {},
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun RulesScreenErrorPreview() {
    MaterialTheme {
        RulesScreenContent(
            uiState = RulesUiState(
                rules = Resource.Error(
                    Failure.ApplicationException("Failed to connect to database"),
                ),
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowFilterSheet = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty - no rules yet")
@Composable
private fun RulesScreenEmptyPreview() {
    NotificappTheme(darkTheme = false, dynamicColor = false) {
        RulesScreenContent(
            uiState = RulesUiState(rules = Resource.Success(persistentListOf())),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowFilterSheet = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty - no results (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RulesScreenNoResultsPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        RulesScreenContent(
            uiState = RulesUiState(
                rules = Resource.Success(persistentListOf()),
                allRules = persistentListOf(Rule(id = "1", name = "ICA Purchase", description = null)),
                searchQuery = "zzz",
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowFilterSheet = {},
        )
    }
}

// endregion
