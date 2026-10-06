package dev.gaferneira.notificapp.features.home.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.style.Style
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplateInfo
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.core.ui.components.AppIcon
import dev.gaferneira.notificapp.core.ui.components.IconBadge
import dev.gaferneira.notificapp.core.ui.components.RuleTemplateCard
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.OnResumeEffect
import dev.gaferneira.notificapp.domain.model.RuleCoverage
import dev.gaferneira.notificapp.features.home.contract.HomeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.features.home.contract.HomeUiState
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import dev.gaferneira.notificapp.features.home.contract.RecentActivityUi
import dev.gaferneira.notificapp.features.home.contract.RecurringSuggestionUi
import dev.gaferneira.notificapp.features.home.contract.WeekStats
import dev.gaferneira.notificapp.features.home.viewmodel.HomeViewModel
import dev.gaferneira.notificapp.util.isIgnoringBatteryOptimizations
import dev.gaferneira.notificapp.util.openBatteryOptimizationSettings
import dev.gaferneira.notificapp.util.openNotificationListenerSettings
import dev.gaferneira.notificapp.util.timeAgo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.Date

@Composable
fun HomeScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Platform state read in the UI layer (keeps PowerManager out of the ViewModel); refreshed on every resume
    // because the user toggles it in system settings and comes back.
    var isBatteryOptimized by remember { mutableStateOf(false) }

    OnResumeEffect {
        isBatteryOptimized = !isIgnoringBatteryOptimizations(context)
        viewModel.onEvent(HomeEvent.OnResume)
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.NavigateToRuleEditor ->
                navigateTo(Routes.ruleEditor(ruleId = effect.ruleId, notificationId = effect.notificationId), null)

            is HomeEffect.NavigateToNotificationDetail ->
                navigateTo(Routes.notificationDetails(effect.notificationId), null)

            is HomeEffect.NavigateToInbox -> navigateTo(Routes.inbox(initialStatus = effect.initialStatus), null)

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
        isBatteryOptimized = isBatteryOptimized,
        onOpenBatterySettings = { openBatteryOptimizationSettings(context) },
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
    isBatteryOptimized: Boolean = false,
    onOpenBatterySettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { HomeTopBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { MainBottomNav(selectedDestination = AppDestinations.HOME, navigateTo = navigateTo) },
    ) { innerPadding ->
        // No content until the first emission, or the initial state flashes a misleading access-off banner.
        if (uiState.isLoading) {
            HomeLoadingState(modifier = Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }
        if (uiState.hasError) {
            HomeErrorState(
                onRetry = { onEvent(HomeEvent.OnRetry) },
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            )
            return@Scaffold
        }

        // First run: the checklist is the whole story, so hide the empty stats and activity below it.
        val isFirstRun = uiState.isFirstRun()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            statusItems(
                uiState = uiState,
                showBatteryHint = isBatteryOptimized && !isFirstRun,
                navigateTo = navigateTo,
                actions = StatusActions(
                    onEnableAccess = onEnableAccess,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onResumeMonitoring = { onEvent(HomeEvent.OnResumeMonitoring) },
                ),
            )

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

            if (!isFirstRun) activityItems(uiState, onEvent)
        }
    }
}

private fun LazyListScope.activityItems(uiState: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    item {
        WeekStatsRow(
            weekStats = uiState.weekStats,
            actions = StatTileActions(
                onCapturedClick = { onEvent(HomeEvent.OnCapturedClick) },
                onRulesFiredClick = { onEvent(HomeEvent.OnRulesFiredClick) },
            ),
        )
    }
    item {
        RecentActivitySection(
            recentActivity = uiState.recentActivity,
            onRowClick = { onEvent(HomeEvent.OnRecentActivityClick(it)) },
            onSeeAll = { onEvent(HomeEvent.OnSeeAllActivity) },
        )
    }
}

