package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.EditorMode
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep

/** Sticky bottom bar of the editor; which actions it offers depends on the mode and, when guided, the step. */
@Composable
internal fun EditorBottomBar(uiState: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    when {
        uiState.mode == EditorMode.SINGLE_PAGE -> ActionBar(
            primaryLabel = stringResource(R.string.rule_editor_action_save),
            primaryEnabled = uiState.canSave,
            onPrimary = { onEvent(UiEvent.OnSaveClicked) },
            secondaryLabel = stringResource(R.string.rule_editor_action_cancel),
            onSecondary = { onEvent(UiEvent.OnBackClicked) },
            modifier = modifier,
        )
        uiState.currentStep == EditorStep.REVIEW -> ActionBar(
            primaryLabel = stringResource(R.string.rule_editor_action_save),
            primaryEnabled = uiState.canSave,
            onPrimary = { onEvent(UiEvent.OnSaveClicked) },
            secondaryLabel = stringResource(R.string.rule_editor_action_previous),
            onSecondary = { onEvent(UiEvent.OnPreviousStepClicked) },
            modifier = modifier,
        )
        else -> ActionBar(
            primaryLabel = stringResource(R.string.rule_editor_action_next),
            primaryEnabled = uiState.canGoNext,
            onPrimary = { onEvent(UiEvent.OnNextStepClicked) },
            secondaryLabel = if (uiState.currentStep.previous() != null) stringResource(R.string.rule_editor_action_previous) else null,
            onSecondary = { onEvent(UiEvent.OnPreviousStepClicked) },
            supportingText = uiState.nextBlockedBy?.nextBlockedMessage?.let { stringResource(it) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ActionBar(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Explains a disabled primary action so it is never a silent dead end.
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Announced politely when the reason appears or changes, so a disabled Next is never silent.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ActionRow(primaryLabel, primaryEnabled, onPrimary, secondaryLabel, onSecondary)
        }
    }
}

@Composable
private fun ActionRow(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondaryLabel != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(secondaryLabel)
            }
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Text(primaryLabel)
        }
    }
}
