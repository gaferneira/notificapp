package dev.gaferneira.notificapp.features.ruledetails.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.LocalIoDispatcher
import dev.gaferneira.notificapp.core.ui.utils.shareRuleJson
import dev.gaferneira.notificapp.domain.model.RuleStats
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiEffect
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiEvent
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiState
import dev.gaferneira.notificapp.features.ruledetails.viewmodel.RuleDetailsViewModel

@Composable
fun RuleDetailsScreen(
    ruleId: String,
    modifier: Modifier = Modifier,
    viewModel: RuleDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ioDispatcher = LocalIoDispatcher.current
    val snackbarHostState = remember { SnackbarHostState() }
    val chooserTitle = stringResource(R.string.rule_details_share_chooser_title)

    LaunchedEffect(ruleId) {
        viewModel.setRuleId(ruleId)
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.ShareRule -> shareRuleJson(
                context = context,
                ioDispatcher = ioDispatcher,
                ruleName = effect.ruleName,
                json = effect.json,
                chooserTitle = chooserTitle,
            )

            is UiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.asString(context))
        }
    }

    RuleDetailsScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
internal fun RuleDetailsScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { RuleDetailsTopBar(canAct = uiState.rule != null, onEvent = onEvent) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val rule = uiState.rule
            when {
                uiState.error != null -> RuleDetailsErrorState(
                    message = uiState.error.asString(),
                    onRetry = { onEvent(UiEvent.OnRetryClicked) },
                    modifier = Modifier.align(Alignment.Center),
                )

                rule != null -> RuleDetailsContent(
                    rule = rule,
                    stats = uiState.stats,
                    onToggleActive = { onEvent(UiEvent.OnToggleActiveClicked) },
                    onGoLive = { onEvent(UiEvent.OnGoLiveClicked) },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    val dryRunRule = uiState.rule?.takeIf { it.isDryRun }
    if (uiState.showGoLiveConfirmation && dryRunRule != null) {
        GoLiveDialog(
            rule = dryRunRule,
            onConfirm = { onEvent(UiEvent.OnGoLiveConfirmed) },
            onDismiss = { onEvent(UiEvent.OnGoLiveDismissed) },
        )
    }

    if (uiState.showDeleteConfirmation) {
        DeleteRuleDialog(
            ruleName = uiState.rule?.name.orEmpty(),
            onConfirm = { onEvent(UiEvent.OnDeleteConfirmed) },
            onDismiss = { onEvent(UiEvent.OnDeleteDismissed) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDetailsTopBar(
    canAct: Boolean,
    onEvent: (UiEvent) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.rule_details_title)) },
        navigationIcon = {
            IconButton(onClick = { onEvent(UiEvent.OnBackClicked) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.rule_details_back),
                )
            }
        },
        actions = {
            if (canAct) {
                TextButton(onClick = { onEvent(UiEvent.OnEditClicked) }) {
                    Text(stringResource(R.string.rule_details_edit))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.rule_details_more_options),
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rule_details_share)) },
                            onClick = {
                                menuExpanded = false
                                onEvent(UiEvent.OnShareClicked)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.rule_details_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEvent(UiEvent.OnDeleteClicked)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RuleDetailsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.rule_details_retry))
        }
    }
}

@Composable
private fun DeleteRuleDialog(
    ruleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_details_delete_title)) },
        text = { Text(stringResource(R.string.rule_details_delete_message, ruleName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.rule_details_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rule_details_cancel))
            }
        },
    )
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun RuleDetailsScreenPreview() {
    NotificappTheme(dynamicColor = false) {
        RuleDetailsScreenContent(
            uiState = UiState(rule = previewRule(), isLoading = false, stats = RuleStats(totalMatches = 5, matchesLast7Days = 2, matchesLast30Days = 5, testModeMatches = 5, lastTriggeredAt = System.currentTimeMillis())),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuleDetailsScreenPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        RuleDetailsScreenContent(
            uiState = UiState(rule = previewRule(), isLoading = false, stats = RuleStats(totalMatches = 5, matchesLast7Days = 2, matchesLast30Days = 5, testModeMatches = 5, lastTriggeredAt = System.currentTimeMillis())),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RuleDetailsScreenErrorPreview() {
    NotificappTheme(dynamicColor = false) {
        RuleDetailsScreenContent(
            uiState = UiState(isLoading = false, error = UiText.DynamicString("Failed to load rule")),
            onEvent = {},
        )
    }
}

// endregion
