package dev.gaferneira.notificapp.core.ui.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes [json] to a cache file and launches the share sheet for it via a [FileProvider] URI,
 * so the rule can be sent to any app that accepts a text/JSON attachment (Messages, email,
 * a cloud-storage "save to" target, etc.) without granting broader file access.
 *
 * @param chooserTitle Localized title for the system chooser.
 */
suspend fun shareRuleJson(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    ruleName: String,
    json: String,
    chooserTitle: String,
) {
    val file = withContext(ioDispatcher) {
        val exportsDir = File(context.cacheDir, "rule-exports").apply { mkdirs() }
        val fileName = ruleName.ifBlank { "rule" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        File(exportsDir, "$fileName.json").apply { writeText(json) }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
