package dev.gaferneira.notificapp.features.rules.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Screen

/**
 * Rules screen for managing extraction rules.
 *
 * This is a placeholder screen that will be implemented in the future.
 * It displays a bottom navigation bar for navigating between main screens.
 *
 * @param navigateTo Navigation callback that accepts a route and optional NavOptions
 * @param modifier Modifier for the screen
 */
@Composable
fun RulesScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.RULES,
                navigateTo = navigateTo,
            )
        },
    ) { innerPadding ->
        Text(
            text = "Rules Screen (Coming Soon)",
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
