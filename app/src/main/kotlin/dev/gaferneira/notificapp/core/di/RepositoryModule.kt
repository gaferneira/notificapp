package dev.gaferneira.notificapp.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gaferneira.notificapp.core.data.repository.NotificationRepositoryImpl
import dev.gaferneira.notificapp.core.data.repository.RuleExecutionRepositoryImpl
import dev.gaferneira.notificapp.core.data.repository.RuleRepositoryImpl
import dev.gaferneira.notificapp.core.data.repository.SelectedAppRepositoryImpl
import dev.gaferneira.notificapp.core.data.repository.UserPreferencesRepositoryImpl
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository

/**
 * Dagger module for binding repository interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    /**
     * Binds SelectedAppRepository interface to its implementation.
     */
    @Binds
    abstract fun bindSelectedAppRepository(impl: SelectedAppRepositoryImpl): SelectedAppRepository

    /**
     * Binds NotificationRepository interface to its implementation.
     */
    @Binds
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    /**
     * Binds RuleRepository interface to its implementation.
     */
    @Binds
    abstract fun bindRuleRepository(impl: RuleRepositoryImpl): RuleRepository

    /**
     * Binds UserPreferencesRepository interface to its implementation.
     */
    @Binds
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    /**
     * Binds RuleExecutionRepository interface to its implementation.
     */
    @Binds
    abstract fun bindRuleExecutionRepository(impl: RuleExecutionRepositoryImpl): RuleExecutionRepository
}
