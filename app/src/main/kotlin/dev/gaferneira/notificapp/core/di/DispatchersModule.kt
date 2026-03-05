package dev.gaferneira.notificapp.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dagger module for providing coroutine dispatchers.
 *
 * Provides IO and Default dispatchers with the @Dispatcher qualifier
 * for dependency injection per ADR 008.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    /**
     * Provides the IO dispatcher for disk and network operations.
     */
    @Provides
    @Dispatcher(DispatcherType.IO)
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Provides the Default dispatcher for CPU-intensive operations.
     */
    @Provides
    @Dispatcher(DispatcherType.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