private class StatusActions(
    val onEnableAccess: () -> Unit,
    val onOpenBatterySettings: () -> Unit,
    val onResumeMonitoring: () -> Unit,
)

/** Monitoring banner and battery hint; the first-run checklist already reports access/apps status itself. */
private fun LazyListScope.statusItems(
    uiState: HomeUiState,
    showBatteryHint: Boolean,
    navigateTo: (Screen, NavOptions?) -> Unit,
    actions: StatusActions,
) {
    if (uiState.section !is HomeSection.StarterRules) {
        item {
            MonitoringStatusBanner(
                monitoring = uiState.monitoring,
                onResumeMonitoring = actions.onResumeMonitoring,
                onManageApps = { navigateTo(Routes.appSelection(), null) },
                onEnableAccess = actions.onEnableAccess,
            )
        }
    }
    if (showBatteryHint && uiState.monitoring.isListenerEnabled) {
        item { BatteryHintCard(onOpenSettings = actions.onOpenBatterySettings) }
    }
}

@Composable
private fun HomeLoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_loading_description)
    Box(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun HomeErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.home_error_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.home_error_retry))
        }
    }
}

@Composable
private fun BatteryHintCard(onOpenSettings: () -> Unit) {
    TonalCard(style = NotificappStyles.warningCardStyle) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.home_battery_hint_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.home_battery_hint_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.home_battery_hint_button))
            }
        }
    }
}

private fun HomeUiState.isFirstRun(): Boolean = section is HomeSection.StarterRules && weekStats == WeekStats() && recentActivity.isEmpty()

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
                    imageVector = Icons.Outlined.NotificationsActive,
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
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun MonitoringStatusBanner(
    monitoring: MonitoringStatus,
    onResumeMonitoring: () -> Unit,
    onManageApps: () -> Unit,
    onEnableAccess: () -> Unit,
) {
    when {
        monitoring.isPaused -> PausedBanner(onResume = onResumeMonitoring)
        !monitoring.isListenerEnabled -> AccessOffBanner(onEnableAccess = onEnableAccess)
        monitoring.monitoredAppCount == 0 -> NoAppsBanner(onManageApps = onManageApps)
        else -> MonitoringHeroCard(monitoring = monitoring, onManageApps = onManageApps)
    }
}

@Composable
private fun AccessOffBanner(onEnableAccess: () -> Unit) {
    WarningBanner(
        message = stringResource(R.string.home_banner_access_off_message),
        description = stringResource(R.string.home_banner_access_off_description),
        buttonText = stringResource(R.string.home_banner_enable_access_button),
        onClick = onEnableAccess,
    )
}

/** Monitoring is globally paused: nothing is captured until the user resumes. */
@Composable
private fun PausedBanner(onResume: () -> Unit) {
    val message = stringResource(R.string.home_banner_monitoring_paused)
    WarningBanner(
        message = message,
        description = message,
        buttonText = stringResource(R.string.home_banner_monitoring_paused_resume),
        onClick = onResume,
    )
}

/** Access is on but no app is selected, so nothing would ever be captured. */
@Composable
private fun NoAppsBanner(onManageApps: () -> Unit) {
    WarningBanner(
        message = stringResource(R.string.home_banner_no_apps_message),
        description = stringResource(R.string.home_banner_no_apps_description),
        buttonText = stringResource(R.string.home_banner_no_apps_button),
        onClick = onManageApps,
    )
}

@Composable
private fun WarningBanner(message: String, description: String, buttonText: String, onClick: () -> Unit) {
    TonalCard(style = NotificappStyles.warningCardStyle) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(buttonText)
            }
        }
    }
}

