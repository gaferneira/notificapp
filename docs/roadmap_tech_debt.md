# Technical Debt Roadmap

This document details every technical debt item identified in the July 2026 architecture review, with the concrete solution for each.

## Summary

| ID    | Item | Priority | Effort | Status |
|-------|------|----------|--------|--------|
| TD-1  | Rule storage schema: normalized tables vs JSON column | Decision | — | Open (ADR 011 `Proposed`) — decide right after Phase 1, before Phase 2 starts |
| TD-2  | Room destructive migration silently wipes data on schema bump | P0 | Small | Open — real migrations required from next schema change; remove fallback before first release |
| TD-3  | `NotificationDeduplicator.recentHashes` unsynchronized map | P1 | Small | Open |
| TD-4  | `FieldExtractor` JSON parser has no recursion depth guard | P1 | Small | Open — fix before Phase 2 (community rules) |
| TD-5  | Zero ViewModel test coverage | P2 | Medium | Open — backfill before Phase 3 adds more ViewModels |
| TD-6  | Screen composables growing past 700 lines | P3 | Small (policy) | Open — soft budget for new screens only |
| TD-7  | No static analysis beyond formatting (Detekt absent) | P3 | Small | Open |
| TD-8  | OpenSpec coverage: only 2 of 9 shipped features have specs | Decision | — | Open — decide before Phase 2 opens the repo to contributors |

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

Tracked formally in `docs/adr/011-rule-definition-storage.md` (status: `Proposed`). Timing note: Phase 2's first task is "define the versioned JSON rule format" — that decision and this one are the same decision. Make the call explicitly right after Phase 1 wraps, as a dedicated short design pass, rather than letting it get decided implicitly by whatever Phase 2 code gets written first.

---

## TD-2: Room relies on destructive migration — every schema bump silently wipes user data (P0)

### Context

`AppDatabase.kt` declares `version = 2` with `exportSchema = true`, and both `app/schemas/.../1.json` and `2.json` exist — so the schema history is captured. But `DatabaseModule.kt` builds the database with `.fallbackToDestructiveMigration()` (commented "App not yet published - data loss acceptable") and registers no `Migration` objects. That is a deliberate and reasonable pre-release choice, but its failure mode is the worst kind for this product: any schema version bump **silently deletes every captured notification, rule, and extraction** on upgrade. No crash, no warning — the user just opens the app to an empty database. For an app whose pitch is "your notifications become a dataset you own," a silent wipe on update is a trust-destroying event.

The risk compounds because every future roadmap phase adds entities (Phase 3 data lifecycle settings, Phase 4 `Webhook` + failed-delivery-queue tables), so the schema version will keep climbing — and F-Droid users update in place.

### Solution

1. Keep `fallbackToDestructiveMigration()` only while genuinely pre-release; it must be removed in the same PR that ships the first public (F-Droid) build. Make that a release-checklist item now so it can't be forgotten.
2. From the next schema bump onward, write explicit migrations: diff the exported schema JSONs (e.g., `2.json` vs `3.json`), write `object MIGRATION_2_3 : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { ... } }`, and register via `.addMigrations(...)` in `DatabaseModule`.
3. Add migration tests using `androidx.room:room-testing`'s `MigrationTestHelper`, asserting each migration runs cleanly against the exported previous-version schema.
4. Going forward, treat "ships a Migration + a migration test" as a merge gate for any PR that changes a `@Entity`, same weight as Spotless/unit tests today.
5. The v1 → v2 gap needs no retroactive migration if no external users ever ran v1 — destructive fallback already handled dev installs — but confirm that assumption explicitly before closing this item.

---

## TD-3: `NotificationDeduplicator.recentHashes` unsynchronized concurrent map (P1)

### Context

`recentHashes` is a plain `mutableMapOf<String, Long>()` read and mutated inside the suspend function `isDuplicate`, which runs on the IO dispatcher and can be invoked concurrently if notifications arrive in a burst. A plain `HashMap` mutated concurrently can throw `ConcurrentModificationException` or, in worse cases, corrupt its internal bucket structure. This is exactly the kind of bug that stays invisible in manual testing (single notification at a time) and shows up under real load — precisely the hero use case #2 scenario (a bank/payment app firing several transaction notifications close together).

### Solution

