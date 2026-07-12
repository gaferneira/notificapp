# ADR 011 – Rule Definition Storage: Normalized Tables, JSON as Wire Format Only

## Status
Accepted (2026-07-06) — normalized schema stays as the storage source of truth; JSON is the Phase 2 import/export wire format only, not a storage migration. Amended (2026-07-06, TD-9): the wire format is now a dedicated DTO layer rather than the domain models directly — see Amendment below. Amended (2026-07-12, flexible-rule-conditions): **conditions specifically** now persist as a JSON polymorphic column, superseding this ADR's original normalized-storage decision *for the `rule_conditions` table only* — see second Amendment below.

## Pre-launch context

Notificapp has not shipped a public release — see CLAUDE.md's "Development Status" section, which is the canonical statement of this policy. Until the first APK ships, schema bumps, wire-format breaks, and destructive migrations are all free: there is no installed base or exported-rule corpus to protect. Amendments below cite this note rather than re-deriving it; revisit any migration/back-compat shortcuts taken under it once a real release exists.

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

## Amendment (2026-07-12, flexible-rule-conditions): conditions move to a JSON polymorphic column

The original decision deferred JSON-column storage "unless rule shape genuinely starts churning (e.g. OR-groups, AI-generated rules landing)." That revisit trigger has now fired: the `flexible-rule-conditions` change restructures `RuleCondition` from a single flat `(condition, operator, value)` shape into a **sealed interface** of typed condition families — `ContentMatchCondition`, `DayOfWeekCondition(days)`, `TimeRangeCondition(start, end)` — with a deferred `DeviceState` family already designed to slot in. The flat, three-typed-column `rule_conditions` schema cannot represent these heterogeneous shapes (`Set<DayOfWeek>`, `LocalTime` pairs) without either a column-per-family explosion or stringly-typed abuse, and each new family would otherwise owe a multi-column Room migration — exactly the churn cost this ADR anticipated.

**What changed:** the `rule_conditions` table drops its `condition` / `operator` / `value` columns and gains a single `payload` JSON `TEXT` column. Each row still holds one condition, keyed by its own `id`, under the `rule_id` foreign key (cascade + index preserved) — so per-condition addressability for the editor's remove/edit-by-id is unchanged and the `getConditionsForRule` DAO query is untouched. The `payload` is a kotlinx `@SerialName`-discriminated polymorphic serialization of the sealed condition hierarchy, reusing the **same `ConditionDto` layer as the wire format**.

This dual use (one class as both the Room storage payload and the wire DTO) is a **new coupling introduced by this amendment, not an existing pattern being mirrored.** `ExtractionMethodDto` does not actually play that dual role: `RuleFieldMapper.toEntity`/`toDomain` serializes the **domain** `RuleField.ExtractionMethod` class directly (its own `@Serializable` sealed hierarchy, via a Room-local `Json` instance) for column storage; `ExtractionMethodDto` is used only by `RuleWireMapper` on the wire path and is kept deliberately separate from storage. There is no existing case in this codebase of one class serving both roles simultaneously. Accepting that coupling for `ConditionDto` is justified on its own terms, not by precedent: unlike `RuleField`, where the Room and wire representations can currently evolve independently because they're two different classes, a future schema change to `ConditionDto` will now hit stored rows and exported rule files at the same time. See "Tradeoff accepted" below for the full risk statement and the documented exit. Adding a future condition family (including the deferred `DeviceState`) is therefore code-only: a new sealed subtype + `@SerialName` discriminator + one `RuleMatcher` `when` arm, with **no Room migration and no wire-format breaking change**.

**Scope of this amendment:** conditions **only**. `rule_fields`, `rule_actions`, `rule_target_apps`, and `rules` metadata stay normalized exactly as before — this amendment does not migrate them and does not revisit the normalized-vs-JSON question for the rest of the schema. It narrowly supersedes the "keep the normalized schema" decision for the `rule_conditions` table.

**Wire format:** `RULE_EXPORT_SCHEMA_VERSION` bumps `1 → 2` because the condition wire shape changes; no v1-decode back-compat branch is owed (see "Pre-launch context" above). The golden-file test is regenerated to the v2 shape.

**Migration gap:** no `Migration(1, 2)` is added for the `rule_conditions` schema change; `AppDatabase`'s destructive fallback handles it in DEBUG builds, per "Pre-launch context" above. Release builds still crash loudly on a missing migration rather than silently wipe (TD-10's guard, unrelated to pre-launch status) — a real `Migration(1, 2)` becomes owed only once a release build predating this change is in active use.

**Tradeoff accepted:** dual-using `ConditionDto` for both storage and wire couples the column format to the wire schema. This is a **new coupling with no existing precedent in this codebase** — `ExtractionMethodDto` is wire-only in practice; `RuleFieldMapper` serializes the domain `RuleField.ExtractionMethod` class directly for Room storage instead of reusing `ExtractionMethodDto` (see "What changed" above). Accepting this coupling means a future `ConditionDto` schema change affects both stored rows and exported rule files simultaneously, unlike `RuleField`'s storage/wire representations, which evolve independently today. It's accepted here because two condition families don't yet warrant a separate storage DTO and mapper. If the two must diverge later, split into a dedicated `ConditionStorageDto` — noted here as the documented exit. Per-condition-internal querying (never used today) remains unavailable inside the JSON column, consistent with the original ADR's observation that no query pattern needs it.
