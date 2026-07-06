package dev.gaferneira.notificapp.features.ruleeditor.ui.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun JsonPathConfig(
    path: String,
    onPathChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.DataObject,
            title = "JSON PATH",
        )
        OutlinedTextField(
            value = path,
            onValueChange = onPathChange,
            label = { Text("JSON Path") },
            placeholder = { Text("e.g., $.amount") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
    }
}
