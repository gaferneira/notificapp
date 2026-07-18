package dev.gaferneira.notificapp.core.notification.action.alarm

import dev.gaferneira.notificapp.domain.model.AlarmBackgroundConfig
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_FULLSCREEN_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_MAX_COUNT
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SOUND_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_VIBRATION_ENABLED
import dev.gaferneira.notificapp.domain.model.VibrationPattern

/**
 * What to show and play for a ringing alarm. [title]/[text]/[appName] come from the notification
 * that triggered the alarm so the ongoing notification reflects it, rather than static text.
 *
 * [sourceKey] is the triggering notification's [dev.gaferneira.notificapp.domain.model.Notification.sbnKey]
 * (null when there is no real source, e.g. the rule editor's alarm preview). It lets
 * `AlarmController.stopIfSource` stop only the alarm a given notification actually started when
 * that notification is dismissed, rather than any alarm that happens to be ringing.
 *
 * [snoozeCount] is a direct top-level field (rather than folded into [options]) so
 * `AlarmService.scheduleReRing` can rebuild the re-ring request with a plain
 * `current.copy(snoozeCount = current.snoozeCount + 1)`. The rest of the ring behavior is grouped
 * into [options] to stay clear of detekt's `LongParameterList` constructor threshold (7) — see
 * [AlarmRingOptions]. The `val ... get() = options.x` properties below keep call-site access
 * flat (`request.vibrationEnabled`, `request.snoozeEnabled`, ...) even though the fields live in
 * the nested [options] under the hood.
 */
data class AlarmRequest(
    val soundUri: String?,
    val title: String,
    val text: String,
    val appName: String,
    val snoozeCount: Int = 0,
    val options: AlarmRingOptions = AlarmRingOptions(),
    val sourceKey: String? = null,
) {
    val vibrationEnabled: Boolean get() = options.vibrationEnabled
    val fullScreenEnabled: Boolean get() = options.fullScreenEnabled
    val soundEnabled: Boolean get() = options.soundEnabled
    val vibrationPattern: VibrationPattern get() = options.vibrationPattern
    val snoozeEnabled: Boolean get() = options.snooze.enabled
    val snoozeDurationMinutes: Int get() = options.snooze.durationMinutes
    val snoozeMaxCount: Int get() = options.snooze.maxCount
    val backgroundType: AlarmBackgroundType get() = options.background.type
    val backgroundPresetId: String? get() = options.background.presetId
    val backgroundImageUri: String? get() = options.background.imageUri
    val backgroundImageIsDark: Boolean get() = options.background.imageIsDark
    val suppressNotification: Boolean get() = options.suppressNotification

    /**
     * Whether this alarm can still be snoozed for this ringing episode. Pure over [snoozeCount]/
     * [snoozeEnabled]/[snoozeMaxCount] so it's unit-testable without the `AlarmService` Android
     * `Service` it's used from (this project has no Robolectric in its test stack) - gates both
     * `AlarmService.handleSnooze`'s re-ring decision and `buildNotification()`'s Snooze action.
     */
    val canSnoozeAgain: Boolean get() = snoozeEnabled && snoozeCount < snoozeMaxCount
}

/**
 * Ring-behavior portion of [AlarmRequest], grouped into a single nested value object so
 * [AlarmRequest]'s own constructor stays comfortably under detekt's `LongParameterList`
 * constructor threshold (7) despite carrying every alarm option. [background] reuses the domain's
 * [AlarmBackgroundConfig] directly since its shape (type/presetId/imageUri) already matches what
 * the ringing seam needs.
 */
data class AlarmRingOptions(
    val vibrationEnabled: Boolean = DEFAULT_ALARM_VIBRATION_ENABLED,
    val fullScreenEnabled: Boolean = DEFAULT_ALARM_FULLSCREEN_ENABLED,
    val soundEnabled: Boolean = DEFAULT_ALARM_SOUND_ENABLED,
    val vibrationPattern: VibrationPattern = VibrationPattern.BASIC_CALL,
    val snooze: AlarmSnoozeSettings = AlarmSnoozeSettings(),
    val background: AlarmBackgroundConfig = AlarmBackgroundConfig(),
    /** Skips the ongoing notification (and its foreground promotion) — used by the sheet's preview, which already shows [AlarmActivity][dev.gaferneira.notificapp.features.alarm.ui.AlarmActivity] directly. */
    val suppressNotification: Boolean = false,
)

/**
 * Snooze-specific portion of [AlarmRingOptions]. The count-so-far lives at the top level of
 * [AlarmRequest] ([AlarmRequest.snoozeCount]) since it changes per re-ring, unlike these
 * settings which are fixed for the alarm's whole ringing episode.
 */
data class AlarmSnoozeSettings(
    val enabled: Boolean = DEFAULT_ALARM_SNOOZE_ENABLED,
    val durationMinutes: Int = DEFAULT_ALARM_SNOOZE_DURATION_MINUTES,
    val maxCount: Int = DEFAULT_ALARM_SNOOZE_MAX_COUNT,
)

/**
 * Narrow seam over "start a ringing alarm", kept separate so [AlarmActionExecutor] stays a pure
 * unit testable against a fake, without touching Android's `Context`/`startForegroundService`.
 *
 * The Android implementation ([AndroidAlarmController]) starts the [AlarmService] foreground
 * service, which then owns the ring lifecycle (looping audio, vibration, the ongoing
 * Dismiss/Snooze notification). The executor's job ends once the service is started.
 */
interface AlarmController {
    /**
     * Start ringing the alarm described by [request]. If an alarm is already ringing it is
     * restarted for this request, so at most one alarm rings at a time.
     *
     * Returns `true` if the alarm was started, or `false` if it was refused — notably when app
     * notifications are disabled, since without the ongoing notification the alarm would have no
     * Dismiss/Snooze controls and could not be stopped.
     */
    fun start(request: AlarmRequest): Boolean

    /**
     * Stop the ringing alarm if — and only if — it was started by the notification identified by
     * [sourceKey] (its `sbnKey`). A no-op when no alarm is ringing, or a different one is.
     */
    fun stopIfSource(sourceKey: String)
}