/** Hero status card: the healthy, monitoring state as the screen's headline. */
@Composable
private fun MonitoringHeroCard(monitoring: MonitoringStatus, onManageApps: () -> Unit) {
    TonalCard(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_banner_manage_apps),
                onClick = onManageApps,
            ),
        style = NotificappStyles.heroCardStyle,
    ) {
        // The row is clickable as a whole and merged into one TalkBack sentence ("Monitoring active, 3 apps ...").
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.size(56.dp),
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                iconSize = 32.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.home_banner_monitoring_active),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = monitoringSummary(monitoring),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
        text = text.uppercase(Locale.current.platformLocale),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.semantics { heading() },
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
                category = stringResource(template.categoryRes),
                name = stringResource(template.nameRes),
                description = stringResource(template.descriptionRes),
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
            SuggestionCard(
                suggestion = suggestion,
                onCreate = { onCreateFromSuggestion(suggestion) },
                onSkip = { onSkipSimilar(suggestion) },
            )
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: RecurringSuggestionUi, onCreate: () -> Unit, onSkip: () -> Unit) {
    TonalCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(packageName = suggestion.packageName, appName = suggestion.appName)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = suggestion.appName.uppercase(Locale.current.platformLocale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = suggestion.sampleTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    suggestion.sampleContent?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = pluralStringResource(R.plurals.home_suggestion_seen_times, suggestion.occurrences, suggestion.occurrences),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.home_skip_similar)) }
                FilledTonalButton(onClick = onCreate) { Text(stringResource(R.string.home_create_rule_from_this)) }
            }
        }
    }
}

private class StatTileActions(
    val onCapturedClick: () -> Unit,
    val onRulesFiredClick: () -> Unit,
)

@Composable
private fun WeekStatsRow(weekStats: WeekStats, actions: StatTileActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionEyebrow(text = stringResource(R.string.home_last_7_days_title))
        if (weekStats.rulesFired > 0) {
            Text(
                text = pluralStringResource(R.plurals.home_week_summary, weekStats.rulesFired, weekStats.rulesFired),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val tileModifier = Modifier.weight(1f).fillMaxHeight()
            StatTile(
                icon = Icons.Outlined.Inbox,
                label = stringResource(R.string.home_stat_captured),
                value = weekStats.captured,
                onClickLabel = stringResource(R.string.home_stat_captured_open),
                onClick = actions.onCapturedClick,
                modifier = tileModifier,
            )
            StatTile(
                icon = Icons.Outlined.Bolt,
                label = stringResource(R.string.home_stat_rules_fired),
                value = weekStats.rulesFired,
                onClickLabel = stringResource(R.string.home_stat_rules_fired_open),
                onClick = actions.onRulesFiredClick,
                modifier = tileModifier,
            )
        }
    }
}

/** A navigation tile: icon badge, big number and label, with a chevron so it reads as tappable. */
@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: Int,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalCard(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, style = NotificappStyles.iconBadgeSubtleStyle, modifier = Modifier.size(32.dp), iconSize = 18.dp)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
            RecentActivityEmptyState()
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
private fun RecentActivityEmptyState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(
                text = stringResource(R.string.home_recent_activity_empty),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_recent_activity_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentActivityRow(activity: RecentActivityUi, onClick: () -> Unit) {
    val locale = Locale.current.platformLocale
    val relativeTime = remember(activity.executedAt, locale) { Date(activity.executedAt).timeAgo(locale = locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_recent_activity_open),
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = activity.packageName, appName = activity.appName)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activity.ruleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val detail = listOfNotNull(activity.subtitle, activity.appName).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
        packageName = "com.example.messaging",
        appName = "Messaging App",
        executedAt = System.currentTimeMillis() - PREVIEW_ACTIVITY_AGE_MILLIS,
    ),
)

private const val PREVIEW_ACTIVITY_AGE_MILLIS = 5 * 60 * 1000L

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
                weekStats = WeekStats(captured = 42, rulesFired = 12),
                recentActivity = previewActivity,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

