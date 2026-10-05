package dev.gaferneira.notificapp.features.onboarding.contract

/**
 * Contract for the Onboarding screen.
 *
 * The onboarding screen has two steps (the third onboarding step, App Selection, is its own
 * screen):
 * 1. Value Statement - Introduces the app value proposition
 * 2. Permission Explanation - Explains and requests notification access
 *
 * Granting access is not handled here: `MainViewModel` owns the transition to App Selection.
 */
object OnboardingContract {

    /**
     * UI State for the onboarding screen.
     */
    data class UiState(
        /** Current step in the onboarding flow */
        val currentStep: OnboardingStep = OnboardingStep.VALUE_STATEMENT,
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

    /** Total steps in the initial-setup flow, including App Selection (shown in the progress bars). */
    const val TOTAL_SETUP_STEPS = 3

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
    }
}
