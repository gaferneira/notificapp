package dev.gaferneira.notificapp.core.ui.navigation

import dev.gaferneira.notificapp.core.ui.navigation.NavOptions.Builder
import kotlin.reflect.KClass

/**
 * Navigation options for controlling navigation behavior, similar to Navigation 2's NavOptions.
 *
 * Provides options for:
 * - Popping up to a specific destination
 * - Clearing the entire back stack
 * - Single top behavior
 *
 * @property popUpTo The destination to pop up to before navigating (null if not specified)
 * @property popUpToInclusive Whether to also pop the popUpTo destination
 * @property launchSingleTop Whether to avoid multiple copies of the same destination
 * @property clearStack Whether to clear the entire back stack before navigating
 */
data class NavOptions(
    val popUpTo: KClass<out Screen>? = null,
    val popUpToInclusive: Boolean = false,
    val launchSingleTop: Boolean = false,
    val clearStack: Boolean = false,
) {
    /**
     * Builder for NavOptions to provide a fluent API
     */
    class Builder {
        private var popUpTo: KClass<out Screen>? = null
        private var popUpToInclusive: Boolean = false
        private var launchSingleTop: Boolean = false
        private var clearStack: Boolean = false

        /**
         * Pop up to a specific destination before navigating
         */
        fun popUpTo(
            destination: KClass<out Screen>,
            inclusive: Boolean = false,
        ) {
            popUpTo = destination
            popUpToInclusive = inclusive
        }

        fun popUpTo(
            destination: Screen,
            inclusive: Boolean = false,
        ) {
            popUpTo(destination.javaClass.kotlin, inclusive)
        }

        /**
         * Avoid multiple copies of the same destination on the top of the back stack
         */
        fun launchSingleTop() {
            launchSingleTop = true
        }

        /**
         * Clear the entire back stack before navigating.
         * Useful for bottom navigation tab switching.
         */
        fun clearStack() {
            clearStack = true
        }

        fun build(): NavOptions = NavOptions(
            popUpTo = popUpTo,
            popUpToInclusive = popUpToInclusive,
            launchSingleTop = launchSingleTop,
            clearStack = clearStack,
        )
    }
}

/**
 * DSL function to build NavOptions
 *
 * Example:
 * ```
 * navigator.navigate(Routes.inbox(), navOptions {
 *     clearStack()
 * })
 * ```
 */
fun navOptions(block: Builder.() -> Unit): NavOptions = Builder().apply(block).build()
