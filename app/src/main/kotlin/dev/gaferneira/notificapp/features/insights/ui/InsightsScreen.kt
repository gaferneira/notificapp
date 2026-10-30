package dev.gaferneira.notificapp.features.insights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.EmptyStateMessage
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.navigation.navOptions
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.features.insights.contract.InsightsEffect
import dev.gaferneira.notificapp.features.insights.contract.InsightsEvent
import dev.gaferneira.notificapp.features.insights.contract.InsightsUiState
import dev.gaferneira.notificapp.features.insights.viewmodel.InsightsViewModel

@Composable
fun InsightsScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is InsightsEffect.NavigateToInbox -> navigateTo(Routes.inbox(initialStatus = effect.initialStatus, since = effect.since, app = effect.app), null)
            is InsightsEffect.NavigateToDataBrowser -> navigateTo(Routes.dataBrowser(ruleId = effect.ruleId, since = effect.since), null)
            is InsightsEffect.NavigateToRuleDetails -> navigateTo(Routes.ruleDetails(effect.ruleId), null)
            InsightsEffect.NavigateToWebhooks -> navigateTo(Routes.webhookList(), null)
            // Rules is a bottom-nav tab: switch to it like the nav bar does instead of stacking it.
            InsightsEffect.NavigateToRules -> navigateTo(Routes.rules(), navOptions { clearStack() })
            is InsightsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message.asString(context))
        }
    }

    InsightsScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        navigateTo = navigateTo,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InsightsScreenContent(
    uiState: InsightsUiState,
    onEvent: (InsightsEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { MainBottomNav(selectedDestination = AppDestinations.DATA, navigateTo = navigateTo) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        val summary = uiState.summary
        when {
            // A failure after a successful load keeps the dashboard (it is reported through a snackbar instead).
            summary == null && uiState.error != null -> InsightsErrorState(
                onRetry = { onEvent(InsightsEvent.OnRetry) },
                modifier = contentModifier.padding(horizontal = 16.dp),
            )
            summary == null -> InsightsLoadingState(modifier = contentModifier)
            else -> InsightsLoaded(uiState = uiState, summary = summary, onEvent = onEvent, modifier = contentModifier)
        }
    }
}

@Composable
private fun InsightsLoaded(
    uiState: InsightsUiState,
    summary: InsightsSummary,
    onEvent: (InsightsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        uiState.actionBreakdown?.let { breakdown ->
            ActionBreakdownSheet(
                state = breakdown,
                onRuleClick = { onEvent(InsightsEvent.OnActionBreakdownRuleClick(it)) },
                onDismiss = { onEvent(InsightsEvent.OnActionBreakdownDismiss) },
            )
        }
        // Shown while a period switch or retry refreshes data that is already on screen.
        if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (uiState.isGlobalEmpty) {
            EmptyStateMessage(
                title = stringResource(R.string.insights_empty_title),
                message = stringResource(R.string.insights_empty_message),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                icon = Icons.Outlined.Insights,
                actionLabel = stringResource(R.string.insights_empty_action),
                onAction = { onEvent(InsightsEvent.OnGoToRulesClick) },
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                InsightsContent(uiState = uiState, summary = summary, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun InsightsLoadingState(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InsightsErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.insights_error_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.insights_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.insights_error_retry)) }
    }
}
