package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.literalKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.numberKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.rememberClearFocusKeyboardActions

@Composable
fun TextAfterKeywordConfig(
    keyword: String,
    maxLength: Int?,
    onKeywordChange: (String) -> Unit,
    onMaxLengthChange: (Int?) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.TextFields,
            title = "KEYWORD",
        )
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text("Keyword") },
            placeholder = { Text("Text to search for") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = literalKeyboardOptions(ImeAction.Next),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
        OutlinedTextField(
            value = maxLength?.toString() ?: "",
            onValueChange = { onMaxLengthChange(it.toIntOrNull()) },
            label = { Text("Max Length (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = numberKeyboardOptions(ImeAction.Done),
            keyboardActions = rememberClearFocusKeyboardActions(),
        )
    }
}

@Composable
fun TextBeforeKeywordConfig(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.TextFields,
            title = "KEYWORD",
        )
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text("Keyword") },
            placeholder = { Text("Text to search for") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = literalKeyboardOptions(ImeAction.Done),
            keyboardActions = rememberClearFocusKeyboardActions(),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
    }
}
