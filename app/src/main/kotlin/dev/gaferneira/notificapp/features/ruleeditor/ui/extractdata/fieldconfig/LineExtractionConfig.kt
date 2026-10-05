package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.numberKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.rememberClearFocusKeyboardActions

@Composable
fun LineExtractionConfig(
    lineNumber: Int,
    onLineNumberChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.AutoMirrored.Default.List,
            title = "LINE",
        )
        OutlinedTextField(
            value = lineNumber.toString(),
            onValueChange = { onLineNumberChange(it.toIntOrNull()?.coerceAtLeast(1) ?: 1) },
            label = { Text("Line Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = numberKeyboardOptions(ImeAction.Done),
            keyboardActions = rememberClearFocusKeyboardActions(),
        )
    }
}
