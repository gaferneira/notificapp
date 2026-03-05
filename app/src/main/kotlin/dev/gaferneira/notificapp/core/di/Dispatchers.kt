package dev.gaferneira.notificapp.core.di

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Qualifier annotation for coroutine dispatchers.
 * Used to inject specific dispatchers (IO, Default) for testability.
 *
 * ADR: 008-Injected Dispatchers
 */
@Qualifier
@Retention(RUNTIME)
annotation class Dispatcher(val type: DispatcherType)

/**
 * Enum representing the available dispatcher types.
 */
enum class DispatcherType {
    /** For CPU-intensive work */
    Default,

    /** For IO operations (disk, network) */
    IO,
}
