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

## 6. Per-alarm style toggle (from device feedback)

- [x] 6.1 Add `ALARM_FULLSCREEN_ENABLED_KEY` + `DEFAULT_ALARM_FULLSCREEN_ENABLED` (true) + `isAlarmFullScreenEnabled()` to `RuleAction`; `createAlarm` takes `fullScreenEnabled`
- [x] 6.2 Thread `fullScreenEnabled` through `AlarmRequest` → executor → `AlarmService` extras; only `setFullScreenIntent` when enabled
- [x] 6.3 Add a "Full-screen alarm (call style)" toggle to `AlarmOptionsSelector`; wire through `ActionBottomSheet` contract/viewmodel (state field + event + confirm + init-for-edit)
- [x] 6.4 Update `AlarmActionExecutorTest` (new `AlarmRequest` field + full-screen-disabled case); `test`/`spotless`/`detekt`/`assembleDebug` green
- [x] 6.5 In-app grant path: when full-screen is enabled but `canUseFullScreenIntent()` is false (Android 14+), show a hint routing to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` — device testing on Android 16 showed the OS silently downgrades to notification without the grant, making the toggle look inert

## 5. Verification, quality gates

- [x] 5.1 `./gradlew testDebugUnitTest`, `spotlessApply`, `detekt`, `assembleDebug` all green (no new baseline entries)
- [x] 5.2 Manually verified on-device (Android 16): with `USE_FULL_SCREEN_INTENT` granted, alarm rings locked → full-screen call UI shows over the lock screen; Dismiss/Snooze work from it; notification-only toggle degrades correctly. Confirmed the OS restricts the permission by default on 14+ (hence the in-app grant hint, 6.5) and that full-screen only takes over when locked/screen-off
- [x] 5.3 No new ADR needed — the full-screen UI is a presentation layer over the service lifecycle already covered by ADR 013
