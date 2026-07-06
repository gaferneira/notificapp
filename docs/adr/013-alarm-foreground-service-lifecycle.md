# ADR 013 – Foreground Service Owns Alarm Lifecycle; Executor Delegates

## Status
Accepted

## Context
The `CREATE_ALARM` action (ADR 010) was fire-and-forget: `AlarmActionExecutor` played a single, non-looping `Ringtone` plus a one-shot vibration from inside `NotificappListenerService`'s IO coroutine, then returned `SUCCESS`. Nothing owned the sound afterward, so a ringing alarm could not be stopped or snoozed, and a user who stepped away could miss it entirely.

An alarm that rings until the user acts needs a component with a real, user-visible lifecycle — a background coroutine on `serviceScope` (cancelled on listener disconnect, with no stop handle) cannot provide one. This is also the app's first self-posted notification, first notification channel, and first foreground service; none existed before.

This decision covers iteration 1 (persistent, stoppable alarm surfaced via an ongoing notification). The full-screen phone-call-style UI is iteration 2 and builds on the same service and stop path.

## Decision
1. **A started foreground `AlarmService` owns the ring.** `AlarmActionExecutor` no longer plays audio; it delegates through a narrow `AlarmController` seam whose Android implementation calls `startForegroundService`. The service owns looping playback, repeating vibration, the ongoing notification, and the stop/snooze paths for the whole ring. Commands are one-way intents (`ACTION_START` / `ACTION_DISMISS` / `ACTION_SNOOZE`), mirroring the existing controller/holder style (ADR 010) rather than binding.

2. **`ActionExecutor` completion semantics are clarified.** For an executor that hands off to a longer-lived owner, `ActionOutcome.SUCCESS` means "successfully initiated," not "effects finished." The alarm keeps ringing after `execute()` returns.

3. **Looping via `MediaPlayer` (`isLooping = true`)** rather than `Ringtone.setLooping` (API 28+), giving one uniform code path across the supported range (minSdk 26). `AndroidAlarmPlayer` is bound `@Singleton` so `stop()` (from dismiss/snooze) acts on the same player instance `play()` started — a per-injection instance would leak an unstoppable ring. Audio focus (`USAGE_ALARM`, transient gain) is held while ringing and abandoned on stop.

4. **Foreground-service type `mediaPlayback`.** The service plays audio, so this is the honest, Play-review-safe typed category, avoiding `specialUse`'s written-justification requirement.

5. **Snooze re-ring via inexact `AlarmManager` (`setAndAllowWhileIdle`).** A minutes-long snooze does not warrant `SCHEDULE_EXACT_ALARM`; inexact still fires through Doze. The re-ring is a `getForegroundService` `PendingIntent` carrying the captured sound/vibration. Fixed 5-minute delay in iteration 1.

6. **Ringing is unconditional; the notification is best-effort.** On Android 13+ the ongoing notification needs `POST_NOTIFICATIONS`; posting is guarded so a missing grant cannot crash the service. Silently dropping a user-configured alarm is worse than ringing without the on-screen Dismiss/Snooze controls.

## Consequences

**Positive:**
- The alarm rings until dismissed/snoozed and is always stoppable — the core defect is fixed.
- Playback lifetime is decoupled from the notification pipeline, so listener disconnects no longer orphan or cancel a ring.
- The `AlarmController` seam keeps `AlarmActionExecutor` a pure unit test (no `Context`, no emulator), consistent with `SystemNotificationController` / `AlarmPlayer`.
- Iteration 2's full-screen UI can reuse this service and stop path unchanged.

**Negative:**
- Introduces the app's first foreground service and its Android 12+/13+/14+ start restrictions and permissions (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`) — surface that must be verified on-device across API levels.
- The `@Singleton` requirement on `AndroidAlarmPlayer` is load-bearing but not compiler-enforced; if dropped, stop silently targets the wrong instance.
- Service/notification/`AlarmManager` wiring is Android-framework code with thin pure-Kotlin test coverage (only the executor delegation is unit tested); correctness leans on manual on-device verification.
- The relaxed `ActionExecutor` contract ("SUCCESS = initiated") is a convention future delegating actions must follow, not a type-level guarantee.
