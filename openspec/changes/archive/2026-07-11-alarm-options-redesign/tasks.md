# Tasks: Alarm Options Redesign

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~650-850 (2 new domain files, 6 modified core files, 3 modified UI files, 2 new tests + extended fixtures) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (domain+repo) -> PR 2 (service/executor/activity runtime) -> PR 3 (UI redesign) -> PR 4 (tests+verification) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Domain model + repository (Phase 1) | PR 1 | Base: main. No UI/runtime dependents yet; independently testable. |
| 2 | Runtime seam: player, service, executor, activity (Phase 2) | PR 2 | Base: main (after PR 1 merges). Depends on Phase 1 types. |
| 3 | UI redesign (Phase 3) | PR 3 | Base: main (after PR 2 merges). Depends on Phase 1+2 accessors/fields. |
| 4 | Tests + verification (Phase 4) | PR 4 | Base: main (after PR 3 merges), or fold test tasks into each prior PR if reviewer prefers smaller diffs. |

## Phase 1: Domain Model & Repository

- [x] 1.1 Create `domain/model/VibrationPattern.kt`: enum `BASIC_CALL`/`PULSE`/`LONG` with `id`, `timings: LongArray`, `repeatIndex`; `fromId(id: String?)` fallback to `BASIC_CALL`.
- [x] 1.2 Create `domain/model/AlarmBackground.kt`: `enum class AlarmBackgroundType { NONE, PRESET, IMAGE }` and `enum class AlarmBackgroundPreset(id, colorHexStops: List<String>)` (e.g. `SUNRISE`, `OCEAN`, `MIDNIGHT`) with `fromId(id: String?)` fallback to a default preset.
- [x] 1.3 In `domain/model/RuleAction.kt`, add `*_KEY`/`DEFAULT_*` constants + accessors for `ALARM_SOUND_ENABLED_KEY` (default true), `ALARM_VIBRATION_PATTERN_KEY` (default `BASIC_CALL.id`), `ALARM_SNOOZE_ENABLED_KEY` (default true), `ALARM_SNOOZE_DURATION_MINUTES_KEY` (default 5, clamp 1..60), `ALARM_SNOOZE_MAX_COUNT_KEY` (default 3, clamp 1..10), `ALARM_BACKGROUND_TYPE_KEY` (default NONE), `ALARM_BACKGROUND_PRESET_KEY`, `ALARM_BACKGROUND_IMAGE_URI_KEY`.
- [x] 1.4 Extend `RuleAction.createAlarm(...)` with the eight new params (defaulted) writing into `buildMap`; group them into a small `AlarmOptionsConfig` value object if the param count would push `createAlarm` past detekt's `LongParameterList` threshold (6). (Also folded the pre-existing `fullScreenEnabled` param into `AlarmOptionsConfig` — see note below.)
- [x] 1.5 Add `RuleRepository.isImageUriReferencedByOtherAlarmAction(uri: String, excludingActionId: String): Boolean` to `domain/repository/RuleRepository.kt`; implement in `core/data/repository/RuleRepositoryImpl` as a linear scan of stored `CREATE_ALARM` action configs comparing the background image URI, excluding the given action id.

## Phase 2: Runtime Seam (Player, Service, Executor, Activity)

