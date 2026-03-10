package dev.gaferneira.notificapp.features.inbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.inbox.contract.InboxEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxEvent
import dev.gaferneira.notificapp.features.inbox.contract.InboxListItem
import dev.gaferneira.notificapp.features.inbox.contract.InboxUiState
import dev.gaferneira.notificapp.features.inbox.contract.NotificationItem
import dev.gaferneira.notificapp.features.inbox.viewmodel.InboxViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * Inbox Screen displaying paginated notifications with 2-hour time headers.
 *
 * Uses Jetpack Paging 3 for efficient handling of large datasets:
 * - Notifications loaded in pages from database
 * - 2-hour time headers inserted via insertSeparators()
 * - Automatic prefetching as user scrolls
 * - Load states for refresh and append operations
 */
@Composable
fun InboxScreen(
    modifier: Modifier = Modifier,
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notifications = viewModel.notifications.collectAsLazyPagingItems()

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is InboxEffect.NavigateToNotificationDetail -> {
                    navigateTo(Screen.NotificationDetails(effect.notificationId), null)
                }

                is InboxEffect.ShowError -> {
                    // TODO: Show snackbar or toast
                }
                InboxEffect.ShowPermissionRequired -> {
                    // TODO: Show permission dialog
                }
            }
        }
    }

    InboxScreenContent(
        uiState = uiState,
        notifications = notifications,
        onEvent = viewModel::onEvent,
        navigateTo = navigateTo,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxScreenContent(
    uiState: InboxUiState,
    notifications: LazyPagingItems<InboxListItem>,
    onEvent: (InboxEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Inbox",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Live Notification Feed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter and Sort",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.INBOX,
                navigateTo = navigateTo,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { onEvent(InboxEvent.OnSearchQueryChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search notifications...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )

            // Notification list with paging
            NotificationList(
                notifications = notifications,
                onNotificationClick = { onEvent(InboxEvent.OnNotificationClick(it)) },
                onRetry = { notifications.retry() },
            )
        }

        // Filter Bottom Sheet
        if (showFilterSheet) {
            InboxFilterBottomSheet(
                currentSelectedApps = uiState.selectedApps,
                currentStatusFilter = uiState.statusFilter,
                onFilterApplied = { selectedApps, statusFilter ->
                    onEvent(InboxEvent.OnAppFilterChange(selectedApps, statusFilter))
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false },
            )
        }
    }
}

@Composable
private fun NotificationList(
    notifications: LazyPagingItems<InboxListItem>,
    onNotificationClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    // Handle load states
    when (val refreshState = notifications.loadState.refresh) {
        is LoadState.Loading -> {
            LoadingState()
        }
        is LoadState.Error -> {
            ErrorState(
                message = refreshState.error.message ?: "Failed to load notifications",
                onRetry = onRetry,
            )
        }
        is LoadState.NotLoading -> {
            if (notifications.itemCount == 0) {
                EmptyState()
            } else {
                NotificationColumn(
                    notifications = notifications,
                    onNotificationClick = onNotificationClick,
                )
            }
        }
    }
}

@Composable
private fun NotificationColumn(
    notifications: LazyPagingItems<InboxListItem>,
    onNotificationClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = notifications.itemCount,
            key = notifications.itemKey {
                when (it) {
                    is InboxListItem.TimeHeader -> "header-${it.timestamp}"
                    is InboxListItem.NotificationRow -> it.notification.id
                }
            },
            contentType = notifications.itemContentType {
                when (it) {
                    is InboxListItem.TimeHeader -> "header"
                    is InboxListItem.NotificationRow -> "notification"
                }
            },
        ) { index ->
            val item = notifications[index]
            if (item != null) {
                when (item) {
                    is InboxListItem.TimeHeader -> {
                        TimeHeader(label = item.label)
                    }
                    is InboxListItem.NotificationRow -> {
                        NotificationCard(
                            notification = item.notification,
                            onClick = { onNotificationClick(item.notification.id) },
                        )
                    }
                }
            }
        }

        // Handle append load state (loading more items)
        when (val appendState = notifications.loadState.append) {
            is LoadState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is LoadState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Error loading more notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { notifications.retry() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                // No more items or not loading
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Error loading notifications",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No notifications yet\nEnable notification access in settings",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    // Status based on whether rules have been applied
    val isProcessed = notification.isProcessed
    val statusColor = if (isProcessed) {
        Color(0x802B962B)
    } else {
        MaterialTheme.colorScheme.error // Red for unprocessed
    }

    // Load app icon
    val appIcon: ImageBitmap? = remember(notification.appPackageName) {
        try {
            context.packageManager.getApplicationIcon(notification.appPackageName)
                .toBitmap()
                .asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator bar on the left
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .heightIn(min = 60.dp)
                    .fillMaxHeight()
                    .background(statusColor),
            )

            Spacer(Modifier.width(4.dp))

            // App icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (appIcon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = appIcon,
                        contentDescription = "${notification.appName} icon",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    // Placeholder with first letter of app name
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = notification.appName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                // Top row: App name and timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // App name
                    Text(
                        text = notification.appName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // Timestamp
                    Text(
                        text = notification.formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title
                notification.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Content row: Description and status icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    // Content
                    notification.content?.let { content ->
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Status icon - circular background with icon inside
                    Box(
                        modifier = Modifier
                            .background(
                                color = statusColor,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isProcessed) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "${notification.appliedRulesCount} rule${if (notification.appliedRulesCount > 1) "s" else ""} applied",
                                tint = MaterialTheme.colorScheme.background,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = "No rules applied",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewInboxScreen() {
    val previewItems = listOf(
        InboxListItem.NotificationRow(
            notification = NotificationItem(
                id = "3",
                appName = "WhatsApp",
                appPackageName = "com.whatsapp",
                title = "John Doe",
                content = "Hey, are we still meeting at 8?",
                timestamp = System.currentTimeMillis(),
                formattedTime = "18:42",
                isProcessed = false,
            ),
        ),
        InboxListItem.NotificationRow(
            notification = NotificationItem(
                id = "2",
                appName = "Gmail",
                appPackageName = "com.google.android.gm",
                title = "Meeting reminder",
                content = "Your meeting starts in 15 minutes",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                formattedTime = "18:25",
                isProcessed = true,
            ),
        ),
        InboxListItem.TimeHeader(
            timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
            label = "2 hours ago",
        ),
        InboxListItem.NotificationRow(
            notification = NotificationItem(
                id = "1",
                appName = "Slack",
                appPackageName = "com.Slack",
                title = "New message in #general",
                content = "Alice: Great work on the release!",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
                formattedTime = "16:30",
                isProcessed = true,
            ),
        ),
    )

    NotificappTheme {
        // Create a fake LazyPagingItems for preview
        val pagingData = PagingData.from(previewItems)
        val lazyPagingItems = flowOf(pagingData).collectAsLazyPagingItems()

        InboxScreenContent(
            uiState = InboxUiState(),
            notifications = lazyPagingItems,
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "Inbox Preview - Direct Items")
@Composable
fun PreviewInboxScreenDirect() {
    val previewItems = listOf(
        InboxListItem.NotificationRow(
            notification = NotificationItem(
                id = "3",
                appName = "WhatsApp",
                appPackageName = "com.whatsapp",
                title = "John Doe",
                content = "Hey, are we still meeting at 8?",
                timestamp = System.currentTimeMillis(),
                formattedTime = "18:42",
                isProcessed = false,
            ),
        ),
        InboxListItem.NotificationRow(
            notification = NotificationItem(
                id = "2",
                appName = "Gmail",
                appPackageName = "com.google.android.gm",
                title = "Meeting reminder",
                content = "Your meeting starts in 15 minutes",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                formattedTime = "18:25",
                isProcessed = true,
            ),
        ),
        InboxListItem.TimeHeader(
            timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
            label = "2 hours ago",
        ),
        InboxListItem.NotificationRow(
            notification = NotificationItem(
                id = "1",
                appName = "Slack",
                appPackageName = "com.Slack",
                title = "New message in #general",
                content = "Alice: Great work on the release!",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
                formattedTime = "16:30",
                isProcessed = true,
            ),
        ),
    )

    NotificappTheme {
        // Use a direct LazyColumn for preview instead of LazyPagingItems
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Inbox",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = previewItems.size,
                    key = { index ->
                        when (val item = previewItems[index]) {
                            is InboxListItem.TimeHeader -> "header-${item.timestamp}"
                            is InboxListItem.NotificationRow -> item.notification.id
                        }
                    },
                ) { index ->
                    when (val item = previewItems[index]) {
                        is InboxListItem.TimeHeader -> TimeHeader(label = item.label)
                        is InboxListItem.NotificationRow -> NotificationCard(
                            notification = item.notification,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
