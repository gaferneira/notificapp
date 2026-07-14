package dev.gaferneira.notificapp.features.appselection.contract

import dev.gaferneira.notificapp.domain.model.AppInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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
        val availableApps: ImmutableList<AppInfo> = persistentListOf(),
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
        val displayApps: ImmutableList<AppInfo>
            get() = availableApps

        /** Filtered apps based on search query (maintains display order) */
        val filteredApps: ImmutableList<AppInfo>
            get() = if (searchQuery.isBlank()) {
                displayApps
            } else {
                displayApps.filter { app ->
                    app.name.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
                }.toImmutableList()
            }

        /** Selected apps for display in the "Selected" section at top */
        val selectedApps: ImmutableList<AppInfo>
            get() = displayApps.filter { selectedPackageNames.contains(it.packageName) }.toImmutableList()

        /** Unselected apps for display after selected ones */
        val unselectedApps: ImmutableList<AppInfo>
            get() = displayApps.filterNot { selectedPackageNames.contains(it.packageName) }.toImmutableList()

        /** Whether at least one app is selected */
        val hasSelection: Boolean
            get() = selectedPackageNames.isNotEmpty()

        /** Count of selected apps */
        val selectedCount: Int
            get() = selectedPackageNames.size

        /** Whether every app currently visible (post-search) is selected */
        val areAllFilteredSelected: Boolean
            get() = filteredApps.isNotEmpty() && filteredApps.all { selectedPackageNames.contains(it.packageName) }
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User toggled an app's selection */
        data class OnAppToggled(val packageName: String, val isSelected: Boolean) : UiEvent()

        /** User clicked Select All / Deselect All (applies to currently filtered apps) */
        data object OnSelectAllToggled : UiEvent()

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
