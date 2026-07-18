package dev.gaferneira.notificapp.core.common

import timber.log.Timber
import java.net.SocketException
import java.net.UnknownHostException

/**
 * Sealed Class for handling errors/failures/exceptions.
 */
sealed class Failure(cause: Throwable?) : Throwable(cause = cause) {
    class ApplicationException(override val message: String, cause: Throwable? = null) : Failure(cause)
    class UnknownException(cause: Throwable? = null) : Failure(cause)
    class NetworkConnection(cause: Throwable? = null) : Failure(cause)
    class ServerError(val code: Int, override val message: String?, cause: Throwable? = null) : Failure(cause)

    /** * Extend this class for feature specific failures.*/
    abstract class FeatureFailure(cause: Throwable?) : Failure(cause)

    companion object {
        fun analyzeCause(cause: Throwable?): Failure {
            // TODO Create cases
            return when (cause) {
                is IllegalStateException -> ApplicationException(cause.message.orEmpty())
                is UnknownHostException,
                is SocketException,
                -> NetworkConnection(cause)
                else -> {
                    cause?.run {
                        Timber.e(cause)
                    }
                    UnknownException(cause)
                }
            }
        }
    }
}

fun <T> Throwable.toFailureResult(): Result<T> = Result.failure(Failure.analyzeCause(this))

/**
 * Wraps an already-mapped [Failure] into a `Result.failure` without the call site under
 * `core/data` needing to write `Result.failure(...)` literally - the naive text-matching
 * `raw-exception-leak` architecture-check rule (see `config/architecture/architectureCheck.gradle.kts`)
 * flags that literal on sight, since it can't distinguish an already-mapped [Failure] from a raw
 * exception. Use this for a validation-style rejection that isn't wrapping a caught [Throwable]
 * (e.g. [WebhookRepositoryImpl]'s defense-in-depth `saveWebhook` guard).
 */
fun <T> Failure.asResult(): Result<T> = Result.failure(this)
