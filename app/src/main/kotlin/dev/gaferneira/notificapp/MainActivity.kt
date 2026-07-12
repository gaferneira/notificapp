package dev.gaferneira.notificapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.core.ui.navigation.NavigationCommand
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Navigator
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.navigation.rememberNavigationState
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.appselection.ui.AppSelectionScreen
import dev.gaferneira.notificapp.features.inbox.ui.InboxScreen
import dev.gaferneira.notificapp.features.notificationdetail.ui.NotificationDetailScreen
import dev.gaferneira.notificapp.features.onboarding.ui.OnboardingScreen
import dev.gaferneira.notificapp.features.ruleeditor.ui.RuleEditorScreen
import dev.gaferneira.notificapp.features.rules.ui.RulesScreen
import dev.gaferneira.notificapp.features.settings.ui.SettingsScreen
import dev.gaferneira.notificapp.util.isNotificationListenerEnabled
import dev.gaferneira.notificapp.util.openNotificationListenerSettings
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var navigationHandler: NavigationHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Notificapp(
                        navigationHandler = navigationHandler,
                    )
                }
            }
        }
    }
}

/**
 * App flow states:
 * 1. ONBOARDING - No notification permission granted
 * 2. APP_SELECTION - Permission granted but no apps selected (initial setup)
 * 3. MAIN_APP - Permission granted and at least one app selected
 */
enum class AppFlowState {
    ONBOARDING,
    APP_SELECTION,
    MAIN_APP,
}

@Composable
fun Notificapp(
    navigationHandler: NavigationHandler,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appFlowState by viewModel.appFlowState.collectAsStateWithLifecycle()

    // Re-check on every resume - but only before reaching the main app. Once in MAIN_APP,
    // re-deriving the flow state on every resume would tear down and recreate the NavDisplay
    // below (rememberNavigationState resets its back stack whenever it's freshly composed),
    // wiping the user's navigation stack and any in-progress screen state every time the
    // activity resumes - including after returning from an external activity launched via an
    // ActivityResultContract (e.g. a system picker).
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED && appFlowState != AppFlowState.MAIN_APP) {
            viewModel.recheckFlowState(isNotificationListenerEnabled(context))
        }
    }

    // null = still checking; keep the splash/blank frame until the first check resolves
    val currentFlowState = appFlowState ?: return

    // Navigation setup
    val startRoute: Screen = when (currentFlowState) {
        AppFlowState.ONBOARDING -> Routes.onboarding()
        AppFlowState.APP_SELECTION -> Routes.appSelection(isInitialSetup = true)
        AppFlowState.MAIN_APP -> Routes.inbox()
    }

    val navigationState = rememberNavigationState(startRoute = startRoute)
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // Handle navigation commands from ViewModels. Lifecycle-gated so the back stack is never
    // mutated while the Activity is stopped; commands emitted during a stop/gap queue in the
    // NavigationHandler's Channel and deliver once collection resumes, instead of being dropped.
    LaunchedEffect(navigationHandler, navigator, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            navigationHandler.navigationFlow.collect { command ->
                when (command) {
                    is NavigationCommand.Navigate -> navigator.navigate(command.screen)
                    is NavigationCommand.GoBack -> navigator.goBack()
                    is NavigationCommand.ClearAndNavigate -> navigator.clearAndNavigate(command.screen)
                }
            }
        }
    }

    // Handle back button
    BackHandler(enabled = navigator.canGoBack()) {
        navigator.goBack()
    }

    NotificappNavHost(navigator = navigator, context = context)
}

@Composable
private fun NotificappNavHost(navigator: Navigator, context: Context) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = navigator.state.backStack,
            onBack = { navigator.goBack() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Screen.Onboarding> {
                    OnboardingScreen(
                        onOpenNotificationSettings = { openNotificationListenerSettings(context) },
                    )
                }

                entry<Screen.AppSelection> { screen ->
                    AppSelectionScreen()
                }

                entry<Screen.Inbox> {
                    InboxScreen(
                        navigateTo = navigator::navigate,
                    )
                }

                entry<Screen.Rules> {
                    RulesScreen(
                        navigateTo = navigator::navigate,
                    )
                }

                entry<Screen.Settings> {
                    SettingsScreen(
                        navigateTo = navigator::navigate,
                    )
                }

                // Detail screens with slide transitions
                entry<Screen.NotificationDetails> { screen ->
                    NotificationDetailScreen(
                        notificationId = screen.notificationId,
                    )
                }

                entry<Screen.RuleEditor> { screen ->
                    RuleEditorScreen(
                        ruleId = screen.ruleId,
                        notificationId = screen.notificationId,
                    )
                }
            },
        )

        // Debug overlay (only in debug builds)
        DebugNavOverlay(navigator = navigator)
    }
}

/**
 * Debug overlay for navigation debugging.
 * Only shown in debug builds.
 *
 * @param navigator The navigator to observe
 */
@Composable
private fun DebugNavOverlay(navigator: Navigator) {
    // Show only in debug builds
    if (BuildConfig.DEBUG) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(8.dp),
        ) {
            Text(
                text = "Stack: ${navigator.backStackSize()}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Current: ${navigator.currentRoute()?.javaClass?.simpleName ?: "None"}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NotificappPreview() {
    NotificappTheme {
        // Preview without dependencies
        Text("Notificapp Preview")
    }
}
