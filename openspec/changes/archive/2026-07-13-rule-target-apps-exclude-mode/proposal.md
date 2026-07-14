# Proposal: Rule Target-Apps Include/Exclude Mode

## Intent

Today a rule's app scope is two-state: `targetApps == null` fires for all apps, a non-null list fires ONLY for the listed apps (include-only). Users cannot express the common inverse — "run this rule for every app EXCEPT these" (roadmap.md line 118, Phase 3). Adding a few noisy apps to an exclude list is far easier than enumerating every other installed app. This closes that gap.

## Scope

### In Scope
- Add a rule-level include/exclude flag (`Rule.isIncludeMode: Boolean = true`) alongside the existing `targetApps` list; `targetApps == null` still means "all apps" (flag ignored).
- Persist it via an additive Room column (`is_include_mode`, default true) — `is_global` and the `rule_target_apps` join table stay as-is. Destructive schema bump (no `Migration`, pre-launch).
- Restructure `RuleDao.getRulesForApp`/`getActiveRulesForApp` for the exclude branch (`NOT EXISTS` subquery, keep batch `getTargetAppsForRules` — no N+1).
- Mirror an exclude query on the backtest path (`NotificationDao` `NOT IN` variant + `getNotificationsForBacktest` mode-aware branch).
- RuleEditor include/exclude toggle (`WhenSection`, `RuleUiModel`, `RuleEditorViewModel`).
- Rule-list summary variant ("All apps except N") in `RulesScreen`.
- Rules-list app filter (`RulesViewModel`, `FilterBottomSheetViewModel`) evaluates effective scope, not list membership (see business rules).
- Wire-format bump v2→v3: new `appScopeMode`/`isIncludeMode` DTO field + golden-file test update.

### Out of Scope
- Per-app mixed include/exclude within one rule (rejected — roadmap wants all-or-nothing).
- Sealed `AppScope` domain rewrite (explicitly not chosen — see Approach).
- Back-compat decoding of older wire versions (ADR 011 precedent, pre-launch).

## Capabilities

### New Capabilities
- `rule-app-scope`: the rule app-targeting model (all / include-listed / exclude-listed), its enforcement in live matching AND backtest, and wire-format representation. No existing spec covers app targeting today.

### Modified Capabilities
- None.

## Approach

Boolean-flag model (exploration Approach 1), **overriding** the exploration's Approach-2 (sealed `AppScope`) recommendation per locked user decision: reuse the existing shape, add one boolean, no new enum, no third state. Guard the meaningless combination (flag set with `targetApps == null`) at the mapper/construction boundary rather than in the type system.

## Business Rules & Edge Cases

- `targetApps == null` → unconditionally global; `isIncludeMode` ignored.
- Non-null + `isIncludeMode = true` → fires only for listed apps (today's default).
- Non-null + `isIncludeMode = false` → fires for every app NOT listed.
- Filtering rules by "App X" MUST surface an exclude-mode rule that does NOT list App X (it fires for X). Requires effective-scope evaluation per rule, not membership check.
- Empty non-null list: treat as "all apps" for both modes (matches current null-or-empty wire semantics).
- Backtest and live-match are two independent "does this rule apply" checks — both must be updated together.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/model/Rule.kt` | Modified | Add `isIncludeMode` |
| `core/data/local/entity/RuleEntity.kt` | Modified | Add `is_include_mode` column |
| `core/data/local/dao/RuleDao.kt` | Modified | Exclude branch (`NOT EXISTS`) |
| `core/data/local/mapper/RuleMapper.kt` | Modified | Carry flag both ways |
| `core/data/repository/RuleRepositoryImpl.kt` | Modified | Plumb flag through save/load |
| `core/data/repository/NotificationRepositoryImpl.kt` + `domain/repository/NotificationRepository.kt` | Modified | Backtest exclude query |
| `features/ruleeditor/{viewmodel,domain,ui/components}` | Modified | Toggle + draft model |
| `features/rules/ui/RulesScreen.kt` | Modified | "All apps except N" summary |
| `features/rules/viewmodel/{RulesViewModel,FilterBottomSheetViewModel}.kt` | Modified | Effective-scope filtering |
| `core/rulesharing/dto/RuleExportDto.kt` + `RuleWireMapper.kt` | Modified | v2→v3 field + golden test |
| `docs/rule-format.md`, `docs/capabilities.md` | Modified | Document mode; fix stale `schemaVersion` (doc says 1, code=2→3) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| DAO restructure reintroduces N+1 | Med | Keep `getTargetAppsForRules` batch; review query plan |
| Live-match vs backtest drift | Med | Update both queries in same change; shared test fixtures |
| Latent invalid state (flag + null apps) | Low | Guard at mapper boundary |
| Filter CPU cost (per-rule scope eval in memory) | Low | Small rule counts; evaluate in ViewModel, not SQL |
| Forgotten golden-file update | Low | Golden test fails loudly on shape change |

## Rollback Plan

Revert the change branch. Pre-launch: no persisted user data or exported rules to preserve, so the destructive schema bump and wire-format v3 carry no migration obligation on rollback.

## Dependencies

- None external. Confirm `RULE_EXPORT_SCHEMA_VERSION` is `2` in code (verified) before bumping to `3`.

## Success Criteria

- [ ] A rule can be saved and edited in exclude mode via the RuleEditor toggle.
- [ ] An exclude-mode rule fires for apps NOT in its list and skips listed apps (live match).
- [ ] Backtest yields the same set for an exclude-mode rule.
- [ ] Filtering the rules list by an app surfaces exclude-mode rules that omit that app.
- [ ] Export/import round-trips `isIncludeMode`; golden-file test updated to v3.
- [ ] `./gradlew test detekt architectureCheck` pass; capabilities.md updated.

## Proposal Question Round (needs user review — could not ask interactively)

Assumptions locked unless corrected:
1. Toggle copy: "Only these apps" / "All apps except these" as a segmented control in `WhenSection`. OK?
2. FilterBottomSheet facet chips: an exclude-mode rule contributes conceptually to "all apps", so it appears under every app facet except the ones it lists. Confirm this is the desired faceting (vs. showing exclude rules only under a separate "All apps" facet).
3. Empty non-null `targetApps` collapses to "all apps" in both modes — acceptable, or should the UI forbid saving an exclude rule with an empty list?
