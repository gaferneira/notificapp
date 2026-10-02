package dev.gaferneira.notificapp.features.home.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplateInfo
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.core.ui.components.RuleTemplateCard
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.OnResumeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.features.home.contract.HomeUiState
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import dev.gaferneira.notificapp.features.home.contract.RecentActivityUi
import dev.gaferneira.notificapp.features.home.contract.RecurringSuggestionUi
import dev.gaferneira.notificapp.features.home.contract.WeekStats
import dev.gaferneira.notificapp.features.home.viewmodel.HomeViewModel
import dev.gaferneira.notificapp.util.openNotificationListenerSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun HomeScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
        onBrowseTemplates = { navigateTo(Routes.ruleTemplates(), null) },
        onEnableAccess = { openNotificationListenerSettings(context) },
        onCreateFromTemplate = { template ->
            navigateTo(Routes.ruleEditor(templateAssetFileName = template.assetFileName), null)
        },
        // Navigation comes back through HomeEffect.NavigateToRuleEditor; navigating here too would stack two editors.
        onCreateFromScratch = { viewModel.onEvent(HomeEvent.OnCreateRuleFromScratch) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    onBrowseTemplates: () -> Unit,
    onCreateFromTemplate: (RuleTemplateInfo) -> Unit = {},
    onCreateFromScratch: () -> Unit = {},
    onEnableAccess: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { HomeTopBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.HOME,
                navigateTo = navigateTo,
            )
        },
    ) { innerPadding ->
        // Render nothing until the first emission: the initial state (listener off, no rules) would
        // otherwise flash a misleading "Notification access is disabled" banner.
        if (uiState.isLoading) return@Scaffold

        // First run: the checklist is the whole story, so hide the empty stats and activity below it.
        val isFirstRun = uiState.section is HomeSection.StarterRules &&
            uiState.weekStats == WeekStats() &&
            uiState.recentActivity.isEmpty()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // While the first-run checklist is showing it already reports access/apps status.
            if (uiState.section !is HomeSection.StarterRules) {
                item {
                    MonitoringStatusBanner(
                        monitoring = uiState.monitoring,
                        onManageApps = { navigateTo(Routes.appSelection(), null) },
                        onEnableAccess = onEnableAccess,
                    )
                }
            }

            item {
                HomeSectionContent(
                    uiState = uiState,
                    onEvent = onEvent,
                    navigateTo = navigateTo,
                    onBrowseTemplates = onBrowseTemplates,
                    onCreateFromTemplate = onCreateFromTemplate,
                    onCreateFromScratch = onCreateFromScratch,
                    onEnableAccess = onEnableAccess,
                )
            }

            if (!isFirstRun) {
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
}

/** The state-dependent section: first-run checklist, recurring suggestions, or nothing. */
@Composable
private fun HomeSectionContent(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    onBrowseTemplates: () -> Unit,
    onCreateFromTemplate: (RuleTemplateInfo) -> Unit,
    onCreateFromScratch: () -> Unit,
    onEnableAccess: () -> Unit,
) {
    when (val section = uiState.section) {
        is HomeSection.StarterRules -> GetStartedSection(
            monitoring = uiState.monitoring,
            templates = section.templates,
            onEnableAccess = onEnableAccess,
            onManageApps = { navigateTo(Routes.appSelection(), null) },
            onCreateFromTemplate = onCreateFromTemplate,
            onCreateFromScratch = onCreateFromScratch,
            onSeeMoreTemplates = onBrowseTemplates,
        )

        is HomeSection.Recurring -> RecurringSuggestionsSection(
            suggestions = section.suggestions,
            onCreateFromSuggestion = { onEvent(HomeEvent.OnCreateRuleFromSuggestion(it)) },
            onSkipSimilar = { onEvent(HomeEvent.OnSkipSimilar(it)) },
        )

        HomeSection.None -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

@Composable
private fun MonitoringStatusBanner(
    monitoring: MonitoringStatus,
    onManageApps: () -> Unit,
    onEnableAccess: () -> Unit,
) {
    TonalCard(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(
                onClickLabel = stringResource(
                    if (monitoring.isListenerEnabled) R.string.home_banner_manage_apps else R.string.home_banner_enable_access,
                ),
                onClick = if (monitoring.isListenerEnabled) onManageApps else onEnableAccess,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (monitoring.isListenerEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
            Text(
                text = if (monitoring.isListenerEnabled) monitoringSummary(monitoring) else stringResource(R.string.home_banner_inactive),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "3 apps monitored · 2 rules"; the rules part is omitted until a rule exists (the starter section covers that). */
@Composable
private fun monitoringSummary(monitoring: MonitoringStatus): String {
    val apps = pluralStringResource(R.plurals.home_banner_apps_monitored, monitoring.monitoredAppCount, monitoring.monitoredAppCount)
    if (monitoring.ruleCount == 0) return apps
    val rules = pluralStringResource(R.plurals.home_banner_rules, monitoring.ruleCount, monitoring.ruleCount)
    return "$apps · $rules"
}

@Composable
internal fun SectionEyebrow(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
internal fun StarterTemplates(
    templates: ImmutableList<RuleTemplateInfo>,
    onCreateFromTemplate: (RuleTemplateInfo) -> Unit,
    onCreateFromScratch: () -> Unit,
    onSeeMoreTemplates: () -> Unit,
    cardStyle: Style = Style,
    cardShape: Shape = MaterialTheme.shapes.large,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        templates.forEach { template ->
            RuleTemplateCard(
                category = template.category,
                name = template.name,
                description = template.description,
                onClick = { onCreateFromTemplate(template) },
                style = cardStyle,
                shape = cardShape,
                descriptionMaxLines = 2,
            )
        }
        // Templates are the recommended path for newcomers: "see more" stays a quiet link right
        // under them, and building from scratch is the visually secondary, advanced action.
        TextButton(
            onClick = onSeeMoreTemplates,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        ) {
            Icon(imageVector = Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.home_see_more_templates),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        OutlinedButton(onClick = onCreateFromScratch, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.home_create_rule_from_scratch),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun RecurringSuggestionsSection(
    suggestions: ImmutableList<RecurringSuggestionUi>,
    onCreateFromSuggestion: (RecurringSuggestionUi) -> Unit,
    onSkipSimilar: (RecurringSuggestionUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionEyebrow(text = stringResource(R.string.home_recurring_title))
            Text(
                text = stringResource(R.string.home_recurring_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        suggestions.forEach { suggestion ->
            TonalCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppLetterAvatar(appName = suggestion.appName)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = suggestion.appName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = suggestion.sampleTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        suggestion.sampleContent?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onCreateFromSuggestion(suggestion) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            ) {
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
}

@Composable
private fun AppLetterAvatar(appName: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = appName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun WeekStatsRow(weekStats: WeekStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionEyebrow(text = stringResource(R.string.home_this_week_title))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(label = stringResource(R.string.home_stat_records), value = weekStats.records, modifier = Modifier.weight(1f))
            StatTile(label = stringResource(R.string.home_stat_rules_fired), value = weekStats.rulesFired, modifier = Modifier.weight(1f))
            StatTile(label = stringResource(R.string.home_stat_apps_active), value = weekStats.appsActive, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    TonalCard(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionEyebrow(text = stringResource(R.string.home_recent_activity_title))
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
        }
        if (recentActivity.isEmpty()) {
            Text(text = stringResource(R.string.home_recent_activity_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            TonalCard {
                recentActivity.forEachIndexed { index, activity ->
                    RecentActivityRow(activity = activity, onClick = { onRowClick(activity.notificationId) })
                    if (index != recentActivity.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivityRow(activity: RecentActivityUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = activity.ruleName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val detail = listOfNotNull(activity.subtitle, activity.appName).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val previewMonitoring = MonitoringStatus(isListenerEnabled = true, monitoredAppCount = 3, ruleCount = 0)

private val previewActivity = persistentListOf(
    RecentActivityUi(
        notificationId = "1",
        ruleName = "Dismiss spam",
        title = "You have a new message",
        subtitle = null,
        appName = "Messaging App",
    ),
)

@Preview(name = "First run", showBackground = true)
@Preview(name = "First run (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenFirstRunPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = previewMonitoring,
                section = HomeSection.StarterRules(RuleTemplates.all.take(2).toImmutableList()),
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "First run, access off", showBackground = true)
@Composable
private fun HomeScreenFirstRunAccessOffPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = MonitoringStatus(isListenerEnabled = false, monitoredAppCount = 0, ruleCount = 0),
                section = HomeSection.StarterRules(RuleTemplates.all.take(2).toImmutableList()),
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "Active", showBackground = true)
@Preview(name = "Active (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenActivePreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = previewMonitoring.copy(ruleCount = 5),
                weekStats = WeekStats(records = 42, rulesFired = 12, appsActive = 3),
                recentActivity = previewActivity,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}
