package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.WEBHOOK_ALL_BUILTINS
import dev.gaferneira.notificapp.domain.model.WEBHOOK_FIELD_ID_PREFIX
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Grouped "Notification" / "Extracted fields" chip row for inserting a `{{token}}` placeholder
 * into a template-authored action config. Shared by every action whose config is a template
 * string with the webhook TEMPLATE-mode placeholder convention (`SEND_WEBHOOK`'s JSON template,
 * `READ_ALOUD`'s spoken-text template) so the chip list, ordering, and token shape stay identical
 * across both editors.
 */
@Composable
fun TemplateFieldChipRow(
    ruleFields: ImmutableList<RuleField>,
    onTokenSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = remember(ruleFields) {
        (WEBHOOK_ALL_BUILTINS.map { it to it } + ruleFields.map { "$WEBHOOK_FIELD_ID_PREFIX${it.id}" to it.name })
            .toImmutableList()
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = tokens, key = { it.first }) { (token, label) ->
            SuggestionChip(
                onClick = { onTokenSelected(token) },
                label = { Text(label) },
            )
        }
    }
}
