package dev.gaferneira.notificapp.core.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Extension function to navigate from a ViewModel using NavigationHandler.
 *
 * Usage:
 * ```kotlin
 * class MyViewModel @Inject constructor(
 *     private val navigationHandler: NavigationHandler
 * ) : ViewModel() {
 *     fun onButtonClick() {
 *         navigationHandler.navigateTo(Routes.inbox())
 *     }
 * }
 * ```
 *
 * @param screen The destination screen
 * @param clearStack Whether to clear the back stack before navigating
 */
fun ViewModel.navigateTo(
    navigationHandler: NavigationHandler,
    screen: Screen,
    clearStack: Boolean = false,
) {
    viewModelScope.launch {
        if (clearStack) {
            navigationHandler.clearAndNavigate(screen)
        } else {
            navigationHandler.navigate(screen)
        }
    }
}

/**
 * Extension function to go back from a ViewModel using NavigationHandler.
 *
 * Usage:
 * ```kotlin
 * class MyViewModel @Inject constructor(
 *     private val navigationHandler: NavigationHandler
 * ) : ViewModel() {
 *     fun onBackClick() {
 *         navigationHandler.goBack()
 *     }
 * }
 * ```
 */
fun ViewModel.goBack(navigationHandler: NavigationHandler) {
    viewModelScope.launch {
        navigationHandler.goBack()
    }
}
