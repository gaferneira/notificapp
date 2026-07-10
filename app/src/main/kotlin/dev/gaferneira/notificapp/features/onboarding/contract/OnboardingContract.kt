package dev.gaferneira.notificapp.features.onboarding.contract

/**
 * Contract for the Onboarding screen.
 *
 * The onboarding flow has two states:
 * 1. Value Statement - Introduces the app value proposition
 * 2. Permission Explanation - Explains and requests notification access
 */
object OnboardingContract {

    /**
     * UI State for the onboarding screen.
     */
    data class UiState(
        /** Current step in the onboarding flow */
        val currentStep: OnboardingStep = OnboardingStep.VALUE_STATEMENT,
        /** Whether notification listener permission is granted */
        val hasNotificationPermission: Boolean = false,
        /** Loading state when checking permission */
        val isLoading: Boolean = false,
        /** Shown when the user returns from system settings without granting access */
        val showPermissionDeniedHint: Boolean = false,
    )

    /**
     * Steps in the onboarding flow.
     */
    enum class OnboardingStep {
        /** First screen showing value proposition */
        VALUE_STATEMENT,

        /** Second screen explaining and requesting permission */
        PERMISSION_EXPLANATION,
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User clicked "Get Started" on value statement screen */
        data object OnGetStartedClicked : UiEvent()

        /** User clicked "Grant Access" to open system settings */
        data object OnGrantAccessClicked : UiEvent()

        /** User clicked back arrow on permission screen */
        data object OnBackClicked : UiEvent()

        /** Check permission status (called on resume) */
        data object CheckPermission : UiEvent()
    }

    /**
     * One-time effects (navigation, system actions).
     */
    sealed class UiEffect {
        /** Navigate to system notification listener settings */
        data object OpenNotificationSettings : UiEffect()

        /** Onboarding completed, navigate to main app */
        data object NavigateToMainApp : UiEffect()
    }
}
