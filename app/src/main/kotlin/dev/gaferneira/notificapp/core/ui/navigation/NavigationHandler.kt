package dev.gaferneira.notificapp.core.ui.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
 * Usage in MainActivity (lifecycle-gated so commands aren't collected while stopped):
 * ```kotlin
 * LaunchedEffect(navigationHandler, navigator, lifecycleOwner) {
 *     lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
 *         navigationHandler.navigationFlow.collect { command ->
 *             when (command) {
 *                 is NavigationCommand.Navigate -> navigator.navigate(command.screen)
 *                 is NavigationCommand.GoBack -> navigator.goBack()
 *             }
 *         }
 *     }
 * }
 * ```
 */
@Singleton
class NavigationHandler @Inject constructor() {
    // A Channel (unlike a SharedFlow with replay = 0) suspends/buffers a send when no collector
    // is attached instead of silently dropping it - commands emitted during a configuration
    // change or between MainActivity's LaunchedEffect restarts are delivered once collection
    // resumes, rather than vanishing into a frames-wide gap with no collector subscribed.
    private val _navigationFlow = Channel<NavigationCommand>(Channel.BUFFERED)
    val navigationFlow = _navigationFlow.receiveAsFlow()

    /**
     * Navigate to a screen.
     *
     * @param screen The destination screen
     */
    suspend fun navigate(screen: Screen) {
        _navigationFlow.send(NavigationCommand.Navigate(screen))
    }

    /**
     * Navigate back.
     */
    suspend fun goBack() {
        _navigationFlow.send(NavigationCommand.GoBack)
    }

    /**
     * Clear back stack and navigate to a screen.
     *
     * @param screen The destination screen
     */
    suspend fun clearAndNavigate(screen: Screen) {
        _navigationFlow.send(NavigationCommand.ClearAndNavigate(screen))
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
