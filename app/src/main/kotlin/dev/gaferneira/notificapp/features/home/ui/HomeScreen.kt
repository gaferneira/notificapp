package dev.gaferneira.notificapp.features.home.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplateInfo
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.LocalIoDispatcher
import dev.gaferneira.notificapp.core.ui.utils.OnResumeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.features.home.contract.HomeUiState
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import dev.gaferneira.notificapp.features.home.contract.RecentActivityUi
import dev.gaferneira.notificapp.features.home.contract.RecurringSuggestionUi
import kotlinx.collections.immutable.ImmutableList
import dev.gaferneira.notificapp.features.home.contract.WeekStats
import dev.gaferneira.notificapp.features.home.viewmodel.HomeViewModel
import dev.gaferneira.notificapp.features.rules.ui.RuleTemplatePickerSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTemplatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ioDispatcher = LocalIoDispatcher.current

    OnResumeEffect { viewModel.onEvent(HomeEvent.OnResume) }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.NavigateToRuleEditor ->
                navigateTo(Routes.ruleEditor(ruleId = effect.ruleId, notificationId = effect.notificationId), null)

            is HomeEffect.NavigateToNotificationDetail ->
                navigateTo(Routes.notificationDetails(effect.notificationId), null)

            HomeEffect.NavigateToInbox -> navigateTo(Routes.inbox(), null)

            is HomeEffect.ShowError -> snackbarHostState.showSnackbar(effect.message.asString(context))
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        navigateTo = navigateTo,
        snackbarHostState = snackbarHostState,
        onShowTemplatePicker = { showTemplatePicker = true },
    )

    if (showTemplatePicker) {
        RuleTemplatePickerSheet(
            onTemplateSelected = { template ->
                showTemplatePicker = false
                coroutineScope.launch {
                    val text = withContext(ioDispatcher) {
                        runCatching {
                            context.assets.open("rules/${template.assetFileName}")
                                .bufferedReader()
                                .use { it.readText() }
                        }.getOrNull()
                    }
                    viewModel.onEvent(HomeEvent.OnRuleTemplateTextReceived(text.orEmpty()))
                }
            },
            onStartFromScratch = {
                showTemplatePicker = false
                viewModel.onEvent(HomeEvent.OnCreateRuleFromScratch)
                navigateTo(Routes.ruleEditor(), null)
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    onShowTemplatePicker: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.HOME,
                navigateTo = navigateTo,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { MonitoringStatusBanner(monitoring = uiState.monitoring) }

            item {
                when (val section = uiState.section) {
                    is HomeSection.StarterRules -> StarterRulesSection(
                        templates = section.templates,
                        onCreateFromTemplate = onShowTemplatePicker,
                        onSeeMoreTemplates = onShowTemplatePicker,
                    )

                    is HomeSection.Recurring -> RecurringSuggestionsSection(
                        suggestions = section.suggestions,
                        onCreateFromSuggestion = { onEvent(HomeEvent.OnCreateRuleFromSuggestion(it)) },
                        onSkipSimilar = { onEvent(HomeEvent.OnSkipSimilar(it)) },
                    )

                    HomeSection.None -> Unit
                }
            }

            item { WeekStatsRow(weekStats = uiState.weekStats) }

            item {
                RecentActivitySection(
                    recentActivity = uiState.recentActivity,
                    onRowClick = { onEvent(HomeEvent.OnRecentActivityClick(it)) },
                    onSeeAll = { onEvent(HomeEvent.OnSeeAllActivity) },
                )
            }
        }
    }
}

@Composable
private fun MonitoringStatusBanner(monitoring: MonitoringStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (monitoring.isListenerEnabled) {
                    stringResource(R.string.home_banner_active, monitoring.monitoredAppCount, monitoring.ruleCount)
                } else {
                    stringResource(R.string.home_banner_inactive)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StarterRulesSection(
    templates: ImmutableList<RuleTemplateInfo>,
    onCreateFromTemplate: () -> Unit,
    onSeeMoreTemplates: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.home_starter_rules_title), style = MaterialTheme.typography.titleMedium)
        templates.forEach { template ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = template.name, style = MaterialTheme.typography.titleSmall)
                    Text(text = template.description, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCreateFromTemplate) {
                        Text(stringResource(R.string.home_create_rule_from_this))
                    }
                }
            }
        }
        OutlinedButton(onClick = onSeeMoreTemplates) { Text(stringResource(R.string.home_see_more_templates)) }
    }
}

@Composable
private fun RecurringSuggestionsSection(
    suggestions: ImmutableList<RecurringSuggestionUi>,
    onCreateFromSuggestion: (RecurringSuggestionUi) -> Unit,
    onSkipSimilar: (RecurringSuggestionUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.home_recurring_title), style = MaterialTheme.typography.titleMedium)
        suggestions.forEach { suggestion ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = suggestion.appName, style = MaterialTheme.typography.labelMedium)
                    Text(text = suggestion.sampleTitle, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onCreateFromSuggestion(suggestion) }) {
                            Text(stringResource(R.string.home_create_rule_from_this))
                        }
                        TextButton(onClick = { onSkipSimilar(suggestion) }) {
                            Text(stringResource(R.string.home_skip_similar))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStatsRow(weekStats: WeekStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(label = stringResource(R.string.home_stat_records), value = weekStats.records, modifier = Modifier.weight(1f))
        StatTile(label = stringResource(R.string.home_stat_rules_fired), value = weekStats.rulesFired, modifier = Modifier.weight(1f))
        StatTile(label = stringResource(R.string.home_stat_apps_active), value = weekStats.appsActive, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RecentActivitySection(
    recentActivity: ImmutableList<RecentActivityUi>,
    onRowClick: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.home_recent_activity_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
        }
        if (recentActivity.isEmpty()) {
            Text(text = stringResource(R.string.home_recent_activity_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            recentActivity.forEach { activity ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onRowClick(activity.notificationId) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = activity.ruleName, style = MaterialTheme.typography.labelMedium)
                        activity.title?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenContentPreview() {
    NotificappTheme(darkTheme = false, dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = MonitoringStatus(
                    isListenerEnabled = true,
                    monitoredAppCount = 3,
                    ruleCount = 5,
                ),
                weekStats = WeekStats(records = 42, rulesFired = 12, appsActive = 3),
                recentActivity = persistentListOf(
                    RecentActivityUi(
                        notificationId = "1",
                        ruleName = "Dismiss spam",
                        title = "You have a new message",
                        subtitle = null,
                        appName = "Messaging App",
                    ),
                ),
                section = HomeSection.StarterRules(
                    templates = persistentListOf(),
                ),
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowTemplatePicker = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenContentPreviewDark() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = MonitoringStatus(
                    isListenerEnabled = true,
                    monitoredAppCount = 3,
                    ruleCount = 5,
                ),
                weekStats = WeekStats(records = 42, rulesFired = 12, appsActive = 3),
                recentActivity = persistentListOf(
                    RecentActivityUi(
                        notificationId = "1",
                        ruleName = "Dismiss spam",
                        title = "You have a new message",
                        subtitle = null,
                        appName = "Messaging App",
                    ),
                ),
                section = HomeSection.StarterRules(
                    templates = persistentListOf(),
                ),
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onShowTemplatePicker = {},
        )
    }
}
