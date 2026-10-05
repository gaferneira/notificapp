package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.EditorMode
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState

/**
 * Top bar: Back plus title. Existing rules additionally get an overflow menu holding Delete, so
 * the destructive action never sits next to Save as a bare icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    TopAppBar(
        title = {
            Text(
                stringResource(
                    if (uiState.rule.id == null) R.string.rule_editor_title_new else R.string.rule_editor_title_edit,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = { onEvent(UiEvent.OnBackClicked) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.rule_editor_back),
                )
            }
        },
        actions = {
            if (uiState.mode == EditorMode.SINGLE_PAGE && uiState.canDelete) {
                OverflowMenu(enabled = !uiState.isSaving, onDelete = { onEvent(UiEvent.OnDeleteClicked) })
            }
        },
    )
}

@Composable
private fun OverflowMenu(enabled: Boolean, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.rule_editor_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.rule_editor_menu_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
