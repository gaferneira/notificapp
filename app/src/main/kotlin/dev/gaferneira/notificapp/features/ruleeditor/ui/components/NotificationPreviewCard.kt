package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Card showing a notification (app name, title, content) followed by its extracted field
 * values as chips. Shared between "Test against history" (one card per historical match) and
 * the Extract-data sheet's live sample-notification preview (one card for the current draft).
 *
 * @param extractedFields resolved (field name, extracted value) pairs, in display order
 */
@Composable
fun NotificationPreviewCard(
    appName: String,
    title: String?,
    content: String?,
    extractedFields: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title ?: content ?: "(no content)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            content?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ExtractedFieldChips(fields = extractedFields, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Renders resolved (name, value) pairs as suggestion chips; renders nothing when [fields] is empty. */
@Composable
fun ExtractedFieldChips(fields: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    if (fields.isEmpty()) return

    Column(modifier = modifier) {
        fields.forEach { (name, value) ->
            SuggestionChip(
                onClick = {},
                label = { Text("$name: $value") },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
