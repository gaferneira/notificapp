# Proposal: Rule Condition Combinator (ALL / ANY)

## Intent

Rules today evaluate their flat `conditions` list as AND-only (`RuleMatcher.matches` uses `.all`). "OR condition groups" is an explicit Idea Backlog item in `docs/roadmap.md:280`. The root-level combinator is the one schema bit the sealed-`RuleCondition` + JSON `payload` design does *not* absorb migration-free — condition *families* live in the payload, but the combinator lives on the `rules` table.

This change installs that schema seam now, while pre-launch (no installed base, per CLAUDE.md "Development Status"). Adding it now is a cheap additive/nullable column; adding it post-launch would cost a `rules`-table migration plus a wire-format version bump with a back-compat decode branch. This is domain + storage + wire + matcher only — no user-facing feature.

## Scope

### In Scope
- `domain/model/`: new `ConditionCombinator { ALL, ANY }` enum; add `Rule.conditionLogic: ConditionCombinator = ConditionCombinator.ALL`.
- `core/extraction/RuleMatcher.kt`: `matches()` gains a combinator param (default `ALL`) selecting `all {}` vs `any {}`; empty list still returns `true` (global rule) regardless of combinator.
- `core/extraction/RuleEngine.kt` (caller at line 42): pass `rule.conditionLogic`.
- `core/data/local/entity/RuleEntity.kt`: add `condition_logic` column with default; `core/data/local/mapper/RuleMapper.kt`: map both ways.
- Wire: add field to `RuleDto` in `core/rulesharing/dto/RuleExportDto.kt` (default `ALL` on decode); bump `RULE_EXPORT_SCHEMA_VERSION` `1 → 2`; map in `RuleWireMapper`; regenerate golden `app/src/test/resources/rule-export-v1.json`.
- Docs: ADR 011 has been removed (its pre-launch policies are now in `CLAUDE.md`). Update `docs/rule-format.md` and `docs/capabilities.md` to document the combinator field.
- Tests [strict TDD, `./gradlew test`]: RuleMatcher ANY-vs-ALL, RuleMapper round-trip, codec golden.

### Out of Scope (Non-Goals)
- NO editor/UI change — rule editor stays ALL-only; combinator is not user-exposed here.
- NO nested `ConditionGroup` / boolean tree — documented as future extension point only.
- NO hand-written Room `Migration` — pre-launch destructive fallback per CLAUDE.md; release build's missing-migration guard unchanged.

## Approach

Additive field defaulting to `ALL`, threaded from domain → entity/mapper → matcher and DTO. `ALL` preserves today's AND semantics exactly; `ANY` selects OR. Decode defaults absent fields to `ALL` for pre-launch forward-compat hygiene.

## References
- CLAUDE.md "Development Status" — pre-launch destructive-schema policy.
- `docs/roadmap.md:280` — "OR condition groups" backlog item.

## Rollback Plan

Field defaults to `ALL` and empty-conditions still matches, so all existing rules behave identically. Revert is a clean removal of an additive enum, column, field, matcher param, and the schema-version bump.

## Success Criteria
- [ ] `ANY` rule matches when at least one condition matches; `ALL` unchanged.
- [ ] Empty conditions still match under both combinators.
- [ ] Entity/mapper round-trip and codec golden pass at schema v2.
- [ ] No UI/editor behavior change.
