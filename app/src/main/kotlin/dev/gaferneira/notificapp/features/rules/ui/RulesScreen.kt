package dev.gaferneira.notificapp.features.rules.ui

import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.features.rules.contract.RulesUiState
import dev.gaferneira.notificapp.features.rules.viewmodel.RulesViewModel

@Composable
fun RulesScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RulesEffect.NavigateToRuleEditor -> {
                    navigateTo(Screen.RuleEditor(ruleId = effect.ruleId), null)
                }

                is RulesEffect.ShowError -> {
                    // Could show a snackbar here
                }

                is RulesEffect.ShowDeleteConfirmation -> {
                    // Could show a confirmation dialog here
                }
            }
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
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RulesScreenContent(
    uiState: RulesUiState,
    onEvent: (RulesEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    onShowFilterSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
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
                            if (uiState.filter.isActive()) {
                                Badge {
                                    Text(
                                        uiState.filter.activeFilterCount().toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                    ) {
                        IconButton(
                            onClick = onShowFilterSheet,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter and Sort",
                            )
                        }
                    }
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
            IconButton(
                onClick = { onEvent(RulesEvent.OnAddRuleClick) },
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Rule",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
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
                        onSearchChange = { onEvent(RulesEvent.OnSearchQueryChange(it)) },
                        onRuleClick = { onEvent(RulesEvent.OnRuleClick(it)) },
                        onRuleToggleActive = { onEvent(RulesEvent.OnRuleToggleActive(it)) },
                    )
                }
            }
        }
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
        IconButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Retry",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuccessState(
    rules: List<Rule>,
    searchQuery: String,
    filter: RuleFilter,
    onSearchChange: (String) -> Unit,
    onRuleClick: (String) -> Unit,
    onRuleToggleActive: (String) -> Unit,
) {
    Column {
        // Search bar
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

        Spacer(modifier = Modifier.height(8.dp))

        // Rules list
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
            // Determine grouping based on sort option
            val groupedRules = when (filter.sortBy) {
                RuleFilter.SortBy.CATEGORY_ASC -> {
                    rules.groupBy { it.category ?: "Uncategorized" }
                }
                RuleFilter.SortBy.STATUS -> {
                    rules.groupBy { if (it.isActive) "Enabled" else "Disabled" }
                }
                else -> {
                    // Flat list - no grouping
                    mapOf("" to rules)
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
                                RuleFilter.SortBy.CATEGORY_ASC -> {
                                    CategoryHeader(
                                        category = groupKey,
                                        ruleCount = groupRules.size,
                                    )
                                }
                                RuleFilter.SortBy.STATUS -> {
                                    StatusHeader(
                                        status = groupKey,
                                        ruleCount = groupRules.size,
                                    )
                                }
                                else -> { /* No header for flat list */ }
                            }
                        }
                    }

                    items(
                        items = groupRules,
                        key = { it.id },
                    ) { rule ->
                        RuleCard(
                            rule = rule,
                            onClick = { onRuleClick(rule.id) },
                            onToggleActive = { onRuleToggleActive(rule.id) },
                        )
                    }
                }
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

    // Get app name
    val appName: String? = remember(rule.targetApps) {
        if (rule.targetApps.isNullOrEmpty()) {
            return@remember "All apps"
        } else if (rule.targetApps.size == 1) {
            primaryApp?.name
        } else {
            "Apps: " + rule.targetApps.joinToString(", ") { it.name }
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
            // Left side: App icon and rule info
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // App icon or placeholder
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

                // Rule name and app label
                Column {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (appName != null) "App: $appName" else "App: All apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Right side: Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Enable/Disable toggle
                Switch(
                    checked = rule.isActive,
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
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun RulesScreenPreview() {
    MaterialTheme {
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
                            fields = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "2",
                            name = "Klarna Payment",
                            description = "Track Klarna payments",
                            category = "Finance",
                            isActive = false,
                            targetApps = emptyList(),
                            fields = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "3",
                            name = "PostNord Tracker",
                            description = "Track deliveries",
                            category = "Deliveries",
                            isActive = true,
                            targetApps = emptyList(),
                            fields = emptyList(),
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
    MaterialTheme {
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
                            fields = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "2",
                            name = "Klarna Payment",
                            description = "Track Klarna payments",
                            category = "Finance",
                            isActive = false,
                            targetApps = emptyList(),
                            fields = emptyList(),
                            actions = emptyList(),
                        ),
                        Rule(
                            id = "3",
                            name = "PostNord Tracker",
                            description = "Track deliveries",
                            category = "Deliveries",
                            isActive = true,
                            targetApps = emptyList(),
                            fields = emptyList(),
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
