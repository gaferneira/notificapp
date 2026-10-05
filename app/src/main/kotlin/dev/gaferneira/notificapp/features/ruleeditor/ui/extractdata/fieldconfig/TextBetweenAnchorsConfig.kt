package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HorizontalRule
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
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.rememberClearFocusKeyboardActions

@Composable
fun TextBetweenAnchorsConfig(
    startAnchor: String,
    endAnchor: String,
    onStartAnchorChange: (String) -> Unit,
    onEndAnchorChange: (String) -> Unit,
    errors: Map<String, UiText>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.HorizontalRule,
            title = stringResource(R.string.field_config_anchors_section),
        )
        OutlinedTextField(
            value = startAnchor,
            onValueChange = onStartAnchorChange,
            label = { Text(stringResource(R.string.config_start_anchor)) },
            placeholder = { Text(stringResource(R.string.config_start_anchor_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = literalKeyboardOptions(ImeAction.Next),
            isError = errors.contains("startAnchor"),
            supportingText = errors["startAnchor"]?.let { { Text(it.asString()) } },
        )
        OutlinedTextField(
            value = endAnchor,
            onValueChange = onEndAnchorChange,
            label = { Text(stringResource(R.string.config_end_anchor)) },
            placeholder = { Text(stringResource(R.string.config_end_anchor_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = literalKeyboardOptions(ImeAction.Done),
            keyboardActions = rememberClearFocusKeyboardActions(),
            isError = errors.contains("endAnchor"),
            supportingText = errors["endAnchor"]?.let { { Text(it.asString()) } },
        )
    }
}
