package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import android.content.res.Configuration
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * Card showing a notification (app name, title, content) followed by its extracted field
 * values as chips. Shared between "Test against history" (one card per historical match) and
 * the Extract-data sheet's live sample-notification preview (one card for the current draft).
 *
 * @param extractedFields resolved fields, in display order
 */
@Composable
fun NotificationPreviewCard(
    appName: String,
    title: String?,
    content: String?,
    extractedFields: List<ExtractedField>,
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

/** Renders resolved fields as suggestion chips; renders nothing when [fields] is empty. */
@Composable
fun ExtractedFieldChips(fields: List<ExtractedField>, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true, name = "PreviewCard Light")
@Preview(showBackground = true, name = "PreviewCard Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationPreviewCardPreview() {
    NotificappTheme(dynamicColor = false) {
        NotificationPreviewCard(
            appName = "ICA Bank",
            title = "Purchase confirmed",
            content = "Totalt: 153,50 kr",
            extractedFields = listOf(ExtractedField("Amount", "153,50 kr")),
            modifier = Modifier.padding(16.dp),
        )
    }
}
