package dev.gaferneira.notificapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.appselection.ui.AppSelectionScreen
import dev.gaferneira.notificapp.features.inbox.ui.InboxScreen
import dev.gaferneira.notificapp.features.notificationdetail.ui.NotificationDetailScreen
import dev.gaferneira.notificapp.features.onboarding.ui.OnboardingScreen
import dev.gaferneira.notificapp.features.ruleeditor.ui.RuleEditorScreen
import dev.gaferneira.notificapp.features.rules.ui.RulesScreen
import dev.gaferneira.notificapp.features.settings.ui.SettingsScreen
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    repository: SelectedAppRepository = hiltViewModel<MainViewModel>().repository,
    navigationHandler: NavigationHandler,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Track current flow state
    var appFlowState by rememberSaveable { mutableStateOf(AppFlowState.ONBOARDING) }

    // Track if we're checking the state (to prevent flickering)
    var isCheckingState by remember { mutableStateOf(true) }

    // Check state on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Don't reset isCheckingState here to avoid UI flicker
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial state check - properly checks for selected apps
    LaunchedEffect(Unit) {
        isCheckingState = true
        appFlowState = determineAppFlowState(context, repository)
        isCheckingState = false
    }

    // Re-check when resumed (permission might have changed) - but only before reaching the main
    // app. Once in MAIN_APP, re-deriving the flow state on every resume would tear down and
    // recreate the NavDisplay below (rememberNavigationState resets its back stack whenever it's
    // freshly composed), wiping the user's navigation stack and any in-progress screen state
    // every time the activity resumes - including after returning from an external activity
    // launched via an ActivityResultContract (e.g. a system picker).
    LaunchedEffect(lifecycleOwner.lifecycle.currentStateAsState().value) {
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED && appFlowState != AppFlowState.MAIN_APP) {
            isCheckingState = true
            appFlowState = determineAppFlowState(context, repository)
            isCheckingState = false
        }
    }

    // Show loading while checking state
    if (isCheckingState) {
        return
    }

    // Navigation setup
    val startRoute: Screen = when (appFlowState) {
        AppFlowState.ONBOARDING -> Routes.onboarding()
        AppFlowState.APP_SELECTION -> Routes.appSelection(isInitialSetup = true)
        AppFlowState.MAIN_APP -> Routes.inbox()
    }

    val navigationState = rememberNavigationState(startRoute = startRoute)
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // Handle navigation commands from ViewModels
    LaunchedEffect(navigationHandler, navigator) {
        navigationHandler.navigationFlow.onEach { command ->
            when (command) {
                is NavigationCommand.Navigate -> navigator.navigate(command.screen)
                is NavigationCommand.GoBack -> navigator.goBack()
                is NavigationCommand.ClearAndNavigate -> navigator.clearAndNavigate(command.screen)
            }
        }.launchIn(this)
    }

    // Handle back button
    BackHandler(enabled = navigator.canGoBack()) {
        navigator.goBack()
    }

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
                        onOpenNotificationSettings = { openNotificationSettings(context) },
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
 * Determine which flow state the app should be in.
 * Checks notification permission and if any apps are selected.
 */
suspend fun determineAppFlowState(context: Context, repository: SelectedAppRepository): AppFlowState {
    // First check notification permission
    if (!isNotificationServiceEnabled(context)) {
        return AppFlowState.ONBOARDING
    }

    // Permission granted - check if we have selected apps
    return try {
        val selectedApps = repository.getAllApps()
        val hasApps = selectedApps.isSuccess && selectedApps.getOrNull()?.isNotEmpty() == true

        if (hasApps) {
            AppFlowState.MAIN_APP
        } else {
            AppFlowState.APP_SELECTION
        }
    } catch (e: Exception) {
        // If we can't check, default to app selection
        AppFlowState.APP_SELECTION
    }
}

/**
 * Check if notification listener service is enabled for this app.
 */
private fun isNotificationServiceEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    )
    return flat?.contains(packageName) == true
}

/**
 * Open system notification listener settings.
 */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
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
