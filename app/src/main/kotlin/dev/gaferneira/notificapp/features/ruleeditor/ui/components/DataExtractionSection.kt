package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.ruleeditor.domain.ExtractionFieldUiModel

/**
 * The "And then" data extraction section.
 * Shows extraction fields with auto-generate button in the header.
 */
@Composable
fun DataExtractionSection(
    fields: List<ExtractionFieldUiModel>,
    onAutoGenerate: () -> Unit,
    onAddField: () -> Unit,
    onRemoveField: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header with title and auto-generate button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "And then",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(Data Extraction)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Define the data fields you want to extract from the notification text using rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Auto-generate button
            IconButton(
                onClick = onAutoGenerate,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Auto-generate extraction",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Field cards
        fields.forEach { field ->
            ExtractionFieldItem(
                field = field,
                onRemove = { onRemoveField(field.id) },
            )
        }

        // Add field button (outlined style)
        AddButton(
            text = "Add field",
            onClick = onAddField,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExtractionFieldItem(
    field: ExtractionFieldUiModel,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor = when (field.name.lowercase()) {
        "amount", "price", "total" -> MaterialTheme.colorScheme.tertiaryContainer
        "merchant", "store", "seller" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val iconTint = when (field.name.lowercase()) {
        "amount", "price", "total" -> MaterialTheme.colorScheme.tertiary
        "merchant", "store", "seller" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    val icon = when (field.name.lowercase()) {
        "amount", "price", "total" -> Icons.Default.Payments
        "merchant", "store", "seller" -> Icons.Default.Store
        else -> Icons.Default.Payments
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Field icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = field.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = field.methodSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Drag handle and delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove field",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DataExtractionSectionPreview() {
    NotificappTheme {
        DataExtractionSection(
            fields = listOf(
                ExtractionFieldUiModel(
                    id = "1",
                    name = "Merchant",
                    methodType = "text_between_anchors",
                    methodSummary = "Regex: (.*) at .*",
                ),
                ExtractionFieldUiModel(
                    id = "2",
                    name = "Amount",
                    methodType = "smart_amount",
                    methodSummary = "Find: currency pattern",
                ),
            ),
            onAutoGenerate = {},
            onAddField = {},
            onRemoveField = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DataExtractionSectionEmptyPreview() {
    NotificappTheme {
        DataExtractionSection(
            fields = emptyList(),
            onAutoGenerate = {},
            onAddField = {},
            onRemoveField = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
