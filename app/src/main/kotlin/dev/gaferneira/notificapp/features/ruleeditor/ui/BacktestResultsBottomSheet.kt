package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.ruleeditor.domain.BacktestMatch
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.NotificationPreviewCard

/**
 * Bottom sheet showing the results of testing a draft rule against captured notification
 * history ("Test against history"). Purely a preview: no data was persisted to produce it.
 *
 * @param results Notifications the draft rule matched, with their extracted field values
 * @param testedCount Total number of historical notifications the rule was tested against
 * @param fields The draft rule's extraction fields, used to resolve field names for display
 * @param onDismiss Called when the sheet should be dismissed
 * @param modifier Modifier for the bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestResultsBottomSheet(
    results: List<BacktestMatch>,
    testedCount: Int,
    fields: List<RuleField>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxHeight(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BacktestResultsContent(results = results, testedCount = testedCount, fields = fields)
    }
}

@Composable
private fun BacktestResultsContent(
    results: List<BacktestMatch>,
    testedCount: Int,
    fields: List<RuleField>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = "Test results",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        BacktestSummary(resultCount = results.size, testedCount = testedCount)

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(results) { result ->
                BacktestResultCard(
                    result = result,
                    fields = fields,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun BacktestSummary(resultCount: Int, testedCount: Int, modifier: Modifier = Modifier) {
    val summary = if (resultCount == 0) {
        "No matches found in the most recent $testedCount captured notification${if (testedCount == 1) "" else "s"}"
    } else {
        "Matched $resultCount of the most recent $testedCount captured notifications"
    }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 16.dp),
    )
}

@Composable
private fun BacktestResultCard(
    result: BacktestMatch,
    fields: List<RuleField>,
    modifier: Modifier = Modifier,
) {
    val extractedFields = fields.mapNotNull { field ->
        result.extractedData[field.id]?.let { value -> field.name to value }
    }
    NotificationPreviewCard(
        appName = result.notification.appName,
        title = result.notification.title,
        content = result.notification.content,
        extractedFields = extractedFields,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun BacktestResultsContentPreview() {
    NotificappTheme {
        BacktestResultsContent(
            results = emptyList(),
            testedCount = 12,
            fields = emptyList(),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}
