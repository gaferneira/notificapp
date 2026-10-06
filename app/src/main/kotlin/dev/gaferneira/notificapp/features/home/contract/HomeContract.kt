package dev.gaferneira.notificapp.features.home.contract

import androidx.compose.runtime.Immutable
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplateInfo
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.RuleCoverage
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class HomeUiState(
    val monitoring: MonitoringStatus = MonitoringStatus(),
    val weekStats: WeekStats = WeekStats(),
    val recentActivity: ImmutableList<RecentActivityUi> = persistentListOf(),
    val section: HomeSection = HomeSection.None,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

@Immutable
data class MonitoringStatus(
    val isListenerEnabled: Boolean = false,
    val isPaused: Boolean = false,
    val monitoredAppCount: Int = 0,
    val ruleCount: Int = 0,
)

/** Rolling last-7-days counters behind the Home stat tiles. */
@Immutable
data class WeekStats(
    val captured: Int = 0,
    val rulesFired: Int = 0,
)

@Immutable
data class RecentActivityUi(
    val notificationId: String,
    val ruleName: String,
    val title: String?,
    val subtitle: String?,
    val packageName: String,
    val appName: String,
    /** When the rule fired (epoch millis), shown as a relative time. */
    val executedAt: Long,
)

sealed interface HomeSection {
    data class StarterRules(val templates: ImmutableList<RuleTemplateInfo>) : HomeSection
    data class Recurring(val suggestions: ImmutableList<RecurringSuggestionUi>) : HomeSection
    data object None : HomeSection
}

@Immutable
data class RecurringSuggestionUi(
    val packageName: String,
    val appName: String,
    val normalizedTitleKey: String,
    val sampleTitle: String,
    val sampleContent: String?,
    val sampleNotificationId: String,
    val occurrences: Int,
    val coverage: RuleCoverage,
)

sealed interface HomeEvent {
    data object OnResume : HomeEvent
    data object OnRetry : HomeEvent
    data object OnCreateRuleFromScratch : HomeEvent
    data class OnCreateRuleFromSuggestion(val suggestion: RecurringSuggestionUi) : HomeEvent
    data class OnSkipSimilar(val suggestion: RecurringSuggestionUi) : HomeEvent
    data class OnRecentActivityClick(val notificationId: String) : HomeEvent
    data object OnSeeAllActivity : HomeEvent
    data object OnResumeMonitoring : HomeEvent
    data object OnCapturedClick : HomeEvent
    data object OnRulesFiredClick : HomeEvent
}

sealed interface HomeEffect {
    data class NavigateToRuleEditor(val ruleId: String? = null, val notificationId: String? = null) : HomeEffect
    data class NavigateToNotificationDetail(val notificationId: String) : HomeEffect

    /** [initialStatus] overrides the saved inbox filter for that visit only; null keeps it. */
    data class NavigateToInbox(val initialStatus: NotificationStatusFilter? = null) : HomeEffect
    data class ShowError(val message: UiText) : HomeEffect
}
