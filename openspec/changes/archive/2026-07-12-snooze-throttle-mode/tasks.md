# Tasks: Snooze Throttle Mode

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~550-750 (prod ~350-450, tests ~200-300) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (domain+data) → PR 2 (tracker+executor) → PR 3 (UI) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Domain + data foundation (config keys, `SUPPRESSED` outcome, DAO/repo lookback) | PR 1 | Base: main. No behavior change yet; additive only. |
| 2 | Throttle tracker + executor branch + clock seam | PR 2 | Base: PR 1 branch. Core rate-limit logic + unit tests. |
| 3 | UI: outcome card, window selector, sheet wiring | PR 3 | Base: PR 2 branch. User-facing; needs PR 1+2 merged first. |

## Phase 1: Domain model & config (D1)

- [x] 1.1 `RuleAction.kt`: add `SnoozeMode.THROTTLE`, `SNOOZE_THROTTLE_WINDOW_MINUTES_KEY`/`DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES=10`, `SNOOZE_THROTTLE_RESET_AT_KEY`
- [x] 1.2 Add `getThrottleWindowMinutes()` (clamped 1..1440) and `getThrottleResetAt(): Long` (default 0L) accessors
- [x] 1.3 Add `createThrottleSnooze(id, windowMinutes, resetAt, isEnabled)` factory mirroring `createScheduledSnooze`
- [x] 1.4 `ActionOutcome.kt`: add `SUPPRESSED` case

## Phase 2: Data layer (D3)

- [x] 2.1 `RuleExecutionDao.kt`: add `getRecentExecutionsForPackageSince(packageName, since)` join query
- [x] 2.2 `RuleExecutionRepository`: add `lastThrottleDeliveryAt(actionId, packageName, sinceMs): Result<Long?>`, filtering rows where `actionOutcomes[actionId] == SUCCESS`, max `createdAt`
- [x] 2.3 Test: DAO query returns rows within window, excludes older rows (Room/robolectric or existing DAO test harness) — deviation: no Room/Robolectric JVM test infra exists in this repo (room-testing is androidTest-only); covered instead via `RuleExecutionRepositoryImplTest` with a mocked DAO exercising the same filter/max-createdAt logic.
- [x] 2.4 Test: repository method fails-open (`Result.failure`/empty → null) per D3

## Phase 3: Clock seam & tracker (D2, D6, D9)

- [x] 3.1 Add `CurrentTimeProvider.nowEpochMillis()`; implement in `SystemCurrentTimeProvider`
- [x] 3.2 New `core/notification/action/NotificationThrottleTracker.kt` (`@Singleton @Inject`): mutex-guarded `shouldDeliver(actionId, packageName, windowMs, resetAt)` per D2, `UNKNOWN_PACKAGE` fallback, `cleanupOldEntries`
- [x] 3.3 Tests (`NotificationThrottleTrackerTest`): first match delivers; in-window match suppressed; boundary match (`now-last==window`) delivers; `resetAt` invalidates stale delivery; DB fallback on cold key; concurrent near-simultaneous matches (D9) resolve to exactly one delivery — post-verify follow-up added two more cases closing CRITICAL gaps from the verify report: `independent windows per targeted app` (same `actionId`, two different `packageName`s isolate correctly) and `an unresolvable source package falls back to a consistent scope key` (blank `packageName` still throttles via the `UNKNOWN_PACKAGE` fallback)

## Phase 4: Executor wiring (D7)

- [x] 4.1 Inject `NotificationThrottleTracker` into `SnoozeActionExecutor`; add `SnoozeMode.THROTTLE -> executeThrottle(...)` per D7 (deliver → `SUCCESS`; drop → `cancel(sbnKey)` → `SUPPRESSED`)
- [x] 4.2 `SnoozeActionExecutorTest`: throttle delivers first match, suppresses second in-window, delivers after window elapses, null-controller/sbnKey still yields `SKIPPED`
- [x] 4.3 `NotificationDetailScreen.kt`: add `SUPPRESSED` branch to the three exhaustive `ActionChip` `when` blocks (muted color, `⊘` glyph, "throttled" label)

## Phase 5: UI (D5, D8)

- [x] 5.1 `SnoozeOutcome.kt`: add `THROTTLE` case with card copy ("Let the first through, mute the rest")
- [x] 5.2 New `ThrottleWindowSelector` composable (`features/ruleeditor/ui/components/`) modeled on `SnoozeDurationSelector` (5m/10m/30m/1h + custom)
- [x] 5.3 `SnoozeBottomSheet.kt`: hoist `throttleWindowMinutes` state; extend `toInitialOutcome()`/`buildSnoozeAction()` to call `createThrottleSnooze`, stamping `resetAt=now` on window change or disable→enable transition (D5), else preserving prior `resetAt` — the disable→re-enable half of this is implemented in `RuleEditorViewModel.toggleAction` (the raw enable/disable toggle path), since `SnoozeBottomSheet` doesn't own the enable switch itself.
- [x] 5.4 ViewModel/UI state tests for the new outcome selector (Turbine, per CLAUDE.md conventions) — added as plain state-assertion unit tests in `RuleEditorViewModelTest` (existing tests in this class don't use Turbine either; the ViewModel change under test is synchronous state, not a Flow/effect).
- [x] 5.5 Post-verify follow-up: new `SnoozeBottomSheetTest.kt` closing the third CRITICAL gap from the verify report (D5 window-duration-change half). No Compose-UI/androidTest convention exists in this repo yet, so `shouldResetThrottleWatermark` was widened from `private` to `internal` and unit-tested directly as a pure decision function instead of standing up a new instrumentation-test harness for one function.

## Phase 6: Quality gates & archive

- [x] 6.1 `./gradlew spotlessApply`, `detekt`, `architectureCheck`, `test` — all pass clean. `detekt` and `architectureCheck` baselines were updated only for pre-existing debt from the just-merged scheduled-snooze feature (not yet baselined) plus one new `raw-exception-leak` entry for `RuleExecutionRepositoryImpl.lastThrottleDeliveryAt`, consistent with rule 6's documented "helper doesn't exist yet" exception; see apply report for details.
- [x] 6.2 Merge delta specs into `openspec/specs/action-execution/spec.md` and `openspec/specs/snooze-scheduling/spec.md`
- [x] 6.3 Move change to `openspec/changes/archive/YYYY-MM-DD-snooze-throttle-mode/`
