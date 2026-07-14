# Design: Rule Condition Combinator (ALL / ANY)

## Technical Approach

Install the root-level combinator schema seam (the one bit the sealed-`RuleCondition` + JSON
`payload` design does NOT absorb migration-free because it lives on the `rules` table, not
inside a condition payload). A single additive enum threads through five layers domain →
extraction → data → wire → docs, defaulting to `ALL` so every existing rule and every absent
field decodes to today's AND semantics. No UI, no nested boolean tree. Settled by proposal
`sdd/rule-condition-combinator/proposal` (id 88); this documents HOW, layer by layer.

## Architecture Decisions

| Decision | Choice | Alternative rejected | Rationale |
|----------|--------|----------------------|-----------|
| Combinator injection into matcher | `matches()` param, default `ALL` | Read `rule.conditionLogic` inside `RuleMatcher` | Keeps the pure matcher decoupled from the `Rule` aggregate; `RuleMatcher` never imports `Rule` today (only `RuleCondition`) — preserves purity + testability. `RuleEngine` owns the `rule → param` wiring. |
| Storage encoding | Enum-as-string TEXT column, `defaultValue = "ALL"` | Ordinal int | Forward-safe: reordering/inserting enum members never corrupts stored rows; matches the codebase's string-wire convention (`toWireString`). |
| Migration | Destructive fallback (debug), no `Migration(1,2)` | Hand-written Room migration | Pre-launch, no installed base (CLAUDE.md "Development Status"). Release missing-migration guard (TD-10) stays untouched. |
| Nested `ConditionGroup` | NOT built; documented as sanctioned future extension point | Build boolean tree now | YAGNI + product's simplicity positioning. The `rule_conditions` JSON `payload` column already makes a future `ConditionGroup` sealed subtype a code-only additive change — no migration, no wire break beyond a version bump. |

## Data Flow

    Rule.conditionLogic ──toEntity──> RuleEntity.condition_logic (TEXT "ALL"/"ANY")
         │                                      │
         │                              toDomain (unknown → ALL)
         ▼                                      ▼
    RuleEngine.evaluateRule ──> RuleMatcher.matches(notif, conditions, now, combinator)
                                       │
                       conditions.isEmpty() → true  (BEFORE selecting all/any)
                       ALL → conditions.all { }   ANY → conditions.any { }

    Wire:  Rule ⇄ RuleDto.conditionLogic (SerialName, decode default ALL) ⇄ schemaVersion 2

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `domain/model/ConditionCombinator.kt` | Create | `enum class ConditionCombinator { ALL, ANY }`, own file. |
| `domain/model/Rule.kt` | Modify | Add `conditionLogic: ConditionCombinator = ConditionCombinator.ALL`. |
| `core/extraction/RuleMatcher.kt` | Modify | `matches()` gains `combinator: ConditionCombinator = ConditionCombinator.ALL`; keep `if (conditions.isEmpty()) return true` first, then `when (combinator) { ALL -> all; ANY -> any }`. Stay pure Kotlin. |
| `core/extraction/RuleEngine.kt` | Modify | Line 42 call passes `rule.conditionLogic`. |
| `core/data/local/entity/RuleEntity.kt` | Modify | Add `@ColumnInfo(name = "condition_logic", defaultValue = "ALL") val conditionLogic: String = "ALL"`. |
| `core/data/local/mapper/RuleMapper.kt` | Modify | `toEntity`: `domain.conditionLogic.name`. `toDomain`: `runCatching { ConditionCombinator.valueOf(entity.conditionLogic) }.getOrDefault(ALL)`. |
| `core/rulesharing/dto/RuleExportDto.kt` | Modify | Add `@SerialName("conditionLogic") val conditionLogic: String = "ALL"` to `RuleDto`; bump `RULE_EXPORT_SCHEMA_VERSION` 1 → 2. |
| `core/rulesharing/RuleWireMapper.kt` | Modify | `toDto`: `conditionLogic = conditionLogic.name`. `toDomain`: `runCatching { valueOf(rule.conditionLogic) }.getOrDefault(ALL)` (unknown → ALL, never throws — unlike condition/method strict mapping). |
| `app/src/test/resources/rule-export-v1.json` | Modify | Regenerate to v2 shape (`schemaVersion: 2`, `conditionLogic: "ALL"`). Filename stays `rule-export-v1.json`. |
| `docs/rule-format.md`, `docs/capabilities.md` | Modify | Document `conditionLogic` field / ALL-ANY capability. ADR 011 has been removed — its pre-launch policies are now in `CLAUDE.md`. |

## Interfaces / Contracts

```kotlin
enum class ConditionCombinator { ALL, ANY }

fun matches(
    notification: Notification,
    conditions: List<RuleCondition>,
    now: LocalDateTime,
    combinator: ConditionCombinator = ConditionCombinator.ALL,
): Boolean {
    if (conditions.isEmpty()) return true // global rule, combinator-independent
    val predicate = { c: RuleCondition -> matchesCondition(notification, c, now) }
    return when (combinator) {
        ConditionCombinator.ALL -> conditions.all(predicate)
        ConditionCombinator.ANY -> conditions.any(predicate)
    }
}
```

## Testing Strategy (strict TDD — tests first)

| Layer | What to Test | Approach |
|-------|--------------|----------|
| Extraction | ALL vs ANY selection; empty-list → true under both; single-condition parity | `RuleMatcherTest` (pure JVM, JUnit5/Kotest) |
| Data | `RuleMapper` round-trip; `condition_logic` ↔ enum; legacy/unknown/blank string → ALL | Mapper unit test |
| Wire | `RuleWireMapper` round-trip; decode **without** `conditionLogic` field → ALL | Codec test |
| Wire (golden) | v2 golden regen locks `schemaVersion: 2` + `conditionLogic` | Golden-file test |

**Coverage gaps (accepted):** no editor/UI tests (combinator not user-exposed here); no
`RuleEngine`-level integration test added — matcher unit coverage + existing engine tests suffice.

## Migration / Rollout

No Room `Migration` — destructive fallback in DEBUG per CLAUDE.md pre-launch policy. Release
missing-migration guard unchanged. Wire back-compat handled by decode default, not a version branch.

## Open Questions

None — design is settled.
