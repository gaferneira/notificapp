package dev.gaferneira.notificapp.core.di

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dev.gaferneira.notificapp.core.notification.action.AlarmActionExecutor
import dev.gaferneira.notificapp.core.notification.action.AlarmPlayer
import dev.gaferneira.notificapp.core.notification.action.AndroidAlarmPlayer
import dev.gaferneira.notificapp.core.notification.action.AndroidTorchController
import dev.gaferneira.notificapp.core.notification.action.DismissActionExecutor
import dev.gaferneira.notificapp.core.notification.action.FlashAlertActionExecutor
import dev.gaferneira.notificapp.core.notification.action.SaveDataActionExecutor
import dev.gaferneira.notificapp.core.notification.action.SnoozeActionExecutor
import dev.gaferneira.notificapp.core.notification.action.TorchController
import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionType

/**
 * Map key annotation for binding [ActionExecutor] implementations by [ActionType].
 */
@MapKey
annotation class ActionTypeKey(val value: ActionType)

/**
 * Dagger module for binding [ActionExecutor] implementations into a multibinding map, keyed by
 * [ActionType]. `ActionDispatcher` looks up the executor for each action's type. Per ADR 010, a
 * future action type without a registered binding yields `SKIPPED` rather than a silent no-op or
 * a crash.
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

    /**
     * Binds the executor for [ActionType.CREATE_ALARM].
     */
    @Binds
    @IntoMap
    @ActionTypeKey(ActionType.CREATE_ALARM)
    abstract fun bindAlarm(impl: AlarmActionExecutor): ActionExecutor

    /**
     * Binds the real Android-backed [AlarmPlayer] used by [AlarmActionExecutor].
     */
    @Binds
    abstract fun bindAlarmPlayer(impl: AndroidAlarmPlayer): AlarmPlayer

    /**
     * Binds the executor for [ActionType.FLASH_ALERT].
     */
    @Binds
    @IntoMap
    @ActionTypeKey(ActionType.FLASH_ALERT)
    abstract fun bindFlashAlert(impl: FlashAlertActionExecutor): ActionExecutor

    /**
     * Binds the real Android-backed [TorchController] used by [FlashAlertActionExecutor].
     */
    @Binds
    abstract fun bindTorchController(impl: AndroidTorchController): TorchController
}
