## Why

The `CREATE_ALARM` action is fire-and-forget: `AlarmActionExecutor` calls `AlarmPlayer.play()` (a single, non-looping `Ringtone.play()`) plus a one-shot 800ms vibration, then returns `SUCCESS`. Nothing owns the sound after that, so a user who steps away can miss it entirely, and there is no user-facing way to stop or snooze a ringing alarm. For an "alarm" to be useful it must ring until the user acts on it — which requires a component with a lifecycle, not a background coroutine inside the notification pipeline.

This is iteration 1 of 2. It delivers a persistent, stoppable alarm surfaced through an ongoing (pinned) notification with Dismiss and Snooze. The full-screen phone-call-style UI is deferred to iteration 2, which builds on the foreground service and stop path introduced here.

## What Changes

- **BREAKING (internal contract):** `CREATE_ALARM` execution changes from playing audio inline to starting a foreground `AlarmService` that owns playback for its whole lifetime. `AlarmActionExecutor.execute()` returns after the service is started; it no longer represents the full duration of the alarm.
- Alarm audio now **loops** until explicitly stopped, and vibration **repeats** (when enabled), instead of playing once.
- A new foreground `AlarmService` owns the ringing lifecycle: looping playback, repeating vibration, the ongoing notification, and the stop/snooze paths. It stops itself (and releases audio focus) when dismissed.
- A new **ongoing, non-dismissable notification** is posted while the alarm rings, exposing two actions: **Dismiss** (stop now) and **Snooze** (stop now, re-ring after a delay).
- Introduces the app's **first self-posted notification**, its **first notification channel** (high-importance "Alarms"), and its **first foreground service** — none exist today.
- Adds a stop path (`AlarmPlayer.stop()` + the mechanism the notification actions use to reach the service).
- Adds required permissions/manifest entries: `POST_NOTIFICATIONS` (Android 13+, runtime), `FOREGROUND_SERVICE`, a foreground-service type declaration (Android 14+), and the `AlarmService` `<service>` element.

Out of scope (iteration 2): the full-screen phone-call-style Activity and the `USE_FULL_SCREEN_INTENT` permission flow.

## Capabilities

### New Capabilities
- `alarm-playback`: The ringing lifecycle of a `CREATE_ALARM` action — looping audio + repeating vibration owned by a foreground service, the ongoing Dismiss/Snooze notification, audio-focus handling, and the stop/snooze/re-ring behavior. Defines how a ringing alarm starts, is surfaced to the user, and is stopped.

### Modified Capabilities
- `action-execution`: The `CREATE_ALARM` executor no longer plays audio inline and no longer treats `SUCCESS` as "the alarm finished." It now starts the `AlarmService` and returns; the alarm's lifetime is owned by that service, not by the executor's suspend call.

## Impact

- **Code (modified):** `AlarmActionExecutor` (delegates to the service instead of `AlarmPlayer.play()`), `AlarmPlayer`/`AndroidAlarmPlayer` (add looping + `stop()`, repeating vibration), `core/di/ActionModule.kt` (bindings), `AndroidManifest.xml` (permissions + service), likely a new `strings.xml` entries for channel + actions.
- **Code (new):** `AlarmService` (foreground service), the stop/snooze trigger (e.g. a `BroadcastReceiver` or service `startService` intents), a notification builder for the ongoing alarm, and a notification-channel setup path.
- **Permissions:** first runtime `POST_NOTIFICATIONS` request (Android 13+); `FOREGROUND_SERVICE` + a foreground-service type (Android 14+ requires a declared type — `mediaPlayback` vs `specialUse` is a design decision).
- **Specs:** new `alarm-playback` spec; delta to `action-execution`.
- **Tests:** `AlarmActionExecutorTest` updates (now asserts service start, not `play()`); new pure-Kotlin tests for the snooze/stop decision logic where it can be isolated from Android. Service/notification wiring is largely Android-framework and will be manually verified on-device.
- **Docs/ADR:** likely a short ADR for "foreground service owns alarm lifecycle" since it's the app's first FGS and changes the action-execution contract.
