package dev.gaferneira.notificapp.features.webhook.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.AuthTypeUi
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.HttpMethodUi
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiEffect
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiEvent
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiState
import dev.gaferneira.notificapp.features.webhook.viewmodel.WebhookEditorViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Webhook Editor screen: create/edit a webhook and send a one-shot test payload.
 *
 * @param webhookId The id of the webhook being edited, or null when creating a new one
 * @param onBackClick Back navigation callback
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel for state management
 */
@Composable
fun WebhookEditorScreen(
    webhookId: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebhookEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(webhookId) {
        viewModel.onEvent(UiEvent.LoadWebhook(webhookId))
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.NavigateBack -> onBackClick()
            is UiEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is UiEffect.ShowTestResult -> snackbarHostState.showSnackbar(effect.result.toMessage())
        }
    }

    WebhookEditorScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** authValue is never included here - only the [WebhookTestResult] variant/HTTP code. */
private fun WebhookTestResult.toMessage(): String = when (this) {
    is WebhookTestResult.Success -> "Test sent: HTTP $httpCode"
    is WebhookTestResult.MalformedBody -> "Failed: malformed response"
    is WebhookTestResult.ServerError -> "Failed: HTTP $httpCode"
    is WebhookTestResult.NetworkError -> "Failed: no connection"
    is WebhookTestResult.InvalidHeaderValue -> "Failed: invalid header value"
    is WebhookTestResult.InvalidUrl -> "Failed: invalid URL"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookEditorScreenContent(
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
                title = { Text(if (uiState.id != null) "Edit webhook" else "New webhook") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(paddingValues))
            return@Scaffold
        }

        WebhookEditorFields(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun WebhookEditorFields(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { onEvent(UiEvent.OnNameChanged(it)) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item {
            OutlinedTextField(
                value = uiState.url,
                onValueChange = { onEvent(UiEvent.OnUrlChanged(it)) },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }

        item { HttpMethodSection(uiState = uiState, onEvent = onEvent) }

        item { HeaderRowsSection(uiState = uiState, onEvent = onEvent) }

        item { QueryParamRowsSection(uiState = uiState, onEvent = onEvent) }

        item { AuthSection(uiState = uiState, onEvent = onEvent) }

        if (uiState.errors.isNotEmpty()) {
            item { ErrorsList(errors = uiState.errors) }
        }

        item { WebhookEditorActions(uiState = uiState, onEvent = onEvent) }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/** "Send test payload" + "Save" action row. */
@Composable
private fun WebhookEditorActions(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { onEvent(UiEvent.OnSendTestClicked) },
            enabled = !uiState.isSending,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (uiState.isSending) "Sending..." else "Send test payload")
        }
        Button(
            onClick = { onEvent(UiEvent.OnSave) },
            enabled = !uiState.isSaving,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (uiState.isSaving) "Saving..." else "Save")
        }
    }
}

@Composable
private fun HeaderRowsSection(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Custom headers",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        uiState.headerRows.forEachIndexed { index, (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { onEvent(UiEvent.OnHeaderRowKeyChanged(index, it)) },
                    label = { Text("Key") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { onEvent(UiEvent.OnHeaderRowValueChanged(index, it)) },
                    label = { Text("Value") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { onEvent(UiEvent.OnRemoveHeaderRow(index)) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove header row")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        OutlinedButton(onClick = { onEvent(UiEvent.OnAddHeaderRow) }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text("Add header")
        }
    }
}

@Composable
private fun HttpMethodSection(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    var isMethodDialogVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "HTTP method",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        HttpMethodSelector(
            selectedLabel = uiState.method.label(),
            onClick = { isMethodDialogVisible = true },
        )
    }

    if (isMethodDialogVisible) {
        HttpMethodDialog(
            currentValue = uiState.method,
            onValueSelected = {
                onEvent(UiEvent.OnMethodChanged(it))
                isMethodDialogVisible = false
            },
            onDismiss = { isMethodDialogVisible = false },
        )
    }
}

@Composable
private fun HttpMethodSelector(selectedLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Method", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HttpMethodDialog(
    currentValue: HttpMethodUi,
    onValueSelected: (HttpMethodUi) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HTTP method") },
        text = {
            Column {
                HttpMethodUi.entries.forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueSelected(method) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = method == currentValue,
                            onClick = { onValueSelected(method) },
                        )
                        Text(text = method.label(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun QueryParamRowsSection(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Query parameters",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        uiState.queryParamRows.forEachIndexed { index, (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { onEvent(UiEvent.OnQueryParamRowKeyChanged(index, it)) },
                    label = { Text("Key") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { onEvent(UiEvent.OnQueryParamRowValueChanged(index, it)) },
                    label = { Text("Value") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { onEvent(UiEvent.OnRemoveQueryParamRow(index)) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove query parameter")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        OutlinedButton(onClick = { onEvent(UiEvent.OnAddQueryParamRow) }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text("Add query parameter")
        }
    }
}

@Composable
private fun AuthSection(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    var isAuthDialogVisible by remember { mutableStateOf(false) }
    var isAuthValueVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Authentication",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        AuthTypeSelector(
            selectedLabel = uiState.authType.label(),
            onClick = { isAuthDialogVisible = true },
        )

        if (uiState.authType != AuthTypeUi.NONE) {
            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.authType == AuthTypeUi.API_KEY_HEADER) {
                OutlinedTextField(
                    value = uiState.authHeaderName,
                    onValueChange = { onEvent(UiEvent.OnAuthHeaderNameChanged(it)) },
                    label = { Text("Header name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = uiState.authValue,
                onValueChange = { onEvent(UiEvent.OnAuthValueChanged(it)) },
                label = { Text("Value") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isAuthValueVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isAuthValueVisible = !isAuthValueVisible }) {
                        Icon(
                            imageVector = if (isAuthValueVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isAuthValueVisible) "Hide value" else "Show value",
                        )
                    }
                },
            )
        }
    }

    if (isAuthDialogVisible) {
        AuthTypeDialog(
            currentValue = uiState.authType,
            onValueSelected = {
                onEvent(UiEvent.OnAuthTypeChanged(it))
                isAuthDialogVisible = false
            },
            onDismiss = { isAuthDialogVisible = false },
        )
    }
}

/** Clickable row showing the currently selected auth type; opens [AuthTypeDialog] on tap. */
@Composable
private fun AuthTypeSelector(selectedLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Auth type", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AuthTypeDialog(
    currentValue: AuthTypeUi,
    onValueSelected: (AuthTypeUi) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auth type") },
        text = {
            Column {
                AuthTypeUi.entries.forEach { authType ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueSelected(authType) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = authType == currentValue,
                            onClick = { onValueSelected(authType) },
                        )
                        Text(text = authType.label(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun AuthTypeUi.label(): String = when (this) {
    AuthTypeUi.NONE -> "None"
    AuthTypeUi.API_KEY_HEADER -> "API key header"
    AuthTypeUi.BEARER_TOKEN -> "Bearer token"
}

private fun HttpMethodUi.label(): String = when (this) {
    HttpMethodUi.GET -> "GET"
    HttpMethodUi.POST -> "POST"
    HttpMethodUi.PUT -> "PUT"
    HttpMethodUi.PATCH -> "PATCH"
    HttpMethodUi.DELETE -> "DELETE"
}

@Composable
private fun ErrorsList(errors: ImmutableList<String>) {
    Column {
        errors.forEach { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun WebhookEditorScreenPreview() {
    NotificappTheme {
        WebhookEditorScreenContent(
            uiState = UiState(
                name = "Home Assistant",
                url = "http://homeassistant.local:8123/api/webhook/abc",
                headerRows = persistentListOf("X-Custom" to "value"),
                authType = AuthTypeUi.API_KEY_HEADER,
            ),
            onEvent = {},
            onBackClick = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WebhookEditorScreenPreviewDark() {
    NotificappTheme {
        WebhookEditorScreenContent(
            uiState = UiState(name = "Home Assistant", url = "http://homeassistant.local:8123/api/webhook/abc"),
            onEvent = {},
            onBackClick = {},
        )
    }
}
