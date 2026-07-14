# Design: Rule Target-Apps Include/Exclude Mode

## Technical Approach

Add one boolean to the existing shape (locked model, overrides the exploration's sealed `AppScope`). `Rule.isIncludeMode: Boolean = true` rides alongside the untouched `targetApps: ImmutableList<AppInfo>?`. Applicability semantics live in ONE pure-Kotlin predicate on the domain model; every consumer (live-match DAO, backtest DAO, rules-list filter, summary text) mirrors that predicate. Empty non-null list collapses to "all apps" for both modes (spec + fixes a latent store bug). No wire `schemaVersion` bump (locked constraint 3 + spec override the proposal's stale "v2→v3" text).

Single source of truth (domain, `Rule.kt`):

```kotlin
fun Rule.appliesToPackage(pkg: String): Boolean = when {
    targetApps.isNullOrEmpty() -> true
    isIncludeMode -> targetApps.any { it.packageName == pkg }
    else -> targetApps.none { it.packageName == pkg }
}
```

## Architecture Decisions

| Decision | Options | Choice + Rationale |
|---|---|---|
| DB encoding | rename/repurpose `is_global` \| add 2nd column | **Add `is_include_mode` (default 1), keep `is_global`.** Preserves the indexed global fast-path, smallest blast radius, no third enum state. Two booleans encode 3 states; `is_global=1` ignores the mode. |
| `is_global` meaning | `targetApps==null` \| `isNullOrEmpty` | **`isNullOrEmpty`.** Fixes latent bug: today empty non-null list → `is_global=0`, no rows → never fires. Spec demands empty=all-apps. |
| Live-match query | LEFT JOIN + `ta.package_name` \| `EXISTS`/`NOT EXISTS` | **`NOT EXISTS`/`EXISTS` subqueries.** LEFT JOIN can't express set-negation without row duplication; correlated subqueries keep one row per rule and reuse the batch `getTargetAppsForRules` load (no N+1). |
| Backtest vs live | share SQL \| duplicate + link | **Duplicate, both SQL-level, cross-linked comments + shared applicability test.** Different tables/directions (rules-by-app vs notifications-by-ruleset); sharing is impossible and Kotlin-side filtering would break the `LIMIT` bound the backtest relies on. |
| Wire field | new field default `true` \| version bump | **`@SerialName("isIncludeMode")=true`, no bump.** Additive field whose default reproduces legacy semantics = forward/back tolerant without a compat shim. |
| Zero-apps + exclude | forbid save \| collapse to all | **Collapse (no save-block).** Editor already does NOT forbid empty for include (only name is validated) — task premise corrected. Disable the toggle when zero apps selected instead. |

## Data Flow

    RuleEditor (mode toggle) ─→ RuleUiModel.isIncludeMode ─→ Rule ─→ RuleMapper ─→ is_include_mode
                                                                                        │
    live:   getActiveRulesForApp(pkg) ──EXISTS/NOT EXISTS──┐                            │
    backtest: getRecentByPackageNames[Excluding]           ├─ mirror ─ Rule.appliesToPackage ┘
    filter: RulesViewModel/FilterSheet ────────────────────┘

## SQL Shape (RuleDao, active variant)

```sql
SELECT r.* FROM rules r
WHERE r.is_active = 1 AND (
  r.is_global = 1
  OR (r.is_include_mode = 1 AND EXISTS
      (SELECT 1 FROM rule_target_apps ta WHERE ta.rule_id = r.id AND ta.package_name = :packageName))
  OR (r.is_include_mode = 0 AND NOT EXISTS
      (SELECT 1 FROM rule_target_apps ta WHERE ta.rule_id = r.id AND ta.package_name = :packageName)))
ORDER BY r.updated_at DESC
```
`getRulesForApp` = same without `is_active`. NotificationDao gains `getRecentByPackageNamesExcluding` (`package_name NOT IN (:packageNames) ... LIMIT`), with a comment linking it to RuleDao's NOT EXISTS branch.

