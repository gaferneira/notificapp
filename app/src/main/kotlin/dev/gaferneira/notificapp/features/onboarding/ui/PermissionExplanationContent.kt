package dev.gaferneira.notificapp.features.onboarding.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.IconBadge
import dev.gaferneira.notificapp.core.ui.components.StatusPill
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.theme.LocalAppTokens
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent

/**
 * Permission Explanation screen - Second step of onboarding.
 * Explains why we need notification access and provides grant button.
 */
@Composable
internal fun PermissionExplanationContent(
    showPermissionDeniedHint: Boolean,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // BoxWithConstraints exposes the viewport height so the scrollable Column below can be
    // given `heightIn(min = viewport height)`. That preserves the header-on-top /
    // CTA-pinned-to-bottom layout when the content fits on screen, while still allowing
    // scrolling (instead of clipping) on small screens or at large font scales. A
    // `Spacer(Modifier.weight(1f))` cannot be combined with `verticalScroll` because a
    // scrollable Column is measured with an infinite max height, so the two content groups
    // below are split into their own Column and spaced with the outer `SpaceBetween`
    // arrangement instead, matching the previous visual result at normal font size.
    BoxWithConstraints(modifier = modifier) {
        val minHeight = this.maxHeight
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                PermissionExplanationHeader(onBackClicked = { onEvent(UiEvent.OnBackClicked) })

                Spacer(modifier = Modifier.height(24.dp))

                // Permission toggle card
                PermissionToggleCard()

                Spacer(modifier = Modifier.height(24.dp))

                PermissionDetails()
            }

            PermissionExplanationFooter(
                showPermissionDeniedHint = showPermissionDeniedHint,
                onGrantAccessClicked = { onEvent(UiEvent.OnGrantAccessClicked) },
            )
        }
    }
}

/** Optional denied hint, the Grant Access CTA, and the "processed locally" security footer. */
@Composable
private fun PermissionExplanationFooter(
    showPermissionDeniedHint: Boolean,
    onGrantAccessClicked: () -> Unit,
) {
    Column {
        if (showPermissionDeniedHint) {
            PermissionDeniedHint()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onGrantAccessClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = stringResource(R.string.onboarding_permission_grant_access),
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
                text = stringResource(R.string.onboarding_processed_locally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** Back button, screen title and headline for the Permission Explanation step. */
@Composable
private fun PermissionExplanationHeader(onBackClicked: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClicked) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.onboarding_permission_back_cd),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Text(
            text = stringResource(R.string.onboarding_permission_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        // Empty space to balance the back button
        Spacer(modifier = Modifier.size(48.dp))
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.onboarding_permission_headline),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        lineHeight = MaterialTheme.typography.headlineLarge.fontSize * 1.2,
    )
}

/** The three "why we need this" bullet rows on the Permission Explanation step. */
@Composable
private fun PermissionDetails() {
    PermissionDetailItem(
        icon = Icons.Default.Notifications,
        title = stringResource(R.string.onboarding_permission_detail_1_title),
        description = stringResource(R.string.onboarding_permission_detail_1_description),
    )

    Spacer(modifier = Modifier.height(16.dp))

    PermissionDetailItem(
        icon = Icons.Default.Settings,
        title = stringResource(R.string.onboarding_permission_detail_2_title),
        description = stringResource(R.string.onboarding_permission_detail_2_description),
    )

    Spacer(modifier = Modifier.height(16.dp))

    PermissionDetailItem(
        icon = Icons.Default.Lock,
        title = stringResource(R.string.onboarding_permission_detail_3_title),
        description = stringResource(R.string.onboarding_permission_detail_3_description),
    )
}

/**
 * Permission toggle card showing the notification access status.
 *
 * The actual toggle lives in system settings, not in this app - rendering a
 * fake `Switch`-shaped control here would look interactive but do nothing,
 * which teaches distrust right before the highest-friction step of onboarding.
 */
@Composable
private fun PermissionToggleCard() {
    TonalCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(icon = Icons.Default.Notifications)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_permission_toggle_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.onboarding_permission_toggle_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Non-interactive status - the real toggle is in system settings
                StatusPill(
                    icon = Icons.Default.Settings,
                    text = stringResource(R.string.onboarding_permission_toggle_status),
                )
            }
        }
    }
}

/**
 * Inline warning shown after the user returns from system settings without
 * actually granting notification access, so a failed grant isn't silent.
 */
@Composable
private fun PermissionDeniedHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.onboarding_permission_denied_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * Individual permission detail item.
 */
@Composable
private fun PermissionDetailItem(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon = icon, style = NotificappStyles.iconBadgeSubtleStyle)

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
                color = LocalAppTokens.current.mutedOnBackground,
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4,
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun PermissionExplanationContentPreview() {
    NotificappTheme(dynamicColor = false) {
        PermissionExplanationContent(showPermissionDeniedHint = false, onEvent = {})
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionExplanationContentPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        PermissionExplanationContent(showPermissionDeniedHint = false, onEvent = {})
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun PermissionExplanationContentDeniedHintPreview() {
    NotificappTheme(dynamicColor = false) {
        PermissionExplanationContent(showPermissionDeniedHint = true, onEvent = {})
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionExplanationContentDeniedHintPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        PermissionExplanationContent(showPermissionDeniedHint = true, onEvent = {})
    }
}
