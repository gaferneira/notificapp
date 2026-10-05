package dev.gaferneira.notificapp.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.repository.RuleTemplateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Implementation of [RuleTemplateRepository] backed by the `assets/rules/` folder.
 *
 * Returns `Result<T>` (ADR 006) and reads on an injected IO dispatcher (ADR 008).
 */
internal class RuleTemplateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleTemplateRepository {

    override suspend fun getTemplateText(assetFileName: String): Result<String> = withContext(ioDispatcher) {
        try {
            Result.success(
                context.assets.open("$ASSET_FOLDER/$assetFileName").bufferedReader().use { it.readText() },
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to read rule template: $assetFileName")
            e.toFailureResult()
        }
    }

    private companion object {
        const val ASSET_FOLDER = "rules"
    }
}
