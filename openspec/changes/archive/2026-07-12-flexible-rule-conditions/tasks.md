# Tasks: Flexible Rule Conditions

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~650-850 (prod ~350-450, tests ~250-350, docs ~50) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (domain+storage+wire) → PR 2 (matcher/engine+call sites+tests) → PR 3 (editor UI) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

**Resolved delivery decision (apply phase):** `single-pr` with `size:exception` — the user
explicitly accepted shipping this change as one PR on the current branch instead of the
recommended 3-PR chain, after being told the estimated ~650-850 changed lines exceed the 400-line
review budget. Implemented as a single coherent change across all 7 phases below.

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Sealed `RuleCondition` + JSON storage + wire DTO/schema bump (D1, D3, D4, D8) | PR 1 | Base: main. No matcher behavior change; ADR amendment included. |
| 2 | Matcher/engine dispatch + `now` threading + call sites + core tests (D2, D6, D6a) | PR 2 | Base: PR 1 branch. Pure-Kotlin logic + unit tests. |
| 3 | Editor UI: day/time pickers, contract, displayText, fixtures (D5) | PR 3 | Base: PR 2 branch. User-facing; needs PR 1+2 merged first. |

## Phase 1: Domain model (D1)

- [x] 1.1 `domain/model/RuleCondition.kt`: replace flat class with sealed interface `RuleCondition { id }` and subtypes `ContentMatchCondition`, `DayOfWeekCondition(days: Set<DayOfWeek>)`, `TimeRangeCondition(start: LocalTime, end: LocalTime)`
- [x] 1.2 Fix every domain/data compile break from the sealed refactor (mechanical, compiler-driven) excluding editor UI (Phase 6)

## Phase 2: Storage (D3, D8)

- [x] 2.1 `core/data/local/entity/RuleConditionEntity.kt`: collapse `condition`/`operator`/`value` columns to single `payload: String` column
- [x] 2.2 `core/data/local/mapper/RuleConditionMapper.kt`: `RuleCondition ↔ ConditionDto ↔ JSON` round-trip via one `Json` instance with polymorphic resolution
- [x] 2.3 `AppDatabase.kt`: bump `CURRENT_VERSION`; confirm `DatabaseModule.applyDebugOnlyFallback()` still gates `fallbackToDestructiveMigration()` to `BuildConfig.DEBUG` only — no `Migration` object written (per D3/ADR amendment)
- [x] 2.4 Test (`RuleConditionMapperTest`): each subtype round-trips storage without data loss — maps to rule-storage spec scenarios "Content/Day-of-week/Time-range condition round-trips through storage" and "Mixed-family condition list round-trips in order"

## Phase 3: Wire format (D4, D8)

- [x] 3.1 `core/rulesharing/dto/ConditionDto.kt`: `@SerialName`-discriminated sealed hierarchy (`content_match`, `day_of_week`, `time_range`)
- [x] 3.2 `core/rulesharing/RuleWireMapper.kt`: map each `RuleCondition` subtype ↔ `ConditionDto` subtype (`when`, symmetric to `ExtractionMethodDto` blocks)
- [x] 3.3 `core/rulesharing/dto/RuleExportDto.kt`: bump `RULE_EXPORT_SCHEMA_VERSION` 1 → 2
- [x] 3.4 `core/rulesharing/RuleJsonCodec.kt`: catch `SerializationException` on an unrecognized condition discriminator, map to import failure (fail-the-import posture)
- [x] 3.5 Regenerate `app/src/test/resources/rule-export-v1.json` golden file to the v2 shape; update `RuleJsonCodecGoldenFileTest`
- [x] 3.6 Test (`RuleJsonCodecTest`): export/re-import round-trips a rule with mixed condition families — maps to rule-storage spec scenario "Exported rule with typed conditions re-imports identically"
- [x] 3.7 `docs/adr/011-rule-definition-storage.md`: confirm the 2026-07-12 amendment text committed matches the shipped shape (already drafted in design.md D8 — verify no drift during implementation)

## Phase 4: Matcher/engine dispatch (D2)

- [x] 4.1 `core/extraction/RuleMatcher.kt`: add `now: LocalDateTime` param to `matches`/`matchesCondition`; sealed `when` dispatch (`ContentMatchCondition` → existing logic verbatim, `DayOfWeekCondition` → `now.dayOfWeek in days`, `TimeRangeCondition` → wrap-aware minute-truncated comparison)
- [x] 4.2 `core/extraction/RuleEngine.kt`: `evaluate(notification, rules)` gains mandatory `now: LocalDateTime` param, forwarded to `RuleMatcher.matches`
- [x] 4.3 Test (`RuleMatcherTest`): day-of-week scenarios — single day, multi-day set, non-listed day, empty set never matches, all 7 individual days (spec.md "Day-of-week matching" scenarios)
- [x] 4.4 Test (`RuleMatcherTest`): time-range scenarios — same-day within/outside bounds, inclusive boundaries, overnight wrap both sides, overnight excludes daytime gap, degenerate `start == end` matches only exact instant (spec.md "Time-range matching, including overnight wrap" scenarios)
- [x] 4.5 Test (`RuleMatcherTest`): content-match regression — identical behavior to pre-change (spec.md "Content-match condition preserves existing behavior" scenarios)
- [x] 4.6 Test (`RuleMatcherTest`/`RuleEngineTest`): mixed-family AND combination — all-match and any-one-fails cases (spec.md "Multiple conditions on a rule combine with AND" scenarios); evaluation never throws for any sealed member (spec.md "Sealed condition evaluation is total and fail-closed")

