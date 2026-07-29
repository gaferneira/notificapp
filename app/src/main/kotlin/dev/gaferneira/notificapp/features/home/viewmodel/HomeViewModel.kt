package dev.gaferneira.notificapp.features.home.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.di.HomeDataSources
import dev.gaferneira.notificapp.core.notification.RecurringNotificationSuggester
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec.withFreshIdentityForImport
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.RecentActivity
import dev.gaferneira.notificapp.domain.model.RecurringSuggestion
import dev.gaferneira.notificapp.features.home.contract.HomeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.features.home.contract.HomeUiState
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import dev.gaferneira.notificapp.features.home.contract.RecentActivityUi
import dev.gaferneira.notificapp.features.home.contract.RecurringSuggestionUi
import dev.gaferneira.notificapp.features.home.contract.WeekStats
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dataSources: HomeDataSources,
    private val recurringNotificationSuggester: RecurringNotificationSuggester,
    private val listenerStatus: NotificationListenerStatusProvider,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<HomeUiState, HomeEvent, HomeEffect>(HomeUiState()) {

    private val ruleRepository = dataSources.ruleRepository
    private val selectedAppRepository = dataSources.selectedAppRepository
    private val ruleExecutionRepository = dataSources.ruleExecutionRepository
    private val notificationRepository = dataSources.notificationRepository
    private val suggestionDismissalRepository = dataSources.suggestionDismissalRepository

    private val isListenerEnabled = MutableStateFlow(false)
    private val zoneId: ZoneId = ZoneId.systemDefault()

    init {
        checkListenerStatus()
        observeHome()
    }

    override fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OnResume -> checkListenerStatus()
            is HomeEvent.OnRuleTemplateTextReceived -> onRuleTemplateTextReceived(event.text)
            HomeEvent.OnCreateRuleFromScratch -> sendEffect(HomeEffect.NavigateToRuleEditor())
            // No dedicated "all templates" screen exists yet; RuleTemplatePickerSheet already
            // surfaces every template, so there's nowhere else to navigate to.
            HomeEvent.OnSeeMoreTemplates -> Unit
            is HomeEvent.OnCreateRuleFromSuggestion -> sendEffect(
                HomeEffect.NavigateToRuleEditor(notificationId = event.suggestion.sampleNotificationId),
            )
            is HomeEvent.OnSkipSimilar -> onSkipSimilar(event.suggestion)
            is HomeEvent.OnRecentActivityClick -> sendEffect(HomeEffect.NavigateToNotificationDetail(event.notificationId))
            HomeEvent.OnSeeAllActivity -> sendEffect(HomeEffect.NavigateToInbox)
        }
    }

    private fun observeHome() {
        val weekStart = startOfCurrentWeekMillis(zoneId)
        val suggestionWindowStart = Instant.now().minusSeconds(SUGGESTION_WINDOW_DAYS * SECONDS_PER_DAY).toEpochMilli()
        val rulesFlow = ruleRepository.observeAllRules()

        val statusFlow = combine(
            rulesFlow,
            selectedAppRepository.observeEnabledApps(),
            isListenerEnabled,
        ) { rules, enabledApps, listenerEnabled ->
            Triple(rules, enabledApps, listenerEnabled)
        }

        val statsFlow = combine(
            ruleExecutionRepository.observeExecutionCountSince(weekStart),
            notificationRepository.observeActiveAppCountSince(weekStart),
            ruleExecutionRepository.observeRecentActivity(RECENT_ACTIVITY_LIMIT),
            notificationRepository.observeCountSince(weekStart),
        ) { rulesFired, appsActive, recentActivity, recordsCount ->
            HomeStats(rulesFired, appsActive, recentActivity, recordsCount)
        }

        val suggestionsFlow = combine(
            notificationRepository.observeRecentSince(suggestionWindowStart, SUGGESTION_SCAN_LIMIT),
            suggestionDismissalRepository.observeDismissals(),
            rulesFlow,
        ) { recentNotifications, dismissals, allRules ->
            recurringNotificationSuggester.suggest(
                notifications = recentNotifications,
                activeRules = allRules.filter { it.isActive },
                dismissals = dismissals,
                zoneId = zoneId,
            )
        }

        viewModelScope.launch {
            combine(statusFlow, statsFlow, suggestionsFlow) {
                    (rules, enabledApps, listenerEnabled),
                    stats,
                    suggestions,
                 ->
                buildState(
                    HomeStateParams(
                        ruleCount = rules.size,
                        monitoredAppCount = enabledApps.size,
                        listenerEnabled = listenerEnabled,
                        rulesFired = stats.rulesFired,
                        appsActive = stats.appsActive,
                        recentActivity = stats.recentActivity,
                        recordsCount = stats.recordsCount,
                        suggestions = suggestions,
                    ),
                )
            }.collectLatest { newState ->
                setState { newState }
            }
        }
    }

    private fun buildState(params: HomeStateParams): HomeUiState = buildHomeUiState(params)

    private fun onRuleTemplateTextReceived(text: String) {
        viewModelScope.launch {
            RuleJsonCodec.decode(text)
                .onSuccess { result ->
                    val rule = result.rule.withFreshIdentityForImport()
                    ruleRepository.saveRule(rule)
                        .onSuccess {
                            sendEffect(HomeEffect.NavigateToRuleEditor(ruleId = rule.id))
                        }
                        .onFailure { e ->
                            Timber.e(e, "Failed to save rule from template")
                            sendEffect(HomeEffect.ShowError(UiText.StringResource(R.string.home_error_create_rule_from_template)))
                        }
                }
                .onFailure { e ->
                    Timber.w(e, "Failed to decode rule template")
                    sendEffect(HomeEffect.ShowError(UiText.StringResource(R.string.home_error_create_rule_from_template)))
                }
        }
    }

    private fun onSkipSimilar(suggestion: RecurringSuggestionUi) {
        viewModelScope.launch {
            suggestionDismissalRepository.dismiss(suggestion.packageName, suggestion.normalizedTitleKey)
                .onFailure { e ->
                    Timber.e(e, "Failed to dismiss suggestion: ${suggestion.packageName} / ${suggestion.normalizedTitleKey}")
                    sendEffect(HomeEffect.ShowError(UiText.StringResource(R.string.home_error_dismiss_suggestion)))
                }
        }
    }

    private fun checkListenerStatus() {
        viewModelScope.launch(ioDispatcher) {
            try {
                isListenerEnabled.value = listenerStatus.isEnabled()
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to check notification listener status")
            }
        }
    }

    companion object {
        private const val RECENT_ACTIVITY_LIMIT = 5
        private const val SUGGESTION_WINDOW_DAYS = 14L
        private const val SUGGESTION_SCAN_LIMIT = 500
        private const val SECONDS_PER_DAY = 86_400L
    }
}

