# Tasks

## 1. Manifest, permissions, and notification channel

- [x] 1.1 Add `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` `<uses-permission>` entries to `AndroidManifest.xml`
- [x] 1.2 Declare the `AlarmService` `<service>` element with `android:foregroundServiceType="mediaPlayback"` and `android:exported="false"`
- [x] 1.3 Add a notification-channel setup path that creates a high-importance "Alarms" channel (created lazily on first use or at app start); add channel name/description strings to `strings.xml`

## 2. Extend AlarmPlayer for looping + stop

- [x] 2.1 Add `stop()` to the `AlarmPlayer` interface
- [x] 2.2 In `AndroidAlarmPlayer`, make `play()` loop using `MediaPlayer` (`isLooping = true`, uniform across minSdk 26+) with alarm `AudioAttributes` (`USAGE_ALARM`)
- [x] 2.3 Make `vibrate()` a repeating waveform and have `stop()` cancel both the ringtone and the vibration
- [x] 2.4 Bind `AndroidAlarmPlayer` as `@Singleton` so `play()`/`stop()` act on the same instance
- [x] 2.5 Add audio-focus request (gain on play, abandon on stop)

## 3. AlarmService (foreground owner)

- [x] 3.1 Create `AlarmService` as an `@AndroidEntryPoint` foreground service handling `ACTION_START`, `ACTION_DISMISS`, `ACTION_SNOOZE` intents (sound URI + vibration flag as `ACTION_START` extras)
- [x] 3.2 On `ACTION_START`: enforce single-active-alarm (stop any current ring), `startForeground()` with the ongoing notification, then start looping playback via `AlarmPlayer`
- [x] 3.3 Build the ongoing, non-dismissable notification with Dismiss and Snooze actions as `PendingIntent`s back to the service
- [x] 3.4 On `ACTION_DISMISS`: stop playback, remove the notification, `stopSelf()`, no re-ring
- [x] 3.5 On `ACTION_SNOOZE`: stop playback + notification immediately, schedule a one-shot `AlarmManager` (`setAndAllowWhileIdle`, inexact) that re-sends `ACTION_START` after the 5-minute snooze delay, then `stopSelf()`
- [x] 3.6 Guard notification posting so a missing `POST_NOTIFICATIONS` grant does not crash the service and the alarm still rings
- [x] 3.7 Single stop routine that releases audio focus, stops sound/vibration, and removes the notification in every stop branch

## 4. Rewire the executor

- [x] 4.1 Introduce an `AlarmController` seam (interface + Android impl that starts `AlarmService`) so `AlarmActionExecutor` stays a pure unit
- [x] 4.2 Change `AlarmActionExecutor.execute()` to start the alarm via `AlarmController` and return `SUCCESS` (initiated), passing sound URI + vibration flag from the `RuleAction`
- [x] 4.3 Bind `AlarmController` in `ActionModule` and remove the now-unused direct `AlarmPlayer` injection from the executor

## 5. Tests

- [x] 5.1 Update `AlarmActionExecutorTest` to assert it triggers `AlarmController.start(...)` with the right sound URI + vibration flag and returns `SUCCESS` (no longer asserts `AlarmPlayer.play`)
- [~] 5.2 Pure-Kotlin tests for isolatable decision logic — N/A: the snooze delay is a fixed constant and single-active-alarm is a straight-line `alarmPlayer.stop()` inside the Android `Service`; nothing extractable into a meaningful pure-JVM unit without test theater. Executor delegation (5.1) is the real seam and is covered.
- [x] 5.3 Run `./gradlew test` green (33 tasks, all unit tests pass)

## 6. Verification, docs, quality gates

- [x] 6.1 Manually verified on-device (Galaxy S24 Ultra, Android 16): alarm rings until dismissed, Dismiss stops it, Snooze re-rings after the delay, single active alarm, and permission handling — confirmed by the user
- [x] 6.2 Add ADR 013 ("Foreground service owns alarm lifecycle; executor delegates") and reference it from `docs/adr/README.md`
- [x] 6.3 Run `./gradlew spotlessApply` and `./gradlew detekt` clean (no new baseline entries; touched files had none)

## 7. On-device fixes (from iteration-1 verification)

- [x] 7.1 Require notification permission: `AlarmController.start()` returns whether it started; `AndroidAlarmController` refuses to start when `areNotificationsEnabled()` is false; `AlarmActionExecutor` returns `SKIPPED` when not started
- [x] 7.2 Guard the snooze re-ring path in `AlarmService.handleStart` — if notifications are disabled, satisfy the foreground obligation and stop without ringing
- [x] 7.3 Request `POST_NOTIFICATIONS` (Android 13+) when the Create Alarm action is selected in `AlarmOptionsSelector`, with a warning banner shown when it is denied
- [x] 7.4 Dynamic notification content: thread the triggering notification's title/content/app-name through `AlarmController.start()` → `AlarmService` extras → an expandable `BigTextStyle` notification; carry the same through the snooze re-ring
- [x] 7.5 Update `AlarmActionExecutorTest` for the new signature (title/text/app-name) and the `SKIPPED`-when-not-started case; `./gradlew test`, `spotlessApply`, `detekt` all green
- [x] 7.6 Re-verified on device — user confirmed the permission requirement and dynamic notification content
