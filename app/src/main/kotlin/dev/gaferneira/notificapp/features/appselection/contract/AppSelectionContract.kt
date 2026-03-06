package dev.gaferneira.notificapp.features.appselection.contract

/**
 * Contract for the App Selection screen.
 *
 * Shows installed apps and allows user to select which ones to monitor.
 * This screen appears after onboarding if no apps are selected.
 */
object AppSelectionContract {

    /**
     * UI State for the app selection screen.
     */
    data class UiState(
        /** List of all installed apps that can send notifications */
        val availableApps: List<AppInfo> = emptyList(),
        /** Set of package names that are currently selected */
        val selectedPackageNames: Set<String> = emptySet(),
        /** Current search query */
        val searchQuery: String = "",
        /** Whether data is loading */
        val isLoading: Boolean = true,
        /** Error message if loading failed */
        val error: String? = null,
        /** Whether this is the initial setup (no back button) or accessed from settings */
        val isInitialSetup: Boolean? = null,
    ) {
        /**
         * Display order of apps - maintains stable positions during selection.
         * Selected apps are shown at the top only on initial load.
         * After that, apps stay in their positions to avoid jarring UI.
         */
        val displayApps: List<AppInfo>
            get() = availableApps

        /** Filtered apps based on search query (maintains display order) */
        val filteredApps: List<AppInfo>
            get() = if (searchQuery.isBlank()) {
                displayApps
            } else {
                displayApps.filter { app ->
                    app.name.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
                }
            }

        /** Selected apps for display in the "Selected" section at top */
        val selectedApps: List<AppInfo>
            get() = displayApps.filter { selectedPackageNames.contains(it.packageName) }

        /** Unselected apps for display after selected ones */
        val unselectedApps: List<AppInfo>
            get() = displayApps.filterNot { selectedPackageNames.contains(it.packageName) }

        /** Whether at least one app is selected */
        val hasSelection: Boolean
            get() = selectedPackageNames.isNotEmpty()

        /** Count of selected apps */
        val selectedCount: Int
            get() = selectedPackageNames.size
    }

    /**
     * Information about an installed app.
     */
    data class AppInfo(
        /** Package name (unique identifier) */
        val packageName: String,
        /** Display name of the app */
        val name: String,
        /** App category or type (optional) */
        val category: String? = null,
    )

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User toggled an app's selection */
        data class OnAppToggled(val packageName: String, val isSelected: Boolean) : UiEvent()

        /** User typed in search field */
        data class OnSearchQueryChanged(val query: String) : UiEvent()

        /** User clicked Continue/Save button */
        data object OnContinueClicked : UiEvent()

        /** User clicked Back button (only available when accessed from settings) */
        data object OnBackClicked : UiEvent()

        /** User dismissed error */
        data object OnDismissError : UiEvent()

        /** Refresh the app list */
        data object OnRefresh : UiEvent()
    }

    /**
     * One-time effects (navigation, actions).
     */
    sealed class UiEffect {
        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}
