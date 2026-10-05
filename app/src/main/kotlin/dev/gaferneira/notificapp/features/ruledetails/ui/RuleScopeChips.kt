package dev.gaferneira.notificapp.features.ruledetails.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.AppIcon
import dev.gaferneira.notificapp.domain.model.RuleSummary.AppScope

/** Apps shown as chips before the rest collapses into a "+N more" chip. */
internal const val MAX_SCOPE_CHIPS = 6

/**
 * App scope of a rule: a label (All apps / Only these apps / All apps except) followed by one chip
 * per app (icon + name), at most [MAX_SCOPE_CHIPS] with the remainder as a "+N more" chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RuleScopeChips(scope: AppScope, modifier: Modifier = Modifier) {
    val label = when (scope) {
        AppScope.AllApps -> R.string.rule_details_scope_all
        is AppScope.Only -> R.string.rule_details_scope_include
        is AppScope.Except -> R.string.rule_details_scope_exclude
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val apps = scope.apps
        if (apps.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                apps.take(MAX_SCOPE_CHIPS).forEach { app ->
                    ScopeChip(text = app.name.ifBlank { app.packageName }) {
                        AppIcon(packageName = app.packageName, appName = app.name, size = 20.dp)
                    }
                }
                val hidden = apps.size - MAX_SCOPE_CHIPS
                if (hidden > 0) {
                    ScopeChip(text = pluralStringResource(R.plurals.rule_details_scope_more_apps, hidden, hidden))
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(text: String, leading: (@Composable () -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
