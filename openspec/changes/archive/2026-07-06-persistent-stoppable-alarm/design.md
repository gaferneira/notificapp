## Context

Today `CREATE_ALARM` is fire-and-forget. `AlarmActionExecutor.execute()` runs inside `NotificappListenerService.serviceScope` (an IO coroutine), calls `AndroidAlarmPlayer.play()` — a single, non-looping `Ringtone.play()` — plus one 800ms vibration, and returns `SUCCESS`. Nothing owns the sound afterward and there is no stop control.

Constraints that shape this design:
- The app currently posts **no notifications of its own**: no `NotificationChannel`, no `NotificationCompat`, no foreground service anywhere. This change introduces all three for the first time.
- The alarm is **event-driven**, not clock-scheduled — it fires when a notification matches a rule, not at a wall-clock time. So `AlarmManager` is not the primary playback mechanism (though it is the natural fit for the snooze *re-ring* delay).
- `ActionExecutor.execute()` is a `suspend` contract that today implies "run to completion." A ring-until-dismissed alarm cannot honor that inside a background coroutine.
- Android 13+ gates the ongoing notification behind the runtime `POST_NOTIFICATIONS` permission. Android 14+ requires every started foreground service to declare a `foregroundServiceType`.

## Goals / Non-Goals

**Goals:**
- Alarm rings (looping audio + repeating vibration) until the user dismisses or snoozes it.
- The ringing is owned by a foreground service, decoupled from the notification-listener coroutine.
- An ongoing notification surfaces Dismiss and Snooze.
- A clean stop path exists and is reachable from the notification actions.
- Keep the `ActionExecutor` seam intact and unit-testable (executor asserts "started the service," not "played audio").
- Keep pure decision logic (snooze duration, single-active-alarm) testable off-device.

**Non-Goals:**
- The full-screen phone-call-style Activity and `USE_FULL_SCREEN_INTENT` flow (iteration 2).
- Per-rule custom snooze durations for the alarm's *re-ring* (can reuse a sensible default now; revisit in iteration 2 if needed).
- Scheduling alarms at wall-clock times / persistent alarms that survive reboot.
- A settings screen for the alarm channel.

## Decisions

### D1: A started foreground service (`AlarmService`) owns the ringing
The executor starts `AlarmService` via an explicit `Intent` with an `ACTION_START` action (carrying sound URI + vibration flag as extras); the service calls `startForeground()` with the ongoing notification and owns a single `AlarmPlayer` instance for the whole ring. Dismiss/Snooze/Stop are delivered as further intents (`ACTION_DISMISS`, `ACTION_SNOOZE`) to the same service.

- **Why not keep playing in `serviceScope`?** That scope is cancelled on listener disconnect and has no user-facing lifecycle or stop handle; a looping sound there is unstoppable and unobservable. A foreground service is the Android-sanctioned owner for user-visible ongoing audio.
- **Why a started service over a bound one?** The trigger (executor) and the controls (notification actions) are fire-and-forget one-way commands; `startService`/`startForegroundService` with action intents fits exactly and needs no binding lifecycle.
- **Alternative considered — `AlarmManager` for playback:** rejected; `AlarmManager` schedules *future* triggers, it doesn't hold a ringing sound. It IS used for the snooze re-ring delay (see D4).

