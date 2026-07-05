package dev.gaferneira.notificapp.core.di

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dev.gaferneira.notificapp.core.notification.action.DismissActionExecutor
import dev.gaferneira.notificapp.core.notification.action.SaveDataActionExecutor
import dev.gaferneira.notificapp.core.notification.action.SnoozeActionExecutor
import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionType

/**
 * Map key annotation for binding [ActionExecutor] implementations by [ActionType].
 */
@MapKey
annotation class ActionTypeKey(val value: ActionType)

/**
 * Dagger module for binding [ActionExecutor] implementations into a multibinding map, keyed by
 * [ActionType]. `ActionDispatcher` looks up the executor for each action's type.
 *
 * `ActionType.CREATE_ALARM` is deliberately unregistered — per ADR 010, a missing binding makes
 * the dispatcher return `SKIPPED`, which is the correct truthful state for an unimplemented
 * action type.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ActionModule {

    /**
     * Binds the executor for [ActionType.DISMISS_NOTIFICATION].
     */
    @Binds
    @IntoMap
    @ActionTypeKey(ActionType.DISMISS_NOTIFICATION)
    abstract fun bindDismiss(impl: DismissActionExecutor): ActionExecutor

    /**
     * Binds the executor for [ActionType.SNOOZE_NOTIFICATION].
     */
    @Binds
    @IntoMap
    @ActionTypeKey(ActionType.SNOOZE_NOTIFICATION)
    abstract fun bindSnooze(impl: SnoozeActionExecutor): ActionExecutor

    /**
     * Binds the executor for [ActionType.SAVE_DATA].
     */
    @Binds
    @IntoMap
    @ActionTypeKey(ActionType.SAVE_DATA)
    abstract fun bindSaveData(impl: SaveDataActionExecutor): ActionExecutor
}