private val previewSuggestions = persistentListOf(
    RecurringSuggestionUi(
        packageName = "com.example.bank",
        appName = "Bank App",
        normalizedTitleKey = "card purchase",
        sampleTitle = "Card purchase approved",
        sampleContent = "You spent 12.50 at Coffee Corner. Remaining balance and a very long trailing description follow here.",
        sampleNotificationId = "n1",
        occurrences = 7,
        coverage = RuleCoverage.UNCOVERED,
    ),
)

@Preview(name = "Access off", showBackground = true)
@Preview(name = "Access off (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenAccessOffPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = MonitoringStatus(isListenerEnabled = false, monitoredAppCount = 3, ruleCount = 2),
                weekStats = WeekStats(captured = 42, rulesFired = 12),
                recentActivity = previewActivity,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "Active with suggestions", showBackground = true)
@Preview(name = "Active with suggestions (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenSuggestionsPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = previewMonitoring.copy(ruleCount = 2),
                section = HomeSection.Recurring(previewSuggestions),
                weekStats = WeekStats(captured = 42, rulesFired = 1),
                recentActivity = previewActivity,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "Active, empty activity", showBackground = true)
@Preview(name = "Active, empty activity (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenEmptyActivityPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = previewMonitoring.copy(ruleCount = 2),
                weekStats = WeekStats(captured = 5),
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "Loading", showBackground = true)
@Preview(name = "Loading (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenLoadingPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(uiState = HomeUiState(), onEvent = {}, navigateTo = { _, _ -> }, onBrowseTemplates = {})
    }
}

@Preview(name = "Error", showBackground = true)
@Preview(name = "Error (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenErrorPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(isLoading = false, hasError = true),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "No apps monitored", showBackground = true)
@Preview(name = "No apps monitored (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenNoAppsPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = previewMonitoring.copy(monitoredAppCount = 0, ruleCount = 2),
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "Battery hint", showBackground = true)
@Preview(name = "Battery hint (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenBatteryHintPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = previewMonitoring.copy(ruleCount = 5),
                weekStats = WeekStats(captured = 42, rulesFired = 12),
                recentActivity = previewActivity,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
            isBatteryOptimized = true,
        )
    }
}

private val previewLongActivity = RecentActivityUi(
    notificationId = "long",
    ruleName = "Descartar notificaciones promocionales repetidas de la aplicación bancaria",
    title = "Compra con tarjeta aprobada",
    subtitle = "Pagaste 12,50 en Coffee Corner. El saldo restante y una descripción final muy larga continúan aquí",
    packageName = "com.example.bank",
    appName = "Aplicación bancaria de ejemplo",
    executedAt = System.currentTimeMillis() - PREVIEW_ACTIVITY_AGE_MILLIS,
)

@Preview(name = "Large font", showBackground = true, fontScale = 2f)
@Composable
private fun HomeScreenLargeFontPreview() {
    NotificappTheme(dynamicColor = false) {
        HomeScreenContent(
            uiState = HomeUiState(
                monitoring = previewMonitoring.copy(ruleCount = 5),
                weekStats = WeekStats(captured = 42, rulesFired = 12),
                recentActivity = previewActivity,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
            onBrowseTemplates = {},
        )
    }
}

@Preview(name = "Long text", showBackground = true)
@Composable
private fun LongTextRowsPreview() {
    NotificappTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TonalCard { RecentActivityRow(activity = previewLongActivity, onClick = {}) }
            SuggestionCard(
                suggestion = RecurringSuggestionUi(
                    packageName = "com.example.bank",
                    appName = "Aplicación bancaria de ejemplo con nombre largo",
                    normalizedTitleKey = "compra",
                    sampleTitle = "Compra con tarjeta aprobada en un comercio con un nombre extremadamente largo",
                    sampleContent = previewLongActivity.subtitle,
                    sampleNotificationId = "n2",
                    occurrences = 7,
                    coverage = RuleCoverage.UNCOVERED,
                ),
                onCreate = {},
                onSkip = {},
            )
        }
    }
}
