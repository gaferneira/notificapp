# ADR 011 – Rule Definition Storage: Normalized Tables, JSON as Wire Format Only

## Status
Accepted (2026-07-06) — normalized schema stays as the storage source of truth; JSON is the Phase 2 import/export wire format only, not a storage migration. Amended (2026-07-06, TD-9): the wire format is now a dedicated DTO layer rather than the domain models directly — see Amendment below.

## Context
A rule's definition is normalized across five Room tables (`rules`, `rule_conditions`, `rule_fields`, `rule_actions`, `rule_target_apps`). The product concept of "what a rule is" is still maturing (OR-groups, nested conditions, and AI-generated rules are all plausible — see `docs/roadmap.md`), and every shape change costs a multi-table migration plus mapper churn across five entity mappers.

Meanwhile, observed query patterns only ever filter on rule *metadata* (`is_active`, `is_global`, target packages) — never on condition or field internals. The domain `Rule` model is already `@Serializable`, and Phase 2 needs a versioned JSON representation as the rule-sharing format regardless of how storage is decided.

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

## Amendment (2026-07-06, TD-9): wire format moved to a dedicated DTO layer

The original decision above (paragraph 2) had import/export serialize the domain models (`Rule`, `RuleCondition`, `RuleField`, `RuleAction`) directly — their `@Serializable` annotations *were* the wire format. We identified this as unsafe once real users and shared rule files exist: any domain rename would silently break every previously-exported rule.

**What changed:** `core/rulesharing/dto/` (`RuleExportDto`, `RuleDto`, `ConditionDto`, `FieldDto`, `ExtractionMethodDto`, `ActionDto`, `AppInfoDto`) is now the canonical, explicitly `@SerialName`-pinned wire format, with `RuleWireMapper` converting to/from the domain models. `Rule`, `RuleCondition`, and `AppInfo` are no longer `@Serializable` at all. `RuleField.ExtractionMethod` keeps `@Serializable` because Room's `RuleFieldMapper` still serializes it directly for column storage (per the original ADR's "storage stays exactly as-is" decision) — that dual use is now documented in code rather than incidental.

**What did not change:** storage is still the five normalized tables; JSON is still wire-format-only, produced from and consumed into those tables via the existing mappers. This amendment only closes the "one Kotlin class shape defines the domain model, the wire format, and DB column content simultaneously" gap from the original decision — it doesn't revisit the normalized-vs-JSON-storage question itself.

See `docs/rule-format.md` for the wire format specification and `app/src/test/resources/rule-export-v1.json` for the format locked as a golden-file test.
