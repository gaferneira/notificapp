package dev.gaferneira.notificapp.features.onboarding.viewmodel

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.OnboardingStep
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEffect
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiState
import javax.inject.Inject

/**
 * ViewModel for the Onboarding screen (Value Statement and Permission Explanation steps).
 *
 * It does NOT navigate: once notification access is granted, `MainViewModel` re-derives the app
 * flow state and swaps the start route to App Selection - the single owner of that transition.
 * This ViewModel only drives the step UI and the "access not enabled yet" hint.
 *
 * The current step and the "user opened system settings" flag live in [SavedStateHandle] so they
 * survive process death (the user is sent to system settings, where the OS may kill the app).
 *
 * @param listenerStatus Seam for checking notification-listener permission status
 * @param savedStateHandle Persists the step and the settings-requested flag across process death
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val listenerStatus: NotificationListenerStatusProvider,
    private val savedStateHandle: SavedStateHandle,
) : MviViewModel<UiState, UiEvent, UiEffect>(
    UiState(currentStep = savedStateHandle.restoreStep()),
) {

    private var hasRequestedNotificationAccess: Boolean
        get() = savedStateHandle[KEY_REQUESTED_ACCESS] ?: false
        set(value) {
            savedStateHandle[KEY_REQUESTED_ACCESS] = value
        }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnGetStartedClicked -> showStep(OnboardingStep.PERMISSION_EXPLANATION)
            is UiEvent.OnBackClicked -> showStep(OnboardingStep.VALUE_STATEMENT)
            is UiEvent.OnGrantAccessClicked -> openNotificationSettings()
            is UiEvent.CheckPermission -> checkPermissionStatus()
        }
    }

    private fun showStep(step: OnboardingStep) {
        savedStateHandle[KEY_STEP] = step.name
        setState { copy(currentStep = step) }
    }

    private fun openNotificationSettings() {
        hasRequestedNotificationAccess = true
        sendEffect(UiEffect.OpenNotificationSettings)
    }

    /**
     * Refresh the denied hint. Called once per resume (the only trigger), so returning from system
     * settings without granting access shows the hint; if access WAS granted, `MainViewModel`'s
     * own resume re-check swaps the route and this screen leaves composition.
     */
    private fun checkPermissionStatus() {
        val hasPermission = listenerStatus.isEnabled()
        setState { copy(showPermissionDeniedHint = hasRequestedNotificationAccess && !hasPermission) }
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
        const val KEY_REQUESTED_ACCESS = "onboarding_requested_access"

        fun SavedStateHandle.restoreStep(): OnboardingStep = get<String>(KEY_STEP)
            ?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
            ?: OnboardingStep.VALUE_STATEMENT
    }
}