// File-scoped so it's reachable from the top-level buildHomeUiState() below, which lives outside
// the class (and therefore outside the companion object's visibility) for testability.
private const val STARTER_TEMPLATE_COUNT = 3

private fun startOfCurrentWeekMillis(zoneId: ZoneId): Long {
    val today = LocalDate.now(zoneId)
    val startOfWeek = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return startOfWeek.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

private fun RecurringSuggestion.toUi(): RecurringSuggestionUi = RecurringSuggestionUi(
    packageName = packageName,
    appName = appName,
    normalizedTitleKey = normalizedTitleKey,
    sampleTitle = sampleTitle,
    sampleNotificationId = sampleNotificationId,
    occurrences = occurrences,
    coverage = coverage,
)

private fun RecentActivity.toUi(): RecentActivityUi = RecentActivityUi(
    notificationId = notificationId,
    ruleName = ruleName,
    title = notificationTitle,
    subtitle = notificationContent,
    appName = appName,
)

private data class HomeStats(
    val rulesFired: Int,
    val appsActive: Int,
    val recentActivity: List<RecentActivity>,
    val recordsCount: Int,
)

private data class HomeStateParams(
    val ruleCount: Int,
    val monitoredAppCount: Int,
    val listenerEnabled: Boolean,
    val rulesFired: Int,
    val appsActive: Int,
    val recentActivity: List<RecentActivity>,
    val recordsCount: Int,
    val suggestions: List<RecurringSuggestion>,
)

private fun buildHomeUiState(params: HomeStateParams): HomeUiState {
    val section = when {
        params.ruleCount == 0 -> HomeSection.StarterRules(RuleTemplates.all.take(STARTER_TEMPLATE_COUNT).toImmutableList())
        params.suggestions.isNotEmpty() -> HomeSection.Recurring(params.suggestions.map { it.toUi() }.toImmutableList())
        else -> HomeSection.None
    }

    return HomeUiState(
        monitoring = MonitoringStatus(
            isListenerEnabled = params.listenerEnabled,
            monitoredAppCount = params.monitoredAppCount,
            ruleCount = params.ruleCount,
        ),
        weekStats = WeekStats(
            records = params.recordsCount,
            rulesFired = params.rulesFired,
            appsActive = params.appsActive,
        ),
        recentActivity = params.recentActivity.map { it.toUi() }.toImmutableList(),
        section = section,
        isLoading = false,
    )
}
