package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.EditorMode
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.InitArgs
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.LoadError
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.RuleEditorViewModel
import kotlinx.coroutines.launch

/**
 * Rule editor host: wires the ViewModel to the content. The same state renders either the guided
 * create flow or the single-page editor, depending on [UiState.mode].
 */
@Composable
fun RuleEditorScreen(
    modifier: Modifier = Modifier,
    ruleId: String? = null,
    notificationId: String? = null,
    templateAssetFileName: String? = null,
    viewModel: RuleEditorViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Idempotent: the ViewModel ignores a repeat of the same args (rotation, recomposition).
    LaunchedEffect(ruleId, notificationId, templateAssetFileName) {
        viewModel.onEvent(UiEvent.Initialize(InitArgs(ruleId, notificationId, templateAssetFileName)))
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.ShowError -> {
                coroutineScope.launch { snackbarHostState.showSnackbar(effect.message.asString(context)) }
            }
        }
    }

    RuleEditorScreenContent(
        uiState = uiState.value,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
internal fun RuleEditorScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val context = LocalContext.current

    // Sheets and dialogs own their back press; the editor's back logic only applies underneath them.
    val isOverlayVisible = uiState.isMatchingLogicSheetVisible ||
        uiState.isAppSheetVisible ||
        uiState.isActionTypePickerVisible ||
        uiState.isActionSheetVisible ||
        uiState.pendingExtractDataRemovalId != null ||
        uiState.backtestResults != null ||
        uiState.showDeleteConfirmation ||
        uiState.showUnsavedChangesDialog
    BackHandler(enabled = !isOverlayVisible) { onEvent(UiEvent.OnBackClicked) }

    // Save/delete failures: shown once, then cleared through OnDismissError.
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(message = error.asString(context), withDismissAction = true)
            onEvent(UiEvent.OnDismissError)
        }
    }

    val isFormVisible = uiState.loadError == null && !uiState.isLoading

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { EditorTopBar(uiState = uiState, onEvent = onEvent) },
        bottomBar = {
            if (isFormVisible) {
                EditorBottomBar(uiState = uiState, onEvent = onEvent)
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            RuleEditorBody(uiState = uiState, onEvent = onEvent)
            RuleEditorBottomSheets(uiState = uiState, onEvent = onEvent)
            RuleEditorDialogs(uiState = uiState, onEvent = onEvent)
        }
    }
}

/** Form (or progress / blocking load error) plus the saving indicator. */
@Composable
private fun BoxScope.RuleEditorBody(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    when {
        uiState.loadError != null -> LoadErrorState(
            error = uiState.loadError,
            onRetry = { onEvent(UiEvent.OnRetryLoadClicked) },
            onBack = { onEvent(UiEvent.OnBackClicked) },
            modifier = Modifier.align(Alignment.Center),
        )
        uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        uiState.mode == EditorMode.GUIDED -> GuidedCreateContent(uiState = uiState, onEvent = onEvent)
        else -> SinglePageEditContent(uiState = uiState, onEvent = onEvent)
    }

    if (uiState.isSaving) {
        val savingDescription = stringResource(R.string.rule_editor_saving)
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .semantics { contentDescription = savingDescription },
        )
    }
}

/** Blocking state shown instead of the form when the initial load fails. */
@Composable
private fun LoadErrorState(
    error: LoadError,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = error.message.asString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (error.canRetry) {
            Button(onClick = onRetry) { Text(stringResource(R.string.rule_editor_load_retry)) }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.rule_editor_load_back)) }
    }
}
