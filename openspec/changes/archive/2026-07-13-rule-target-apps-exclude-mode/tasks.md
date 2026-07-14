# Tasks: Rule Target-Apps Include/Exclude Mode

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~550-700 (14 files + 2 test-heavy layers + docs) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (domain+data) -> PR 2 (wire format+docs) -> PR 3 (UI) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Domain model + DB/DAO/mapper/repository, incl. `appliesToPackage` and live/backtest parity test | PR 1 | Foundation; base = main (or tracker branch if feature-branch-chain). Everything else depends on this. |
| 2 | Wire format (DTO/mapper/golden fixture) + docs | PR 2 | Depends only on PR 1's domain model. Independent of PR 3. |
| 3 | Editor UI, Rules-list filter/summary, `FilterBottomSheetViewModel` dep | PR 3 | Depends only on PR 1's domain model; can run parallel to PR 2. |

If `feature-branch-chain` is chosen: PR 1 base = tracker branch; PR 2 and PR 3 both base = PR 1's branch (siblings, not stacked on each other).

## Phase 1: Domain & Persistence Foundation

- [x] 1.1 Add `isIncludeMode: Boolean = true` to `domain/model/Rule.kt`; add `fun Rule.appliesToPackage(pkg: String): Boolean` per design's 3-branch predicate.
- [x] 1.2 Add `is_include_mode` column (default `1`) to `core/data/local/entity/RuleEntity.kt`.
- [x] 1.3 Bump `core/data/local/AppDatabase.kt` version; keep destructive fallback (no `Migration`, pre-launch policy).
- [x] 1.4 Update `core/data/local/mapper/RuleMapper.kt`: `is_global = targetApps.isNullOrEmpty()`, carry `isIncludeMode` both directions.
- [x] 1.5 Update `core/data/local/dao/RuleDao.kt`: rewrite `getActiveRulesForApp`/`getRulesForApp` with `EXISTS`/`NOT EXISTS` branches per design SQL shape.
- [x] 1.6 Add `getRecentByPackageNamesExcluding` to `core/data/local/dao/NotificationDao.kt` (`NOT IN` + `LIMIT`), with a comment cross-linking it to RuleDao's `NOT EXISTS` branch.
- [x] 1.7 Update `domain/repository/NotificationRepository.kt` + `core/data/repository/NotificationRepositoryImpl.kt`: `getNotificationsForBacktest(targetPackages, isIncludeMode, limit)` mode branch.

## Phase 2: Data Layer Tests (Foundation Verification)

- [x] 2.1 Unit test `appliesToPackage` — table test covering include/exclude/null/empty-list branches (Given-When-Then).
- [x] 2.2 DAO test (in-memory Room) for `RuleDao` `EXISTS`/`NOT EXISTS` variants — verify no N+1, matches spec scenarios "Rule fires for a non-listed app" / "does not fire for a listed app" / "empty list collapses to all apps".
- [x] 2.3 Shared parity test asserting `NotificationDao.getRecentByPackageNamesExcluding` and `Rule.appliesToPackage` agree for the same rule/app combos — drift guard for spec scenario "Backtest agrees with live match for exclude mode".

## Phase 3: Wire Format & Docs (depends on Phase 1 only)

- [x] 3.1 Add `isIncludeMode` field to `core/rulesharing/dto/RuleExportDto.kt` after `targetApps`; keep `RULE_EXPORT_SCHEMA_VERSION = 2`.
- [x] 3.2 Map `isIncludeMode` in `core/rulesharing/RuleWireMapper.kt` `toDto`/`toDomain`.
- [x] 3.3 Update `app/src/test/resources/rule-export-v1.json` golden fixture: add `"isIncludeMode": true`, keep `schemaVersion: 2`.
- [x] 3.4 Add exclude-mode round-trip case to `RuleJsonCodecTest` (export exclude rule, re-import, assert `isIncludeMode = false` and `targetApps` preserved).
- [x] 3.5 Update `docs/rule-format.md`: document `isIncludeMode`, fix stale schemaVersion note.
- [x] 3.6 Update `docs/capabilities.md`: add app-scope include/exclude capability entry.

## Phase 4: Editor UI (depends on Phase 1 only)

- [x] 4.1 Add `isIncludeMode` to `features/ruleeditor/domain/RuleUiModel.kt`; wire `fromDomain`/`toEntity`.
- [x] 4.2 Add `OnAppScopeModeChanged` event + handling to `features/ruleeditor/viewmodel/RuleEditorViewModel.kt`; pass mode into backtest call.
- [x] 4.3 Add segmented control ("Only these apps" / "All apps except these") to `features/ruleeditor/ui/components/WhenSection.kt` app-selection step, shown only when >=1 app selected; disable toggle when zero apps selected.
- [x] 4.4 Update AppsCard summary text to render "All apps except N" in exclude mode.
- [x] 4.5 Test: `RuleEditorViewModelTest` covers `OnAppScopeModeChanged` state transition and backtest invocation with the correct mode.

## Phase 5: Rules List Filtering & Summary (depends on Phase 1 only)

- [x] 5.1 Update `features/rules/viewmodel/RulesViewModel.kt` app filter to use `selectedApps.any { rule.appliesToPackage(it) }`.
- [x] 5.2 Inject monitored-apps source (`SelectedAppRepository` / `observeAppsWithNotifications`) into `features/rules/viewmodel/FilterBottomSheetViewModel.kt`; compute `availableApps` as union(rule-referenced apps, monitored apps) so exclude-only apps remain filterable.
- [x] 5.3 Update `features/rules/ui/RulesScreen.kt` — both rule-summary display sites — to render "All apps except N" / "Except {name}" for exclude-mode rules.
- [x] 5.4 Test: `RulesViewModelTest` — filtering by an unlisted app surfaces an exclude-mode rule and excludes it when filtering by a listed app (spec scenarios).
- [x] 5.5 Test: `FilterBottomSheetViewModelTest` (new) — `availableApps` includes monitored apps not referenced by any rule; existing category/status/sort behavior unaffected.

## Phase 6: Cross-Cutting Verification

- [x] 6.1 Run `./gradlew spotlessApply detekt architectureCheck test` and fix any new findings before marking the change ready for review.
