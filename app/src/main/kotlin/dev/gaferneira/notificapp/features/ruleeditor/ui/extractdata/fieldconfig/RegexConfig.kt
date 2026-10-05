package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
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
fun RegexConfig(
    pattern: String,
    captureGroup: Int,
    onPatternChange: (String) -> Unit,
    onCaptureGroupChange: (Int) -> Unit,
    error: UiText?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.Code,
            title = stringResource(R.string.field_config_regex_section),
        )
        OutlinedTextField(
            value = pattern,
            onValueChange = onPatternChange,
            label = { Text(stringResource(R.string.config_regex_pattern)) },
            placeholder = { Text(stringResource(R.string.config_regex_pattern_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = literalKeyboardOptions(ImeAction.Next),
            isError = error != null,
            supportingText = error?.let { { Text(it.asString()) } },
        )
        OutlinedTextField(
            value = captureGroup.toString(),
            onValueChange = { onCaptureGroupChange(it.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
            label = { Text(stringResource(R.string.config_capture_group)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = numberKeyboardOptions(ImeAction.Done),
            keyboardActions = rememberClearFocusKeyboardActions(),
        )
    }
}
