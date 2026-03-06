package dev.gaferneira.notificapp.features.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEffect
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent
import dev.gaferneira.notificapp.features.onboarding.viewmodel.OnboardingViewModel

/**
 * Onboarding screen with two states:
 * 1. Value Statement - Shows app value proposition
 * 2. Permission Explanation - Explains and requests notification access
 *
 * @param onOpenNotificationSettings Callback to open system notification settings
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel for state management
 */
@Composable
fun OnboardingScreen(
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is UiEffect.OpenNotificationSettings -> onOpenNotificationSettings()
                is UiEffect.NavigateToMainApp -> {
                    // Navigation handled by ViewModel via NavigationHandler
                }
            }
        }
    }

    // Check permission when screen resumes (user returning from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(UiEvent.CheckPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    OnboardingScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingScreenContent(
    uiState: OnboardingContract.UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Loading indicator
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Animated content between steps
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    if (targetState == OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION) {
                        // Going forward: slide in from right, slide out to left
                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        // Going back: slide in from left, slide out to right
                        (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "onboarding_transition",
            ) { step ->
                when (step) {
                    OnboardingContract.OnboardingStep.VALUE_STATEMENT -> {
                        ValueStatementContent(
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION -> {
                        PermissionExplanationContent(
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Value Statement screen - First step of onboarding.
 * Shows the app value proposition with animated notification cards.
 */
@Composable
private fun ValueStatementContent(
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top section with logo and privacy badge
        Column {
            Spacer(modifier = Modifier.height(48.dp))

            // Header with logo and privacy badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "NOTIFICAPP",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // Privacy First badge
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Privacy First",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Animated notification preview cards
            NotificationPreviewCards()
        }

        // Bottom section with text and CTA
        Column {
            // Headline
            Text(
                text = "Extract data from\nyour notifications.",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = MaterialTheme.typography.headlineLarge.fontSize * 1.2,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = "Notificapp lets you capture structured fields like amounts, merchants, or status codes. All data is processed locally on your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.4,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Get Started button
            Button(
                onClick = { onEvent(UiEvent.OnGetStartedClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "PROCESSED LOCALLY ON YOUR DEVICE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Preview notification cards showing different notification types.
 */
@Composable
private fun NotificationPreviewCards() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Card notification (e.g., bank/credit card)
            NotificationCardRow(
                icon = Icons.Default.Notifications,
                iconBackground = MaterialTheme.colorScheme.primary,
                title = "•••• 4532",
                subtitle = "Payment processed",
                statusColor = MaterialTheme.colorScheme.primary,
            )

            // Shopping cart notification
            NotificationCardRow(
                icon = Icons.Default.CheckCircle,
                iconBackground = MaterialTheme.colorScheme.tertiary,
                title = "Order confirmed",
                subtitle = "Tracking: #8392",
                statusColor = MaterialTheme.colorScheme.tertiary,
            )

            // Alert/Error notification
            NotificationCardRow(
                icon = Icons.Default.Settings,
                iconBackground = MaterialTheme.colorScheme.error,
                title = "Delivery exception",
                subtitle = "Rescheduled",
                statusColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Individual notification preview row.
 */
@Composable
private fun NotificationCardRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBackground: Color,
    title: String,
    subtitle: String,
    statusColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon
        Surface(
            shape = CircleShape,
            color = iconBackground.copy(alpha = 0.2f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconBackground,
                )
            }
        }

        // Text content
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Status indicator
        Box(
            modifier = Modifier
                .size(32.dp, 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(statusColor),
        )
    }
}

/**
 * Permission Explanation screen - Second step of onboarding.
 * Explains why we need notification access and provides grant button.
 */
@Composable
private fun PermissionExplanationContent(
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onEvent(UiEvent.OnBackClicked) },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }

            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )

            // Empty space to balance the back button
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "Enable Notification Access",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = MaterialTheme.typography.headlineLarge.fontSize * 1.2,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Permission toggle card
        PermissionToggleCard()

        Spacer(modifier = Modifier.height(24.dp))

        // Permission details
        PermissionDetailItem(
            icon = Icons.Default.Notifications,
            title = "Read notification text to extract data",
            description = "Parses specific information like order numbers or dates.",
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionDetailItem(
            icon = Icons.Default.Settings,
            title = "Only process apps you select",
            description = "You maintain full control over which apps are monitored.",
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionDetailItem(
            icon = Icons.Default.Lock,
            title = "No data ever leaves your device",
            description = "Privacy-first design with 100% local processing.",
        )

        Spacer(modifier = Modifier.weight(1f))

        // Grant Access button
        Button(
            onClick = { onEvent(UiEvent.OnGrantAccessClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = "Grant Access",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Processed locally on your device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Permission toggle card showing the notification access toggle.
 */
@Composable
private fun PermissionToggleCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Access",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Allow app to read alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Toggle (decorative - actual toggle is in system settings)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp, 28.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                        ) {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Illustration area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Decorative gear/cog icon representing system settings
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * Individual permission detail item.
 */
@Composable
private fun PermissionDetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4,
            )
        }
    }
}

// Preview
@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun OnboardingScreenValueStatementPreview() {
    NotificappTheme {
        OnboardingScreenContent(
            uiState = OnboardingContract.UiState(
                currentStep = OnboardingContract.OnboardingStep.VALUE_STATEMENT,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun OnboardingScreenPermissionExplanationPreview() {
    NotificappTheme {
        OnboardingScreenContent(
            uiState = OnboardingContract.UiState(
                currentStep = OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION,
            ),
            onEvent = {},
        )
    }
}
