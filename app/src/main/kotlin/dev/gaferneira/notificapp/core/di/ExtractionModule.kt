package dev.gaferneira.notificapp.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gaferneira.notificapp.core.notification.NotificationNormalizer
import javax.inject.Singleton

/**
 * Dagger module for providing extraction-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExtractionModule {

    /**
     * Provides the NotificationNormalizer.
     *
     * @return NotificationNormalizer instance
     */
    @Provides
    @Singleton
    fun provideNotificationNormalizer(): NotificationNormalizer = NotificationNormalizer()
}
