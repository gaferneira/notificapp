package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.ExpandableHeader

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
        ExpandableHeader(
            title = stringResource(R.string.advanced_settings_title),
            summary = summary,
            expanded = expanded,
            actionLabel = stringResource(
                if (expanded) R.string.advanced_settings_collapse_cd else R.string.advanced_settings_expand_cd,
            ),
            onClick = { userExpanded = !expanded },
        )

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                content()
            }
        }
    }
}
