# Tasks: Rule Condition Combinator (ALL / ANY)

Reference: `openspec/changes/rule-condition-combinator/spec.md`, `openspec/changes/rule-condition-combinator/design.md`. See CLAUDE.md "Development Status" for pre-launch policies.

Strict TDD is ON — tasks marked `[TDD]` must be written test-first (`./gradlew test`) before the corresponding production code.

## 1. Domain model

### Task 1.1 — Add `ConditionCombinator` enum and `Rule.conditionLogic` field
- Sequential, blocks everything downstream. No parallelism.
- Create `domain/model/ConditionCombinator.kt`: plain enum `{ ALL, ANY }`.
- Modify `domain/model/Rule.kt`: add `conditionLogic: ConditionCombinator = ConditionCombinator.ALL`.
- Acceptance criteria:
  - Enum has exactly two members, no Android imports.
  - `Rule` compiles with all existing construction call sites unchanged (default parameter absorbs them).
  - Satisfies spec scenario "Default combinator is ALL" at the domain-model level (a `Rule` built without the param has `conditionLogic == ALL`).
- Estimate: <1 hour.

## 2. Extraction layer

### Task 2.1 — `[TDD]` `RuleMatcher` combinator dispatch
- Sequential, depends on 1.1.
- Write failing tests first in `RuleMatcherTest` covering:
  - Scenario "ALL matches only when every condition matches" (3 conditions, all match → true; one fails → false).
  - Scenario "ANY matches when at least one condition matches" (3 conditions, exactly one matches → true).
  - Scenario "Empty conditions list matches under both combinators" (empty list + ALL → true; empty list + ANY → true).
  - Scenario "ANY with every condition failing does not match".
  - Scenario "ANY short-circuits correctly regardless of match order" (first fails, later matches → true; verifies no early-exit bug from naive `.any` misuse).
- Then implement: `RuleMatcher.matches` gains a `combinator: ConditionCombinator = ConditionCombinator.ALL` parameter; keep the existing `isEmpty()` short-circuit first (unchanged), then `when (combinator) { ALL -> conditions.all { ... }; ANY -> conditions.any { ... } }`.
- Acceptance criteria:
  - All 5 scenarios above pass as new test cases; all pre-existing `RuleMatcherTest` cases (AND-only) still pass unmodified (default param).
  - `RuleMatcher.kt` stays pure Kotlin (no Android imports) — required by `architectureCheck` domain/extraction purity conventions.
  - `./gradlew test --tests "*RuleMatcherTest*"` green.
- Estimate: 1-2 hours.

