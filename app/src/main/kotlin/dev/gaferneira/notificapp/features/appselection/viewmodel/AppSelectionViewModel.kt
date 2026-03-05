package dev.gaferneira.notificapp.features.appselection.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiEffect
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiEvent
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the App Selection screen.
 *
 * Loads installed apps that can send notifications and allows user to select
 * which ones to monitor. Persists selections to the repository.
 */
@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selectedAppRepository: SelectedAppRepository,
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
            is UiEvent.OnSearchQueryChanged -> {
                updateSearchQuery(event.query)
            }
            is UiEvent.OnContinueClicked -> {
                saveSelectionsAndContinue()
            }
            is UiEvent.OnBackClicked -> {
                // Just navigate back without saving (changes are saved immediately on toggle)
                sendEffect(UiEffect.NavigateBack)
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
     * Load installed apps that can send notifications.
     * Sorts apps initially so selected apps appear at the top.
     * After loading, the order remains stable to avoid jarring UI when selecting.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch(ioDispatcher) {
            setState { copy(isLoading = true, error = null) }

            try {
                val packageManager = context.packageManager
                val installedApps = loadAppsWithNotifications(packageManager)

                // Check which apps are already selected
                val existingApps = selectedAppRepository.getAllApps().getOrNull() ?: emptyList()
                val selectedPackages = existingApps
                    .filter { it.isEnabled }
                    .map { it.packageName }
                    .toSet()

                // Determine if this is initial setup (no apps selected yet)
                val isInitialSetup = existingApps.isEmpty()

                // Sort apps initially: selected first (alphabetically), then unselected (alphabetically)
                // This creates a stable order that won't change during selection
                val sortedApps = installedApps.sortedWith(
                    compareByDescending<AppSelectionContract.AppInfo> { selectedPackages.contains(it.packageName) }
                        .thenBy { it.name.lowercase() },
                )

                withContext(Dispatchers.Default) {
                    setState {
                        copy(
                            availableApps = sortedApps,
                            selectedPackageNames = selectedPackages,
                            isLoading = false,
                            isInitialSetup = isInitialSetup,
                        )
                    }
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
     * Load apps that have notification capability.
     * Uses queryIntentActivities to work correctly on Android 11+ with package visibility restrictions.
     */
    private fun loadAppsWithNotifications(packageManager: PackageManager): List<AppSelectionContract.AppInfo> {
        val apps = mutableListOf<AppSelectionContract.AppInfo>()
        val seenPackages = mutableSetOf<String>()

        // Query all apps that can handle MAIN/LAUNCHER intent
        // This works on Android 11+ with the <queries> declaration in manifest
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
        }

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue

            // Skip duplicates
            if (packageName in seenPackages) continue
            seenPackages.add(packageName)

            // Skip our own app
            if (packageName == context.packageName) continue

            // Load app info
            val applicationInfo = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
            } catch (e: Exception) {
                continue
            }

            // Skip pure system apps that can't send notifications meaningfully
            val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Include user apps and updated system apps
            if (!isSystemApp || isUpdatedSystemApp) {
                val appName = applicationInfo.loadLabel(packageManager).toString()
                    .ifBlank { packageName }

                // Try to determine category
                val category = when {
                    packageName.contains("mail", ignoreCase = true) ||
                        packageName.contains("gmail", ignoreCase = true) ||
                        packageName.contains("outlook", ignoreCase = true) ||
                        packageName.contains("yahoo", ignoreCase = true) -> "Email"
                    packageName.contains("whatsapp", ignoreCase = true) ||
                        packageName.contains("telegram", ignoreCase = true) ||
                        packageName.contains("messenger", ignoreCase = true) ||
                        packageName.contains("slack", ignoreCase = true) ||
                        packageName.contains("discord", ignoreCase = true) -> "Messaging"
                    packageName.contains("bank", ignoreCase = true) ||
                        packageName.contains("finance", ignoreCase = true) ||
                        packageName.contains("revolut", ignoreCase = true) ||
                        packageName.contains("paypal", ignoreCase = true) ||
                        packageName.contains("crypto", ignoreCase = true) -> "Financial"
                    packageName.contains("shop", ignoreCase = true) ||
                        packageName.contains("amazon", ignoreCase = true) ||
                        packageName.contains("ebay", ignoreCase = true) ||
                        packageName.contains("aliexpress", ignoreCase = true) ||
                        packageName.contains("food", ignoreCase = true) ||
                        packageName.contains("deliver", ignoreCase = true) -> "Shopping"
                    packageName.contains("uber", ignoreCase = true) ||
                        packageName.contains("lyft", ignoreCase = true) ||
                        packageName.contains("transport", ignoreCase = true) ||
                        packageName.contains("travel", ignoreCase = true) -> "Transport"
                    else -> null
                }

                apps.add(
                    AppSelectionContract.AppInfo(
                        packageName = packageName,
                        name = appName,
                        category = category,
                    ),
                )
            }
        }

        return apps
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
            sendEffect(UiEffect.NavigateToMainApp)
        } else {
            sendEffect(UiEffect.NavigateBack)
        }
    }
}
