package dev.gaferneira.notificapp.features.ruledetails.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.AppIcon
import dev.gaferneira.notificapp.core.ui.components.DryRunBadge
import dev.gaferneira.notificapp.core.ui.components.StatusPill
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.utils.getCategoryIcon
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleStats
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.displayText
import kotlinx.collections.immutable.persistentListOf
import java.text.DateFormat
import java.util.Date

/**
 * Read-only body of the rule details screen: header, statistics, app scope, WHEN, DO and metadata cards.
 */
@Composable
internal fun RuleDetailsContent(
    rule: Rule,
    stats: RuleStats?,
    onToggleActive: () -> Unit,
    onGoLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (rule.isDryRun) {
            item { GoLiveBanner(onGoLive = onGoLive, testModeMatches = stats?.testModeMatches ?: 0) }
        }
        item { RuleHeaderCard(rule = rule, onToggleActive = onToggleActive) }
        if (stats != null) {
            item { RuleStatsCard(stats = stats, isDryRun = rule.isDryRun) }
        }
        item { RuleScopeCard(rule = rule) }
        item { RuleWhenCard(rule = rule) }
        item { RuleDoCard(rule = rule) }
        item { RuleMetadataCard(rule = rule) }
    }
}

@Composable
private fun RuleHeaderCard(
    rule: Rule,
    onToggleActive: () -> Unit,
) {
    TonalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (rule.isDryRun) {
                Spacer(modifier = Modifier.width(8.dp))
                DryRunBadge()
            }
        }
        rule.category?.let { category ->
            StatusPill(
                icon = getCategoryIcon(category),
                text = category,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        rule.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // The whole row is the toggle target so the label is announced with the switch state.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .toggleable(value = rule.isActive, role = Role.Switch, onValueChange = { onToggleActive() }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.rule_details_active_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = rule.isActive, onCheckedChange = null)
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun RuleScopeCard(rule: Rule) {
    val apps = rule.targetApps
    TonalCard {
        SectionTitle(stringResource(R.string.rule_details_section_apps))
        if (apps.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.rule_details_scope_all),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(
                    if (rule.isIncludeMode) R.string.rule_details_scope_include else R.string.rule_details_scope_exclude,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            apps.forEach { app -> AppRow(app = app) }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(packageName = app.packageName, appName = app.name, size = 32.dp)
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RuleWhenCard(rule: Rule) {
    TonalCard {
        SectionTitle(stringResource(R.string.rule_details_section_when))
        if (rule.conditions.isEmpty()) {
            Text(
                text = stringResource(R.string.rule_details_no_conditions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (rule.conditions.size > 1) {
                Text(
                    text = stringResource(
                        if (rule.conditionLogic == ConditionCombinator.ALL) {
                            R.string.rule_details_logic_all
                        } else {
                            R.string.rule_details_logic_any
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            rule.conditions.forEach { condition ->
                Text(
                    text = condition.displayText(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleDoCard(rule: Rule) {
    TonalCard {
        SectionTitle(stringResource(R.string.rule_details_section_do))
        if (rule.actions.isEmpty()) {
            Text(
                text = stringResource(R.string.rule_details_no_actions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rule.actions.forEach { action -> ActionRow(action = action) }
        }
    }
}

@Composable
private fun ActionRow(action: RuleAction) {
    val ui = action.type.ui()
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = ui.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(ui.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(ui.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!action.isEnabled) {
                Text(
                    text = stringResource(R.string.rule_details_action_disabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action.type == ActionType.SAVE_DATA && action.fields.isNotEmpty()) {
            ExtractedFields(fields = action.fields)
        }
    }
}

@Composable
private fun ExtractedFields(fields: List<RuleField>) {
    Column(modifier = Modifier.padding(start = 36.dp, top = 4.dp)) {
        Text(
            text = stringResource(R.string.rule_details_extracted_fields),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        fields.forEach { field ->
            val required = stringResource(R.string.rule_details_field_required)
            Text(
                text = if (field.isRequired) "${field.name} ($required)" else field.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun RuleMetadataCard(rule: Rule) {
    // Follow the app's locale (in-app language picker), not just the JVM default.
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    TonalCard {
        SectionTitle(stringResource(R.string.rule_details_section_info))
        MetadataRow(
            label = stringResource(R.string.rule_details_created),
            value = formatter.format(Date(rule.createdAt)),
        )
        MetadataRow(
            label = stringResource(R.string.rule_details_updated),
            value = formatter.format(Date(rule.updatedAt)),
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Sample rule shared by the screen previews. */
internal fun previewRule(): Rule = Rule(
    id = "preview",
    name = "Bank payment alerts with a deliberately long name to check wrapping",
    description = "Extracts the amount and merchant from card payment notifications.",
    category = "Finance",
    isDryRun = true,
    targetApps = persistentListOf(AppInfo("com.bank.app", "Bank"), AppInfo("com.wallet", "Wallet")),
    conditions = persistentListOf(
        RuleCondition.ContentMatchCondition(
            id = "c1",
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "payment",
        ),
    ),
    actions = persistentListOf(
        RuleAction.createSaveData(
            id = "a1",
            fields = persistentListOf(
                RuleField(
                    id = "f1",
                    name = "Amount",
                    method = RuleField.ExtractionMethod.SmartAmountDetection,
                    isRequired = true,
                ),
            ),
        ),
        RuleAction.createFlashAlert(id = "a2", isEnabled = false),
    ),
)
