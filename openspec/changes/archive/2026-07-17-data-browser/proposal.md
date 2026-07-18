# Proposal: Data Screen — Browse, Filter, Statistics, Export & Delete (Data-Layer First)

## Intent

Every rule extracts structured data (amounts, dates, codes) into `extracted_field_values`, but users have **no way to see, search, quantify, export, or clean up** what their rules captured. The data accrues invisibly and unboundedly. This change delivers the roadmap "Data Screen" (new bottom-nav tab, Inbox → **Data** → Rules → Settings) starting with a solid **data layer**: repository + DAO queries for paginated browse, multi-filter/search/sort, statistics aggregation, on-demand export, and safe deletion. Success = a user can find any extracted value, understand extraction volume, export the filtered set, and delete what they no longer want — backed by tested queries a later phase can visualize.

## Business Problem / Outcome

- **Problem**: extracted data is write-only today — invisible, unsearchable, un-exportable, un-deletable. It contradicts the local-first/user-control privacy story (private captures pile up with no cleanup path).
- **Outcome**: users gain full read/aggregate/export/delete control over their extracted data; the data layer exposes trend series so a future phase can chart without re-plumbing.

## Scope

### In Scope (Phase 1 — data layer + minimal functional UI)
- `DataBrowserRepository` (ADR 005/006/008 shape): `Result<T>`, dispatcher-injected, composes RuleExecution/ExtractedFieldValue/Notification/Rule DAOs.
- Paging3 joined browse query (execution + value + rule name + app/package + field name) via a new flat projection DTO (`DataBrowserRow`).
- Combined filter query: rule + source app + date range + field type + text search; sort by date/rule/app/field. Mirrors `NotificationDao.getFilteredPaged` (`IN` + `hasFilter` flag idiom).
- Text search over `value_text`: new FTS4 virtual table mirroring `notifications_fts` + reuse `FtsQuerySanitizer`.
- Statistics aggregation queries: total count, this-week count, most-active rule, per-rule counts, per-app counts, day-bucketed trend series (7/30 days) — **data only, no chart**.
- Export: CSV + JSON serialization of the currently filtered set → Android share sheet (reuse `RulesScreen` FileProvider/`ACTION_SEND` pattern); new CSV serializer.
- Delete: single-entry; bulk-delete-with-filters via filtered-ID SELECT → transactional delete-by-id (with a UI confirmation step in the contract).
- Nav wiring: `Screen.Data`, 4th `AppDestinations.DATA` tab, MainActivity entry.
- Minimal functional Compose screen (list + filter/sort controls + summary header + export/delete actions), MVI.

### Out of Scope (this phase)
- Charts/graphs/trend visualization (trend data is computed but not rendered).
- Scheduled/background/automatic export.
- Cross-device / cloud sync of extracted data.
- Retention sweep, retention settings, storage-usage view, backup/restore (separate roadmap items).
- Editing extracted values; per-field analytics beyond the listed aggregates.

## Capabilities

### New Capabilities
- `data-browsing`: paginated, filtered, searchable, sortable browse of extracted rule data.
- `data-statistics`: aggregate counts + computed day-bucketed trend series (no rendering).
- `data-export`: on-demand CSV/JSON export of the filtered set via share sheet.
- `data-deletion`: single and filter-scoped bulk deletion with confirmation.

### Modified Capabilities
- None.

## Approach

Data-layer-first: build and test DAO queries + repository (aggregation, joined Paging, FTS search, filtered export/delete) before UI, replicating the proven `NotificationDao`/`NotificationRepositoryImpl`/`InboxViewModel` Paging3 template and the `RulesScreen` share-sheet pattern. UI stays a thin MVI layer over the repository. Schema changes (FTS4 table, indices) via a clean destructive version bump — pre-launch, no migration shim (CLAUDE.md).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `core/data/local/dao/ExtractedFieldValueDao`, `RuleExecutionDao` | Modified | New paged/joined/filter/aggregation/bulk-delete/export queries |
| `core/data/local/entity/` + `AppDatabase` | Modified | New FTS4 table, indices, version bump |
| `core/data/repository/DataBrowserRepositoryImpl` + `domain/repository/` | New | Repository interface + impl |
| `core/data/export/` (CSV/JSON serializers) | New | CSV serializer; JSON alongside codec approach |
| `domain/model/DataBrowserRow` (+ stats/filter models) | New | Flat projection + stats/filter/sort models |
| `features/databrowser/` | New | Contract + ViewModel + minimal UI |
| `core/ui/navigation/{Screen,MainBottomNav}`, `MainActivity` | Modified | 4th nav tab wiring |
| `docs/capabilities.md`, `docs/roadmap.md` | Modified | Keep functional map in sync |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Export/delete over very large datasets (memory, long delete) | Med | Bounded/chunked export fetch; transactional delete-by-id; confirmation step |
| Delete-while-paginating / stale filtered ID set | Med | Resolve ID set inside the delete transaction; Paging invalidation refreshes list |
| Trend bucketing over epoch-millis (`strftime` vs epoch-day div) | Med | Decide + unit-test in design; store canonical bucket format |
| Zero-result filter combinations / empty states | Low | Explicit empty-state contract; queries return empty, never error |
| Concurrent export while deleting | Low | Export snapshots filtered set; independent read path, no shared mutable state |
| Contract purity (rule 7) leaking projection internals | Low | Map DAO projection to feature model at ViewModel boundary |

## Rollback Plan

Feature is additive behind a new tab. Revert the change branch: remove `features/databrowser/`, the new repository/DAOs/serializers, and the nav tab; restore prior `AppDatabase.CURRENT_VERSION` (destructive bump acceptable pre-launch — no user data to preserve). No other feature depends on the new surface.

## Dependencies

- None external. Reuses existing Paging3, FileProvider, Hilt, Room, `FtsQuerySanitizer`.

## Product Tradeoffs

- **Data-layer-first / UI-minimal now**: the durable value and hardest correctness (joins, FTS, aggregation, safe bulk delete) live in queries; charts are cheap to add later. Shipping tested data primitives first de-risks the visualization phase and avoids overbuilding UI before the data shape is proven.
- **Defer visualization**: trend series is computed and exposed but not rendered — a future phase adds charts with zero data-layer rework.

## Success Criteria

- [ ] User can browse extracted data paginated, filter (rule/app/date/type), search text, and sort.
- [ ] Summary header shows total, this-week, most-active-rule; per-rule/per-app counts available; trend series computable for 7/30 days.
- [ ] User can export the filtered set as CSV or JSON via the share sheet.
- [ ] User can delete a single entry and bulk-delete the filtered set (with confirmation), transactionally and safely mid-pagination.
- [ ] New/changed DAO queries and repository logic are unit-tested; empty/zero-result and large-dataset paths handled.
- [ ] `docs/capabilities.md` updated; architecture/detekt checks pass.
