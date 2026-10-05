package dev.gaferneira.notificapp.features.appselection.contract

import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.AppInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Contract for the App Selection screen.
 *
 * Shows installed apps and allows user to select which ones to monitor.
 * Opt-in: initial setup starts with no app selected, and nothing is monitored until the user picks some.
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
        val error: UiText? = null,
        /** Whether this is the initial setup (no back button) or accessed from settings; null until the screen reports it */
        val isInitialSetup: Boolean? = null,
    ) {
        /**
         * Apps matching the search query, in stable display order. Lazy so the filtering runs at most
         * once per state instance rather than on every read during composition.
         */
        val filteredApps: ImmutableList<AppInfo> by lazy(LazyThreadSafetyMode.NONE) {
            if (searchQuery.isBlank()) {
                availableApps
            } else {
                availableApps.filter { app ->
                    app.name.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
                }.toImmutableList()
            }
        }

        /** Whether at least one app is selected */
        val hasSelection: Boolean
            get() = selectedPackageNames.isNotEmpty()

        /** Count of selected apps */
        val selectedCount: Int
            get() = selectedPackageNames.size

        /** Whether every app currently visible (post-search) is selected */
        val areAllFilteredSelected: Boolean by lazy(LazyThreadSafetyMode.NONE) {
            filteredApps.isNotEmpty() && filteredApps.all { selectedPackageNames.contains(it.packageName) }
        }
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Screen opened with the route's [isInitialSetup] flag; triggers the first load (once). */
        data class OnScreenOpened(val isInitialSetup: Boolean) : UiEvent()

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

        /** Refresh the installed-app list silently, preserving the selection and list order */
        data object OnRefresh : UiEvent()
    }

    /**
     * One-time effects (navigation, actions).
     */
    sealed class UiEffect {
        /** Show error message */
        data class ShowError(val message: UiText) : UiEffect()
    }
}
