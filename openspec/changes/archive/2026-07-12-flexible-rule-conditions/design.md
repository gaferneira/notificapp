## Context

Rules match only on notification *content* today. `RuleCondition` is one flat `(condition, operator, value)` data class, and `RuleMatcher.matchesCondition` always extracts a string from the `Notification` and runs a `MatchingOperator`. There is no seam for a condition that carries **no notification-derived value** — e.g. "only on weekdays" or "only between 22:00 and 06:00". This is the exact rule-shape churn ADR 011 named as its revisit trigger, now unblocked because Notificapp is pre-launch (destructive schema resets and wire-format breaks are allowed).

This change restructures `RuleCondition` into a **sealed interface** with three subtypes and moves condition storage to a JSON polymorphic column, so the *deferred* `DeviceStateCondition` (DND/screen/focus) can later land as a pure additive subtype with no hierarchy, storage, or wire rewrite. Device-state itself is **not** built here.

Constraints that shape this design:

- `core/extraction` (`RuleMatcher`, `RuleEngine`) must stay **pure Kotlin, no Android imports** (CLAUDE.md core/extraction purity rule; architectureCheck rule 5: `domain/**` never imports `features/**`).
- The dependency graph flows `core/notification → core/extraction`, never the reverse. So `RuleMatcher`/`RuleEngine` **cannot import** `CurrentTimeProvider` (it lives in `core/notification/action`) — "now" must arrive as a parameter.
- The codebase already ships `@SerialName`-discriminated polymorphic sealed serialization for the wire format (`ExtractionMethodDto`, 10 subtypes, used by `RuleWireMapper`). But `RuleFieldMapper.toEntity`/`toDomain` serializes the **domain** `RuleField.ExtractionMethod` class directly (its own `@Serializable` sealed hierarchy, via a Room-local `Json` instance with `classDiscriminator = "classType"`) — it never touches `ExtractionMethodDto`. There is no existing case in this codebase of one class serving as both the Room storage payload and the wire DTO simultaneously; conditions dual-using `ConditionDto` this way is a **new coupling**, not a mirrored pattern (see D3's honest risk statement).
- architectureCheck rule 7 (contract purity): `features/*/contract` must not import `core.extraction` internals — the editor maps to feature-owned models at the ViewModel boundary. `RuleCondition` is a `domain/` type (already imported by contracts today), so it stays legal.

## Goals / Non-Goals

**Goals:**
- `RuleCondition` becomes a sealed interface: `ContentMatchCondition` (today's behavior, renamed), `DayOfWeekCondition(days: Set<DayOfWeek>)`, `TimeRangeCondition(start: LocalTime, end: LocalTime)`.
- Condition persistence moves from typed columns to a **single JSON polymorphic `payload` column** on the existing `rule_conditions` table, keeping per-row `id` addressability and the `rule_id` FK/cascade.
- `RuleMatcher` dispatches by condition family via a sealed `when` inside the existing pure `object` — no DI, no Hilt multibinding.
- Day/time evaluation reads "now" through a threaded `LocalDateTime` parameter sourced from the existing `CurrentTimeProvider` seam at the `core/notification` boundary.
- `TimeRangeCondition` **wraps across midnight** (22:00–06:00 matches 22:00–23:59 AND 00:00–06:00).
- Editor UI + `ConditionDto`/`RuleWireMapper` support the two new types; formal ADR 011 amendment.

**Non-Goals:**
- `DeviceStateCondition`, `DeviceStateProvider`, notification-policy permission — deferred future change (this design proves it slots in; see D7).
- OR / condition groups (`ConditionGroup`) — sealed shape makes it natural later; not built.
- Migrating fields/actions to JSON storage — conditions only.

## Decisions

### D1: `RuleCondition` → sealed interface (domain, pure Kotlin, not `@Serializable`)

```kotlin
@Immutable
sealed interface RuleCondition {
    val id: String

    @Immutable
    data class ContentMatchCondition(
        override val id: String,
        val condition: MatchingCondition,
        val operator: MatchingOperator,
        val value: String,
    ) : RuleCondition

    @Immutable
    data class DayOfWeekCondition(
        override val id: String,
        val days: Set<java.time.DayOfWeek>,
    ) : RuleCondition

    @Immutable
    data class TimeRangeCondition(
        override val id: String,
        val start: java.time.LocalTime,
        val end: java.time.LocalTime,
    ) : RuleCondition
}
```

`ContentMatchCondition` fields become non-nullable (the flat class's nullability only existed to model "not yet configured"; the editor now guarantees a fully-formed subtype at construction). Domain stays **not `@Serializable`** — matching the ADR-011 TD-9 amendment posture that domain renames must never silently change wire/column JSON. Serialization is delegated to the DTO layer (D3). `id` on the interface preserves editor remove/edit-by-id.

**Alternatives considered:** (a) keep flat class + `type` enum discriminator — rejected: reintroduces the nullable-soup and can't carry `Set<DayOfWeek>`/`LocalTime` without stringly-typed abuse. (b) Make the sealed interface itself `@Serializable` and store it directly — rejected: recouples domain shape to persisted JSON, the exact gap TD-9 closed.

### D2: `RuleMatcher` — sealed `when` dispatch inside the pure `object`, "now" threaded as a parameter

Keep `RuleMatcher` a dependency-free `object`. Add a `now: LocalDateTime` parameter and dispatch by family:

```kotlin
fun matches(notification: Notification, conditions: List<RuleCondition>, now: LocalDateTime): Boolean =
    conditions.isEmpty() || conditions.all { matchesCondition(notification, it, now) }

private fun matchesCondition(n: Notification, c: RuleCondition, now: LocalDateTime): Boolean = when (c) {
    is RuleCondition.ContentMatchCondition -> matchesContent(n, c)   // today's logic verbatim
    is RuleCondition.DayOfWeekCondition    -> now.dayOfWeek in c.days
    is RuleCondition.TimeRangeCondition    -> matchesTimeRange(c, now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
}

// Wrap semantics: start <= end → [start, end] inclusive; start > end → wraps midnight,
// matches time >= start OR time <= end. Closed-inclusive: a degenerate start == end range
// matches only its exact instant (t == start), per spec.md.
//
// Precision: `now.toLocalTime()` carries second/nanosecond precision from
// `LocalDateTime.now()`, but the editor's TimePicker-sourced `start`/`end` boundaries are
// minute-precision. Comparing full-precision `now` against a minute-precision boundary makes
// the degenerate `start == end` case (spec.md "Degenerate zero-duration range matches only its
// exact instant") a probability-zero event in practice — `now`'s seconds/nanos are essentially
// never exactly zero at the same instant as a user-chosen minute boundary. `now` is therefore
// truncated to minute precision (`truncatedTo(ChronoUnit.MINUTES)`, equivalently
// `LocalTime.of(now.hour, now.minute)`) before the comparison, so the spec'd scenario is
// actually reachable.
private fun matchesTimeRange(c: RuleCondition.TimeRangeCondition, t: LocalTime): Boolean =
    if (c.start <= c.end) t >= c.start && t <= c.end
    else t >= c.start || t <= c.end
```

**Choice:** sealed `when`, not the `ActionExecutor`/`ActionDispatcher` Hilt multibinding.
**Alternatives considered:** Hilt `@IntoMap` multibinding of `ConditionEvaluator`s (mirrors ActionModule).
**Rationale:** the multibinding exists because action executors have real Android/DI dependencies (controllers, repositories, trackers). Condition evaluation is a **pure function of `(notification, condition, now)`** with zero I/O. Introducing DI here would force `RuleMatcher` to stop being an `object`, drag a Hilt graph into `core/extraction`, and violate its documented purity — all cost, no benefit. A sealed `when` is exhaustive (compiler forces every future subtype to be handled) and keeps the module plain-JVM testable. "now" is threaded as a parameter (not an injected `CurrentTimeProvider`) because extraction must not depend on `core/notification` (reverse-dependency).

### D3: JSON polymorphic storage — reuse `ConditionDto` as the dual-use payload

Replace `ConditionDto` with a `@SerialName`-discriminated sealed hierarchy, exactly mirroring `ExtractionMethodDto`:

```kotlin
@Serializable
sealed class ConditionDto {
    abstract val id: String

    @Serializable @SerialName("content_match")
    data class ContentMatch(
        @SerialName("id") override val id: String,
        @SerialName("condition") val condition: String,
        @SerialName("operator") val operator: String,
        @SerialName("value") val value: String,
    ) : ConditionDto()

    @Serializable @SerialName("day_of_week")
    data class DayOfWeek(
        @SerialName("id") override val id: String,
        @SerialName("days") val days: List<String>,   // ISO names: MONDAY..SUNDAY
    ) : ConditionDto()

    @Serializable @SerialName("time_range")
    data class TimeRange(
        @SerialName("id") override val id: String,
        @SerialName("start") val start: String,        // "HH:mm"
        @SerialName("end") val end: String,
    ) : ConditionDto()
}
```

`ConditionDto` is **dual-used** for wire *and* Room column. **This is a new coupling, not an existing precedent.** `ExtractionMethodDto` does *not* actually serve this dual role — `RuleFieldMapper.toEntity`/`toDomain` (`core/data/local/mapper/RuleFieldMapper.kt`) serializes the **domain** `RuleField.ExtractionMethod` class directly, via its own `@Serializable` annotation and a Room-local `Json` instance (`classDiscriminator = "classType"`); `ExtractionMethodDto` is used exclusively by `RuleWireMapper` for the wire path and is kept deliberately separate from storage. So this design introduces dual-use for the first time in the codebase, and it's justified on its own merits rather than by that (nonexistent) precedent: unlike `RuleField`, where Room and wire representations can evolve independently today because they're two different classes, coupling `ConditionDto` to both roles means any future schema change to it will simultaneously affect stored rows *and* exported rule files. That's a real, accepted risk — see "Dual-use `ConditionDto` couples storage to wire schema" under Risks / Trade-offs — not a cost-free reuse of a proven pattern. `RuleConditionEntity` collapses its three typed columns to one JSON string:

```kotlin
internal data class RuleConditionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "rule_id") val ruleId: String,
    @ColumnInfo(name = "payload") val payload: String,  // Json.encodeToString(ConditionDto)
)
```

`RuleConditionMapper` becomes `domain RuleCondition ↔ ConditionDto ↔ JSON string` (one `Json` instance, `polymorphic` resolved by the sealed `@SerialName`). No Room `TypeConverter` needed — the column is already a `String`. Keeping the `rule_conditions` **table** (rather than a JSON blob on `rules`) preserves the FK cascade, the `rule_id` index, per-row `id` addressability, and leaves the existing `getConditionsForRule` DAO query untouched.

**Storage location — table row vs. column-on-`rules`:** chose per-row `payload`. A blob-on-`rules` would force DAO rewrites and lose row-level id addressability the editor relies on for remove/edit.

**No migration:** pre-launch (CLAUDE.md "Development Status", ADR 011 "Pre-launch context") → `AppDatabase` version bump with destructive `fallbackToDestructiveMigration`, no `Migration(1, 2)` written, no compat shim. That fallback is DEBUG-only; release builds still crash loudly on a missing migration rather than silently wipe (TD-10's guard, unrelated to pre-launch status) — see ADR 011's amendment for the full statement.

### D4: Wire mapper + schema versioning

`RuleWireMapper` maps each domain subtype to its `ConditionDto` subtype and back (`when` over the sealed types, symmetric to the existing `ExtractionMethod ↔ ExtractionMethodDto` blocks). `MatchingCondition`/`MatchingOperator` continue to cross the wire as strings via the existing `toWireString`/`fromWireStringStrict` helpers (unrecognized operator/condition still **fails the import** — a content-match rule that can't evaluate is meaningless). An unrecognized **top-level condition discriminator** (e.g. a future `device_state` from a newer export) surfaces as a kotlinx `SerializationException` on decode — caught in `RuleJsonCodec.decode` and mapped to import failure, consistent with today's fail-the-import posture for conditions.

`RULE_EXPORT_SCHEMA_VERSION` **bumps 1 → 2** (the condition shape is a breaking wire change). The golden-file test (`rule-export-v1.json`) is regenerated to v2; because the app is pre-launch there is no v1 file in the wild to decode, so no back-compat decode branch is owed — documented in the ADR amendment.

### D5: Editor UI — one sheet per family, feature-owned draft models

`MatchingLogicBottomSheet` becomes a type picker (Content / Day of week / Time range) dispatching to three config bodies (per TD-13 the sheet only dispatches):
- **Content** — existing condition/operator/value form, unchanged.
- **Day of week** — a 7-chip `FilterChip` row (Mon–Sun) backed by a `Set<DayOfWeek>`.
- **Time range** — two `TimePicker`s (start/end) with a "spans midnight" hint when start > end.

`MatchingLogicContract.UiState` gains a `conditionType` selector plus the day/time draft fields (a sealed `ConditionDraft` or flat optional fields — implementer's call at task time). Contract purity (rule 7) holds: `RuleCondition` is a `domain/` type, and no `core.extraction` internals are imported. `WhenSection.ConditionCard` renders `condition.displayText` — extend the `displayText` extension with `DayOfWeekCondition`/`TimeRangeCondition` branches (e.g. "Weekdays", "22:00–06:00"). `RuleUiModel.triggers` already holds `PersistentList<RuleCondition>`, so it needs no shape change — only its previews/fixtures update to the new subtypes.

### D6: Threading "now" from the boundary

`RuleEngine.evaluate(notification, rules)` gains a `now: LocalDateTime` parameter, forwarded to `RuleMatcher.matches`. `ProcessNotificationUseCase` (in `core/notification`, which already can see `CurrentTimeProvider`) injects the provider and passes `timeProvider.now()` at call time. `RuleEngine`/`RuleMatcher` remain pure and plain-JVM testable with a fixed instant — same testability rationale as ADR 008's dispatcher injection, achieved without a reverse module dependency.

**D6a: the second call site — `RuleEditorViewModel.testAgainstHistory()`.** `ProcessNotificationUseCase` is not the only production caller of `RuleEngine.evaluate`. `RuleEditorViewModel.testAgainstHistory()` (`features/ruleeditor/viewmodel/RuleEditorViewModel.kt`, ~line 430) backtests a draft rule against up to `BACKTEST_NOTIFICATION_LIMIT` (500) previously captured notifications via `ruleEngine.evaluate(notification, listOf(draftRule))`. Once `now` becomes a mandatory parameter, this call site must decide what "now" means for a notification that was captured in the past — this is a required code change to that call site's logic, not a mechanical compile fix.

**Decision:** `testAgainstHistory()` must source `now` from each candidate `Notification`'s own `timestamp` field, not from wall-clock `CurrentTimeProvider.now()`. Each historical notification is evaluated against the `LocalDateTime` it was actually captured at, so backtesting a draft `TimeRangeCondition`/`DayOfWeekCondition` reflects whether that condition *would have matched at the time the notification arrived* — evaluating all 500 candidates against the current wall-clock instant instead would make day/time conditions match or fail uniformly across the whole backtest set regardless of when each notification actually occurred, which defeats the purpose of a historical backtest.

`Notification.timestamp` is a `Long` (epoch millis), not a `LocalDateTime`, so the conversion — and the `ZoneId` it uses — must be explicit: `Instant.ofEpochMilli(notification.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()`. This must use the device's default zone (`ZoneId.systemDefault()`) to match `SystemCurrentTimeProvider`'s zone choice (`core/notification/action/CurrentTimeProvider.kt`, which uses the device's default time zone because "wall clock local time is what a user means by '9am'") — otherwise a backtest run today would evaluate historical notifications against a different zone than the one live rule evaluation uses, producing results that don't match what the same rule would have done in production.

**Known limitation:** `LocalDateTime` (not `ZonedDateTime`/`Instant`) is threaded through matching, so this conversion discards the zone offset that was actually in effect when the notification was captured. Backtesting is therefore "best effort" — it replays the epoch millis converted through *today's* system-default zone rules, not the exact historical DST/zone-offset state at capture time (e.g. a notification captured before a DST transition is converted using the zone's current rules, not the rules in effect at that instant).

### D7: Future `DeviceStateCondition` slots in with zero rewrite (the extensibility obligation)

Concretely, the deferred change adds **only additive** code — nothing in this design's shapes changes:

1. **Domain:** a new `data class DeviceState(override val id, val state: DeviceStateType) : RuleCondition`. Sealed interface gains a member; the `when` in `RuleMatcher` gains one arm (compiler flags it — no silent miss).
2. **Storage/wire:** a new `@SerialName("device_state")` `ConditionDto` subtype. Existing rows/exports are untouched; **no Room migration, no column change, no `RULE_EXPORT_SCHEMA_VERSION` break** (adding a polymorphic subtype is backward-compatible for encode; older builds that can't decode `device_state` fail *that one rule's* import, exactly as an unknown operator does today).
3. **Evaluation:** `DeviceState` is the first condition that needs data `RuleMatcher` can't derive from `(notification, now)`. It arrives as a second threaded context param (e.g. `deviceState: DeviceStateSnapshot`) sourced from a new `DeviceStateProvider` seam at the `core/notification` boundary — the **same parameter-threading mechanism** D6 establishes for the clock. Per the proposal's **fail-closed** contract, an unreadable state does **not** match.

The key property: the *shape* of the sealed hierarchy, the JSON discriminator scheme, the storage column, and the dispatch mechanism are all **open for extension, closed for modification**. Device-state is proven to be additive-only before it is built.

### D8: ADR 011 amendment (written into `docs/adr/011-rule-definition-storage.md`)

Append a dated amendment recording that **conditions specifically** move to a JSON polymorphic `payload` column (kotlinx polymorphic sealed, dual-used `ConditionDto`), why (rule-shape churn — the ADR's named revisit trigger — realized by typed condition families), and the scope boundary (fields/actions stay normalized; only conditions migrate). Full text added in the same change.

## Risks / Trade-offs

- **[~60 call sites reference the flat shape]** High-likelihood but mechanical: the sealed refactor is compiler-checked; every construction/read site fails to compile until updated. Previews/fixtures (`WhenSection`, `TestFixtures`, `RuleUiModelTest`) are the bulk.
- **[Wrapping time ranges]** Off-by-one prone. Wrap semantics are pinned **closed-inclusive** (`[start, end]`; start > end wraps midnight, matching time `>= start` OR `<= end`) and get explicit unit tests: same-day, exact-boundary (both `start` and `end` instants match), the degenerate `start == end` case matching only its exact instant (`t == start`, per spec.md's "Degenerate zero-duration range matches only its exact instant" scenario), and the 22:00–06:00 wrap on both sides of midnight.
- **[Dual-use `ConditionDto` couples storage to wire schema]** This is a **new coupling with no existing precedent** — `ExtractionMethodDto` is wire-only in practice (`RuleFieldMapper` serializes the domain `RuleField.ExtractionMethod` directly for storage; see D3). Accepting it here means a future `ConditionDto` schema change hits stored rows and exported rule files at once. Acceptable pre-launch given the alternative (a separate storage DTO + mapper) is unwarranted upfront complexity for two condition families; if they must diverge later, split into `ConditionStorageDto` (the ADR notes this exit).
- **[JSON column loses per-condition queryability]** No current query filters on condition internals (ADR 011); accepted.
- **[Wire v2 break]** Any v1 export in the wild would fail to import — but there are none (pre-launch). No decode-compat branch owed.

## Open Questions

None outstanding. Scope (two pure-Kotlin condition types), wrap semantics, fail-closed contract, storage location (per-row `payload`), and dispatch strategy (sealed `when`, no DI) are all settled above.
