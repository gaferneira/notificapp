package dev.gaferneira.notificapp.core.ui

import dev.gaferneira.notificapp.core.common.Failure

/**
 * A sealed class representing the different states of a resource (e.g., data loading, success, or error).
 *
 */
sealed class Resource<T>(val data: T? = null) {
    class Loading<T>(data: T? = null, val progress: Int? = null) : Resource<T>(data)
    class Success<T>(data: T?) : Resource<T>(data)
    class Error<T>(val failure: Failure, data: T? = null) : Resource<T>(data)

    fun getDataOrThrow(): T = data ?: throw Exception("Data is null")

    fun isLoading() = this is Loading

    fun getFailureOrNull(): Failure? = if (this is Error) failure else null

    fun <R> map(transform: (value: T) -> R): Resource<R> = when (this) {
        is Loading -> Loading(data?.let { transform(it) })
        is Success -> Success(transform(getDataOrThrow()))
        is Error -> Error(failure, data?.let { transform(it) })
    }
}

fun <T> Result<T>.toResource(): Resource<T> = if (isSuccess) {
    Resource.Success(getOrNull())
} else {
    Resource.Error(Failure.analyzeCause(this.exceptionOrNull()))
}

suspend fun <T> safeResultCall(function: suspend () -> Result<T>): Result<T> = try {
    function()
} catch (e: Exception) {
    Result.failure(e)
}
