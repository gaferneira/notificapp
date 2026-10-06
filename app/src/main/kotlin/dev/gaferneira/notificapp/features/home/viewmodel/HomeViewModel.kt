package dev.gaferneira.notificapp.features.home.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.di.HomeDataSources
import dev.gaferneira.notificapp.core.notification.RecurringNotificationSuggester
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.RecentActivity
import dev.gaferneira.notificapp.domain.model.RecurringSuggestion
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.home.contract.HomeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.features.home.contract.HomeUiState
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import dev.gaferneira.notificapp.features.home.contract.RecentActivityUi
import dev.gaferneira.notificapp.features.home.contract.RecurringSuggestionUi
import dev.gaferneira.notificapp.features.home.contract.WeekStats
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dataSources: HomeDataSources,
    private val recurringNotificationSuggester: RecurringNotificationSuggester,
    private val listenerStatus: NotificationListenerStatusProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<HomeUiState, HomeEvent, HomeEffect>(HomeUiState()) {

    private val ruleRepository = dataSources.ruleRepository
    private val selectedAppRepository = dataSources.selectedAppRepository
    private val ruleExecutionRepository = dataSources.ruleExecutionRepository
    private val notificationRepository = dataSources.notificationRepository
    private val suggestionDismissalRepository = dataSources.suggestionDismissalRepository

    private val isListenerEnabled = MutableStateFlow(false)
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private var observeJob: Job? = null

    /** Time source for the stats/suggestion windows; replaceable in tests (kept out of the Hilt constructor). */
    internal var clock: Clock = Clock.system(zoneId)

    /** Day the current time windows were computed for; a change triggers a re-observe on resume. */
    private var windowsDay: LocalDate? = null

    init {
        checkListenerStatus()
        observeHome()
    }

    override fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OnResume -> {
                checkListenerStatus()
                if (windowsDay != LocalDate.now(clock)) observeHome()
            }
            HomeEvent.OnRetry -> retry()
            HomeEvent.OnCreateRuleFromScratch -> sendEffect(HomeEffect.NavigateToRuleEditor())
            is HomeEvent.OnCreateRuleFromSuggestion -> sendEffect(
                HomeEffect.NavigateToRuleEditor(notificationId = event.suggestion.sampleNotificationId),
            )
            is HomeEvent.OnSkipSimilar -> onSkipSimilar(event.suggestion)
            is HomeEvent.OnRecentActivityClick -> sendEffect(HomeEffect.NavigateToNotificationDetail(event.notificationId))
            HomeEvent.OnSeeAllActivity -> sendEffect(HomeEffect.NavigateToInbox())
            HomeEvent.OnResumeMonitoring -> resumeMonitoring()
            HomeEvent.OnCapturedClick -> sendEffect(HomeEffect.NavigateToInbox(NotificationStatusFilter.ALL))
            HomeEvent.OnRulesFiredClick -> sendEffect(HomeEffect.NavigateToInbox(NotificationStatusFilter.PROCESSED))
        }
    }

    private fun retry() {
        setState { copy(isLoading = true, hasError = false) }
        observeHome()
    }

    private fun observeHome() {
        observeJob?.cancel()
        val today = LocalDate.now(clock)
        windowsDay = today
        val now = Instant.now(clock)
        // Rolling window (not calendar week) so Monday morning never resets the tiles to zero; matches RuleDetails.
        val statsWindowStart = now.minusSeconds(STATS_WINDOW_DAYS * SECONDS_PER_DAY).toEpochMilli()
        val suggestionWindowStart = now.minusSeconds(SUGGESTION_WINDOW_DAYS * SECONDS_PER_DAY).toEpochMilli()
        val rulesFlow = ruleRepository.observeAllRules()

        val statusFlow = combine(
            rulesFlow,
            selectedAppRepository.observeEnabledApps(),
            isListenerEnabled,
            userPreferencesRepository.observeMonitoringPaused(),
        ) { rules, enabledApps, listenerEnabled, paused ->
            HomeStatus(rules, enabledApps, listenerEnabled, paused)
        }

        val statsFlow = observeStats(statsWindowStart)

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

        observeJob = viewModelScope.launch {
            combine(statusFlow, statsFlow, suggestionsFlow) {
                    status,
                    stats,
                    suggestions,
                ->
                buildState(
                    HomeStateParams(
                        ruleCount = status.rules.size,
                        monitoredAppCount = status.enabledApps.size,
                        listenerEnabled = status.listenerEnabled,
                        paused = status.paused,
                        rulesFired = stats.rulesFired,
                        recentActivity = stats.recentActivity,
                        capturedCount = stats.capturedCount,
                        suggestions = suggestions,
                    ),
                )
            }.catch { e ->
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to observe home data")
                setState { copy(isLoading = false, hasError = true) }
            }.collectLatest { newState ->
                setState { newState }
            }
        }
    }

    private fun observeStats(since: Long) = combine(
        ruleExecutionRepository.observeExecutionCountSince(since),
        ruleExecutionRepository.observeRecentActivity(RECENT_ACTIVITY_LIMIT),
        notificationRepository.observeCountSince(since),
    ) { rulesFired, recentActivity, capturedCount ->
        HomeStats(rulesFired, recentActivity, capturedCount)
    }

    private fun buildState(params: HomeStateParams): HomeUiState = buildHomeUiState(params)

    private fun onSkipSimilar(suggestion: RecurringSuggestionUi) {
        viewModelScope.launch {
            suggestionDismissalRepository.dismiss(suggestion.packageName, suggestion.normalizedTitleKey)
                .onFailure { e ->
                    Timber.e(e, "Failed to dismiss suggestion: ${suggestion.packageName} / ${suggestion.normalizedTitleKey}")
                    sendEffect(HomeEffect.ShowError(UiText.StringResource(R.string.home_error_dismiss_suggestion)))
                }
        }
    }

    private fun resumeMonitoring() {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setMonitoringPaused(false)
                .onFailure { e -> Timber.e(e, "Failed to resume monitoring") }
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
        private const val STATS_WINDOW_DAYS = 7L
        private const val SUGGESTION_WINDOW_DAYS = 14L
        private const val SUGGESTION_SCAN_LIMIT = 500
        private const val SECONDS_PER_DAY = 86_400L
    }
}

