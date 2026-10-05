package dev.gaferneira.notificapp.features.notificationdetail.ui.components

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.coil.AppIconData
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.RuleRef
import dev.gaferneira.notificapp.util.timeAgo
import java.util.Date
import java.util.Locale

private const val COLLAPSED_CONTENT_LINES = 6

/**
 * The captured notification: source app, absolute + relative time, title and selectable content
 * that collapses past [COLLAPSED_CONTENT_LINES] lines. When a rule scrubbed the content it explains
 * why the text is gone instead.
 */
@Composable
internal fun NotificationCard(
    notification: Notification,
    redactedByRule: RuleRef?,
    modifier: Modifier = Modifier,
) {
    TonalCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconAsync(packageName = notification.packageName, appName = notification.appName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = rememberPostedAt(notification.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SelectionContainer {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (notification.isContentRedacted) {
                    RedactedNotice(redactedByRule)
                } else {
                    NotificationText(notification)
                }
            }
        }
    }
}

@Composable
private fun NotificationText(notification: Notification) {
    val title = notification.title
    val content = notification.content
    if (title == null && content == null) {
        Text(
            text = stringResource(R.string.notification_detail_no_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (content != null) {
        ExpandableText(text = content, modifier = Modifier.padding(top = if (title != null) 4.dp else 0.dp))
    }
}

@Composable
private fun ExpandableText(text: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_CONTENT_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.notification_detail_show_less else R.string.notification_detail_show_more,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RedactedNotice(redactedByRule: RuleRef?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (redactedByRule != null) {
                stringResource(R.string.notification_detail_redacted_by_rule, redactedByRule.name)
            } else {
                stringResource(R.string.notification_detail_redacted_generic)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Source-app icon loaded off the main thread by Coil, with a letter avatar while loading or on failure. */
@Composable
private fun AppIconAsync(packageName: String, appName: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = AppIconData(packageName),
        contentDescription = null,
        modifier = modifier.size(48.dp),
        loading = { LetterAvatar(appName) },
        error = { LetterAvatar(appName) },
    )
}

@Composable
private fun LetterAvatar(appName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = appName.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** "Oct 5, 2026, 6:42 PM · 3 hours ago", localized for the current (in-app) language. */
@Composable
private fun rememberPostedAt(timestamp: Long): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val absolute = remember(timestamp, locale) {
        DateFormat.getInstanceForSkeleton("yMMMdjm", locale).format(Date(timestamp))
    }
    val relative = remember(timestamp, locale) { Date(timestamp).timeAgo(locale = locale) }
    return stringResource(R.string.notification_detail_posted_at, absolute, relative)
}