## File Changes

| File | Action | Description |
|---|---|---|
| `domain/model/Rule.kt` | Modify | Add `isIncludeMode`; add `appliesToPackage()` |
| `core/data/local/entity/RuleEntity.kt` | Modify | Add `is_include_mode` col (defaultValue "1"); no new index |
| `core/data/local/dao/RuleDao.kt` | Modify | EXISTS/NOT EXISTS in both `get*RulesForApp` |
| `core/data/local/dao/NotificationDao.kt` | Modify | Add `getRecentByPackageNamesExcluding` |
| `core/data/local/mapper/RuleMapper.kt` | Modify | `is_global = targetApps.isNullOrEmpty()`; carry `isIncludeMode` both ways |
| `core/data/local/AppDatabase.kt` | Modify | Bump version; keep destructive fallback (pre-launch) |
| `core/data/repository/NotificationRepositoryImpl.kt` + `domain/repository/NotificationRepository.kt` | Modify | `getNotificationsForBacktest(targetPackages, isIncludeMode, limit)` mode branch |
| `core/rulesharing/dto/RuleExportDto.kt` | Modify | `isIncludeMode` after `targetApps`; keep `SCHEMA_VERSION = 2` |
| `core/rulesharing/RuleWireMapper.kt` | Modify | Map `isIncludeMode` in `toDto`/`toDomain` |
| `features/ruleeditor/domain/RuleUiModel.kt` | Modify | Add `isIncludeMode`; `fromDomain`/`toEntity` carry it (empty→handled by mapper) |
| `features/ruleeditor/viewmodel/RuleEditorViewModel.kt` | Modify | `OnAppScopeModeChanged`; pass mode to backtest |
| `features/ruleeditor/ui/components/WhenSection.kt` + app-selection step | Modify | Segmented control "Only these apps"/"All apps except these" (shown only when ≥1 app); AppsCard "All apps except N" summary |
| `features/rules/viewmodel/RulesViewModel.kt` | Modify | App filter uses `selectedApps.any { rule.appliesToPackage(it) }` |
| `features/rules/viewmodel/FilterBottomSheetViewModel.kt` | Modify | `availableApps` = union(rule-referenced ∪ monitored apps) so exclude-only apps are filterable (new data dep) |
| `features/rules/ui/RulesScreen.kt` | Modify | "All apps except N" / "Except {name}" summary variants (2 sites) |
| `app/src/test/resources/rule-export-v1.json` | Modify | Add `"isIncludeMode": true`; `schemaVersion` stays `2` |
| `docs/rule-format.md`, `docs/capabilities.md` | Modify | Document mode; fix stale `schemaVersion 1→2`; add app-scope capability |

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `appliesToPackage` all-3-modes incl. empty | table test = single source of truth |
| DAO (Robolectric/inMemory) | live EXISTS/NOT EXISTS, no N+1 | in-memory Room |
| Unit | backtest include/exclude/empty branch parity vs `appliesToPackage` | shared fixture asserting live≡backtest (drift guard) |
| Unit | wire round-trip exclude rule; golden fixture v2 shape | `RuleJsonCodecTest` + new exclude case |
| Unit | RulesViewModel filter surfaces exclude rule under omitted app | existing VM test |

## Migration / Rollout

Destructive schema bump, no `Migration` object, no wire compat branch (pre-launch, CLAUDE.md). Rollback = revert branch.

## Open Questions

- [ ] `FilterBottomSheetViewModel` currently has no constructor deps; sourcing the monitored-apps universe adds one injection (SelectedAppRepository / `observeAppsWithNotifications`). Confirm acceptable vs. limiting facets to rule-referenced apps only.
- [ ] Proposal text says wire "v2→v3"; design follows LOCKED constraint 3 + spec (stays v2). Flagging the proposal drift as resolved-by-override.
