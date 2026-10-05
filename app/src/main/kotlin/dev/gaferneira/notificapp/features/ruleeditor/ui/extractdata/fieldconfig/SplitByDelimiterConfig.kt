package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.literalKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.numberKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.rememberClearFocusKeyboardActions

@Composable
fun SplitByDelimiterConfig(
    delimiter: String,
    takeIndex: Int,
    onDelimiterChange: (String) -> Unit,
    onTakeIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.AutoMirrored.Default.CallSplit,
            title = stringResource(R.string.field_config_delimiter_section),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = delimiter,
                onValueChange = onDelimiterChange,
                label = { Text(stringResource(R.string.config_delimiter)) },
                placeholder = { Text(",") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = literalKeyboardOptions(ImeAction.Next),
            )
            OutlinedTextField(
                value = takeIndex.toString(),
                onValueChange = { onTakeIndexChange(it.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
                label = { Text(stringResource(R.string.config_take_index)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = numberKeyboardOptions(ImeAction.Done),
                keyboardActions = rememberClearFocusKeyboardActions(),
            )
        }
    }
}
