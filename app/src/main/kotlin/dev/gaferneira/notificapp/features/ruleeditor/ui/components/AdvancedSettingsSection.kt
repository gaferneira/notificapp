package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R

/**
 * Collapsible card for options that most users don't need to touch (e.g. cooldown, test mode).
 * Tapping the header row toggles an [AnimatedVisibility] reveal of [content], keeping the default
 * height focused on the common-path controls.
 *
 * @param summary Optional one-line state summary under the title, so hidden state stays visible
 * @param expandInitially When true the section starts (and, once seen true, stays) expanded; the
 *   user's own toggling wins afterwards. Survives rotation.
 * @param content The advanced controls, revealed when expanded
 * @param modifier Modifier for the component
 */
@Composable
fun AdvancedSettingsSection(
    modifier: Modifier = Modifier,
    summary: String? = null,
    expandInitially: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Latches on: a later non-default -> default change inside the section must not collapse it.
    var autoExpanded by rememberSaveable { mutableStateOf(expandInitially) }
    var userExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(expandInitially) { if (expandInitially) autoExpanded = true }
    val expanded = userExpanded ?: autoExpanded

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        AdvancedHeader(
            summary = summary,
            expanded = expanded,
            onClick = { userExpanded = !expanded },
        )

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun AdvancedHeader(summary: String?, expanded: Boolean, onClick: () -> Unit) {
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "advancedSettingsChevron")
    val actionLabel = stringResource(
        if (expanded) R.string.advanced_settings_collapse_cd else R.string.advanced_settings_expand_cd,
    )
    val stateText = stringResource(
        if (expanded) R.string.advanced_settings_state_expanded else R.string.advanced_settings_state_collapsed,
    )
    Row(
        modifier = Modifier
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
                text = stringResource(R.string.advanced_settings_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