## Phase 5: Call sites (D6, D6a)

- [x] 5.1 `core/notification/ProcessNotificationUseCase.kt`: inject `CurrentTimeProvider`, pass `timeProvider.now()` at `RuleEngine.evaluate()` call site
- [x] 5.2 `features/ruleeditor/viewmodel/RuleEditorViewModel.kt` `testAgainstHistory()`: derive `now` per-notification via `Instant.ofEpochMilli(notification.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()`, not wall-clock `CurrentTimeProvider.now()`
- [x] 5.3 Test (`ProcessNotificationUseCaseTest`): verifies `RuleEngine.evaluate` is called with `timeProvider.now()`
- [x] 5.4 Test (`RuleEditorViewModelTest`): `testAgainstHistory()` evaluates each historical notification against its own converted timestamp, not a shared wall-clock instant

## Phase 6: Editor UI (D5)

- [x] 6.1 `features/ruleeditor/contract/MatchingLogicContract.kt`: add `conditionType` selector + day/time draft fields to `UiState`; add corresponding `UiEvent`s
- [x] 6.2 `features/ruleeditor/viewmodel/MatchingLogicViewModel.kt`: per-family validation in `initForEdit`/`confirm` (content requires field/operator/value; day requires non-empty `days`; time requires both `start`/`end`)
- [x] 6.3 `features/ruleeditor/ui/extractdata/MatchingLogicBottomSheet.kt`: type picker (Content / Day of week / Time range) dispatching to three config bodies; day-of-week 7-chip `FilterChip` row; time-range two `TimePicker`s with spans-midnight hint
- [x] 6.4 `features/ruleeditor/ui/components/WhenSection.kt`: extend `ConditionCard`'s `displayText` with `DayOfWeekCondition`/`TimeRangeCondition` branches
- [x] 6.5 `features/ruleeditor/domain/RuleUiModel.kt` + fixtures/previews: update to construct the new sealed subtypes (no shape change to `triggers: PersistentList<RuleCondition>` itself)
- [x] 6.6 Fix remaining compile breaks in editor tests/fixtures (`TestFixtures`, `RuleUiModelTest`, `MatchingLogicViewModelTest`) from the sealed refactor
- [x] 6.7 Test (`MatchingLogicViewModelTest`): per-family validation — content/day/time each reject incomplete drafts and accept complete ones

## Phase 7: Quality gates & archive

- [x] 7.1 `./gradlew spotlessApply`, `detekt`, `architectureCheck`, `test` — all pass clean; update baselines only for pre-existing debt touched incidentally, per boy-scout policy
- [x] 7.2 Merge `openspec/changes/flexible-rule-conditions/specs/rule-storage/spec.md` delta into `openspec/specs/rule-storage/spec.md` (new capability); confirm `openspec/specs/rule-conditions/spec.md` is already in place as the new capability spec
- [x] 7.3 Move change to `openspec/changes/archive/YYYY-MM-DD-flexible-rule-conditions/`

### Implementation notes / deviations

- Task 6.4 says "extend `ConditionCard`'s `displayText`" — the `displayText` extension property
  actually lives in `features/ruleeditor/contract/RuleEditorContract.kt` (not `WhenSection.kt`,
  which only imports and reads it). Extended it there against the real current code, matching the
  apply-phase instruction to follow the actual codebase over an idealized description.
- Added `RuleCondition.withId(id: String): RuleCondition` domain extension (`domain/model/RuleCondition.kt`)
  discovered as a necessary judgment call during implementation: the sealed interface has no
  generated `copy()` of its own, and `RuleJsonCodec.withFreshIdentityForImport()` needs to re-id
  every condition regardless of its subtype.
- `MatchingLogicContract`/`MatchingLogicViewModel`/`MatchingLogicBottomSheet` got a `ConditionType`
  selector (`CONTENT` / `DAY_OF_WEEK` / `TIME_RANGE`) plus per-family draft fields, exactly as D5
  described, dispatched via a `SingleChoiceSegmentedButtonRow` type picker mirroring the existing
  `WeekdaySelector`/`SnoozeScheduleSelectors` UI conventions in this codebase.
- Full verification run: `./gradlew spotlessApply detekt architectureCheck :app:testDebugUnitTest`
  all pass clean; 523 unit tests total (up from the pre-change 286), zero new baseline entries in
  either `config/detekt/baseline.xml` or `config/architecture/baseline.txt`.
