package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.numberKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.rememberClearFocusKeyboardActions

@Composable
fun FixedPositionConfig(
    startIndex: Int,
    endIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onEndIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.Straighten,
            title = stringResource(R.string.field_config_position_section),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = startIndex.toString(),
                onValueChange = { onStartIndexChange(it.toIntOrNull() ?: 0) },
                label = { Text(stringResource(R.string.config_start_index)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = numberKeyboardOptions(ImeAction.Next),
            )
            OutlinedTextField(
                value = endIndex.toString(),
                onValueChange = { onEndIndexChange(it.toIntOrNull() ?: 0) },
                label = { Text(stringResource(R.string.config_end_index)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = numberKeyboardOptions(ImeAction.Done),
                keyboardActions = rememberClearFocusKeyboardActions(),
            )
        }
    }
}
