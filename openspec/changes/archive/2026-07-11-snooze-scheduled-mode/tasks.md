# Tasks

## 1. Domain model: config keys and accessors

- [x] 1.1 Add `SNOOZE_MODE_KEY`, `SnoozeMode` (`DURATION` default, `SCHEDULED`) and the schedule config keys (`SNOOZE_SCHEDULE_START_HOUR_KEY`, `_START_MINUTE_KEY`, `_INTERVAL_MINUTES_KEY`, `_WINDOW_END_HOUR_KEY`, `_WINDOW_END_MINUTE_KEY`) to `RuleAction.kt`
- [x] 1.2 Add typed accessors (`getSnoozeMode()`, `getSnoozeSchedule(): SnoozeSchedule?`) following the existing `getSnoozeDurationMinutes()`/`getAlarmSoundUri()` pattern
- [x] 1.3 Add `RuleAction.createScheduledSnooze(...)` factory alongside the existing `createSnooze(...)`
- [x] 1.4 No Room migration needed (config stays a JSON-serialized `Map<String, String>` column) and no `ActionDto`/`RuleWireMapper` changes needed (`ActionDto.config` is a generic passthrough map, confirmed by reading `core/rulesharing/dto/ActionDto.kt`) — no dedicated round-trip test added since the passthrough is structural, not new logic
- [x] 1.4b (found during implementation, not originally scoped) `DoSection.kt`'s action-card subtitle read `getSnoozeDurationMinutes()` unconditionally, which would have shown a misleading fallback duration for `SCHEDULED`-mode actions. Added `RuleAction.snoozeSubtitle()` branching on `getSnoozeMode()`.

## 2. Scheduling calculation (pure Kotlin)

- [x] 2.1 Added `SnoozeScheduleCalculator.nextCheckpoint(now: LocalDateTime, schedule: SnoozeSchedule): LocalDateTime?` in `core/notification/action/` (no Android imports); `SnoozeSchedule` itself lives in `domain/model/RuleAction.kt` so the domain layer doesn't depend on `core/notification`
- [x] 2.2 Unit tests in `SnoozeScheduleCalculatorTest`: before start-of-day, after start with no interval (rolls to tomorrow), mid-window recurring checkpoint math, exactly-at-a-checkpoint, exactly-at-window-end, past-window-end, checkpoint landing at/after window end mid-computation, interval set without a window end

## 3. Time provider seam

- [x] 3.1 Added `CurrentTimeProvider` interface (`now(): LocalDateTime`) and `SystemCurrentTimeProvider`
- [x] 3.2 Bound in `core/di/ActionModule.kt` (`@Binds`)

## 4. Release tracking

- [x] 4.1 Added `SnoozeReleaseTracker` (`@Singleton`, mutex-guarded in-memory set of pending `sbnKey`s, mirroring `NotificationDeduplicator`'s pattern): `markPending(sbnKey)`, `consumeIfPending(sbnKey): Boolean`
- [x] 4.2 Unit tests in `SnoozeReleaseTrackerTest`: mark-then-consume returns true and clears; consume without a prior mark returns false; independent keys don't interfere

## 5. Executor changes

- [x] 5.1 Injected `CurrentTimeProvider` and `SnoozeReleaseTracker` into `SnoozeActionExecutor` (`SnoozeScheduleCalculator` is a stateless `object`, called directly, no DI needed)
- [x] 5.2 Branches on `action.getSnoozeMode()`: `DURATION` keeps the exact prior behavior; `SCHEDULED` follows the flow in `design.md` D4 (consume-pending check → compute checkpoint → snooze-and-mark, or pass-through when `null`)
- [x] 5.3 Updated `SnoozeActionExecutorTest`: new cases for `SCHEDULED` mode — checkpoint math, pending release consumed (no second `snooze()` call), out-of-window match does not call `snooze()`, missing schedule config yields `SKIPPED`

## 6. UI: mode toggle and schedule config

- [x] 6.1 Added a segmented control (`SnoozeModeToggle`) to `SnoozeBottomSheet` ("For a duration" / "On a schedule"); `SnoozeDurationSelector` path is unchanged
- [x] 6.2 Added `ScheduledSnoozeSelector` composable (`features/ruleeditor/ui/actionconfig/`): start-time picker (Material3 `TimePicker` in a dialog), "Repeat" toggle revealing interval chips + window-end time picker; shows a validation message and the sheet's confirm button is disabled when `windowEnd <= start`
- [x] 6.3 Wired `SnoozeBottomSheet`'s `onConfirm` to build the right `RuleAction` via `createSnooze` or `createScheduledSnooze` depending on the toggle state
- [~] 6.4 Preview composables — skipped: none of the sibling `actionconfig/` composables (`AlarmConfig.kt`, `SnoozeConfig.kt`, etc.) have `@Preview`s either, so adding one here would be inconsistent with the existing pattern in this specific directory rather than following it

## 7. Spec reconciliation

- [x] 7.1 Delta spec for `action-execution` written (during the proposal step, before this implementation pass)
- [x] 7.2 Replacement delta spec for `snooze-scheduling` written (same)

## 8. Quality gates

- [ ] 8.1 `./gradlew spotlessApply` — **blocked**: this sandbox's egress policy returns 403 for both the Gradle 9.1.0 wrapper distribution (redirects through a GitHub releases URL) and Google's Maven repo (`dl.google.com`), so no Gradle invocation in this session can even sync, let alone run tasks. Confirmed via direct `curl` to both hosts, not a retry-able transient failure.
- [ ] 8.2 `./gradlew detekt` — same blocker
- [ ] 8.3 `./gradlew architectureCheck` — same blocker
- [ ] 8.4 `./gradlew test` — same blocker; compensated with an extra-careful manual read-through of every changed/new file (imports, call-site signatures, Compose experimental API usage) in lieu of a compiler pass

## 9. Manual verification

- [ ] 9.1 On-device: configure "until time" mode a few minutes out, confirm the notification stays hidden and reappears once at the checkpoint
- [ ] 9.2 On-device: configure a short recurring window (e.g. 2-minute interval over a 10-minute window) with a test app, confirm each checkpoint's release is actually visible (not immediately re-hidden) and matches arriving between checkpoints are held for the next one
- [ ] 9.3 On-device: confirm a match arriving after the window end passes through immediately

## 10. Archive

- [ ] 10.1 Merge delta specs into `openspec/specs/action-execution/spec.md` and `openspec/specs/snooze-scheduling/spec.md`
- [ ] 10.2 Move this change to `openspec/changes/archive/YYYY-MM-DD-snooze-scheduled-mode/`
