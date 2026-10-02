package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.getCategoryIcon

/**
 * Tappable starter-rule card: category icon, eyebrow, name, description and a trailing chevron.
 * The whole card is the tap target. Shared by the Home first-run checklist (compact, 2-line
 * description) and the templates gallery (full description).
 *
 * @param style optional [Style] override, e.g. `NotificappStyles.innerPanelStyle` when nested in another card
 * @param shape ripple clip; keep it in sync with the shape set by [style]
 */
@Composable
fun RuleTemplateCard(
    category: String,
    name: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    shape: Shape = MaterialTheme.shapes.large,
    descriptionMaxLines: Int = Int.MAX_VALUE,
) {
    TonalCard(
        modifier = modifier
            .clip(shape)
            .clickable(
                onClickLabel = stringResource(R.string.rule_template_card_click_label),
                onClick = onClick,
            ),
        style = style,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = getCategoryIcon(category))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuleTemplateCardPreview() {
    NotificappTheme(dynamicColor = false) {
        RuleTemplateCard(
            category = "Finance",
            name = "Bank payment tracker",
            description = "Extracts the amount and date from your bank's payment notifications into a searchable dataset.",
            onClick = {},
        )
    }
}
