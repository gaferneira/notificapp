package dev.gaferneira.notificapp.core.common

import timber.log.Timber
import java.net.SocketException
import java.net.UnknownHostException

/**
 * Sealed Class for handling errors/failures/exceptions.
 */
sealed class Failure(val cause: Throwable?) {
    class ApplicationException(val message: String, cause: Throwable? = null) : Failure(cause)
    class UnknownException(cause: Throwable? = null) : Failure(cause)
    class NetworkConnection(cause: Throwable? = null) : Failure(cause)
    class ServerError(val code: Int, val message: String?, cause: Throwable? = null) : Failure(cause)

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

fun Throwable.toFailure(): Failure = Failure.analyzeCause(this)
