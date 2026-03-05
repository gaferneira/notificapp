package dev.gaferneira.notificapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ExtractionField
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract
import dev.gaferneira.notificapp.features.appselection.ui.AppSelectionScreen
import dev.gaferneira.notificapp.features.inbox.contract.InboxEffect
import dev.gaferneira.notificapp.features.inbox.ui.InboxScreen
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract
import dev.gaferneira.notificapp.features.notificationdetail.ui.NotificationDetailScreen
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract
import dev.gaferneira.notificapp.features.onboarding.ui.OnboardingScreen
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract
import dev.gaferneira.notificapp.features.ruleeditor.ui.AddFieldScreen
import dev.gaferneira.notificapp.features.ruleeditor.ui.RuleEditorScreen
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract
import dev.gaferneira.notificapp.features.settings.ui.SettingsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotificappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Notificapp()
                }
            }
        }
    }
}

/**
 * Main app destinations for bottom navigation.
 */
enum class AppDestinations(val label: String, val icon: ImageVector) {
    INBOX("Inbox", Icons.Default.Notifications),
    RULES("Rules", Icons.Default.Home),
    SETTINGS("Settings", Icons.Default.Settings),
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

/**
 * Navigation routes within the main app.
 */
sealed class MainAppRoute {
    data object Inbox : MainAppRoute()
    data object Rules : MainAppRoute()
    data object Settings : MainAppRoute()
    data object AppSelection : MainAppRoute()
    data class AppSelectionForRule(val preSelectedApps: List<String> = emptyList()) : MainAppRoute()
    data class NotificationDetail(val notificationId: String) : MainAppRoute()
    data class RuleEditor(val ruleId: String? = null, val notificationId: String? = null) : MainAppRoute()
    data class AddField(val sampleText: String, val existingField: ExtractionField? = null) : MainAppRoute()
}

@Composable
fun Notificapp(repository: SelectedAppRepository = hiltViewModel<MainViewModel>().repository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Track current flow state
    var appFlowState by rememberSaveable { mutableStateOf(AppFlowState.ONBOARDING) }

    // Track if we're checking the state (to prevent flickering)
    var isCheckingState by remember { mutableStateOf(true) }

    // Track current route within main app (use a simple enum for saveable state)
    var currentRouteType by rememberSaveable { mutableStateOf(MainAppRouteType.INBOX) }
    var notificationIdParam by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleEditorRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleEditorNotificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var addFieldSampleText by rememberSaveable { mutableStateOf<String?>(null) }

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

    // Re-check when resumed (permission might have changed)
    LaunchedEffect(lifecycleOwner.lifecycle.currentStateAsState().value) {
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            isCheckingState = true
            appFlowState = determineAppFlowState(context, repository)
            isCheckingState = false
        }
    }

    // Show loading while checking state
    if (isCheckingState) {
        return
    }

    // Helper functions for navigation
    fun navigateTo(route: MainAppRoute) {
        when (route) {
            is MainAppRoute.Inbox -> {
                currentRouteType = MainAppRouteType.INBOX
                notificationIdParam = null
            }
            is MainAppRoute.Rules -> {
                currentRouteType = MainAppRouteType.RULES
                notificationIdParam = null
            }
            is MainAppRoute.Settings -> {
                currentRouteType = MainAppRouteType.SETTINGS
                notificationIdParam = null
            }
            is MainAppRoute.AppSelection -> {
                currentRouteType = MainAppRouteType.APP_SELECTION
                notificationIdParam = null
            }
            is MainAppRoute.AppSelectionForRule -> {
                currentRouteType = MainAppRouteType.APP_SELECTION_FOR_RULE
            }
            is MainAppRoute.NotificationDetail -> {
                currentRouteType = MainAppRouteType.NOTIFICATION_DETAIL
                notificationIdParam = route.notificationId
            }
            is MainAppRoute.RuleEditor -> {
                currentRouteType = MainAppRouteType.RULE_EDITOR
                ruleEditorRuleId = route.ruleId
                ruleEditorNotificationId = route.notificationId
            }
            is MainAppRoute.AddField -> {
                currentRouteType = MainAppRouteType.ADD_FIELD
                addFieldSampleText = route.sampleText
            }
        }
    }

