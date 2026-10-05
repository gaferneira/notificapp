package dev.gaferneira.notificapp.features.appselection.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.UiText
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the App Selection screen.
 *
 * Loads installed apps that can send notifications and allows user to select
 * which ones to monitor. Persists selections to the repository as the user toggles them.
 * Opt-in: nothing is selected (or saved) until the user chooses; refreshing the installed-app
 * list never modifies the saved selection.
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

    private var loadJob: Job? = null

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnScreenOpened -> onScreenOpened(event.isInitialSetup)
            is UiEvent.OnAppToggled -> toggleAppSelection(event.packageName, event.isSelected)
            is UiEvent.OnSelectAllToggled -> toggleSelectAll()
            is UiEvent.OnSearchQueryChanged -> setState { copy(searchQuery = event.query) }
            is UiEvent.OnContinueClicked -> saveSelectionsAndContinue()
            is UiEvent.OnBackClicked -> navigateBack()
            is UiEvent.OnDismissError -> setState { copy(error = null) }
            is UiEvent.OnRefresh -> loadInstalledApps(showLoading = uiState.value.availableApps.isEmpty())
        }
    }

    /** Records the route flag and runs the first load; later calls (recomposition, rotation) are no-ops. */
    private fun onScreenOpened(isInitialSetup: Boolean) {
        if (uiState.value.isInitialSetup != null) return
        setState { copy(isInitialSetup = isInitialSetup) }
        loadInstalledApps(showLoading = true)
    }

    private fun navigateBack() {
        viewModelScope.launch {
            navigationHandler.goBack()
        }
    }

    private fun navigateToMainApp() {
        viewModelScope.launch {
            navigationHandler.clearAndNavigate(Routes.home())
        }
    }

    /**
     * Load installed apps that can send notifications. Read-only with respect to the saved
     * selection: it only mirrors what is already persisted. Ignored before the screen reported its
     * route flag and while another load is running. Only the first load (and retries after an error)
     * shows the spinner; refreshes keep the current list, order and scroll position.
     */
    private fun loadInstalledApps(showLoading: Boolean) {
        if (uiState.value.isInitialSetup == null || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch(ioDispatcher) {
            if (showLoading) setState { copy(isLoading = true, error = null) }

            val installedApps = runCatching { installedAppsProvider.getMonitorableApps() }
                .onFailure { if (it is CancellationException) throw it }
                .getOrElse { error ->
                    Timber.e(error, "Failed to load installed apps")
                    setState {
                        copy(
                            isLoading = false,
                            error = UiText.StringResource(R.string.app_selection_error_load, arrayOf(error.message.orEmpty())),
                        )
                    }
                    return@launch
                }
            val installedPackages = installedApps.map { it.packageName }.toSet()
            val persisted = selectedAppRepository.getAllApps().getOrNull().orEmpty()
                .filter { it.isEnabled && it.packageName in installedPackages }
                .map { it.packageName }
                .toSet()

            setState {
                // Repository is only written by this ViewModel, so on refresh the in-memory
                // selection is authoritative (it may be ahead of an in-flight write).
                val selection = if (availableApps.isEmpty()) persisted else selectedPackageNames
                copy(
                    availableApps = orderApps(installedApps, availableApps, selection),
                    selectedPackageNames = selection,
                    isLoading = false,
                    error = null,
                )
            }
            Timber.d("Loaded ${installedApps.size} apps, ${persisted.size} already selected")
        }
    }

    /**
     * First load: selected apps first, then the rest, each alphabetical. Later loads keep the
     * previous order and append newly installed apps alphabetically, so the list doesn't jump.
     */
    private fun orderApps(
        installed: List<AppInfo>,
        previous: List<AppInfo>,
        selection: Set<String>,
    ) = if (previous.isEmpty()) {
        installed.sortedWith(
            compareByDescending<AppInfo> { it.packageName in selection }.thenBy { it.name.lowercase() },
        )
    } else {
        val byPackage = installed.associateBy { it.packageName }
        val kept = previous.mapNotNull { byPackage[it.packageName] }
        val keptPackages = kept.map { it.packageName }.toSet()
        kept + installed.filter { it.packageName !in keptPackages }.sortedBy { it.name.lowercase() }
    }.toImmutableList()

    /** Toggle an app's selection state and save immediately. */
    private fun toggleAppSelection(packageName: String, isSelected: Boolean) {
        val appInfo = uiState.value.availableApps.find { it.packageName == packageName }
        if (appInfo == null) {
            Timber.w("App info not found for package: $packageName")
            return
        }

        setState {
            val newSelection = if (isSelected) selectedPackageNames + packageName else selectedPackageNames - packageName
            copy(selectedPackageNames = newSelection)
        }

        viewModelScope.launch(ioDispatcher) {
            val result = if (isSelected) {
                selectedAppRepository.addApp(SelectedApp(packageName = packageName, appName = appInfo.name, isEnabled = true))
            } else {
                selectedAppRepository.removeApp(packageName)
            }
            result.onFailure { Timber.e(it, "Failed to toggle app $packageName") }
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
            val result = if (shouldSelectAll) {
                selectedAppRepository.addApps(
                    filteredApps.map { app -> SelectedApp(packageName = app.packageName, appName = app.name, isEnabled = true) },
                )
            } else {
                selectedAppRepository.removeApps(filteredPackageNames)
            }
            result.onFailure { Timber.e(it, "Failed to toggle select-all") }
        }
    }

    /** Selections are already persisted on toggle; Continue/Save only navigates. */
    private fun saveSelectionsAndContinue() {
        val currentState = uiState.value

        if (currentState.selectedPackageNames.isEmpty()) {
            sendEffect(UiEffect.ShowError(UiText.StringResource(R.string.app_selection_hint_select_one)))
            return
        }

        if (currentState.isInitialSetup == true) {
            navigateToMainApp()
        } else {
            navigateBack()
        }
    }
}
