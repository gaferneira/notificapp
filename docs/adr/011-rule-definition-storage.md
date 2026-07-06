# ADR 011 – Rule Definition Storage: Normalized Tables, JSON as Wire Format Only

## Status
Accepted (2026-07-06) — normalized schema stays as the storage source of truth; JSON is the Phase 2 import/export wire format only, not a storage migration

## Context
A rule's definition is normalized across five Room tables (`rules`, `rule_conditions`, `rule_fields`, `rule_actions`, `rule_target_apps`). The product concept of "what a rule is" is still maturing (OR-groups, nested conditions, and AI-generated rules are all plausible — see `docs/roadmap.md`), and every shape change costs a multi-table migration plus mapper churn across five entity mappers.

Meanwhile, observed query patterns only ever filter on rule *metadata* (`is_active`, `is_global`, target packages) — never on condition or field internals. The domain `Rule` model is already `@Serializable`, and Phase 2 needs a versioned JSON representation as the rule-sharing format regardless of how storage is decided.

This decision was deferred from Phase 0/1 specifically to be made "right after Phase 1, before Phase 2 starts" (see `docs/roadmap_tech_debt.md` TD-1). Phase 1 completed 2026-07-06; this is that decision.

## Decision
Keep the normalized schema. Do not migrate to a JSON-column storage model at this time.

Phase 2's rule import/export instead serializes the existing `@Serializable` domain models (`Rule`, `RuleCondition`, `RuleField`, `RuleAction`) directly to/from JSON as a **wire format**, with a `schema_version` field for forward compatibility. Storage stays exactly as-is: the JSON is produced from and consumed into the same five normalized tables via the existing mappers, not a new persisted column.

Rationale: no evidence has emerged during Phases 0–1 that rule shape is actually churning, and no query pattern needs JSON-internal filtering. Introducing storage-format migration risk at the same time as the first wire-format work would conflate two independent concerns. Revisit only if rule shape genuinely starts churning (e.g. OR-groups, AI-generated rules landing) or if maintaining both the wire format and five mappers becomes a real (not hypothetical) maintenance burden.

## Consequences

**Positive:**
- No migration risk taken on now; the normalized schema's query patterns (rules list, per-app matching) keep working as-is
- Import/export ships as a self-contained serialization layer on top of existing domain models — no coupling to a storage change
- Normalized tables remain available if per-condition/per-field queries appear later

**Negative:**
- The rule editor's save path continues to disassemble/reassemble rules across five mappers
- If rule shape does churn later, that migration is still owed — this ADR only defers it, it doesn't eliminate the underlying tradeoff

**If a JSON storage column is adopted later** (unchanged from the original proposal, kept for reference):
- Rule-shape changes would become code-only (no Room migration) as long as the JSON schema versioning is respected
- Import/export and Room persistence would share one serialization format
- Querying inside definitions would require SQLite JSON1 functions or in-memory filtering
