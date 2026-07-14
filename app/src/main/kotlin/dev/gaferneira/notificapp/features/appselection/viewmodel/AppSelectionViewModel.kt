package dev.gaferneira.notificapp.features.appselection.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiEffect
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiEvent
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiState
import dev.gaferneira.notificapp.features.appselection.data.InstalledAppsProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the App Selection screen.
 *
 * Loads installed apps that can send notifications and allows user to select
 * which ones to monitor. Persists selections to the repository.
 *
 * @param installedAppsProvider Resolves installed apps without an Android-static app-catalog lookup
 * @param selectedAppRepository Repository for selected apps
 * @param navigationHandler Handler for navigation commands
 * @param ioDispatcher Dispatcher for IO operations
 */
@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val selectedAppRepository: SelectedAppRepository,
    private val navigationHandler: NavigationHandler,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    init {
        loadInstalledApps()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnAppToggled -> {
                toggleAppSelection(event.packageName, event.isSelected)
            }
            is UiEvent.OnSelectAllToggled -> {
                toggleSelectAll()
            }
            is UiEvent.OnSearchQueryChanged -> {
                updateSearchQuery(event.query)
            }
            is UiEvent.OnContinueClicked -> {
                saveSelectionsAndContinue()
            }
            is UiEvent.OnBackClicked -> {
                navigateBack()
            }
            is UiEvent.OnDismissError -> {
                setState { copy(error = null) }
            }
            is UiEvent.OnRefresh -> {
                loadInstalledApps()
            }
        }
    }

    /**
     * Navigate back to previous screen.
     */
    private fun navigateBack() {
        viewModelScope.launch {
            navigationHandler.goBack()
        }
    }

    /**
     * Navigate to main app (inbox).
     */
    private fun navigateToMainApp() {
        viewModelScope.launch {
            navigationHandler.clearAndNavigate(Routes.inbox())
        }
    }

    /**
     * Load installed apps that can send notifications.
     * Sorts apps initially so selected apps appear at the top.
     * After loading, the order remains stable to avoid jarring UI when selecting.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch(ioDispatcher) {
            setState { copy(isLoading = true, error = null) }

            try {
                val installedApps = installedAppsProvider.getMonitorableApps()

                // Check which apps are already selected
                val existingApps = selectedAppRepository.getAllApps().getOrNull() ?: emptyList()

                // Determine if this is initial setup (no apps selected yet)
                val isInitialSetup = existingApps.isEmpty()

                // On initial setup, default to every app selected (opt-out UX) and persist
                // immediately so Continue works even if the user never touches an individual app.
                val selectedPackages = if (isInitialSetup) {
                    val allPackages = installedApps.map { it.packageName }.toSet()
                    selectedAppRepository.addApps(
                        installedApps.map { app ->
                            SelectedApp(packageName = app.packageName, appName = app.name, isEnabled = true)
                        },
                    )
                    allPackages
                } else {
                    existingApps.filter { it.isEnabled }.map { it.packageName }.toSet()
                }

                // Sort apps initially: selected first (alphabetically), then unselected (alphabetically)
                // This creates a stable order that won't change during selection
                val sortedApps = installedApps.sortedWith(
                    compareByDescending<AppInfo> { selectedPackages.contains(it.packageName) }
                        .thenBy { it.name.lowercase() },
                )

                setState {
                    copy(
                        availableApps = sortedApps.toImmutableList(),
                        selectedPackageNames = selectedPackages,
                        isLoading = false,
                        isInitialSetup = isInitialSetup,
                    )
                }

                Timber.d(
                    "Loaded ${sortedApps.size} apps, ${selectedPackages.size} already selected, initialSetup: $isInitialSetup",
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load installed apps")
                setState {
                    copy(
                        isLoading = false,
                        error = "Failed to load apps: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Toggle an app's selection state and save immediately.
     */
    private fun toggleAppSelection(packageName: String, isSelected: Boolean) {
        // Capture current state values before any async operations
        val currentAvailableApps = uiState.value.availableApps

        // Update UI state first
        setState {
            val newSelection = if (isSelected) {
                selectedPackageNames + packageName
            } else {
                selectedPackageNames - packageName
            }
            copy(selectedPackageNames = newSelection)
        }

        // Find the app info from captured state
        val appInfo = currentAvailableApps.find { it.packageName == packageName }
        if (appInfo == null) {
            Timber.w("App info not found for package: $packageName")
            return
        }

        // Save to repository immediately
        viewModelScope.launch(ioDispatcher) {
            try {
                if (isSelected) {
                    // Add the app
                    selectedAppRepository.addApp(
                        SelectedApp(
                            packageName = packageName,
                            appName = appInfo.name,
                            isEnabled = true,
                        ),
                    )
                } else {
                    // Remove the app
                    selectedAppRepository.removeApp(packageName)
                }

                Timber.d("${if (isSelected) "Added" else "Removed"} app $packageName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle app $packageName")
            }
        }
    }

    /**
     * Select or deselect every currently filtered (search-visible) app in one action.
     * Deselects if all filtered apps are already selected, otherwise selects them all.
     */
    private fun toggleSelectAll() {
        val currentState = uiState.value
        val filteredApps = currentState.filteredApps
        if (filteredApps.isEmpty()) return

        val shouldSelectAll = !currentState.areAllFilteredSelected
        val filteredPackageNames = filteredApps.map { it.packageName }

        setState {
            val newSelection = if (shouldSelectAll) {
                selectedPackageNames + filteredPackageNames
            } else {
                selectedPackageNames - filteredPackageNames.toSet()
            }
            copy(selectedPackageNames = newSelection)
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                if (shouldSelectAll) {
                    selectedAppRepository.addApps(
                        filteredApps.map { app ->
                            SelectedApp(packageName = app.packageName, appName = app.name, isEnabled = true)
                        },
                    )
                } else {
                    selectedAppRepository.removeApps(filteredPackageNames)
                }
                Timber.d("${if (shouldSelectAll) "Selected" else "Deselected"} all ${filteredPackageNames.size} filtered apps")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle select-all")
            }
        }
    }

    /**
     * Update the search query.
     */
    private fun updateSearchQuery(query: String) {
        setState { copy(searchQuery = query) }
    }

    /**
     * Save selections and navigate.
     */
    private fun saveSelectionsAndContinue() {
        val currentState = uiState.value

        if (currentState.selectedPackageNames.isEmpty()) {
            sendEffect(UiEffect.ShowError("Please select at least one app"))
            return
        }

        // Get selected apps info for returning to caller
        val selectedApps = currentState.selectedApps

        // Navigate based on context
        if (currentState.isInitialSetup == true) {
            navigateToMainApp()
        } else {
            navigateBack()
        }
    }
}