    // Render appropriate screen based on flow state
    when (appFlowState) {
        AppFlowState.ONBOARDING -> {
            OnboardingScreen(
                onNavigate = { effect ->
                    when (effect) {
                        is OnboardingContract.UiEffect.OpenNotificationSettings -> {
                            openNotificationSettings(context)
                        }
                        is OnboardingContract.UiEffect.NavigateToMainApp -> {
                            // Will recheck state on next launch
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AppFlowState.APP_SELECTION -> {
            AppSelectionScreen(
                onNavigate = { effect ->
                    when (effect) {
                        is AppSelectionContract.UiEffect.NavigateToMainApp -> {
                            appFlowState = AppFlowState.MAIN_APP
                        }
                        is AppSelectionContract.UiEffect.ShowError -> {
                            // Error is shown in the screen via state
                        }
                        else -> {}
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AppFlowState.MAIN_APP -> {
            MainAppContent(
                currentRouteType = currentRouteType,
                notificationId = notificationIdParam,
                ruleEditorRuleId = ruleEditorRuleId,
                ruleEditorNotificationId = ruleEditorNotificationId,
                addFieldSampleText = addFieldSampleText,
                onNavigate = { navigateTo(it) },
                pendingField = null,
            )
        }
    }
}

/**
 * Enum for saveable route types.
 */
enum class MainAppRouteType {
    INBOX,
    RULES,
    SETTINGS,
    APP_SELECTION,
    APP_SELECTION_FOR_RULE,
    NOTIFICATION_DETAIL,
    RULE_EDITOR,
    ADD_FIELD,
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

@Composable
private fun MainAppContent(
    currentRouteType: MainAppRouteType,
    notificationId: String?,
    ruleEditorRuleId: String?,
    ruleEditorNotificationId: String?,
    addFieldSampleText: String?,
    onNavigate: (MainAppRoute) -> Unit,
    pendingField: ExtractionField?,
    modifier: Modifier = Modifier,
) {
    // Track field from AddField for returning to RuleEditor
    var currentPendingField by remember { mutableStateOf<ExtractionField?>(pendingField) }

    // Track selected apps from AppSelection for returning to RuleEditor

    // Determine bottom nav selection based on route
    val currentDestination = when (currentRouteType) {
        MainAppRouteType.INBOX, MainAppRouteType.NOTIFICATION_DETAIL,
        MainAppRouteType.APP_SELECTION, MainAppRouteType.APP_SELECTION_FOR_RULE,
        MainAppRouteType.RULE_EDITOR, MainAppRouteType.ADD_FIELD,
        -> AppDestinations.INBOX
        MainAppRouteType.RULES -> AppDestinations.RULES
        MainAppRouteType.SETTINGS -> AppDestinations.SETTINGS
    }

    // Handle bottom nav selection change
    fun onDestinationChange(destination: AppDestinations) {
        when (destination) {
            AppDestinations.INBOX -> onNavigate(MainAppRoute.Inbox)
            AppDestinations.RULES -> onNavigate(MainAppRoute.Rules)
            AppDestinations.SETTINGS -> onNavigate(MainAppRoute.Settings)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            // Only show bottom nav for main screens, not for sub-screens
            if (currentRouteType != MainAppRouteType.APP_SELECTION &&
                currentRouteType != MainAppRouteType.APP_SELECTION_FOR_RULE &&
                currentRouteType != MainAppRouteType.NOTIFICATION_DETAIL &&
                currentRouteType != MainAppRouteType.RULE_EDITOR &&
                currentRouteType != MainAppRouteType.ADD_FIELD
            ) {
                NavigationBar {
                    AppDestinations.entries.forEach { destination ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                            selected = destination == currentDestination,
                            onClick = { onDestinationChange(destination) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (currentRouteType) {
            MainAppRouteType.INBOX -> {
                InboxScreen(
                    onNavigate = { effect ->
                        when (effect) {
                            is InboxEffect.NavigateToNotificationDetail -> {
                                onNavigate(MainAppRoute.NotificationDetail(effect.notificationId))
                            }
                            is InboxEffect.ShowError -> {
                                // Error shown via UI
                            }
                            is InboxEffect.ShowPermissionRequired -> {
                                // Handle permission required
                            }
                        }
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            MainAppRouteType.RULES -> {
                PlaceholderScreen(
                    name = "Rules",
                    modifier = Modifier.padding(innerPadding),
                )
            }
            MainAppRouteType.SETTINGS -> {
                SettingsScreen(
                    onNavigate = { effect ->
                        when (effect) {
                            is SettingsContract.UiEffect.NavigateToAppSelection -> {
                                onNavigate(MainAppRoute.AppSelection)
                            }
                            is SettingsContract.UiEffect.ShowError -> {
                                // Error shown via UI state
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            MainAppRouteType.APP_SELECTION -> {
                AppSelectionScreen(
                    onNavigate = { effect ->
                        when (effect) {
                            is AppSelectionContract.UiEffect.NavigateToMainApp -> {
                                // From initial setup - go to main app
                                onNavigate(MainAppRoute.Inbox)
                            }
                            is AppSelectionContract.UiEffect.NavigateBack -> {
                                // From settings - go back to settings
                                onNavigate(MainAppRoute.Settings)
                            }
                            is AppSelectionContract.UiEffect.ShowError -> {
                                // Error shown via UI state
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MainAppRouteType.APP_SELECTION_FOR_RULE -> {
                AppSelectionScreen(
                    onNavigate = { effect ->
                        when (effect) {
                            is AppSelectionContract.UiEffect.NavigateBack -> {
                                // User cancelled, just go back
                                onNavigate(MainAppRoute.RuleEditor())
                            }
                            is AppSelectionContract.UiEffect.NavigateToMainApp -> {
                                // This shouldn't happen in this flow, but handle it
                                onNavigate(MainAppRoute.RuleEditor())
                            }
                            is AppSelectionContract.UiEffect.ShowError -> {
                                // Error shown via UI state
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MainAppRouteType.NOTIFICATION_DETAIL -> {
                val notifId = notificationId
                if (notifId != null) {
                    NotificationDetailScreen(
                        notificationId = notifId,
                        onNavigate = { effect ->
                            when (effect) {
                                is NotificationDetailContract.UiEffect.NavigateBack -> {
                                    onNavigate(MainAppRoute.Inbox)
                                }
                                is NotificationDetailContract.UiEffect.NavigateToRuleEditor -> {
                                    // Navigate to rule editor with notification
                                    onNavigate(
                                        MainAppRoute.RuleEditor(
                                            notificationId = notifId,
                                        ),
                                    )
                                }
                                is NotificationDetailContract.UiEffect.NavigateToEditRule -> {
                                    // Navigate to edit specific rule
                                    onNavigate(
                                        MainAppRoute.RuleEditor(
                                            ruleId = effect.ruleId,
                                        ),
                                    )
                                }
                                is NotificationDetailContract.UiEffect.ShowError -> {
                                    // Error shown via UI
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Fallback if no notification ID
                    onNavigate(MainAppRoute.Inbox)
                }
            }
            MainAppRouteType.RULE_EDITOR -> {
                RuleEditorScreen(
                    ruleId = ruleEditorRuleId,
                    notificationId = ruleEditorNotificationId,
                    pendingField = currentPendingField,
                    onNavigate = { effect ->
                        when (effect) {
                            is RuleEditorContract.UiEffect.NavigateBack -> {
                                currentPendingField = null
                                // If we came from a notification, go back to it, otherwise go to inbox
                                val notifId = ruleEditorNotificationId
                                if (notifId != null) {
                                    onNavigate(MainAppRoute.NotificationDetail(notifId))
                                } else {
                                    onNavigate(MainAppRoute.Inbox)
                                }
                            }
                            is RuleEditorContract.UiEffect.NavigateToAddField -> {
                                currentPendingField = null
                                onNavigate(
                                    MainAppRoute.AddField(
                                        sampleText = effect.sampleText,
                                    ),
                                )
                            }
                            is RuleEditorContract.UiEffect.ShowSuccess -> {
                                // Success message shown via UI
                            }
                            is RuleEditorContract.UiEffect.ShowError -> {
                                // Error shown via UI
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MainAppRouteType.ADD_FIELD -> {
                val sample = addFieldSampleText
                if (sample != null) {
                    AddFieldScreen(
                        sampleText = sample,
                        onNavigate = { effect ->
                            when (effect) {
                                is AddFieldContract.UiEffect.ReturnWithField -> {
                                    // Return to RuleEditor with the field
                                    currentPendingField = effect.field
                                    onNavigate(MainAppRoute.RuleEditor())
                                }
                                is AddFieldContract.UiEffect.CancelAndReturn -> {
                                    currentPendingField = null
                                    onNavigate(MainAppRoute.RuleEditor())
                                }
                                is AddFieldContract.UiEffect.ShowError -> {
                                    // Error shown via UI
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Fallback if no sample text
                    onNavigate(MainAppRoute.RuleEditor())
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "$name Screen (Coming Soon)",
        modifier = modifier.fillMaxSize(),
        style = MaterialTheme.typography.headlineMedium,
    )
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

@Preview(showBackground = true)
@Composable
fun NotificappPreview() {
    NotificappTheme {
        Notificapp()
    }
}
