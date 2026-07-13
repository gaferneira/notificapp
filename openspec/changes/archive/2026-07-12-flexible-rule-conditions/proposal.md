# Proposal: Flexible Rule Conditions (typed condition families)

## Intent

Rules can only match on notification *content* today. `RuleCondition` is one flat `(condition, operator, value)` shape and `RuleMatcher.matchesCondition` always extracts a string from the notification and runs a string operator — there is no place for conditions that carry no notification-derived value. Users want rules that also gate on **time** (day-of-week, time-of-day range), with device/system-state conditions to follow. This is the exact rule-shape churn ADR 011 named as its revisit trigger, now unblocked because Notificapp has not launched (destructive migrations and wire-format breaks are allowed per CLAUDE.md "Development Status").

## Scope

### In Scope
- Restructure `RuleCondition` from a flat data class into a **sealed interface** with per-type data classes: `ContentMatchCondition` (today's field+operator+value, renamed), `DayOfWeekCondition(days)`, `TimeRangeCondition(start, end)`. The hierarchy is designed so a future `DeviceStateCondition` slots in as another subtype without a sealed-hierarchy rewrite.
- Move condition storage from the per-column `rule_conditions` shape to a **JSON-serialized polymorphic column** (kotlinx.serialization, `@SerialName`-discriminated) so future condition types are pure Kotlin — no new entity/mapper/migration.
- Split matching by condition family, mirroring the `ActionExecutor`/`ActionDispatcher` Hilt multibinding: pure-Kotlin evaluators for content-match and day/time (day/time reuse the existing `CurrentTimeProvider` seam). `TimeRangeCondition` **wraps across midnight** (22:00–06:00 matches 22:00–23:59 AND 00:00–06:00).
- Rule editor UI + rulesharing DTO (`ConditionDto`/`RuleWireMapper`) support for the two new condition types.
- Formal **amendment to ADR 011** adopting the JSON storage column for conditions.

### Out of Scope
- **`DeviceStateCondition`** (DND, screen on/off, focus mode) — deferred to a separate future change once this sealed-condition foundation lands. That change owns the new Android-boundary `DeviceStateProvider` seam and the `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` runtime grant + editor gating.
- **OR / condition groups** (`ConditionGroup`). The sealed shape makes it natural later — beneficial side effect only, not built here unless requested.
- Migrating fields/actions to JSON storage — conditions only.

## Capabilities

### New Capabilities
- `rule-conditions`: typed condition families (content, day-of-week, time-range), their evaluation, and editor/wire-format support.

### Modified Capabilities
- `rule-storage`: condition persistence moves to a JSON polymorphic column (ADR 011 amendment).

## Approach

Restructure `RuleCondition` into a sealed interface and ship the two pure-Kotlin, permission-free condition types: **day-of-week and time-range** (highest value/lowest complexity, only need the existing `CurrentTimeProvider` seam). `RuleMatcher.matches` dispatches each condition to its family evaluator. The sealed hierarchy and JSON polymorphic storage are deliberately open for extension: the deferred `DeviceStateCondition` adds a new subtype + evaluator + `@SerialName` discriminator with no rewrite of the hierarchy, storage, or wire format. The fail-closed rule below is the guiding evaluation contract for any future non-content condition.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/model/RuleCondition.kt` | Modified | Flat class → sealed interface + subtypes (content, day-of-week, time-range) |
| `core/extraction/RuleMatcher.kt` | Modified | Family dispatch instead of single string path |
| `core/extraction/RuleEngine.kt` | Modified | `evaluate()` gains a mandatory `now: LocalDateTime` parameter, threaded through to `RuleMatcher` |
| `core/notification/ProcessNotificationUseCase.kt` | Modified | Injects `CurrentTimeProvider` and passes `now()` at its `RuleEngine.evaluate()` call site |
| `core/data/local/entity/RuleConditionEntity.kt` + `RuleConditionMapper` | Modified | JSON column instead of typed columns |
| `core/rulesharing/dto/ConditionDto.kt` + `RuleWireMapper.kt` | Modified | Polymorphic wire representation |
| `core/rulesharing/dto/RuleExportDto.kt` | Modified | `RULE_EXPORT_SCHEMA_VERSION` bumps 1 → 2 |
| `core/rulesharing/RuleJsonCodec.kt` | Modified | Catch `SerializationException` for an unrecognized condition discriminator on decode, map to import failure |
| `features/ruleeditor/**` (`WhenSection`, `MatchingLogic*`, contract, `RuleUiModel`) | Modified | Add/edit day-of-week and time-range conditions; `RuleEditorViewModel.testAgainstHistory()` requires a non-mechanical logic change deriving `now` per-notification via explicit `Instant`/`ZoneId` conversion from each notification's own `timestamp` |
| `docs/adr/011-rule-definition-storage.md` | Modified | JSON-storage amendment |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| ~60 call sites reference the flat shape | High | Sealed refactor is mechanical; compiler catches every site |
| Overnight/wrapping time ranges are error-prone | Med | Wrap semantics defined (22:00–06:00 spans midnight); unit-test the wrap and same-day cases explicitly |
| Sealed shape not actually extensible for later device-state | Low | Design review confirms a new subtype needs no hierarchy/storage/wire rewrite; called out in Approach |
| JSON column loses per-condition queryability | Low | No current query filters on condition internals (ADR 011) |

## Rollback Plan

Single change branch; revert restores the flat `RuleCondition`, the `rule_conditions` columns, and the ADR. Because the app is pre-launch, no stored data or exported rules need compat — destructive DB reset is acceptable.

## Dependencies

- `CurrentTimeProvider` seam (from snooze-throttle-mode) — reused, no change.

## Success Criteria

- [ ] A rule can gate on day-of-week and fires only on the selected days.
- [ ] A rule can gate on a time-range, including one that wraps across midnight, and fires only within the window.
- [ ] Adding a hypothetical new condition type (incl. the future device-state type) needs zero Room migration and no sealed-hierarchy rewrite.
- [ ] Content-match rules behave identically to today (regression-free); pure-Kotlin evaluators unit-tested.
- [ ] ADR 011 amendment merged.

## Confirmed decisions (resolved question round)

1. **Fail-closed** — when a condition cannot be evaluated (e.g. the deferred device-state type on data it can't read), it does **not** match. No fail-open over-firing. This is the guiding evaluation semantics for any future non-content condition.
2. **Time-range wraps across midnight** — `TimeRangeCondition` with start > end spans midnight: 22:00–06:00 matches 22:00–23:59 AND 00:00–06:00.
3. **Scope narrowed to time conditions** — this change ships only `DayOfWeekCondition` + `TimeRangeCondition` (pure Kotlin, no new permissions, no `DeviceStateProvider`). `DeviceStateCondition` is a separate future change built on top of this sealed-condition foundation.
