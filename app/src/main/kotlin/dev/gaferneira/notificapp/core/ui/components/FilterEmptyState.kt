package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.LocalAppTokens
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

private val EmptyStateIconSize = 48.dp

/**
 * Centered empty-state block: optional icon, title, message and an optional text action. Stateless
 * and feature-agnostic (plain parameters only) so list screens share one look.
 */
@Composable
fun EmptyStateMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = LocalAppTokens.current.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(EmptyStateIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.lg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(spacing.lg))
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * "Nothing matches your filters" state shared by list screens (Inbox, Data). Shown only when a
 * filter or search is active and yields zero rows; [onClearFilters] resets both.
 */
@Composable
fun FilterEmptyState(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyStateMessage(
        title = stringResource(R.string.filter_empty_title),
        message = stringResource(R.string.filter_empty_message),
        modifier = modifier,
        icon = Icons.Default.FilterListOff,
        actionLabel = stringResource(R.string.filter_empty_clear),
        onAction = onClearFilters,
    )
}

@Preview(showBackground = true)
@Composable
private fun FilterEmptyStatePreview() {
    NotificappTheme(dynamicColor = false) {
        FilterEmptyState(onClearFilters = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun FilterEmptyStateDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        FilterEmptyState(onClearFilters = {})
    }
}
