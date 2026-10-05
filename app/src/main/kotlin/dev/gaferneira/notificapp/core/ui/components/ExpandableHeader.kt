package dev.gaferneira.notificapp.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R

/**
 * Accessible header row of a collapsible section: a button (min 48dp) that announces its
 * expanded/collapsed state and shows a rotating chevron. The caller owns the state and the revealed
 * content; shared by the rule editor's Advanced settings and the rule details When/Do sections.
 *
 * @param actionLabel Spoken label of the click action, e.g. "Collapse advanced settings" when [expanded]
 * @param summary Optional one-line subtitle, so hidden state stays visible while collapsed
 */
@Composable
fun ExpandableHeader(
    title: String,
    expanded: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "expandableHeaderChevron")
    val stateText = stringResource(if (expanded) R.string.expandable_state_expanded else R.string.expandable_state_collapsed)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = stateText }
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}
