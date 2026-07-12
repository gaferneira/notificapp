package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.toFailureResult
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Runs [block], mapping any thrown exception to a [Result.failure] carrying a mapped
 * [dev.gaferneira.notificapp.core.common.Failure] (per ADR 006) instead of the raw exception.
 * [CancellationException] always propagates, so structured concurrency stays intact.
 *
 * Centralizing the catch here means `TooGenericExceptionCaught` is a single, reviewed suppression
 * point instead of one per repository method (per TD-16 boy-scout policy).
 */
@Suppress("TooGenericExceptionCaught") // sole authorized broad-catch boundary for repository DB errors (TD-16)
internal inline fun <T> dbCatching(operation: String, block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.e(e, operation)
    e.toFailureResult()
}
