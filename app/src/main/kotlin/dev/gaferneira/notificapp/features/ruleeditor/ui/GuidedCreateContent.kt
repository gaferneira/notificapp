package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep
import dev.gaferneira.notificapp.features.ruleeditor.domain.shouldAutoFocusName
import dev.gaferneira.notificapp.features.ruleeditor.domain.toSummary
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.StepIndicator

/**
 * Guided create flow: When, Do, then Name & review. Every step edits the same draft; each step keeps
 * its own scroll position so one step's offset never leaks into another.
 */
@Composable
internal fun GuidedCreateContent(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    val scrollStates = EditorStep.entries.associateWith { rememberScrollState() }
    val stepLabels = listOf(
        stringResource(R.string.rule_editor_step_when),
        stringResource(R.string.rule_editor_step_do),
        stringResource(R.string.rule_editor_step_review),
    )

    Column(modifier = modifier.fillMaxSize()) {
        StepIndicator(
            currentStep = uiState.currentStep.ordinal + 1,
            stepLabels = stepLabels,
            onStepClick = { step -> onEvent(UiEvent.OnStepSelected(EditorStep.entries[step - 1])) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
                } else {
                    slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
                }
            },
            label = "guidedStep",
            modifier = Modifier.weight(1f),
        ) { step ->
            StepPage(scrollState = scrollStates.getValue(step)) {
                when (step) {
                    EditorStep.WHEN -> WhenStep(uiState, onEvent)
                    EditorStep.DO -> DoStep(uiState, onEvent)
                    EditorStep.REVIEW -> ReviewStep(uiState, onEvent)
                }
            }
        }
    }
}

@Composable
private fun StepPage(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
private fun WhenStep(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    SectionHeader(
        title = stringResource(R.string.rule_editor_section_when),
        description = stringResource(R.string.rule_editor_section_when_help),
    )
    WhenEditor(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun DoStep(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    SectionHeader(
        title = stringResource(R.string.rule_editor_section_do),
        description = stringResource(R.string.rule_editor_section_do_help),
    )
    DoEditor(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun ReviewStep(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    // Issues from earlier steps (e.g. the user jumped here) are explained with a way back to fix them.
    if (uiState.earlierStepIssues.isNotEmpty()) {
        NeedsAttentionCard(
            issues = uiState.earlierStepIssues,
            onIssueClick = { onEvent(UiEvent.OnIssueClicked(it)) },
            showJumpButton = true,
        )
    }
    SectionHeader(
        title = stringResource(R.string.rule_editor_section_name),
        description = stringResource(R.string.rule_editor_section_name_help),
    )
    // Entering Review with an empty name puts the cursor in the field (and the keyboard up). The flag
    // survives rotation, and a name that already has content never triggers it.
    val nameFocusRequester = remember { FocusRequester() }
    var hasHandledAutoFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (shouldAutoFocusName(uiState.rule.name, hasHandledAutoFocus)) {
            nameFocusRequester.requestFocus()
        }
        hasHandledAutoFocus = true
    }
    NameFields(uiState = uiState, onEvent = onEvent, nameFocusRequester = nameFocusRequester)

    val summary = remember(uiState.rule) { uiState.rule.toSummary() }
    ReviewSummaryCard(summary = summary, modifier = Modifier.fillMaxWidth())

    EditorSectionCard(title = stringResource(R.string.rule_editor_section_settings)) {
        SettingsSection(uiState = uiState, onEvent = onEvent)
    }
}
