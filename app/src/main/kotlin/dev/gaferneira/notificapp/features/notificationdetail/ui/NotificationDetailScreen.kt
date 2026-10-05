package dev.gaferneira.notificapp.features.notificationdetail.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.domain.model.ExtractionPreview
import dev.gaferneira.notificapp.domain.model.FieldChange
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.LoadStatus
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.Message
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEffect
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiState
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.EmptyExecutions
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.ExecutionCard
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.LoadingState
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.MatchedRulesHeader
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.MessageState
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.NotificationCard
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.TestRulesPreviewSheet
import dev.gaferneira.notificapp.features.notificationdetail.viewmodel.NotificationDetailViewModel

/** Bottom padding that keeps the last card clear of the extended FAB (56dp + 16dp margins + slack). */
private val FabClearance = 96.dp

@Composable
fun NotificationDetailScreen(
    modifier: Modifier = Modifier,
    notificationId: String,
    viewModel: NotificationDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(notificationId) {
        viewModel.setNotificationId(notificationId)
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.NavigateBack -> viewModel.onEvent(UiEvent.OnBackClicked)
            is UiEffect.OpenApp -> if (!launchApp(context, effect.packageName)) {
                snackbarHostState.showSnackbar(context.getString(R.string.notification_detail_message_open_app_failed))
            }
            is UiEffect.ShowMessage -> snackbarHostState.showSnackbar(context.getString(effect.message.textRes()))
        }
    }

    val packageName = uiState.sourcePackageName
    val isAppLaunchable = remember(packageName) {
        packageName != null && context.packageManager.getLaunchIntentForPackage(packageName) != null
    }

    NotificationDetailScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        isAppLaunchable = isAppLaunchable,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@StringRes
private fun Message.textRes(): Int = when (this) {
    Message.EXTRACTED_DATA_UPDATED -> R.string.notification_detail_message_updated
    Message.UPDATE_FAILED -> R.string.notification_detail_message_update_failed
    Message.DELETE_FAILED -> R.string.notification_detail_message_delete_failed
}

/** Starts the app's launcher activity; `false` when it can no longer be launched. */
private fun launchApp(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } ?: return false
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationDetailScreenContent(
    uiState: UiState,
    snackbarHostState: SnackbarHostState,
    isAppLaunchable: Boolean,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val isLoaded = uiState.loadStatus == LoadStatus.LOADED

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.notification?.appName ?: stringResource(R.string.notification_detail_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(UiEvent.OnBackClicked) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (isLoaded) {
                        OverflowMenu(
                            isAppLaunchable = isAppLaunchable,
                            onOpenApp = { onEvent(UiEvent.OnOpenSourceAppClicked) },
                            onDelete = { showDeleteDialog = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // One "Create rule" entry point: the FAB once there are matches, the empty state's button otherwise.
        floatingActionButton = {
            if (isLoaded && uiState.executions.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onEvent(UiEvent.OnCreateRuleClicked) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.notification_detail_create_rule)) },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            NotificationDetailBody(uiState = uiState, onEvent = onEvent)
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            onConfirm = {
                showDeleteDialog = false
                onEvent(UiEvent.OnDeleteNotificationClicked)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun BoxScope.NotificationDetailBody(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    when (uiState.loadStatus) {
        LoadStatus.LOADING -> LoadingState(Modifier.align(Alignment.Center))
        LoadStatus.LOADED -> NotificationDetailContent(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.fillMaxSize(),
        )
        LoadStatus.NOT_FOUND -> MessageState(
            title = stringResource(R.string.notification_detail_not_found_title),
            message = stringResource(R.string.notification_detail_not_found_message),
            actionLabel = stringResource(R.string.notification_detail_go_back),
            onAction = { onEvent(UiEvent.OnBackClicked) },
            modifier = Modifier.align(Alignment.Center),
        )
        LoadStatus.DELETED -> MessageState(
            title = stringResource(R.string.notification_detail_deleted_title),
            message = stringResource(R.string.notification_detail_deleted_message),
            actionLabel = stringResource(R.string.notification_detail_go_back),
            onAction = { onEvent(UiEvent.OnBackClicked) },
            modifier = Modifier.align(Alignment.Center),
        )
        LoadStatus.ERROR -> MessageState(
            title = stringResource(R.string.notification_detail_error_title),
            message = stringResource(R.string.notification_detail_error_message),
            actionLabel = stringResource(R.string.notification_detail_retry),
            onAction = { onEvent(UiEvent.OnRetryClicked) },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun OverflowMenu(
    isAppLaunchable: Boolean,
    onOpenApp: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.notification_detail_more_options_cd),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isAppLaunchable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notification_detail_menu_open_app)) },
                    onClick = {
                        expanded = false
                        onOpenApp()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.notification_detail_menu_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_detail_delete_title)) },
        text = { Text(stringResource(R.string.notification_detail_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.notification_detail_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notification_detail_cancel)) }
        },
    )
}

@Composable
private fun NotificationDetailContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notification = uiState.notification ?: return
    var showUpdateConfirm by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "notification") {
            NotificationCard(notification = notification, redactedByRule = uiState.redactedByRule)
        }
        item(key = "header") {
            MatchedRulesHeader(
                count = uiState.executions.size,
                isTesting = uiState.isPreviewLoading,
                testEnabled = !uiState.isPreviewLoading && !uiState.isApplyingUpdate,
                onTestRules = { onEvent(UiEvent.OnTestRulesClicked) },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (uiState.executions.isEmpty()) {
            item(key = "empty") {
                EmptyExecutions(onCreateRule = { onEvent(UiEvent.OnCreateRuleClicked) })
            }
        } else {
            items(items = uiState.executions, key = { it.execution.id }) { details ->
                ExecutionCard(
                    details = details,
                    onOpenRule = { onEvent(UiEvent.OnOpenRuleClicked(details.ruleId)) },
                )
            }
        }
    }

    if (uiState.preview != null || uiState.previewFailure != null) {
        TestRulesPreviewSheet(
            preview = uiState.preview,
            failure = uiState.previewFailure,
            canApply = uiState.canApplyPreview,
            isApplying = uiState.isApplyingUpdate,
            onApply = { showUpdateConfirm = true },
            onRetry = { onEvent(UiEvent.OnTestRulesClicked) },
            onDismiss = { onEvent(UiEvent.OnDismissPreview) },
        )
    }

    val preview = uiState.preview
    if (showUpdateConfirm && preview != null) {
        UpdateConfirmDialog(
            changedFields = preview.changedFieldCount(),
            onConfirm = {
                showUpdateConfirm = false
                onEvent(UiEvent.OnUpdateExtractedDataClicked)
            },
            onDismiss = { showUpdateConfirm = false },
        )
    }
}

private fun ExtractionPreview.changedFieldCount(): Int = matches
    .filter { it.update != null }
    .sumOf { match -> match.fieldDiffs.count { it.change != FieldChange.UNCHANGED } }

@Composable
private fun UpdateConfirmDialog(changedFields: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_detail_update_title)) },
        text = { Text(pluralStringResource(R.plurals.notification_detail_update_message, changedFields, changedFields)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.notification_detail_update_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notification_detail_cancel)) }
        },
    )
}
