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
import androidx.compose.ui.unit.dp

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
            title = "POSITION",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = startIndex.toString(),
                onValueChange = { onStartIndexChange(it.toIntOrNull() ?: 0) },
                label = { Text("Start Index") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = endIndex.toString(),
                onValueChange = { onEndIndexChange(it.toIntOrNull() ?: 0) },
                label = { Text("End Index") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
    }
}
