# Tasks: Data Screen — Browse, Filter, Statistics, Export & Delete

Reference: `openspec/changes/data-browser/specs/*/spec.md`, `openspec/changes/data-browser/design.md`. Strict TDD is ON — `[TDD]` tasks are test-first (`./gradlew test`) before production code.

## Review Workload Forecast

| Field                   | Value                                                                  |
|-------------------------|------------------------------------------------------------------------|
| Estimated changed lines | ~900-1300 (9 new prod files, 4 modified, ~8-10 new test files, 2 docs) |
| 400-line budget risk    | High                                                                   |
| Chained PRs recommended | Yes                                                                    |
| Suggested split         | PR 1 (data layer) → PR 2 (feature UI + nav + docs)                     |
| Delivery strategy       | ask-on-risk                                                            |
| Chain strategy          | stacked-to-main (confirmed)                                            |

Decision needed before apply: No (confirmed 2026-07-16)
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                                                                                                                                            | Likely PR | Notes                                                                                                                                                   |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Domain models, FTS entity + schema v2, `DataBrowserDao` (+ tests), `DataBrowserRepositoryImpl` (+ tests), CSV/JSON streaming serializers (+ tests), DI wiring   | PR 1      | Base: `main`/tracker. Compiles and passes `./gradlew test` standalone — new public surface is unused until PR 2, safe to merge alone (stacked-to-main). |
| 2    | `features/databrowser/` (contract/viewmodel/ui, + tests), bottom-nav "Data" tab wiring, `docs/capabilities.md` + `docs/roadmap.md` updates, final full gate run | PR 2      | Base: PR 1 (merged to main) or PR 1 branch if not yet merged. Depends on PR 1's repository/domain surface.                                              |

Rationale for stacked-to-main over feature-branch-chain: PR 1 is fully dead-code-safe (new domain/data types, zero call sites until PR 2 wires the ViewModel), so it can merge to `main` independently once its own tests/gates pass — no shared tracker branch needed.

## Phase 1: Schema Foundation

- [x] 1.1 Create `domain/model/DataBrowserRow.kt`: `DataBrowserRow`, `DataBrowserFilter`, `DataStatistics`, `TrendPoint`, `CountBucket`, `DataSort` enum, `ExportFormat` enum.
- [x] 1.2 Create `domain/repository/DataBrowserRepository.kt` per design's interface (`browsePaged`, `searchPaged`, `statistics`, `exportRows(filter, sink, format)`, `previewDelete`, `deleteById`, `deleteByIds`).
- [x] 1.3 Create `core/data/local/entity/ExtractedFieldValueFtsEntity.kt` (`@Fts4(contentEntity = ExtractedFieldValueEntity::class)` over `value_text`).
- [x] 1.4 Modify `core/data/local/AppDatabase.kt`: register FTS entity, bump `CURRENT_VERSION` 1→2 (destructive, no `Migration` object per CLAUDE.md pre-launch policy).

## Phase 2: DAO Layer

No automated DAO-layer tests in this change — this project has no Robolectric dependency and no precedent for in-memory-Room/instrumented DAO tests (JVM-only JUnit5/Kotest/MockK/Turbine stack, per design.md Testing Strategy). Each raw `@Query` below is implemented directly and relies on manual verification (real-device/emulator smoke testing); correctness of the join/filter/aggregation logic is instead exercised indirectly by the Phase 3 repository tests against a faked `DataBrowserDao`.

- [x] 2.1 Implement `DataBrowserDao.getFilteredPaged` (`IN (:x) OR :hasFilter=0` idiom, join `extracted_field_values ⋈ rule_fields ⋈ rule_executions ⋈ rules ⋈ notifications` with `rule_executions.was_dry_run = 0`, selecting `notifications.title`/`notifications.content` into `DataBrowserRow.notificationTitle`/`notificationContent`). Manually verify: no filter, single-filter each dimension, combined filters, zero-result, dry-run executions excluded.
- [x] 2.2 Implement `DataBrowserDao.searchFtsPaged` with `rule_executions.was_dry_run = 0`. Manually verify: partial match, reserved-char input via `FtsQuerySanitizer`, no results, dry-run executions excluded.
- [x] 2.3 Implement aggregation `@Query`s (GROUP BY rule_id / package_name, total/week counts) over the same `rule_fields`-joined query with `rule_executions.was_dry_run = 0`. Manually verify: per-rule, per-app, total, this-week, tie-break by rule ID, zero-data, field-type filter/sort sourced from `rule_fields.field_type`/`rule_fields.name`, dry-run executions excluded.
- [x] 2.4 Implement the locked `strftime('%Y-%m-%d', created_at/1000, 'unixepoch', 'localtime')` trend query. Manually verify: midnight-boundary case, 7-day gap-fill, 30-day DST-spanning case, dry-run executions excluded.
- [x] 2.5 Implement `getMatchingIds` (drives both bulk-delete preview and export snapshot) + `deleteByIds` (`DELETE WHERE id IN (:ids)`). Manually verify: matches preview count, no-op on already-deleted, empty filter deletes nothing extra, dry-run rows excluded, **insert between preview and confirm is NOT deleted** — i.e. calling `getMatchingIds` again after a new matching row was inserted must not affect a `deleteByIds` call made with the earlier-resolved ID list.
- [x] 2.6 Implement `getMatchingBatch(batchOfIds: List<String>)` (`WHERE id IN (:batchOfIds)`) over fixed-size (`EXPORT_BATCH_SIZE = 1000`) slices of a pre-resolved ID snapshot, for streaming export. Manually verify: no gaps/dupes across batch boundaries, stable ordering, unaffected by concurrent inserts/deletes after the snapshot is taken.

