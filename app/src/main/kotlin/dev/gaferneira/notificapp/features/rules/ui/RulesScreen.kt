package dev.gaferneira.notificapp.features.rules.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.core.ui.components.DryRunBadge
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.LocalIoDispatcher
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.features.rules.contract.RulesUiState
import dev.gaferneira.notificapp.features.rules.viewmodel.RulesViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

@Composable
fun RulesScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val ioDispatcher = LocalIoDispatcher.current

    // Handle effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is RulesEffect.NavigateToRuleEditor ->
                navigateTo(Routes.ruleEditor(ruleId = effect.ruleId), null)

            is RulesEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)

            is RulesEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)

            is RulesEffect.ShareRule -> shareRuleJson(context, ioDispatcher, effect.ruleName, effect.json)
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

/**
 * Writes [json] to a cache file and launches the share sheet for it via a [FileProvider] URI,
 * so the rule can be sent to any app that accepts a text/JSON attachment (Messages, email,
 * a cloud-storage "save to" target, etc.) without granting broader file access.
 */
private suspend fun shareRuleJson(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    ruleName: String,
    json: String,
) {
    val file = withContext(ioDispatcher) {
        val exportsDir = File(context.cacheDir, "rule-exports").apply { mkdirs() }
        val fileName = ruleName.ifBlank { "rule" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        File(exportsDir, "$fileName.json").apply { writeText(json) }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share rule"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesTopBar(
    filter: RuleFilter,
    onShowFilterSheet: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromClipboard: () -> Unit,
) {
    var showImportMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = "Rules Management",
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
                        contentDescription = "Filter and Sort",
                    )
                }
            }

            Box {
                IconButton(onClick = { showImportMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                    )
                }
                RulesImportMenu(
                    expanded = showImportMenu,
                    onDismiss = { showImportMenu = false },
                    onImportFromFile = onImportFromFile,
                    onImportFromClipboard = onImportFromClipboard,
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
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Import from file") },
            onClick = {
                onDismiss()
                onImportFromFile()
            },
        )
        DropdownMenuItem(
            text = { Text("Import from clipboard") },
            onClick = {
                onDismiss()
                onImportFromClipboard()
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val ioDispatcher = LocalIoDispatcher.current
    val filePickerLauncher = rememberLauncherForActivityResult(
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
                onClick = { onEvent(RulesEvent.OnAddRuleClick) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New rule") },
            )
        },
    ) { innerPadding ->
        RulesBody(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        )
    }

    RulesImportDialogs(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun RulesBody(
    uiState: RulesUiState,
    onEvent: (RulesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (val rulesResource = uiState.rules) {
            is Resource.Loading -> {
                LoadingState()
            }

            is Resource.Error -> {
                ErrorState(
                    failure = rulesResource.failure,
                    onRetry = { onEvent(RulesEvent.LoadRules) },
                )
            }

            is Resource.Success -> {
                SuccessState(
                    rules = rulesResource.data ?: emptyList(),
                    searchQuery = uiState.searchQuery,
                    filter = uiState.filter,
                    onEvent = onEvent,
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
            title = { Text("Couldn't import rule") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { onEvent(RulesEvent.OnDismissImportError) }) {
                    Text("OK")
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
    val appsSummary = rule.targetApps?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ") { it.name }
        ?.let { "Apps: $it" }
        ?: "Apps: All apps"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import \"${rule.name}\"?") },
        text = {
            Column {
                rule.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "${rule.conditions.size} condition(s), ${rule.saveDataFields().size} field(s), ${rule.actions.size} action(s)",
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
                        text = "$skippedActionCount action(s) require a newer version of Notificapp " +
                            "and will be skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This rule will start in dry-run mode: it will log matches but won't " +
                        "dismiss, snooze, or alert until you review the results and turn dry-run off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
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
private fun ErrorState(
    failure: Failure,
    onRetry: () -> Unit,
) {
    val message = when (failure) {
        is Failure.ApplicationException -> failure.message
        is Failure.NetworkConnection -> "Network error. Please check your connection."
        is Failure.ServerError -> failure.message ?: "Server error"
        else -> failure.cause?.message ?: "An unexpected error occurred"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Error loading rules",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun SuccessState(
    rules: List<Rule>,
    searchQuery: String,
    filter: RuleFilter,
    onEvent: (RulesEvent) -> Unit,
) {
    Column {
        RulesSearchBar(
            searchQuery = searchQuery,
            onSearchChange = { onEvent(RulesEvent.OnSearchQueryChange(it)) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No rules yet\nTap + to create your first rule",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            RulesList(rules = rules, filter = filter, onEvent = onEvent)
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
        placeholder = { Text("Search rules...") },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    // Determine grouping based on sort option
    val groupedRules = remember(rules, filter.sortBy) {
        when (filter.sortBy) {
            RuleFilter.SortBy.CATEGORY_ASC -> rules.groupBy { it.category ?: "Uncategorized" }
            RuleFilter.SortBy.STATUS -> rules.groupBy { if (it.isActive) "Enabled" else "Disabled" }
            else -> mapOf("" to rules) // Flat list - no grouping
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        groupedRules.forEach { (groupKey, groupRules) ->
            // Only show header if grouped
            if (groupKey.isNotEmpty()) {
                item(key = "header_$groupKey") {
                    when (filter.sortBy) {
                        RuleFilter.SortBy.CATEGORY_ASC -> CategoryHeader(category = groupKey, ruleCount = groupRules.size)
                        RuleFilter.SortBy.STATUS -> StatusHeader(status = groupKey, ruleCount = groupRules.size)
                        else -> { /* No header for flat list */ }
                    }
                }
            }

            items(items = groupRules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onClick = { onEvent(RulesEvent.OnRuleClick(rule.id)) },
                    onToggleActive = { onEvent(RulesEvent.OnRuleToggleActive(rule.id)) },
                    onExport = { onEvent(RulesEvent.OnExportRuleClick(rule.id)) },
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: String,
    ruleCount: Int,
) {
    val categoryIcon = getCategoryIcon(category)

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
            text = category,
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
    status: String,
    ruleCount: Int,
) {
    val statusIcon = if (status == "Enabled") {
        Icons.Default.CheckCircle
    } else {
        Icons.Default.DoNotDisturb
    }

    val iconTint = if (status == "Enabled") {
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
            text = status,
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
private fun getCategoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "finance", "financial", "banking", "payments" -> Icons.Outlined.AccountBalance
    "deliveries", "delivery", "shipping", "logistics" -> Icons.Outlined.LocalShipping
    "shopping", "e-commerce", "retail" -> Icons.Outlined.ShoppingCart
    "receipts", "transactions", "purchases" -> Icons.Outlined.Receipt
    "payments", "invoices" -> Icons.Outlined.Payments
    else -> Icons.Outlined.Receipt
}

@Composable
private fun RuleCard(
    rule: Rule,
    onClick: () -> Unit,
    onToggleActive: () -> Unit,
    onExport: () -> Unit,
) {
    val context = LocalContext.current
    val primaryApp = rule.targetApps?.takeIf { it.size == 1 }?.firstOrNull()

    // Load app icon if available
    val appIcon: ImageBitmap? = remember(primaryApp) {
        primaryApp?.let { app ->
            try {
                context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap()
                    .asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    // Target apps label - "N apps" for multi-app rules instead of a raw joined list, which
    // both avoids an unbounded line length and avoids double-prefixing with "App:"/"Apps:"
    // (RuleCardInfo already renders "App: $appName").
    val appName: String = remember(rule.targetApps) {
        when {
            rule.targetApps.isNullOrEmpty() -> "All apps"
            rule.targetApps.size == 1 -> primaryApp?.name ?: "1 app"
            else -> "${rule.targetApps.size} apps"
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuleCardInfo(rule = rule, appIcon = appIcon, appName = appName, modifier = Modifier.weight(1f))
            RuleCardActions(isActive = rule.isActive, onToggleActive = onToggleActive, onExport = onExport)
        }
    }
}

@Composable
private fun RuleCardInfo(
    rule: Rule,
    appIcon: ImageBitmap?,
    appName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rule.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rule.isDryRun) {
                    Spacer(modifier = Modifier.width(6.dp))
                    DryRunBadge()
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "App: $appName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleCardActions(
    isActive: Boolean,
    onToggleActive: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExport) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Export rule",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(
            checked = isActive,
            onCheckedChange = { onToggleActive() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
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
                    listOf(
                        Rule(
                            id = "1",
                            name = "ICA Purchase",
                            description = "Extract purchase info from ICA",
                            category = "Finance",
                            isActive = true,
                            targetApps = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "2",
                            name = "Klarna Payment",
                            description = "Track Klarna payments",
                            category = "Finance",
                            isActive = false,
                            targetApps = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "3",
                            name = "PostNord Tracker",
                            description = "Track deliveries",
                            category = "Deliveries",
                            isActive = true,
                            targetApps = emptyList(),
                            actions = emptyList(),
                        ),
                    ),
                ),
                allRules = emptyList(),
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
                    listOf(
                        Rule(
                            id = "1",
                            name = "ICA Purchase",
                            description = "Extract purchase info from ICA",
                            category = "Finance",
                            isActive = true,
                            targetApps = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "2",
                            name = "Klarna Payment",
                            description = "Track Klarna payments",
                            category = "Finance",
                            isActive = false,
                            targetApps = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "3",
                            name = "PostNord Tracker",
                            description = "Track deliveries",
                            category = "Deliveries",
                            isActive = true,
                            targetApps = emptyList(),
                            actions = emptyList(),
                        ),
                    ),
                ),
                allRules = emptyList(),
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

// endregion
