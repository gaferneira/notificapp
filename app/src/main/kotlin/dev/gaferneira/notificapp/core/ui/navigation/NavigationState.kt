package dev.gaferneira.notificapp.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Create a navigation state that persists config changes and process death.
 *
 * For Ask71: We have a single top-level route (Home) with a linear back stack.
 *
 * @param startRoute The initial route when the app starts
 */
@Composable
fun rememberNavigationState(
    startRoute: Screen,
): NavigationState {
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(startRoute)

    LaunchedEffect(startRoute) {
        backStack.add(startRoute)
        backStack.removeIf { it != startRoute }
    }
    return remember(startRoute) {
        NavigationState(
            startRoute = startRoute,
            backStack = backStack,
        )
    }
}

/**
 * State holder for navigation state.
 *
 * Simplified for Ask71's single-stack architecture.
 *
 * @param startRoute The initial route that starts the back stack
 * @param backStack The back stack containing the navigation history
 */
class NavigationState(
    val startRoute: NavKey,
    val backStack: NavBackStack<NavKey>,
)
