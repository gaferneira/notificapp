# Technical Debt Roadmap

This document details every technical debt item identified in the July 2026 architecture review, with the concrete solution for each. 

## Summary

| ID    | Item | Priority | Effort | Status |
|-------|------|----------|--------|--------|
| TD-1  | Rule storage schema: normalized tables vs JSON column | Decision | — | Reevaluate before Roadmap Phase 2 |

---

## TD-1: Rule storage schema — normalized tables vs JSON column (decision, not a refactor)

### Context

The rule definition is normalized across 5 tables (`RuleEntity`, `RuleConditionEntity`, `RuleFieldEntity`, `RuleActionEntity`, `RuleTargetAppEntity`). While the *concept* of a rule is still maturing (OR-groups? nested conditions? AI-suggested rules?), every shape change costs a multi-table Room migration plus mapper churn. Meanwhile the app only ever *queries* rule metadata (`is_active`, `is_global`, target packages) — never condition internals. And `Rule` is already `@Serializable`.

### Options

| | Normalized (current) | JSON definition column |
|---|---|---|
| Query condition/field internals | Possible | Not without JSON1 functions |
| Rule shape change | Multi-table migration | Usually zero migration |
| Import/export (Phase 2) | Needs assembly/disassembly | Trivially the same format |
| Mapper surface | 5 entity mappers | 1 serializer |

### Recommendation

**Reevaluate before starting Roadmap Phase 2** (rule import/export makes the JSON representation canonical anyway). If migrating: keep a thin `rules` table (`id`, `name`, `is_active`, `is_global`, timestamps) + `rule_target_apps` (queried for matching) + a `definition TEXT` column holding the serialized conditions/fields/actions. Include a `schema_version` field inside the JSON for forward migration of rule definitions. Do **not** do this preemptively — only if Phase 2 work confirms rule shape is still churning.
