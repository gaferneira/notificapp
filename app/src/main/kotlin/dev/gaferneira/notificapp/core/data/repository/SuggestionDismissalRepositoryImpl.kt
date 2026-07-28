package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.local.dao.SuggestionDismissalDao
import dev.gaferneira.notificapp.core.data.local.entity.SuggestionDismissalEntity
import dev.gaferneira.notificapp.core.data.local.mapper.SuggestionDismissalMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey
import dev.gaferneira.notificapp.domain.repository.SuggestionDismissalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of [SuggestionDismissalRepository].
 *
 * Follows Repository Pattern (ADR 005) with Result<T> return type (ADR 006) and an injected
 * IO dispatcher (ADR 008).
 */
internal class SuggestionDismissalRepositoryImpl @Inject constructor(
    private val dao: SuggestionDismissalDao,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : SuggestionDismissalRepository {

    override fun observeDismissals(): Flow<Set<SuggestionDismissalKey>> = dao.observeAll()
        .map { entities -> SuggestionDismissalMapper.toDomainSet(entities) }
        .flowOn(ioDispatcher)

    override suspend fun dismiss(packageName: String, normalizedTitleKey: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val key = SuggestionDismissalKey(packageName, normalizedTitleKey)
            val entity: SuggestionDismissalEntity = SuggestionDismissalMapper.toEntity(key, System.currentTimeMillis())
            dao.dismiss(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to dismiss suggestion: $packageName / $normalizedTitleKey")
            e.toFailureResult()
        }
    }
}