## Phase 3: Data Layer

- [x] 3.1 `[TDD]` Write failing repository tests (Result mapping, empty/zero-result, `withTransaction` bulk-delete count via `previewDelete`+`deleteByIds`, dispatcher injection) → implement `core/data/repository/DataBrowserRepositoryImpl.kt` (`internal`, `@Dispatcher(IO)`, composes `DataBrowserDao`+`ExtractedFieldValueDao`, no raw exception leaks — via the shared `dbCatching()` helper, TD-16). **Deviation from design's literal DAO list:** `RuleExecutionDao`/`RuleDao`/`NotificationDao` are not injected, since `DataBrowserDao`'s own joins/aggregations already cover every read this repository needs and `deleteById` delegates to the pre-existing `ExtractedFieldValueDao.delete(id)` — avoids dead constructor params.
- [x] 3.2 `[TDD]` Write failing tests for `DataCsvSerializer` (header once, escaping comma/quote/newline, correctness across multiple streamed batches, empty set) → implement streaming CSV writer over `OutputStream`.
- [x] 3.3 `[TDD]` Write failing tests for `DataJsonSerializer` (well-formed array across multiple batches — no missing/extra brackets/commas, empty array) → implement streaming JSON-array writer (`[` → batches → `]`).
- [x] 3.4 `[TDD]` Write failing test for `exportRows` (mid-export batch failure surfaces as `Result.failure`; id snapshot resolved exactly once regardless of concurrent mutation; zero-result filter produces a valid empty CSV/JSON) → implement in `DataBrowserRepositoryImpl.exportRows` using a one-time `getMatchingIds` snapshot + batched `getMatchingBatch` reads. **Note:** `exportRows` receives an already-open `OutputStream sink`, not a file path, so temp-file-then-rename atomicity at the final share-sheet destination is the Phase 5 UI caller's responsibility — this repository's contract is bounded to snapshot-once + never-materialize-full-set + surface failures so the caller knows not to rename a partial temp file into place.

## Phase 4: DI Wiring

- [x] 4.1 Modify `core/di/DatabaseModule.kt`: provide `dataBrowserDao()`.
- [x] 4.2 Modify `core/di/RepositoryModule.kt`: `@Binds DataBrowserRepository`.

## Phase 5: Feature Layer (TDD)

- [x] 5.1 Create `features/databrowser/contract/DataBrowserContract.kt` (`UiState`, `UiEvent`, `UiEffect` — no `core.extraction` imports, arch rule 7).
- [x] 5.2 `[TDD]` Write failing `DataBrowserViewModelTest` (filter→Pager recreation via `flatMapLatest`, stats load, delete-confirmation flow calling `previewDelete` then `deleteByIds` with the previewed ID list — never re-filtering on confirm, export effect via Turbine + fake repo) → implement `features/databrowser/viewmodel/DataBrowserViewModel.kt`.
- [x] 5.3 Create `features/databrowser/ui/DataBrowserScreen.kt`: paged list, filter/sort controls, plain-text stats header, export action, bulk-delete confirmation dialog showing affected count; `Scaffold.bottomBar` sets `MainBottomNav(selectedDestination = AppDestinations.DATA, ...)` (mirroring InboxScreen/RulesScreen/SettingsScreen); effects via `CollectOneOffEffects`; no chart components.

## Phase 6: Navigation

- [x] 6.1 Modify `core/ui/navigation/Screen.kt`: add `data object Data : Screen()`.
- [x] 6.2 Modify `core/ui/navigation/MainBottomNav.kt`: insert `DATA` between `INBOX` and `RULES` + `when` branch.
- [x] 6.3 Modify `MainActivity.kt`: register `entry<Screen.Data> { }` only — no `selectedDestination` mapping here; that wiring lives in `DataBrowserScreen.kt` (task 5.3), per the existing InboxScreen/RulesScreen/SettingsScreen pattern.

## Phase 7: Documentation

- [x] 7.1 Update `docs/capabilities.md`: document data-browser browse/filter/search/stats/export/delete capabilities.
- [x] 7.2 Update `docs/roadmap.md`: check off Data Screen checkboxes covered by this change. The "Trend: extractions over time (simple bar/line chart …)" bullet bundles trend-data computation with chart rendering — this change delivers computation only, not rendering. Do NOT check that bullet off as-is; either leave it unchecked, or split it into two bullets ("trend data computed" — checked by this change — and "trend chart rendered" — left unchecked, deferred to the visualization phase) so roadmap status isn't overstated.

## Phase 8: Verification Gates (final, blocking)

- [x] 8.1 Run `./gradlew spotlessApply`, `./gradlew detekt`, `./gradlew architectureCheck`, `./gradlew test` — zero new Detekt/architectureCheck baseline entries, full suite green including all new repo/serializer/ViewModel tests (DAO queries are covered by manual verification only, see Phase 2).
- [x] 8.2 Review `git diff --stat` per PR against the Review Workload Forecast before opening each PR.

## Dependency Chain Summary

```
1.1/1.2 (domain) ─┬─→ 1.3/1.4 (schema) ─→ 2.1..2.6 (DAO, manual verification) ─→ 3.1..3.4 (repo+serializers, TDD) ─→ 4.1/4.2 (DI)
                  └──────────────────────────────────────────────────────────────────────────────────→ 5.1 (contract)
4.1/4.2 ─→ 5.1 ─→ 5.2 (ViewModel, TDD) ─→ 5.3 (UI) ─→ 6.1..6.3 (nav) ─→ 7.1/7.2 (docs) ─→ 8.1/8.2 (gates)
```
Phases 1-4 = PR 1. Phases 5-8 = PR 2.
