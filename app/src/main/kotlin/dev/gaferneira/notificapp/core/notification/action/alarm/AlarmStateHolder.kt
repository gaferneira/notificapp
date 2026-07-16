package dev.gaferneira.notificapp.core.notification.action.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is an alarm currently ringing", shared between [AlarmService] (which
 * sets it) and the alarm UI (which observes it to finish when the alarm stops elsewhere).
 *
 * `@Singleton` so both surfaces see the same state for the life of the process — the same reason
 * [AndroidAlarmPlayer] is a singleton. Mirrors the holder style of `SystemNotificationControllerHolder`.
 */
@Singleton
class AlarmStateHolder @Inject constructor() {

    private val _isRinging = MutableStateFlow(false)

    /** Emits `true` while an alarm is ringing, `false` once it has stopped. */
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    private val _ringingSourceKey = MutableStateFlow<String?>(null)

    /**
     * The [dev.gaferneira.notificapp.domain.model.Notification.sbnKey] of the notification whose
     * rule started the currently-ringing alarm, or `null` while nothing is ringing (or the ring
     * has no real source, e.g. the rule editor's alarm preview). Lets `AndroidAlarmController`
     * check "is this notification's alarm the one ringing" without waking [AlarmService] for every
     * unrelated notification dismissal.
     */
    val ringingSourceKey: StateFlow<String?> = _ringingSourceKey.asStateFlow()

    fun setRinging(ringing: Boolean, sourceKey: String? = null) {
        _isRinging.value = ringing
        _ringingSourceKey.value = if (ringing) sourceKey else null
    }
}
