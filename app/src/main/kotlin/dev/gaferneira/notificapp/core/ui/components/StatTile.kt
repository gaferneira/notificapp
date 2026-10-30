@file:Suppress("MatchingDeclarationName") // Compose component file; the composable shares the file with its small input type

package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/** Tone of a [StatTile]'s supporting line. */
enum class StatTileTone { Normal, Error }

/**
 * A big-number tile: [value] over [label], with an optional [icon] badge and [supportingText] line.
 *
 * Two looks, picked by [inset]:
 * - default: a standalone tonal card (headline number); with [onClick] it gets a chevron and is a
 *   button (role + min 48dp touch target, spoken action [onClickLabel]).
 * - `inset = true`: a compact panel meant to sit inside another card (e.g. 7d/30d/total).
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
    supportingTone: StatTileTone = StatTileTone.Normal,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    inset: Boolean = false,
) {
    val styleState = remember { MutableStyleState(null) }
    val shape = if (inset) MaterialTheme.shapes.medium else MaterialTheme.shapes.large
    val interaction = if (onClick != null) {
        Modifier.clip(shape).clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    val base = if (inset) NotificappStyles.innerPanelStyle else NotificappStyles.tonalCardStyle
    Column(
        modifier = modifier
            .then(interaction)
            .heightIn(min = 48.dp)
            .styleable(styleState, base),
    ) {
        if (icon != null || onClick != null) {
            StatTileTopRow(icon = icon, showChevron = onClick != null)
        }
        Text(
            text = value,
            style = if (inset) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = if (inset) FontWeight.SemiBold else FontWeight.Bold,
            modifier = Modifier.padding(top = if (inset || (icon == null && onClick == null)) 0.dp else 8.dp),
            maxLines = 1,
        )
        Text(
            text = label,
            style = if (inset) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = if (supportingTone == StatTileTone.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatTileTopRow(icon: ImageVector?, showChevron: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon = icon, style = NotificappStyles.iconBadgeSubtleStyle, modifier = Modifier.size(32.dp), iconSize = 18.dp)
        } else {
            Spacer(Modifier)
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StatTilePreviewContent() {
    NotificappTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            StatTile(value = "128", label = "Captured", icon = Icons.Outlined.Inbox, onClick = {})
            StatTile(
                value = "42",
                label = "Actions run",
                icon = Icons.Outlined.Bolt,
                supportingText = "3 failed",
                supportingTone = StatTileTone.Error,
                onClick = {},
            )
            StatTile(value = "0", label = "Last 7 days", inset = true)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatTilePreview() = StatTilePreviewContent()

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatTilePreviewDark() = StatTilePreviewContent()
