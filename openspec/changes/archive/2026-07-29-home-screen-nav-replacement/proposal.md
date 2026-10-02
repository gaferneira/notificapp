# Proposal: Home Dashboard replaces Inbox as first bottom-nav destination

## Intent

The app opens on the Inbox — a raw notification list that answers "what arrived?" but never "is Notificapp working for me, and what should I do next?". New users land on an empty feed with no path to a first rule; returning users get no signal that monitoring is alive or that rules are firing. A Home dashboard makes value and next action visible on launch.

## Scope

### In Scope
- `AppDestinations.HOME` + `Screen.Home` + route wiring; Home takes Inbox's first nav slot.
- New `features/home/` MVI package (contract + viewmodel + ui), mirroring `features/rules`.
- Monitoring status banner: listener active/inactive, monitored-app count (`SelectedAppRepository`), rule count (`RuleRepository`).
- Empty state: "Starter Rules" cards from existing `core/rulesharing/RuleTemplates`, with "Create rule from this", "Create from scratch", "See more templates" (reuse `RuleTemplatePickerSheet`).
- Active state: "Recurring Notifications" suggestions from a NEW pure-Kotlin heuristic (same app + same title template, N repeats within M days), with "Create rule from this" / "Skip similar" (persisted dismissal).
- "This Week" stats row (records / rules fired / apps active), derived in-memory per the `DataBrowserRepository.statistics()` pattern.
- "Recent Activity": limited joined feed over `RuleExecutionDao` + notification + app + rule name; rows open `features/notificationdetail`; "see all" is a stub.
- `docs/capabilities.md` sync (CLAUDE.md rule).

### Out of Scope
- Deleting or relocating Inbox code/screen (its move into Data is a later change).
- Wiring "see all" to a real Data destination.
- ML/embedding similarity; Room schema changes beyond a minimal dismissal store.

## Capabilities

### New Capabilities
- `home-dashboard`: launch summary — monitoring status, starter rules, weekly stats, recent activity.
- `recurring-notification-suggestions`: detect repeated notifications and suggest rules; dismissals persist.

### Modified Capabilities
- None.

## Approach

`HomeViewModel` `combine()`s existing repository flows directly — no new use-case/aggregator seam (rejected as a novel pattern for one screen). The recurring-notification heuristic is isolated as a pure-Kotlin class analogous to `NotificationDeduplicator`, independently unit-tested. New DAO/repository methods are additive windowed/joined queries; no new entity except possibly the dismissal store.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `core/ui/navigation/{MainBottomNav,Screen,Routes}.kt` | Modified | HOME replaces INBOX slot |
| `features/home/` | New | Contract + ViewModel + UI |
| `core/notification/` | New | Recurring-notification suggester |
| `core/data/local/` + `domain/repository/` | Modified | Windowed counts, activity feed, dismissal store |
| `docs/capabilities.md` | Modified | New screen; "opens to Inbox" line |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Inbox becomes hard to reach | High | Accepted pre-launch; Data-screen relocation is the follow-up change |
| Noisy/irrelevant suggestions | Med | Conservative thresholds; "Skip similar" persists; exclude apps already covered by a rule |
| Aggregation queries slow on large tables | Low | Indexed windowed queries, limited feed; mirrors proven `statistics()` pattern |
| Fat ViewModel | Med | Heuristic extracted; `detekt` complexity gates |

## Rollback Plan

Single feature-scoped revert: restore `AppDestinations.INBOX` as first entry, drop the `features/home/` package and additive DAO/repository methods. Inbox code is untouched, so reverting nav restores prior behavior exactly. If a dismissal table ships, drop it via a clean schema bump (pre-launch, no data to preserve).

## Dependencies

None external. Requires existing `RuleTemplates`, `RuleExecutionDao`, listener status provider.

## Decisions (product/architecture calls)

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Dismissal persistence: small, purpose-built Room table scoped to this feature (app package + normalized-title-key + dismissedAt) — not a generic reusable table | A generic table designed against one caller tends to fit the wrong shape for whatever second caller shows up later; extract a shared abstraction only once a second real use case exists |
| 2 | Recurring-notification ranking: no-existing-rule-coverage is the primary sort key (not just an exclusion filter), per-app repeat count is the tiebreaker, title-normalization groups repeats within an app | Surfaces genuinely un-automated repetition first instead of treating coverage as binary include/exclude |
| 3 | "See all" is visible and pushes Inbox as a stacked screen (new nav level, not a bottom-nav destination) | Keeps Inbox fully reachable without spending a bottom-nav slot; fits ADR 007's Navigation3 custom Navigator without new nav infra |

## Open Questions (for design phase)
1. "Already covered by a rule" precision: does any rule targeting the app count, or must the rule's conditions plausibly match that notification's content?
2. Title-normalization strategy specifics (case-folding, dynamic-token stripping e.g. amounts/names, similarity threshold).
3. N repeats / M-day window defaults for the recurring-notification heuristic.

## Success Criteria

- [ ] App launches to Home; four nav slots preserved; Inbox code compiles untouched.
- [ ] Zero-rule users see Starter Rules and can create a rule from a template in one tap.
- [ ] Users with rules see accurate weekly stats and recent activity; rows open notification detail.
- [ ] Recurring-notification heuristic has standalone unit tests covering repeat detection and dismissal exclusion.
- [ ] `spotlessApply`, `detekt`, `architectureCheck`, `test` all pass.
