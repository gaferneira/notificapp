package dev.gaferneira.notificapp.core.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Navigation commands for ViewModel-driven navigation.
 *
 * This handler allows ViewModels to trigger navigation without directly
 * accessing UI-layer navigation components.
 *
 * Usage in ViewModel:
 * ```kotlin
 * class MyViewModel @Inject constructor(
 *     private val navigationHandler: NavigationHandler
 * ) : ViewModel() {
 *     fun onItemClick(id: String) {
 *         viewModelScope.launch {
 *             navigationHandler.navigate(Routes.notificationDetails(id))
 *         }
 *     }
 * }
 * ```
 *
 * Usage in MainActivity:
 * ```kotlin
 * LaunchedEffect(Unit) {
 *     navigationHandler.navigationFlow.collect { command ->
 *         when (command) {
 *             is NavigationCommand.Navigate -> navigator.navigate(command.screen)
 *             is NavigationCommand.GoBack -> navigator.goBack()
 *         }
 *     }
 * }
 * ```
 */
@Singleton
class NavigationHandler @Inject constructor() {
    private val _navigationFlow = MutableSharedFlow<NavigationCommand>(extraBufferCapacity = 1)
    val navigationFlow = _navigationFlow.asSharedFlow()

    /**
     * Navigate to a screen.
     *
     * @param screen The destination screen
     */
    suspend fun navigate(screen: Screen) {
        _navigationFlow.emit(NavigationCommand.Navigate(screen))
    }

    /**
     * Navigate back.
     */
    suspend fun goBack() {
        _navigationFlow.emit(NavigationCommand.GoBack)
    }

    /**
     * Clear back stack and navigate to a screen.
     *
     * @param screen The destination screen
     */
    suspend fun clearAndNavigate(screen: Screen) {
        _navigationFlow.emit(NavigationCommand.ClearAndNavigate(screen))
    }
}

/**
 * Navigation commands for the navigation handler.
 */
sealed class NavigationCommand {
    /**
     * Navigate to a specific screen.
     */
    data class Navigate(val screen: Screen) : NavigationCommand()

    /**
     * Go back to the previous screen.
     */
    object GoBack : NavigationCommand()

    /**
     * Clear the back stack and navigate to a screen.
     */
    data class ClearAndNavigate(val screen: Screen) : NavigationCommand()
}
