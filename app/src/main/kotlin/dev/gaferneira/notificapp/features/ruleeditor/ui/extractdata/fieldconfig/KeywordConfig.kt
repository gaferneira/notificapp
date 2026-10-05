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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.literalKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.numberKeyboardOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.rememberClearFocusKeyboardActions

@Composable
fun TextAfterKeywordConfig(
    keyword: String,
    maxLength: Int?,
    onKeywordChange: (String) -> Unit,
    onMaxLengthChange: (Int?) -> Unit,
    error: UiText?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.TextFields,
            title = stringResource(R.string.field_config_keyword_section),
        )
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text(stringResource(R.string.config_keyword)) },
            placeholder = { Text(stringResource(R.string.config_keyword_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = literalKeyboardOptions(ImeAction.Next),
            isError = error != null,
            supportingText = error?.let { { Text(it.asString()) } },
        )
        OutlinedTextField(
            value = maxLength?.toString() ?: "",
            onValueChange = { onMaxLengthChange(it.toIntOrNull()) },
            label = { Text(stringResource(R.string.config_max_length)) },
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
    error: UiText?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.TextFields,
            title = stringResource(R.string.field_config_keyword_section),
        )
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text(stringResource(R.string.config_keyword)) },
            placeholder = { Text(stringResource(R.string.config_keyword_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = literalKeyboardOptions(ImeAction.Done),
            keyboardActions = rememberClearFocusKeyboardActions(),
            isError = error != null,
            supportingText = error?.let { { Text(it.asString()) } },
        )
    }
}
