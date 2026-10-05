package dev.gaferneira.notificapp.features.rules.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.DryRunBadge
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import kotlinx.collections.immutable.persistentListOf

/**
 * Compact row for a rule in the list: icon | name (+ dry-run badge) / description / scope and
 * category | enable switch. Every text line is single-line with ellipsis so long names,
 * descriptions or app names never push the switch off-screen, even at large font scales.
 */
@Composable
internal fun RuleCard(
    rule: Rule,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Only a single-app include-mode rule actually targets this one app; in exclude mode a
    // single listed app is the app the rule does NOT apply to, so it must not drive the icon.
    val primaryApp = rule.targetApps?.takeIf { rule.isIncludeMode && it.size == 1 }?.firstOrNull()

    val appIcon: ImageBitmap? = remember(primaryApp) {
        primaryApp?.let { app ->
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }

    val toggleDescription = stringResource(R.string.rules_card_toggle_cd, rule.name)
    val scopeLabel = ruleScopeLabel(rule, primaryApp)
    val secondaryLabel = rule.category?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.rules_card_scope_category, scopeLabel, it) }
        ?: scopeLabel

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuleCardInfo(
                rule = rule,
                appIcon = appIcon,
                secondaryLabel = secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = rule.isActive,
                onCheckedChange = onToggleActive,
                modifier = Modifier.semantics {
                    contentDescription = toggleDescription
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

/**
 * Short scope label: "N apps" for multi-app rules instead of a raw joined list, which would be an
 * unbounded line length.
 */
@Composable
private fun ruleScopeLabel(rule: Rule, primaryApp: AppInfo?): String {
    val apps = rule.targetApps
    return when {
        apps.isNullOrEmpty() -> stringResource(R.string.rules_scope_all_apps)
        !rule.isIncludeMode -> stringResource(R.string.rules_scope_all_except, apps.size)
        apps.size == 1 -> primaryApp?.name ?: stringResource(R.string.rules_scope_one_app)
        else -> stringResource(R.string.rules_scope_n_apps, apps.size)
    }
}

@Composable
private fun RuleCardInfo(
    rule: Rule,
    appIcon: ImageBitmap?,
    secondaryLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleCardIcon(ruleName = rule.name, appIcon = appIcon)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (rule.isDryRun) {
                    DryRunBadge()
                }
            }
            rule.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RuleCardIcon(ruleName: String, appIcon: ImageBitmap?) {
    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ruleName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// region Previews

private val previewRules = persistentListOf(
    Rule(
        id = "1",
        name = "ICA Purchase",
        description = "Extract purchase info from ICA",
        category = "Finance",
        targetApps = persistentListOf(),
    ),
    Rule(
        id = "2",
        name = "A very long rule name that keeps going well past the available width of the row",
        description = null,
        category = "Finance and personal budgeting with a very long category name",
        isDryRun = true,
        targetApps = persistentListOf(AppInfo("com.example.verylongappnameexample", "An app with a very long display name", null)),
    ),
    Rule(
        id = "3",
        name = "Muted chats",
        description = "A long description that explains in great detail what this rule does for the user",
        isActive = false,
        targetApps = persistentListOf(
            AppInfo("com.a", "A", null),
            AppInfo("com.b", "B", null),
        ),
        isIncludeMode = false,
    ),
)

@Composable
private fun RuleCardPreviewContent() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        previewRules.forEach { RuleCard(rule = it, onClick = {}, onToggleActive = {}) }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RuleCardPreview() {
    NotificappTheme(darkTheme = false, dynamicColor = false) { RuleCardPreviewContent() }
}

@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuleCardPreviewDark() {
    NotificappTheme(darkTheme = true, dynamicColor = false) { RuleCardPreviewContent() }
}

@Preview(showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun RuleCardPreviewLargeFont() {
    NotificappTheme(darkTheme = false, dynamicColor = false) { RuleCardPreviewContent() }
}

// endregion
