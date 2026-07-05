# ADR 011 – Rule Definition Storage: Normalized Tables (JSON Column Under Review)

## Status
Proposed — current normalized schema stands; decision to be finalized before Roadmap Phase 2 (rule import/export)

## Context
A rule's definition is normalized across five Room tables (`rules`, `rule_conditions`, `rule_fields`, `rule_actions`, `rule_target_apps`). The product concept of "what a rule is" is still maturing (OR-groups, nested conditions, and AI-generated rules are all plausible — see `docs/roadmap.md`), and every shape change costs a multi-table migration plus mapper churn across five entity mappers.

Meanwhile, observed query patterns only ever filter on rule *metadata* (`is_active`, `is_global`, target packages) — never on condition or field internals. The domain `Rule` model is already `@Serializable`, and Phase 2 makes a versioned JSON representation the canonical sharing format anyway.

## Decision
Keep the normalized schema **for now** — it works and a preemptive migration buys nothing. Before starting Phase 2, evaluate consolidating to:

- A thin `rules` table: `id`, `name`, `is_active`, `is_global`, timestamps (everything queried by the rules list)
- `rule_target_apps` kept as a table (queried during rule matching per package)
- A `definition TEXT` column holding the serialized conditions/fields/actions, with a `schema_version` field inside the JSON for forward migration

Decision criteria at that point: if rule shape churned during Phases 0–1, or import/export makes dual maintenance of JSON + tables awkward, migrate; if the schema has stabilized and queries over rule internals emerged, keep the tables.

## Consequences

**Positive (of deferring):**
- No speculative migration; the Phase 2 import/export work produces exactly the evidence needed to decide
- Normalized tables remain available if per-condition/per-field queries appear

**Negative (of deferring):**
- Any rule-shape change landed before the decision pays the multi-table migration cost
- The rule editor's save path continues to disassemble/reassemble rules across five mappers

**If the JSON column is adopted later:**
- Rule-shape changes become code-only (no Room migration) as long as the JSON schema versioning is respected
- Import/export and Room persistence share one serialization format
- Querying inside definitions would require SQLite JSON1 functions or in-memory filtering
