package dev.gaferneira.notificapp.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 *
 * This class provides a API for navigation actions that modify the
 * underlying NavigationState's back stack with support for NavOptions.
 *
 * @param state The navigation state to modify
 */
class Navigator(val state: NavigationState) {
    /**
     * Navigate to a new route by adding it to the back stack.
     *
     * @param route The destination route to navigate to
     * @param navOptions Optional navigation options to control the navigation behavior
     */
    fun navigate(route: NavKey, navOptions: NavOptions? = null) {
        if (navOptions != null) {
            // Handle clearStack - clear everything before navigating
            if (navOptions.clearStack) {
                // Add the new route first, then clear everything else
                state.backStack.add(route)
                state.backStack.removeAll { it != route }
                return
            }

            // Handle popUpTo before navigating
            if (navOptions.popUpTo != null) {
                popBackStack(navOptions.popUpTo, navOptions.popUpToInclusive)
            }

            // Handle launchSingleTop - don't add if the same destination is already on top
            if (navOptions.launchSingleTop) {
                val currentTop = state.backStack.lastOrNull()
                if (currentTop != null && currentTop::class == route::class) {
                    // Same destination is already on top, don't add it again
                    // Optionally, you could update the route if it has different parameters
                    return
                }
            }
        }

        // Add the new route to the back stack
        state.backStack.add(route)
    }

    /**
     * Navigate to a new route by adding it to the back stack.
     *
     * @param route The destination route to navigate to
     * @param navBuilder A builder function to configure navigation options
     */
    fun navigate(route: NavKey, navBuilder: NavOptions.Builder.() -> Unit) {
        navigate(route, navOptions(navBuilder))
    }

    /**
     * Navigate back by removing the last entry from the back stack.
     * If the back stack becomes empty, the app will exit.
     *
     * @return true if navigation back was successful, false if already at the start
     */
    fun goBack(): Boolean {
        if (canGoBack()) {
            state.backStack.removeLastOrNull()
            return true
        }
        return false
    }

    /**
     * Pop the back stack up to a specific screen type.
     *
     * @param screenClass The class of the screen to pop up to
     * @param inclusive Whether to also pop the destination screen itself
     * @return true if the screen was found and popped, false otherwise
     */
    fun popBackStack(screenClass: KClass<out Screen>, inclusive: Boolean): Boolean {
        // Find the last index of the screen that is an instance of screenClass
        val lastIndex = state.backStack.indexOfLast { screenClass.isInstance(it) }

        if (lastIndex == -1) {
            // The screen is not in the back stack
            return false
        }

        // Determine how many items to remove
        val itemsToRemove = state.backStack.size - lastIndex - (if (inclusive) 0 else 1)

        repeat(itemsToRemove) {
            state.backStack.removeLastOrNull()
        }
        return true
    }

    /**
     * Pop back stack up to the start destination (clear everything except the first screen)
     */
    fun popToRoot() {
        while (canGoBack()) {
            state.backStack.removeLastOrNull()
        }
    }

    /**
     * Clear the entire back stack and navigate to a new destination
     * Useful for logout flows or starting fresh
     *
     * @param route The new destination to navigate to after clearing
     */
    fun clearAndNavigate(route: NavKey) {
        // Add the new route
        state.backStack.add(route)

        // Clear everything
        state.backStack.removeAll { it != route }
    }

    /**
     * Check if we can navigate back
     */
    fun canGoBack(): Boolean = state.backStack.size > 1

    /**
     * Get the current route (top of the back stack)
     */
    fun currentRoute(): NavKey? = state.backStack.lastOrNull()

    /**
     * Get the size of the back stack
     */
    fun backStackSize(): Int = state.backStack.size
}
