package dev.gaferneira.notificapp.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gaferneira.notificapp.core.notification.AndroidNotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider

/**
 * Dagger module for binding the notification-listener status seam, shared by the Inbox,
 * Settings, and Onboarding ViewModels.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationStatusModule {

    /**
     * Binds NotificationListenerStatusProvider interface to its Settings.Secure-backed implementation.
     */
    @Binds
    abstract fun bindNotificationListenerStatusProvider(
        impl: AndroidNotificationListenerStatusProvider,
    ): NotificationListenerStatusProvider
}
