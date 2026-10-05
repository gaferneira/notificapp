package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep
import kotlinx.coroutines.launch

/**
 * One scrollable page for editing an existing rule or finishing a prefilled one (template /
 * notification): Name, When, Do and Settings, each in its own card. A compact "Needs attention"
 * card on top appears only while something blocks saving and jumps to the offending field.
 */
@Composable
internal fun SinglePageEditContent(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val nameFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val whenRequester = remember { BringIntoViewRequester() }
    val doRequester = remember { BringIntoViewRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.blockingIssues.isNotEmpty()) {
            NeedsAttentionCard(
                issues = uiState.blockingIssues,
                onIssueClick = { issue ->
                    coroutineScope.launch {
                        when (issue.step) {
                            // The name is the first field of the page, so scrolling to the top reveals it.
                            EditorStep.REVIEW -> {
                                scrollState.animateScrollTo(0)
                                nameFocusRequester.requestFocus()
                            }
                            EditorStep.WHEN -> whenRequester.bringIntoView()
                            EditorStep.DO -> doRequester.bringIntoView()
                        }
                    }
                },
            )
        }
        SinglePageHints(uiState = uiState, onEvent = onEvent)

        EditorSectionCard(title = stringResource(R.string.rule_editor_section_name)) {
            NameFields(uiState = uiState, onEvent = onEvent, nameFocusRequester = nameFocusRequester)
        }
        EditorSectionCard(
            title = stringResource(R.string.rule_editor_section_when),
            description = stringResource(R.string.rule_editor_section_when_help),
            modifier = Modifier.bringIntoViewRequester(whenRequester),
        ) {
            WhenEditor(uiState = uiState, onEvent = onEvent)
        }
        EditorSectionCard(
            title = stringResource(R.string.rule_editor_section_do),
            description = stringResource(R.string.rule_editor_section_do_help),
            modifier = Modifier.bringIntoViewRequester(doRequester),
        ) {
            DoEditor(uiState = uiState, onEvent = onEvent)
        }
        SettingsSection(uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun SinglePageHints(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    if (uiState.showTemplateHint) {
        TemplateHintCard()
    }
    if (uiState.showPrefillHint) {
        DismissibleHintCard(
            text = stringResource(R.string.rule_editor_prefill_hint),
            dismissLabel = stringResource(R.string.rule_editor_prefill_hint_dismiss),
            onDismiss = { onEvent(UiEvent.OnPrefillHintDismissed) },
        )
    }
}

@Composable
private fun TemplateHintCard(modifier: Modifier = Modifier) {
    TonalCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.rule_editor_template_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
