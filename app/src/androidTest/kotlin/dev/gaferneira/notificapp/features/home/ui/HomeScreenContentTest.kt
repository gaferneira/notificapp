package dev.gaferneira.notificapp.features.home.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.features.home.contract.HomeUiState
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import dev.gaferneira.notificapp.features.home.contract.WeekStats
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test

/** Stateless [HomeScreenContent] states; strings are resolved from resources so the tests are locale-agnostic. */
class HomeScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun setContent(
        uiState: HomeUiState,
        onEvent: (HomeEvent) -> Unit = {},
        onEnableAccess: () -> Unit = {},
    ) {
        composeRule.setContent {
            NotificappTheme(dynamicColor = false) {
                HomeScreenContent(
                    uiState = uiState,
                    onEvent = onEvent,
                    navigateTo = { _, _ -> },
                    onBrowseTemplates = {},
                    onEnableAccess = onEnableAccess,
                )
            }
        }
    }

    private val activeMonitoring = MonitoringStatus(isListenerEnabled = true, monitoredAppCount = 3, ruleCount = 2)

    @Test
    fun loading_showsLoadingIndicator() {
        setContent(HomeUiState(isLoading = true))

        composeRule.onNodeWithContentDescription(string(R.string.home_loading_description)).assertIsDisplayed()
    }

    @Test
    fun error_showsRetryAndInvokesCallback() {
        val events = mutableListOf<HomeEvent>()
        setContent(HomeUiState(isLoading = false, hasError = true), onEvent = { events += it })

        composeRule.onNodeWithText(string(R.string.home_error_retry)).assertIsDisplayed().performClick()

        events shouldContainExactly listOf(HomeEvent.OnRetry)
    }

    @Test
    fun accessOff_showsEnableAccessCta() {
        var enableClicks = 0
        setContent(
            HomeUiState(
                monitoring = activeMonitoring.copy(isListenerEnabled = false),
                weekStats = WeekStats(records = 4, rulesFired = 1),
                isLoading = false,
            ),
            onEnableAccess = { enableClicks++ },
        )

        composeRule.onNodeWithText(string(R.string.home_banner_enable_access_button)).assertIsDisplayed().performClick()

        check(enableClicks == 1) { "Expected one enable-access click, got $enableClicks" }
    }

    @Test
    fun zeroMonitoredApps_showsNoAppsWarning() {
        setContent(
            HomeUiState(
                monitoring = activeMonitoring.copy(monitoredAppCount = 0),
                weekStats = WeekStats(records = 4, rulesFired = 1),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText(string(R.string.home_banner_no_apps_button)).assertIsDisplayed()
    }

    @Test
    fun firstRun_showsChecklistAndHidesFab() {
        setContent(
            HomeUiState(
                monitoring = MonitoringStatus(isListenerEnabled = false, monitoredAppCount = 0, ruleCount = 0),
                section = HomeSection.StarterRules(RuleTemplates.all.take(2).toImmutableList()),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText(string(R.string.home_checklist_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_new_rule)).assertDoesNotExist()
    }

    @Test
    fun noRecentActivity_showsEmptyState() {
        setContent(
            HomeUiState(
                monitoring = activeMonitoring,
                weekStats = WeekStats(records = 4, rulesFired = 1),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText(string(R.string.home_recent_activity_empty)).assertIsDisplayed()
    }
}
