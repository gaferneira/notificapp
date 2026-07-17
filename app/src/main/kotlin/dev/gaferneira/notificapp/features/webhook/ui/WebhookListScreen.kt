package dev.gaferneira.notificapp.features.webhook.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.features.webhook.contract.WebhookListContract.UiEffect
import dev.gaferneira.notificapp.features.webhook.contract.WebhookListContract.UiEvent
import dev.gaferneira.notificapp.features.webhook.contract.WebhookListContract.UiState
import dev.gaferneira.notificapp.features.webhook.viewmodel.WebhookListViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Webhook List screen: view, edit, and delete user-defined webhooks.
 *
 * @param onBackClick Back navigation callback
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel for state management
 */
@Composable
fun WebhookListScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebhookListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.NavigateToEditor -> Unit // navigation handled via NavigationHandler
            is UiEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    WebhookListScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookListScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Webhooks") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(UiEvent.OnAddClicked) }) {
                Icon(Icons.Default.Add, contentDescription = "Add webhook")
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.webhooks.isEmpty() -> EmptyState(modifier = Modifier.align(Alignment.Center))
                else -> WebhookList(
                    webhooks = uiState.webhooks,
                    onEdit = { onEvent(UiEvent.OnEditClicked(it)) },
                    onDelete = { onEvent(UiEvent.OnDeleteClicked(it)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (uiState.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { onEvent(UiEvent.OnDismissDeleteConfirmation) },
            title = { Text("Delete webhook?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onEvent(UiEvent.OnConfirmDelete) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(UiEvent.OnDismissDeleteConfirmation) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun WebhookList(
    webhooks: ImmutableList<Webhook>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = webhooks, key = { it.id }) { webhook ->
            WebhookListItem(
                webhook = webhook,
                onClick = { onEdit(webhook.id) },
                onDelete = { onDelete(webhook.id) },
            )
        }
    }
}

@Composable
private fun WebhookListItem(
    webhook: Webhook,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = webhook.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = webhook.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete webhook",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "No webhooks yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tap + to add one",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun WebhookListScreenPreview() {
    NotificappTheme {
        WebhookListScreenContent(
            uiState = UiState(
                webhooks = persistentListOf(
                    Webhook(name = "Home Assistant", url = "http://homeassistant.local:8123/api/webhook/abc"),
                ),
                isLoading = false,
            ),
            onEvent = {},
            onBackClick = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WebhookListScreenEmptyPreviewDark() {
    NotificappTheme {
        WebhookListScreenContent(
            uiState = UiState(webhooks = persistentListOf(), isLoading = false),
            onEvent = {},
            onBackClick = {},
        )
    }
}
