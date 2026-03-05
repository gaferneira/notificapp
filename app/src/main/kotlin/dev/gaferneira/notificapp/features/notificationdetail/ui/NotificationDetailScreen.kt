package dev.gaferneira.notificapp.features.notificationdetail.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ExtractionRule
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEffect
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiState
import dev.gaferneira.notificapp.features.notificationdetail.viewmodel.NotificationDetailViewModel
import dev.gaferneira.notificapp.util.timeAgo
import java.util.Date

@Composable
fun NotificationDetailScreen(
    modifier: Modifier = Modifier,
    notificationId: String,
    onNavigate: (UiEffect) -> Unit = {},
    viewModel: NotificationDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Set the notification ID when the screen is first composed
    LaunchedEffect(notificationId) {
        viewModel.setNotificationId(notificationId)
    }

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            onNavigate(effect)
        }
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
                title = { Text("Rules - ${uiState.notification?.appName ?: "Notification"}") },
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
                text = { Text("New Rule") },
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
                        onRetry = { /* Reload */ },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.notification != null -> {
                    NotificationDetailContent(
                        notification = uiState.notification,
                        applicableRules = uiState.applicableRules,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationDetailContent(
    notification: Notification,
    applicableRules: List<NotificationDetailContract.ApplicableRule>,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        item {
            NotificationDataCard(notification = notification)
        }

        // Active Rules Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ACTIVE RULES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )

                // Active Rules Count
                val activeCount = applicableRules.count { it.isActive && it.isApplicable }
                Text(
                    text = "$activeCount active rule${if (activeCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Applicable Rules List
        if (applicableRules.isEmpty()) {
            item {
                EmptyRulesState()
            }
        } else {
            items(
                items = applicableRules,
                key = { it.rule.id },
            ) { applicableRule ->
                RuleCard(
                    applicableRule = applicableRule,
                    onClick = { onEvent(UiEvent.OnEditRuleClicked(applicableRule.rule.id)) },
                    onToggle = { onEvent(UiEvent.OnRuleToggleClicked(applicableRule.rule.id)) },
                )
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
                                modifier = Modifier.size(56.dp),
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
                Text(
                    text = "Raw: ${notification.rawContent.take(200)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    applicableRule: NotificationDetailContract.ApplicableRule,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val rule = applicableRule.rule

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (applicableRule.isApplicable) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Rule Icon (color based on applicability)
            Surface(
                shape = CircleShape,
                color = when {
                    !applicableRule.isActive -> MaterialTheme.colorScheme.surfaceVariant
                    applicableRule.isApplicable -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (applicableRule.isActive) {
                        Icon(
                            imageVector = when {
                                applicableRule.isApplicable -> Icons.Default.CheckCircle
                                else -> Icons.Default.Close
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = when {
                                applicableRule.isApplicable -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                }
            }

            // Rule Info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (applicableRule.isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                rule.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Applicability badge
                if (!applicableRule.isActive) {
                    Text(
                        text = "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (!applicableRule.isApplicable) {
                    Text(
                        text = "Does not apply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRulesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No rules configured",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Create a rule to extract data from this notification",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
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
@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun NotificationDetailScreenPreview() {
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
                applicableRules = listOf(
                    NotificationDetailContract.ApplicableRule(
                        rule = ExtractionRule(
                            id = "1",
                            name = "Purchase notification",
                            description = "Extracts amount & merchant",
                            pattern = "Purchase.*SEK",
                            isActive = true,
                            targetApps = null,
                        ),
                        isApplicable = true,
                        isActive = true,
                    ),
                    NotificationDetailContract.ApplicableRule(
                        rule = ExtractionRule(
                            id = "2",
                            name = "Salary deposit",
                            description = "Captures income events",
                            pattern = "Salary.*deposited",
                            isActive = true,
                            targetApps = null,
                        ),
                        isApplicable = false,
                        isActive = true,
                    ),
                    NotificationDetailContract.ApplicableRule(
                        rule = ExtractionRule(
                            id = "3",
                            name = "Low balance alert",
                            description = "Monitors threshold warnings",
                            pattern = "balance.*low",
                            isActive = false,
                            targetApps = null,
                        ),
                        isApplicable = false,
                        isActive = false,
                    ),
                ),
                isLoading = false,
            ),
            onEvent = {},
        )
    }
}