### Task 2.2 — Wire `RuleEngine` to pass `rule.conditionLogic`
- Sequential, depends on 2.1.
- Modify `core/extraction/RuleEngine.kt` line ~42: pass `rule.conditionLogic` into `RuleMatcher.matches(...)`.
- Acceptance criteria:
  - Existing `RuleEngineTest` suite passes unmodified (all pre-existing test rules default to ALL, behavior-preserving).
  - No new `RuleEngine` integration test required (per design's accepted coverage gap) — matcher unit coverage is sufficient.
- Estimate: <1 hour.

## 3. Data layer

### Task 3.1 — `[TDD]` `RuleEntity.condition_logic` column + `RuleMapper` round-trip
- Sequential, depends on 1.1 (does not depend on section 2; could run in parallel with 2.1/2.2 if desired, but keep sequential per dependency chain in design doc).
- Write failing tests first in the `RuleMapper` test suite covering:
  - Scenario "Persisted rule round-trips its combinator" (`Rule(conditionLogic = ANY)` → entity → domain → `ANY`).
  - Scenario "Pre-existing rows default to ALL" (entity with no/blank/unknown `condition_logic` string → domain `ALL`).
  - Legacy/unknown string input (e.g. garbage or old schema value) → maps to `ALL`, never throws.
- Then implement:
  - `core/data/local/entity/RuleEntity.kt`: add `@ColumnInfo(name = "condition_logic", defaultValue = "ALL") val conditionLogic: String = "ALL"`.
  - `core/data/local/mapper/RuleMapper.kt`: `toEntity` writes `domain.conditionLogic.name`; `toDomain` uses `runCatching { ConditionCombinator.valueOf(entity.conditionLogic) }.getOrDefault(ConditionCombinator.ALL)`.
  - No hand-written `Migration(1, 2)` — rely on destructive-migration fallback per CLAUDE.md pre-launch policy. Do not touch the release-build migration guard.
- Acceptance criteria:
  - Both scenarios pass; round-trip and legacy-default tests are new, isolated test cases (not modifications hiding regressions).
  - `RuleEntity`/mapper visibility rules from `architectureCheck` (rule 1: `internal`) preserved — new column doesn't change class visibility.
  - `./gradlew test --tests "*RuleMapper*"` green.
- Estimate: 1-2 hours.

## 4. Wire format

### Task 4.1 — `[TDD]` Wire DTO field, schema version bump, `RuleWireMapper`, golden file regen
- Sequential, depends on 1.1 and 3.1 (schema version bump is a cross-cutting compatibility concern best done once storage is settled).
- Implement:
  - `core/rulesharing/dto/RuleExportDto.kt`: add `@SerialName("conditionLogic") val conditionLogic: String = "ALL"` to `RuleDto`;
  - `core/rulesharing/RuleWireMapper.kt`: `toDto` writes `domain.conditionLogic.name`; `toDomain` uses `runCatching { ConditionCombinator.valueOf(dto.conditionLogic) }.getOrDefault(ConditionCombinator.ALL)` (unknown/absent → `ALL`, never throws — unlike the stricter condition/method mapping elsewhere in this file).

## 5. Documentation

### Task 5.1 — ADR 011 removal + `docs/rule-format.md` + `docs/capabilities.md`
- Sequential, depends on 4.1 (docs describe the final shipped shape).
- Document the combinator seam and sanction future nested `ConditionGroup` as a code-only extension point in `docs/rule-format.md` instead.
- `docs/rule-format.md`: document schema v2, the `conditionLogic` field, and v1→v2 decode-default behavior.
- `docs/capabilities.md`: note the new per-rule ALL/ANY combinator capability (matching-semantics map, not editor-exposed).
- Acceptance criteria:
  - No production code changes in this task — docs only.
  - Docs accurately reflect only what was implemented (no mention of UI exposure or `ConditionGroup` as built).
- Estimate: <1 hour.

## 6. Verification gates (final, blocking)

### Task 6.1 — Full quality gate run
- Sequential, depends on all prior tasks. This is the final gate before PR.
- Run in order: `./gradlew spotlessApply`, `./gradlew detekt`, `./gradlew architectureCheck`, `./gradlew test`.
- Acceptance criteria:
  - All four commands pass with zero new Detekt/architectureCheck baseline entries introduced.
  - Full test suite green, including all new tests from tasks 2.1, 3.1, 4.1.
  - `git diff --stat` reviewed against the Review Workload Forecast below before opening the PR.
- Estimate: <1 hour (assuming no gate failures; add time if fixes are needed).

## Dependency chain summary

```
1.1 (domain)
 ├─→ 2.1 (RuleMatcher) ─→ 2.2 (RuleEngine wiring)
 └─→ 3.1 (RuleEntity/RuleMapper)
        └─→ 4.1 (wire DTO + schema v2 + golden file)
               └─→ 5.1 (docs)
                      └─→ 6.1 (final gates)
```
Tasks 2.1/2.2 and 3.1 may run in parallel (both depend only on 1.1, touch disjoint files); everything from 4.1 onward is strictly sequential.

## Review Workload Forecast

- **Estimated changed lines**: ~150-220 lines total (1 new file ~10 lines; ~6 modified production files, each a small additive field/branch, roughly 10-30 lines each; 2 test files gaining ~15-25 lines each of new cases; 1 golden JSON fixture regen ~5 line diff; 3 doc files with short additive sections).
- **400-line budget risk**: Low. Well under the 400-line full-4R review trigger; this is an additive, single-seam change with no touched auth/update/security/payments paths.
- **Chained PRs recommended**: No. Small enough to ship as a single PR; splitting would add more review overhead than it saves.
- **Decision needed before apply**: No. Design and spec are settled (both marked "Open Questions: None"); no unresolved architectural choice blocks starting `sdd-apply`.