### D2: Foreground service type = `mediaPlayback`
Declare `android:foregroundServiceType="mediaPlayback"` and request `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. The service plays audio, so `mediaPlayback` is the honest, Play-review-safe type.

- **Alternative — `specialUse`:** rejected for now; it demands a written justification at Play review and is meant for cases no typed category fits. Audio playback fits `mediaPlayback`. Revisit only if the alarm must run with the sound muted (media-focus edge cases).

### D3: Looping via `AlarmPlayer`, not a new abstraction
Extend the existing `AlarmPlayer` interface with `stop()`, make `play()` loop (`Ringtone.setLooping(true)` on API 28+; on older APIs, a completion-restart fallback or `MediaPlayer.isLooping`), and make `vibrate()` use a repeating `VibrationEffect.createWaveform(..., repeat = 0)` that `stop()` cancels. `AndroidAlarmPlayer` keeps ownership of the single active `Ringtone`/vibration and must be a singleton so the service's stop reaches the same instance.

- **Why keep the interface?** It already isolates audio/vibration from the executor for testing. We extend rather than replace, preserving `AlarmActionExecutorTest`'s seam.
- **Note:** `AndroidAlarmPlayer` must be bound `@Singleton` (currently unscoped) so `play()` and `stop()` act on the same ringtone. This is a required fix, not optional.

### D4: Snooze re-ring uses `AlarmManager` one-shot
Snooze stops the current ring immediately, then schedules a single `AlarmManager` (`setExactAndAllowWhileIdle` or `setAndAllowWhileIdle`) callback after the snooze delay that re-sends `ACTION_START` to `AlarmService`. Snooze delay is a fixed **5-minute** constant for iteration 1 (alarm-appropriate; shorter than the 15-min notification-snooze default).

- **Why `AlarmManager`?** It's the only mechanism that reliably fires after a delay when the service is gone/idle. A coroutine `delay` would die with the service.
- **Trade-off:** exact alarms may require `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` on Android 12+. For a snooze of minutes, an *inexact* `setAndAllowWhileIdle` avoids that permission entirely — preferred unless precision matters.

### D5: Notification actions delivered via `PendingIntent` to the service
Dismiss/Snooze are `PendingIntent.getService` (or `getForegroundService`) pointing back at `AlarmService` with the respective action. No separate `BroadcastReceiver` needed, which keeps the stop path in one place.

- **Alternative — `BroadcastReceiver`:** adds an extra hop and another manifest component; only worth it if a non-service caller ever needs to stop the alarm. Not the case in iteration 1.

### D6 (REVISED after on-device testing): Notification permission is required; do not ring without it
**Superseded original decision.** The first cut rang unconditionally and treated the notification as best-effort. On-device testing showed that is wrong: the Dismiss/Snooze controls live *only* on the ongoing notification, so ringing without it produces an **unstoppable** alarm — the exact defect this change exists to fix. Corrected behavior:

- **At configuration time:** when the user selects the `CREATE_ALARM` action, request `POST_NOTIFICATIONS` (Android 13+) and show a warning banner if it is denied. Implemented inside the alarm config composable (`AlarmOptionsSelector`), which is shown exactly when the alarm action is selected — no ViewModel/contract changes.
- **At trigger time:** `AndroidAlarmController` checks `NotificationManagerCompat.areNotificationsEnabled()` and refuses to start the service when notifications are off; `AlarmController.start()` returns whether it started, and `AlarmActionExecutor` maps that to `SUCCESS`/`SKIPPED`. `AlarmService.handleStart` repeats the guard to cover the `AlarmManager` snooze re-ring path (which bypasses the controller).

- **Why require it?** An alarm you cannot stop is worse than an alarm that does not fire. Requiring the permission keeps the "always stoppable" guarantee intact.
- **Not a hard save-block:** configuring the action does not *block* saving on the permission — the trigger-time guard already guarantees safety, and the user can grant later. We prompt + warn instead.

### D7: Ongoing notification shows the triggering notification's content
The notification title/text/app-name flow from the domain `Notification` through `AlarmController.start()` → `AlarmService` intent extras → the notification builder (expandable `BigTextStyle`, app name as sub-text). The snooze re-ring carries the same extras so the re-rung notification stays informative.

- **Why:** static "Alarm" text (the first cut) gives the user no idea what fired; showing the source content mirrors a normal notification and makes the alarm actionable at a glance. (Raised by on-device testing.)

## Risks / Trade-offs

- **[Foreground-service-start restrictions]** Android 12+ limits starting FGS from the background. The trigger originates from `NotificappListenerService` handling a live notification — an allowed context — but this must be verified on-device across API levels. → Mitigation: start via `ContextCompat.startForegroundService` and call `startForeground` within the required window; manually verify on Android 12/13/14.
- **[`mediaPlayback` and audio focus]** Requesting audio focus as an alarm (`USAGE_ALARM`, `AUDIOFOCUS_GAIN_TRANSIENT`) may duck/pause user media; correct for an alarm but verify it doesn't get ducked *by* other apps. → Mitigation: use `AudioAttributes` `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`, and set the ringtone stream to alarm.
- **[Singleton scope change on `AndroidAlarmPlayer`]** If missed, `stop()` hits a different instance than `play()` and the alarm never stops. → Mitigation: bind `@Singleton`; add a test/assertion that stop cancels an active ring on the same instance.
- **[Non-dismissable ongoing notification perceived as stuck]** If a stop path bug leaves the notification up, the user can't clear it. → Mitigation: guarantee the service removes the notification in every stop branch (`finally`/single stop routine); the alarm channel is high-importance but the notification is only ongoing while the service lives.
- **[Contract change ripple]** `AlarmActionExecutorTest` and any callers asserting inline playback break. → Mitigation: update tests to assert the service-start intent; this is expected and covered in tasks.
- **[Snooze exact-alarm permission]** If we choose exact alarms we inherit a permission prompt on Android 12+. → Mitigation: default to inexact `setAndAllowWhileIdle` (D4) to avoid it.

## Resolved Decisions

- **Snooze delay for the alarm re-ring:** fixed **5-minute** constant for iteration 1 (alarm-appropriate; shorter than the 15-min notification-snooze default).
- **ADR:** confirmed — add **ADR 013** ("Foreground service owns alarm lifecycle; executor delegates") covering the app's first foreground service and the `ActionExecutor` completion-semantics change. (011 and 012 already exist.)
- **Looping engine:** use `MediaPlayer` with `isLooping = true` (uniform across minSdk 26+) rather than `Ringtone.setLooping` (API 28+), avoiding a version-branch fallback.

## Open Questions

- **How does the executor reach `AlarmService`?** Inject `@ApplicationContext` into `AlarmActionExecutor` to `startForegroundService`, or route through a thin `AlarmController` seam for testability? Leaning toward a small `AlarmController` interface (Android impl starts the service) so the executor stays a pure unit test — mirrors the existing `SystemNotificationController`/`AlarmPlayer` pattern.
