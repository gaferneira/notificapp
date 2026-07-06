# Tasks

## 1. Manifest and permission

- [x] 1.1 Add the `USE_FULL_SCREEN_INTENT` `<uses-permission>` to `AndroidManifest.xml`
- [x] 1.2 Declare the `AlarmActivity` `<activity>` — `exported=false`, `launchMode=singleInstance`, `noHistory=true`/`excludeFromRecents=true`, `showWhenLocked`/`turnScreenOn`, `Theme.Notificapp`

## 2. Ringing-state sync holder

- [x] 2.1 Create `AlarmStateHolder` (`@Singleton`) exposing `isRinging: StateFlow<Boolean>` with `setRinging(Boolean)` — mirrors the `SystemNotificationControllerHolder` style
- [x] 2.2 Provided via Hilt as a plain `@Singleton @Inject constructor` class (no interface/binding needed)
- [x] 2.3 Pure-Kotlin unit test for the holder (`AlarmStateHolderTest`: starts false, reflects start/stop)

## 3. AlarmService integration

- [x] 3.1 Inject `AlarmStateHolder` into `AlarmService`; set ringing `true` when the ring starts, `false` in the single `stopAlarm()` routine (which the notifications-disabled refuse path also calls)
- [x] 3.2 Attach `setFullScreenIntent(pendingIntent, true)` to the ongoing notification, targeting `AlarmActivity` with the current content
- [x] 3.3 `AlarmActivity.intent(context, request)` factory carries the display content; `AlarmService.dismiss/snoozeIntent` factories expose the stop path to the Activity

## 4. AlarmActivity (full-screen call-style UI)

- [x] 4.1 `AlarmActivity` as an `@AndroidEntryPoint` `ComponentActivity`; `setShowWhenLocked(true)` + `setTurnScreenOn(true)` (API 27+) with pre-27 `WindowManager` flag fallback
- [x] 4.2 Reads content from the launch intent; call-style Compose screen (`NotificappTheme`) showing title / content / app name with large Dismiss and Snooze controls
- [x] 4.3 Dismiss/Snooze buttons `startService` with the `AlarmService` dismiss/snooze intents, then `finish()`
- [x] 4.4 Collects `AlarmStateHolder.isRinging` and `finish()`es when it becomes false (covers dismiss-from-notification and superseding rings)
- [x] 4.5 `@Preview` for the call-style screen

## 5. Verification, quality gates

- [x] 5.1 `./gradlew testDebugUnitTest`, `spotlessApply`, `detekt`, `assembleDebug` all green (no new baseline entries)
- [ ] 5.2 Manually verify on-device: alarm rings locked+screen-off → full-screen shows over lock screen and wakes screen; Dismiss/Snooze work from full-screen; dismissing from the notification closes an open full-screen; snooze re-ring re-shows; with `USE_FULL_SCREEN_INTENT` denied on Android 14+, it degrades to the notification and still stops — **PENDING (on device)**
- [x] 5.3 No new ADR needed — the full-screen UI is a presentation layer over the service lifecycle already covered by ADR 013
