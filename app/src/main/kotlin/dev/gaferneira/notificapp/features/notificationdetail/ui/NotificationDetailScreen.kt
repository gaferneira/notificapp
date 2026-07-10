package dev.gaferneira.notificapp.features.notificationdetail.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.components.DryRunBadge
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExecutionWithDetails
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExtractedFieldDisplay
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.TriggeredActionDisplay
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiState
import dev.gaferneira.notificapp.features.notificationdetail.viewmodel.NotificationDetailViewModel
import dev.gaferneira.notificapp.util.timeAgo
import java.util.Date

@Composable
fun NotificationDetailScreen(
    modifier: Modifier = Modifier,
    notificationId: String,
    viewModel: NotificationDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Set the notification ID when the screen is first composed
    LaunchedEffect(notificationId) {
        viewModel.setNotificationId(notificationId)
    }

    NotificationDetailScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDetailScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.notification?.appName ?: "Notification") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(UiEvent.OnBackClicked) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(UiEvent.OnCreateRuleClicked) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create Rule") },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error,
                        onRetry = { onEvent(UiEvent.OnRetryClicked) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.notification != null -> {
                    NotificationDetailContent(
                        uiState = uiState,
                        onRefreshClicked = { onEvent(UiEvent.OnRefreshClicked) },
                        onCreateRuleClicked = { onEvent(UiEvent.OnCreateRuleClicked) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationDetailContent(
    uiState: UiState,
    onRefreshClicked: () -> Unit,
    onCreateRuleClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notification = uiState.notification ?: return
    val executions = uiState.executions
    val isRefreshing = uiState.isLoading

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        item {
            NotificationDataCard(notification = notification)
        }

        // Executions Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "MATCHED RULES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${executions.size} matched",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(onClick = onRefreshClicked, enabled = !isRefreshing) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-run rules")
                }
            }
        }

        // Executions List
        if (executions.isEmpty()) {
            item {
                EmptyExecutionsState(onCreateRuleClicked = onCreateRuleClicked)
            }
        } else {
            items(
                items = executions,
                key = { it.execution.id },
            ) { execution ->
                ExecutionCard(execution = execution)
            }
        }
    }
}