- [x] 2.1 Change `AlarmPlayer.vibrate()` signature to `vibrate(pattern: VibrationPattern)` in its interface; update `AndroidAlarmPlayer.vibrate()` to build `VibrationEffect.createWaveform(pattern.timings, pattern.repeatIndex)`; skip sound playback in `play()`/caller when sound is disabled.
- [x] 2.2 In `core/notification/action/AlarmController.kt`, add `soundEnabled`, `vibrationPattern`, `snoozeEnabled`, `snoozeDurationMinutes`, `snoozeMaxCount`, `snoozeCount` (default 0), `backgroundType`, `backgroundPresetId`, `backgroundImageUri` to `AlarmRequest`; update `EMPTY_REQUEST` fixture. (Grouped into nested `AlarmRingOptions`/`AlarmSnoozeSettings` + reused domain's `AlarmBackgroundConfig` to stay under detekt's constructor threshold — see deviation note.)
- [x] 2.3 In `AlarmActionExecutor.kt`, read all eight `RuleAction` accessors and map them into the constructed `AlarmRequest`.
- [x] 2.4 In `AlarmService.kt`, add `EXTRA_SOUND_ENABLED`, `EXTRA_VIBRATION_PATTERN`, `EXTRA_SNOOZE_ENABLED`, `EXTRA_SNOOZE_DURATION_MINUTES`, `EXTRA_SNOOZE_MAX_COUNT`, `EXTRA_SNOOZE_COUNT`, `EXTRA_BACKGROUND_TYPE`, `EXTRA_BACKGROUND_PRESET_ID`, `EXTRA_BACKGROUND_IMAGE_URI` to `startIntent`/`toAlarmRequest`; skip `AlarmPlayer.play` when sound disabled; call `vibrate(pattern)` with the resolved pattern.
- [x] 2.5 In `AlarmService.kt`, update `handleSnooze` to guard `if (!current.snoozeEnabled || current.snoozeCount >= current.snoozeMaxCount) { stopAlarm(); return }`; replace hardcoded `SNOOZE_DELAY_MS` with `current.snoozeDurationMinutes * 60_000L`.
- [x] 2.6 In `AlarmService.kt`, update `scheduleReRing` to rebuild the start intent with `EXTRA_SNOOZE_COUNT = current.snoozeCount + 1` so the counter survives the re-ring rebuild; update `buildNotification()` to omit the Snooze action when snooze is exhausted or disabled.
- [x] 2.7 In `AlarmActivity.kt`, add `snoozeEnabled`/`snoozeDurationMinutes`/`snoozeCount`/`snoozeMaxCount`/`backgroundType`/`backgroundPresetId`/`backgroundImageUri` params to `intent(...)` and read them back into the activity's extras. (Also implemented the background render + Snooze button gating in `AlarmCallScreen`, ahead of Phase 3's UI redesign scope, since the task explicitly required it.)

## Phase 3: UI Redesign