// File-scoped so it's reachable from the top-level buildHomeUiState() below, which lives outside
// the class (and therefore outside the companion object's visibility) for testability.
private const val STARTER_TEMPLATE_COUNT = 3

private fun RecurringSuggestion.toUi(): RecurringSuggestionUi = RecurringSuggestionUi(
    packageName = packageName,
    appName = appName,
    normalizedTitleKey = normalizedTitleKey,
    sampleTitle = sampleTitle,
    sampleContent = sampleContent,
    sampleNotificationId = sampleNotificationId,
    occurrences = occurrences,
    coverage = coverage,
)

private fun RecentActivity.toUi(): RecentActivityUi = RecentActivityUi(
    notificationId = notificationId,
    ruleName = ruleName,
    title = notificationTitle,
    subtitle = notificationContent,
    packageName = packageName,
    appName = appName,
    executedAt = executedAt,
)

private data class HomeStatus(
    val rules: List<Rule>,
    val enabledApps: List<SelectedApp>,
    val listenerEnabled: Boolean,
    val paused: Boolean,
)

private data class HomeStats(
    val rulesFired: Int,
    val recentActivity: List<RecentActivity>,
    val capturedCount: Int,
)

private data class HomeStateParams(
    val ruleCount: Int,
    val monitoredAppCount: Int,
    val listenerEnabled: Boolean,
    val paused: Boolean,
    val rulesFired: Int,
    val recentActivity: List<RecentActivity>,
    val capturedCount: Int,
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
            isPaused = params.paused,
            monitoredAppCount = params.monitoredAppCount,
            ruleCount = params.ruleCount,
        ),
        weekStats = WeekStats(
            captured = params.capturedCount,
            rulesFired = params.rulesFired,
        ),
        recentActivity = params.recentActivity.map { it.toUi() }.toImmutableList(),
        section = section,
        isLoading = false,
    )
}
