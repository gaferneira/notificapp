package dev.gaferneira.notificapp.features.ruledetails.ui

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.RuleStats
import java.text.DateFormat
import java.util.Date

/**
 * Compact "Statistics" card: 7-day / 30-day / total tiles, the last trigger time and the
 * test-mode vs live split. While the rule is in dry run the test-mode count is emphasized, since
 * it is the evidence behind the Go-live banner.
 */
@Composable
internal fun RuleStatsCard(
    stats: RuleStats,
    isDryRun: Boolean,
    modifier: Modifier = Modifier,
) {
    TonalCard(modifier = modifier) {
        SectionTitle(stringResource(R.string.rule_details_section_stats))
        if (stats.totalMatches == 0) {
            Text(
                text = stringResource(R.string.rule_details_stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StatsContent(stats = stats, isDryRun = isDryRun)
        }
        Text(
            text = stringResource(R.string.rule_details_stats_retention_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun StatsContent(stats: RuleStats, isDryRun: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.rule_details_stats_last_7_days),
                value = stats.matchesLast7Days.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.rule_details_stats_last_30_days),
                value = stats.matchesLast30Days.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.rule_details_stats_total),
                value = stats.totalMatches.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        stats.lastTriggeredAt?.let { lastTriggeredAt ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rule_details_stats_last_triggered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatLastTriggered(lastTriggeredAt),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = stringResource(R.string.rule_details_stats_split, stats.testModeMatches, stats.liveMatches),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isDryRun) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isDryRun) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Today 14:32" for same-day triggers, otherwise a medium date + short time, in the app locale. */
@Composable
private fun formatLastTriggered(epochMillis: Long): String {
    // Follow the app's locale (in-app language picker), not just the JVM default.
    val locale = LocalConfiguration.current.locales[0]
    val dateTime = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val time = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val date = Date(epochMillis)
    return if (DateUtils.isToday(epochMillis)) {
        stringResource(R.string.rule_details_stats_today, time.format(date))
    } else {
        dateTime.format(date)
    }
}

// region Previews

private val previewStats = RuleStats(
    totalMatches = 15,
    matchesLast7Days = 4,
    matchesLast30Days = 9,
    liveMatches = 3,
    testModeMatches = 12,
    lastTriggeredAt = System.currentTimeMillis(),
)

@Preview(showBackground = true)
@Composable
private fun RuleStatsCardPreview() {
    NotificappTheme(dynamicColor = false) {
        RuleStatsCard(stats = previewStats, isDryRun = false, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuleStatsCardPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        RuleStatsCard(stats = previewStats, isDryRun = false, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun RuleStatsCardDryRunPreview() {
    NotificappTheme(dynamicColor = false) {
        RuleStatsCard(stats = previewStats, isDryRun = true, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuleStatsCardDryRunPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        RuleStatsCard(stats = previewStats, isDryRun = true, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun RuleStatsCardEmptyPreview() {
    NotificappTheme(dynamicColor = false) {
        RuleStatsCard(stats = RuleStats(), isDryRun = false, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuleStatsCardEmptyPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        RuleStatsCard(stats = RuleStats(), isDryRun = false, modifier = Modifier.padding(16.dp))
    }
}

// endregion