@Composable
private fun NotificationDataCard(notification: Notification) {
    val context = LocalContext.current

    // Load app icon
    val appIcon: ImageBitmap? = remember(notification.packageName) {
        try {
            context.packageManager.getApplicationIcon(notification.packageName)
                .toBitmap()
                .asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Timestamp
            Text(
                text = Date(notification.timestamp).timeAgo(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                // App Icon
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Text(
                                text = notification.appName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))

                Column {
                    // Title
                    notification.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Content
                    notification.content?.let { content ->
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Raw content (if different)
            if (notification.rawContent != notification.content) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                RawContentSection(rawContent = notification.rawContent)
            }
        }
    }
}

/**
 * Raw notification payload, expandable since it can be much longer than the
 * fixed 200-char preview used to show regardless of actual length.
 */
@Composable
private fun RawContentSection(rawContent: String) {
    var isExpanded by remember { mutableStateOf(false) }
    val previewLength = 200
    val isTruncatable = rawContent.length > previewLength

    Column {
        Text(
            text = if (isExpanded || !isTruncatable) "Raw: $rawContent" else "Raw: ${rawContent.take(previewLength)}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        if (isTruncatable) {
            TextButton(onClick = { isExpanded = !isExpanded }) {
                Text(if (isExpanded) "Show less" else "Show more")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExecutionCard(execution: ExecutionWithDetails) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Rule Name and Timestamp Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = execution.ruleName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (execution.execution.wasDryRun) {
                        Spacer(modifier = Modifier.width(6.dp))
                        DryRunBadge()
                    }
                }
                Text(
                    text = Date(execution.execution.createdAt).timeAgo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (execution.execution.wasDryRun) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No actions ran - this rule is in dry-run mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Extracted Fields Section
            if (execution.extractedFields.isNotEmpty()) {
                Text(
                    text = "Extracted Fields",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))

                execution.extractedFields.forEach { field ->
                    ExtractedFieldRow(field = field)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Triggered Actions Section
            if (execution.triggeredActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    execution.triggeredActions.forEach { action ->
                        ActionChip(action = action)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractedFieldRow(field: ExtractedFieldDisplay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Field name
        Text(
            text = field.fieldName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Field value
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Field type chip
            FieldTypeChip(fieldType = field.fieldType)
        }
    }
}

@Composable
private fun FieldTypeChip(fieldType: RuleField.FieldType) {
    val (backgroundColor, textColor) = when (fieldType) {
        RuleField.FieldType.STRING -> Pair(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.primary,
        )
        RuleField.FieldType.NUMBER -> Pair(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.tertiary,
        )
        RuleField.FieldType.CURRENCY -> Pair(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.secondary,
        )
        RuleField.FieldType.DATE -> Pair(
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.error,
        )
        RuleField.FieldType.BOOLEAN -> Pair(
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.outline,
        )
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        modifier = Modifier,
    ) {
        Text(
            text = fieldType.name,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ActionChip(action: TriggeredActionDisplay) {
    val outcomeColor = when (action.outcome) {
        ActionOutcome.SUCCESS -> MaterialTheme.colorScheme.tertiary
        ActionOutcome.FAILED -> MaterialTheme.colorScheme.error
        ActionOutcome.SKIPPED, null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val outcomeGlyph = when (action.outcome) {
        ActionOutcome.SUCCESS -> "✓"
        ActionOutcome.FAILED -> "✗"
        ActionOutcome.SKIPPED, null -> "—"
    }
    val outcomeDescription = when (action.outcome) {
        ActionOutcome.SUCCESS -> "succeeded"
        ActionOutcome.FAILED -> "failed"
        ActionOutcome.SKIPPED -> "skipped"
        null -> "no outcome data"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = action.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = outcomeGlyph,
                style = MaterialTheme.typography.labelMedium,
                color = outcomeColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "${action.name} $outcomeDescription"
                },
            )
        }
    }
}

@Composable
private fun EmptyExecutionsState(onCreateRuleClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No rules matched",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Create a rule to extract data from notifications like this",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onCreateRuleClicked) {
            Text("Create Rule")
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// Preview
private fun previewPurchaseExecution(): ExecutionWithDetails = ExecutionWithDetails(
    execution = RuleExecution(
        id = "exec1",
        ruleId = "rule1",
        notificationId = "1",
        extractedData = emptyMap(),
        triggeredActions = listOf("action1", "action2"),
        createdAt = System.currentTimeMillis(),
    ),
    ruleName = "Purchase notification",
    extractedFields = listOf(
        ExtractedFieldDisplay(
            fieldName = "amount",
            fieldType = RuleField.FieldType.CURRENCY,
            value = "249.00 SEK",
        ),
        ExtractedFieldDisplay(
            fieldName = "merchant",
            fieldType = RuleField.FieldType.STRING,
            value = "Hemköp Stockholm",
        ),
    ),
    triggeredActions = listOf(
        TriggeredActionDisplay(name = "Save to Database", outcome = ActionOutcome.SUCCESS),
        TriggeredActionDisplay(name = "Show Toast", outcome = ActionOutcome.FAILED),
    ),
)

private fun previewBudgetExecution(): ExecutionWithDetails = ExecutionWithDetails(
    execution = RuleExecution(
        id = "exec2",
        ruleId = "rule2",
        notificationId = "1",
        extractedData = emptyMap(),
        triggeredActions = listOf("action3"),
        createdAt = System.currentTimeMillis() - 3600000,
    ),
    ruleName = "Budget tracker",
    extractedFields = listOf(
        ExtractedFieldDisplay(
            fieldName = "category",
            fieldType = RuleField.FieldType.STRING,
            value = "Groceries",
        ),
        ExtractedFieldDisplay(
            fieldName = "transaction_id",
            fieldType = RuleField.FieldType.NUMBER,
            value = "12345",
        ),
        ExtractedFieldDisplay(
            fieldName = "is_debit",
            fieldType = RuleField.FieldType.BOOLEAN,
            value = "true",
        ),
    ),
    triggeredActions = listOf(
        TriggeredActionDisplay(name = "Log Transaction", outcome = null),
    ),
)

/** Shared preview state with a notification and two rule executions. */
private fun previewUiStateWithExecutions(): UiState = UiState(
    notification = Notification(
        id = "1",
        packageName = "com.bank.app",
        appName = "Bank App",
        title = "Purchase: 249.00 SEK",
        content = "Transaction at Hemköp Stockholm",
        rawContent = "Purchase: 249.00 SEK at Hemköp Stockholm",
        timestamp = System.currentTimeMillis(),
        isProcessed = false,
    ),
    executions = listOf(previewPurchaseExecution(), previewBudgetExecution()),
    isLoading = false,
)

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun NotificationDetailScreenPreview() {
    NotificappTheme {
        NotificationDetailScreenContent(
            uiState = previewUiStateWithExecutions(),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationDetailScreenPreviewDark() {
    NotificappTheme {
        NotificationDetailScreenContent(
            uiState = previewUiStateWithExecutions(),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun NotificationDetailScreenEmptyPreview() {
    NotificappTheme {
        NotificationDetailScreenContent(
            uiState = UiState(
                notification = Notification(
                    id = "1",
                    packageName = "com.bank.app",
                    appName = "Bank App",
                    title = "Purchase: 249.00 SEK",
                    content = "Transaction at Hemköp Stockholm",
                    rawContent = "Purchase: 249.00 SEK at Hemköp Stockholm",
                    timestamp = System.currentTimeMillis(),
                    isProcessed = false,
                ),
                executions = emptyList(),
                isLoading = false,
            ),
            onEvent = {},
        )
    }
}