Wrap the check-and-insert with a `kotlinx.coroutines.sync.Mutex` (a `ConcurrentHashMap` alone isn't enough — the operation is check-then-act, which needs atomicity across both steps, not just per-call thread safety). Add a test that drives `isDuplicate` from multiple concurrent coroutines to lock in the fix.

---

## TD-4: `FieldExtractor`'s hand-rolled JSON parser has no recursion depth guard (P1)

### Context

`parseJsonObject`/`parseJsonArray`/`parseJsonValue` recurse into nested structures with no depth limit. A deeply nested or malformed JSON-like notification body triggers unbounded recursion and a `StackOverflowError` — which is an `Error`, not an `Exception`, so it escapes the `catch (Exception)` wrapping extraction and can crash notification processing outright.

This becomes a real (not theoretical) attack surface once Phase 2 ships rule import/export and the community rules gallery: untrusted rule definitions matched against arbitrary notification bodies from apps you don't control is exactly the input class that trips this.

### Solution

Thread a `maxDepth` parameter (e.g., 32) through the three parse functions; throw a caught, expected exception when exceeded so the field simply fails to extract instead of crashing the process. Add a unit test with a deliberately deep/malicious payload. Fix before Phase 2 ships, not after.

---

## TD-5: Zero ViewModel test coverage (P2)

### Context

`app/src/test` covers the extraction engine, `ProcessNotificationUseCase`, and the action executors (9 test files) — but no ViewModel, repository, or mapper has any test. Meanwhile several ViewModels already carry non-trivial state-machine logic: `RuleEditorViewModel` (501 lines), `AddFieldViewModel` (360 lines), `AppSelectionViewModel` (323 lines). Every remaining roadmap phase adds more ViewModels (Data Browser in Phase 3, Webhook management in Phase 4, AI extraction toggle in Phase 5) on top of an already-untested presentation layer.

### Solution

Backfill tests for the two highest-risk ViewModels first — `RuleEditorViewModel` (most complex, multi-step form state) and `AddFieldViewModel` — reusing the JUnit 5 / Kotest / MockK / Turbine stack already proven on the extraction engine. From Phase 3 onward, make "new ViewModel ships with tests" a hard rule, not a best-effort one.

---

## TD-6: Screen composables growing past 700 lines (P3, policy not refactor)

### Context

9 of the 15 largest files in the codebase are Screen or BottomSheet composables: `AddFieldBottomSheet` (857 lines), `RulesScreen` (753), `InboxScreen` (729), `OnboardingScreen` (722), `AppSelectionScreen` (699), `NotificationDetailScreen` (652), `RuleEditorScreen` (570), `ActionBottomSheet` (546), `SettingsScreen` (540). None of these are broken, but Phase 3 (Data Browser) and Phase 4 (Webhook management form) both add another large screen on the same pattern, and large composables are harder to preview in isolation and recompose less predictably.

### Solution

No refactor of existing screens is warranted purely for size — don't destabilize working code for cosmetics. Instead, adopt a soft ~400-line budget for **new** screens starting with Phase 3's Data Browser: extract stateless sub-composables per logical section (header, list item, filter row) as the screen is built, not after. Revisit existing large files opportunistically when next touched for a feature change.

---

## TD-7: No static analysis beyond formatting (P3)

### Context

Spotless (ktlint) enforces formatting but nothing enforces complexity or size budgets (long methods, high cyclomatic complexity, unused code) — exactly the class of debt surfacing in TD-5 and TD-6. No Detekt config exists anywhere in the repo.

### Solution

Add Detekt with a generated baseline (`detektBaseline`) to grandfather existing debt without a blocking backlog, plus a modest custom ruleset (`LongMethod`, `LongParameterList`, `ComplexMethod` thresholds) wired into the same pre-commit/CI gate as Spotless. Cheap to add now; pays for itself by catching growth before it needs its own memory note.

---

## TD-8: OpenSpec coverage — only 2 of 9 shipped features have specs (decision)

### Context

`openspec/specs/` currently has `action-execution/spec.md` and `snooze-scheduling/spec.md` — nothing for Inbox, Rules, Rule Editor, Settings, App Selection, Onboarding, or Notification Detail. `openspec/changes/` has no stale in-progress changes (clean), so this isn't drift from abandoned work — it's simply that SDD was adopted mid-project and specs were never backfilled for what came before.

Phase 2's Community Rules Gallery explicitly invites external contributors, and `CONTRIBUTING.md` presumably points at `openspec/specs/` as the contract contributors build against. A contributor touching the rule editor or extraction pipeline today has no spec to conform to for the two most complex, most contribution-likely areas of the app.

### Recommendation

Decide explicitly, before Phase 2 opens the repo to contributions: either (a) backfill specs for the rule editor and extraction pipeline specifically (the areas most likely to receive external PRs), or (b) accept specs as forward-only from here and rely on `ARCHITECTURE.md` + code as the contract for pre-existing features. Don't let this stay an implicit gap — pick one and note the decision here.
