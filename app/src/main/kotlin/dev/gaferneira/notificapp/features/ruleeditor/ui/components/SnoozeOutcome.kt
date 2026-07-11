package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The user-facing outcome a snooze action produces once a rule matches. */
enum class SnoozeOutcome {
    DELAY_EACH_ONE,
    BATCH_AT_TIME,
    BATCH_INTO_DIGEST,
}

private data class SnoozeOutcomeInfo(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val example: String,
)

private fun SnoozeOutcome.info(): SnoozeOutcomeInfo = when (this) {
    SnoozeOutcome.DELAY_EACH_ONE -> SnoozeOutcomeInfo(
        icon = Icons.Default.Timer,
        title = "Delay each one",
        subtitle = "Deliver every match after a set wait.",
        example = "\"News → arrive 20 min later\"",
    )
    SnoozeOutcome.BATCH_AT_TIME -> SnoozeOutcomeInfo(
        icon = Icons.Default.NightsStay,
        title = "Batch at time",
        subtitle = "Collect and deliver at chosen times.",
        example = "\"LinkedIn → 9:00 AM and 5:00 PM daily\"",
    )
    SnoozeOutcome.BATCH_INTO_DIGEST -> SnoozeOutcomeInfo(
        icon = Icons.Default.Inbox,
        title = "Batch into a digest",
        subtitle = "Recurring checkpoints in a window.",
        example = "\"Release every hour from 9 AM to 6 PM\"",
    )
}

/**
 * Vertical list of selectable cards, one per [SnoozeOutcome]. Selecting a card reveals its
 * inline configuration, rendered by [config] for the currently selected outcome.
 *
 * @param selected Currently selected outcome
 * @param onOutcomeSelected Callback when the user picks a different card
 * @param config Inline configuration content for the selected outcome
 * @param modifier Modifier for the component
 */
@Composable
fun SnoozeOutcomeSelector(
    selected: SnoozeOutcome,
    onOutcomeSelected: (SnoozeOutcome) -> Unit,
    modifier: Modifier = Modifier,
    config: @Composable (SnoozeOutcome) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SnoozeOutcome.entries.forEach { outcome ->
            val isSelected = outcome == selected
            val info = outcome.info()
            SnoozeOutcomeCard(
                icon = info.icon,
                title = info.title,
                subtitle = info.subtitle,
                example = info.example,
                isSelected = isSelected,
                onClick = { onOutcomeSelected(outcome) },
                content = if (isSelected) {
                    { config(outcome) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SnoozeOutcomeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    example: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = isSelected, onClick = onClick)
        }

        if (content != null) {
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
