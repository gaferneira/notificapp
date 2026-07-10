package dev.gaferneira.notificapp.features.onboarding.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEffect
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiState
import dev.gaferneira.notificapp.util.isNotificationListenerEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Onboarding screen.
 *
 * Manages the two-step onboarding flow:
 * 1. Value Statement - introduces the app
 * 2. Permission Explanation - requests notification access
 *
 * @param context Application context for checking permission status
 * @param navigationHandler Handler for navigation commands
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navigationHandler: NavigationHandler,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    private var hasRequestedNotificationAccess = false

    init {
        checkPermissionStatus()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnGetStartedClicked -> {
                transitionToPermissionStep()
            }
            is UiEvent.OnGrantAccessClicked -> {
                openNotificationSettings()
            }
            is UiEvent.OnBackClicked -> {
                goBackToValueStatement()
            }
            is UiEvent.CheckPermission -> {
                checkPermissionStatus()
            }
        }
    }

    /**
     * Transition from Value Statement to Permission Explanation.
     */
    private fun transitionToPermissionStep() {
        setState {
            copy(currentStep = OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION)
        }
    }

    /**
     * Go back from Permission Explanation to Value Statement.
     */
    private fun goBackToValueStatement() {
        setState {
            copy(currentStep = OnboardingContract.OnboardingStep.VALUE_STATEMENT)
        }
    }

    /**
     * Open system notification listener settings.
     */
    private fun openNotificationSettings() {
        hasRequestedNotificationAccess = true
        sendEffect(UiEffect.OpenNotificationSettings)
    }

    /**
     * Navigate to main app after permission is granted.
     */
    private fun navigateToMainApp() {
        viewModelScope.launch {
            navigationHandler.clearAndNavigate(Routes.appSelection(isInitialSetup = true))
        }
    }

    /**
     * Check if notification listener permission is granted.
     */
    private fun checkPermissionStatus() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }

            // Small delay to prevent UI flickering
            delay(100)

            val hasPermission = isNotificationListenerEnabled(context)

            setState {
                copy(
                    hasNotificationPermission = hasPermission,
                    isLoading = false,
                    showPermissionDeniedHint = hasRequestedNotificationAccess && !hasPermission,
                )
            }

            if (hasPermission) {
                Timber.d("Notification permission granted, completing onboarding")
                navigateToMainApp()
            }
        }
    }
}