- [x] 3.1 Add `AlarmValueRow` composable in `features/ruleeditor/ui/actionconfig/AlarmConfig.kt` (bold title + colored subtitle summarizing current value + trailing `Switch`, tappable row) replacing `AlarmToggleRow` for Sound/Vibration/Snooze. (`checked`/`onCheckedChange` grouped into a private `AlarmRowToggle` value object to stay under detekt's `LongParameterList` threshold.)
- [x] 3.2 Wire the Sound row to the existing `RingtoneManager` picker, gated by the new sound-enabled switch.
- [x] 3.3 Add a vibration-pattern radio-list picker driven by `VibrationPattern.entries`, wired to the Vibration row.
- [x] 3.4 Add an inline expandable Snooze section with duration (minutes) and max-count number steppers, gated by the snooze-enabled switch.
- [x] 3.5 Add `AlarmBackgroundSection` composable, rendered only when full-screen is enabled: preset swatch grid (from `AlarmBackgroundPreset.entries`) + "Choose image" `OpenDocument` button.
- [x] 3.6 Implement the URI-permission lifecycle in `AlarmBottomSheet.kt`: take grant immediately on pick; release previous picked-but-unsaved URI's grant on replace-within-sheet; release currently-picked URI's grant on dismiss-without-save (only if it differs from the persisted `initial` URI). (State extracted into a private `AlarmSheetState` holder class to keep the composable under detekt's `LongMethod`/`CyclomaticComplexMethod` thresholds - see deviation note below.)
- [x] 3.7 Add `onCheckUriStillReferenced: suspend (uri: String, excludingActionId: String) -> Boolean` callback param to `AlarmBottomSheet`; on save, when the URI changed or type left `IMAGE`, call it before releasing the old grant via `contentResolver.releasePersistableUriPermission`.
- [x] 3.8 Expose a suspend wrapper (`RuleEditorViewModel.isImageUriReferencedByOtherAlarmAction`) around `ruleRepository.isImageUriReferencedByOtherAlarmAction` as the callback passed into `AlarmBottomSheet`, threaded through `RuleEditorScreen` -> `RuleEditorScreenContent` -> `RuleEditorBottomSheets` -> `AlarmBottomSheet` as a plain suspend lambda param (default `{ _, _ -> false }` for previews). **Deviation**: did *not* wrap `onActionSaved` itself in `viewModelScope.launch` as originally worded - `onActionSaved` performs no suspend work (it only updates `UiState` synchronously) and ~7 existing `RuleEditorViewModelTest` cases assert state immediately after `onEvent(UiEvent.OnActionSaved(...))` without advancing the test dispatcher; wrapping it would make those assertions race a queued coroutine for no functional benefit, since the actual suspend URI check already happens in `AlarmBottomSheet`'s own `rememberCoroutineScope().launch { ... }` before `onSave(...)` is invoked.
- [x] 3.9 Extend `AlarmOptions.kt` with the eight new fields (plus a grouped `AlarmOptionsCallbacks` value object); update `AlarmBottomSheet` state (`AlarmSheetState`, seeded from `initial` accessors) and the `RuleAction.createAlarm(...)` call site to pass all eight fields via `AlarmOptionsConfig`.
- [x] 3.10 In `AlarmActivity.kt`'s `AlarmCallScreen`, render the root `Surface` by `backgroundType`: `NONE` -> today's theme background; `PRESET` -> `AlarmBackgroundPreset.fromId(presetId).toBrush()` (local UI extension near `AlarmCallScreen`, not in domain) via `Modifier.background(brush)`; `IMAGE` -> Coil `SubcomposeAsyncImage` fill with scrim, falling back to theme background via the error slot on load failure. (Already implemented in Phase 2 task 2.7, ahead of scope - verified complete, no further changes needed.)
- [x] 3.11 In `AlarmCallScreen`, hide the Snooze button when `snoozeCount >= snoozeMaxCount` or `!snoozeEnabled`; label it with `snoozeDurationMinutes` (e.g. "Snooze (5 min)") when shown. (Already implemented in Phase 2 task 2.7, ahead of scope - verified complete, no further changes needed.)

## Phase 4: Tests & Verification

- [x] 4.1 Add unit tests for `VibrationPattern.fromId`: known id resolves, unrecognized id falls back to `BASIC_CALL`. (`domain/model/VibrationPatternTest.kt`, also covers a `null` id.)
- [x] 4.2 Add unit tests for `AlarmBackgroundPreset.fromId`: null id, unrecognized id, and `PRESET` type with no id selected all fall back to the default preset. (`domain/model/AlarmBackgroundTest.kt`, also covers `AlarmBackgroundType.fromName`.)
- [x] 4.3 Extend `RuleAction` accessor tests: sound-enabled default/round-trip, snooze duration/max-count clamp bounds (1..60 / 1..10), legacy rule with no snooze keys resolves `snoozeMaxCount = 3`. (`domain/model/RuleActionTest.kt` - also added vibration-pattern and background type/preset/image round-trip coverage.)
- [x] 4.4 Extend `AlarmActionExecutorTest` (and its fake controller/`EMPTY_REQUEST` fixture) to assert all eight new fields map correctly into `AlarmRequest`. (Existing tests already covered `vibrationEnabled`/`fullScreenEnabled`; added `soundEnabled`, `vibrationPattern`, snooze enabled/duration/maxCount, and background preset/image mapping.)
- [x] 4.5 Add `AlarmService` test(s) for snooze-exhausted behavior: `handleSnooze` calls `stopAlarm()` instead of re-ringing when `snoozeCount >= snoozeMaxCount` or `snoozeEnabled == false`. **Deviation**: `AlarmService` extends Android's `Service` and this project's test stack has no Robolectric, so it cannot be instantiated in a JVM unit test (confirmed: no pre-existing `AlarmService` test file, no `androidTest` source set, no Robolectric dependency in `app/build.gradle.kts`). Extracted the exhausted/disabled decision into a new pure `AlarmRequest.canSnoozeAgain: Boolean` property (`AlarmController.kt`) that `AlarmService.handleSnooze`/`buildNotification()` now both gate on, and unit-tested that property directly (`core/notification/action/AlarmRequestTest.kt`) - covers every case the task asked for (exhausted count, count over max, snooze disabled) without requiring Robolectric.
- [x] 4.6 Add `RuleEditorViewModel` test(s) for the URI-reference-check flow: the exposed suspend wrapper (`isImageUriReferencedByOtherAlarmAction`) delegates to `ruleRepository.isImageUriReferencedByOtherAlarmAction` and returns its result unchanged, for both the "still referenced" and "no longer referenced" cases. (`ImageUriReferenceCheckTests` in `RuleEditorViewModelTest.kt`. Per the 3.8 deviation, `onActionSaved` itself was not changed, so there is no separate "launches" behavior to test there.)
- [x] 4.7 Extend `TestFixtures.kt` with any new alarm-option builders needed by 4.1-4.6 (reuse, don't duplicate, existing `createTestRuleAction`/alarm fixtures). **No changes needed**: `RuleAction.createAlarm(...)` (the production factory, exercised directly) and the existing `createTestAction`/plain `RuleAction(...)` constructor already covered every case the new tests needed - no new fixture builders were required.
- [x] 4.8 Run `./gradlew spotlessApply && ./gradlew detekt && ./gradlew test`; fix any formatting/detekt/test failures before marking the change complete. All three pass clean (see apply report for the full command output).
