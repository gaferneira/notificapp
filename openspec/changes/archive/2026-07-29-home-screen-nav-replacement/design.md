# Design: Home Dashboard replaces Inbox as first bottom-nav destination

## Technical Approach

A new `features/home/` MVI package whose `HomeViewModel` `combine()`s existing repository flows plus a small set of additive windowed/joined queries — no new use-case seam (proposal Approach). The one genuinely algorithmic piece, recurring-notification detection, is factored into two pure-Kotlin, dependency-free classes in `core/notification/`: `NotificationTitleNormalizer` (string → stable group key) and `RecurringNotificationSuggester` (grouping + ranking + rule-coverage classification). Neither touches the DB; the ViewModel feeds them already-queried data, mirroring how `RuleMatcher`/`RuleEngine` stay pure and are driven from the outside.

Rule coverage is computed by reusing the **live** matcher — `Rule.appliesToPackage()` + `RuleMatcher.matches()` — not a re-implementation. Both are already pure, already the production semantics, and the suggester already holds the notifications in memory for grouping, so the "accurate" option costs essentially nothing.

Nav is a slot swap (`INBOX` → `HOME`) plus a stacked `Screen.Inbox` push from "See all"; one small `MainBottomNav` signature relaxation is required (see D7).

## Architecture Decisions

| # | Decision | Choice | Rejected | Rationale |
|---|---|---|---|---|
| D1 | **OQ1 — "covered by a rule" precision** | Tri-state `RuleCoverage`: `UNCOVERED` (no active rule applies to the package) / `APP_ONLY` (a rule applies to the package but its conditions don't fire on the group's representative notification) / `CONDITIONS_MATCH` (a rule applies *and* matches). Suppress `CONDITIONS_MATCH`; rank `UNCOVERED` above `APP_ONLY` | (a) App-level only (binary include/exclude); (b) evaluate conditions over *all* notifications in the group | `RuleMatcher.matches()` is pure, zero-I/O, and is the exact live semantics — reusing it means suggestions can never contradict what the engine actually does. App-level-only would silently hide a "Bank: transfer received" pattern just because an unrelated "Bank: promo" rule exists on the same package — the most common real case. Evaluating one representative (most-recent) notification per group, not all N, keeps cost at `groups × applicableRules` condition evaluations (bounded by the 500-row scan and typically <20 rules). Suppressing `CONDITIONS_MATCH` is not a violation of proposal Decision #2 (coverage as sort key): a rule that *literally already fires on this notification* isn't a low-ranked suggestion, it's a wrong one. The two remaining states honour Decision #2 as the primary sort key. |
| D2 | **RuleMatcher's `now` argument** | Pass the *representative notification's own timestamp* (`Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDateTime()`), not wall-clock now | `LocalDateTime.now()` | `RuleCondition.DayOfWeekCondition`/`TimeRangeCondition` are evaluated against `now`. Judging historical coverage against *today at 3pm* would report a "quiet hours 22:00–07:00" rule as not covering a notification it demonstrably did cover at 23:00. `zoneId` is a suggester parameter, keeping the class pure. |
| D3 | **OQ2 — title normalization** | Deterministic token-stripping normalizer + **exact equality** of the normalized key. No fuzzy/edit-distance similarity | Levenshtein/Jaccard with a threshold | Three reasons, in order of weight: (1) proposal Decision #1's dismissal table persists `normalized_title_key` — a fuzzy scheme has **no stable key to persist**, so "Skip similar" could not be implemented against it without storing exemplar text and re-fuzzing on every read; (2) no threshold is defensible pre-launch with zero usage data — normalization *is* the fuzziness, but expressed as individually reviewable, individually unit-testable regex rules; (3) exact-match grouping is O(n) via `groupBy`, fuzzy is O(n²) across groups. |
| D4 | **OQ3 — thresholds** | **4 occurrences within 14 days, spanning ≥ 2 distinct local calendar days**; max 3 suggestions; scan capped at the 500 most-recent windowed notifications | 3-in-7-days; user-configurable settings | A 7-day window structurally cannot see biweekly cadence (payroll, billing cycles, subscription renewals) — exactly the highest-value automation targets — and biases entirely toward daily chatter. 14 days sees a biweekly pattern twice. With a 14-day window, N=3 is too weak: a single package's 3-day in-transit update burst would qualify. The **≥2 distinct calendar days** guard does more work than the exact N — it is what separates "recurring pattern" from "one event that emitted 6 updates in an afternoon", and pure counting cannot make that distinction at any N. Cap of 3 keeps Recent Activity above the fold. |
| D5 | **Where thresholds live** | `internal const val` defaults in `RecurringNotificationSuggester.Companion`, surfaced as a `Config` data class with defaults so tests override without reflection. **Not** settings-exposed in v1 | DataStore preference + Settings UI | These are heuristic tuning knobs, not user preferences. Exposing them makes users debug our algorithm, permanently freezes the numbers as a compatibility surface, and costs DataStore keys + settings rows + translated strings for a feature with zero usage data. Direct precedent: `NotificationDeduplicator.DUPLICATE_WINDOW_MS`/`DB_LOOKBACK_MS` are private constants and always have been. Promote to settings only if real users complain. |
| D6 | **Recent-activity feed shape** | Single joined query `rule_executions ⋈ notifications ⋈ rules`, `LIMIT :limit`, `was_dry_run = 0`. **No** `extracted_field_values` join | Per-row extracted-field lookup to render "…$1,240.00 deposit" | A per-execution field lookup is textbook N+1 fan-out (the PERF-008 / DATA-01/03/05 family already flagged as manual-review debt). v1 renders `ruleName` + notification title/content, which the mock's example line already contains. Deliberate scope trim vs. the mock; revisit with a single grouped `IN (:ids)` query if it proves necessary. |
| D7 | **Bottom nav on stacked Inbox** | Relax `MainBottomNav(selectedDestination: AppDestinations?)` to nullable; `InboxScreen` passes `null` and gains a back affordance | (a) Keep `AppDestinations.INBOX` as a dead enum entry; (b) drop the bottom bar from Inbox entirely | `AppDestinations.INBOX` disappears, so `InboxScreen`'s `selectedDestination = AppDestinations.INBOX` **will not compile** — the proposal's "Inbox code compiles untouched" criterion needs a 2-line amendment, called out honestly here. Nullable is the smallest honest change: it expresses "this is a stacked screen, no tab is current" and is reusable by any future stacked screen. Keeping a dead `INBOX` entry would put a 5th tab back in the bar. |
| D8 | **Dismissal store** | New `suggestion_dismissals` table, composite PK `(package_name, normalized_title_key)`, `INSERT OR REPLACE`. No FK to `notifications`/`selected_apps` | Surrogate id + uniqueness index; generic `dismissals(type, key)` table | Composite PK makes re-dismissal an idempotent timestamp refresh — duplicate rows are structurally impossible, no dedupe logic. No FK because a dismissal must outlive the retention sweep that deletes the notifications it was derived from. Generic table rejected per proposal Decision #1. |
| D9 | **DB version** | `AppDatabase.CURRENT_VERSION` `1` → `2`, no `Migration` object | Hand-written migration | Pre-launch destructive policy (CLAUDE.md). Note: `webhook-delivery/design.md` says "2→3"; the committed code reads `CURRENT_VERSION = 1` after `79985bf refactor: fix database version`. **Apply phase must bump `current + 1` as read from source**, not the number quoted here. |
| D10 | **Suggester DB access** | None — `suggest()` is a pure function over `(notifications, rules, dismissals, zoneId, config)` | Inject repositories into the suggester | Matches `RuleMatcher`/`FieldExtractor`/`NotificationNormalizer`. Unit-testable with plain fixtures, no MockK, no coroutines, no `runTest`. |

## Interfaces / Contracts

### `core/notification/NotificationTitleNormalizer.kt` (pure `object`)

```kotlin
object NotificationTitleNormalizer {
    /** @return a stable grouping key, or null when the title carries no stable structure. */
    fun normalize(title: String?): String?
}
```

Algorithm, in strict order (order matters — `url` before `id`, currency before bare digits):

1. `title` null/blank → `null`.
2. NFKC-normalize, `trim()`, `lowercase(Locale.ROOT)`.
3. Replace, in sequence:
   | Pattern (illustrative) | Placeholder |
   |---|---|
   | `https?://\S+` | `<url>` |
   | `\S+@\S+\.\S+` | `<email>` |
   | `[€$£¥₹]\s?\d[\d.,]*` and `\d[\d.,]*\s?(usd\|eur\|cop\|mxn\|ars\|gbp)\b` | `<amt>` |
   | `\b\d{1,2}:\d{2}(:\d{2})?\s?(am\|pm)?\b` | `<time>` |
   | `\b\d{1,4}[-/]\d{1,2}[-/]\d{1,4}\b` | `<date>` |
   | `\b(?=[a-z0-9-]*\d)[a-z0-9-]{6,}\b` (order/tracking ids) | `<id>` |
   | `\d+` (residual digit runs) | `<num>` |
4. Collapse punctuation + whitespace runs to a single space; trim.
5. **Guard**: if the residual with placeholders removed has < 3 alphabetic characters, return `null`. Prevents a garbage bucket where `"$40.00"` and `"12/05"` collapse into one meaningless group.

Compiled `Regex` instances are `private val` fields on the object — never compiled per call (PERF-001/002 discipline).

**Accepted limitation**: personal names are not strippable by regex, so messaging-app titles (`"Gabriel"`) group per-contact rather than per-app. Mitigated by D1 coverage ranking, the ≥2-distinct-days guard, and persisted "Skip similar". Documented, not solved, in v1.

### `core/notification/RecurringNotificationSuggester.kt` (pure, `@Inject constructor()`, no deps)

```kotlin
class RecurringNotificationSuggester @Inject constructor() {
    fun suggest(
        notifications: List<Notification>,          // pre-windowed + capped by the caller
        activeRules: List<Rule>,
        dismissals: Set<SuggestionDismissalKey>,
        zoneId: ZoneId,
        config: Config = Config(),
    ): List<RecurringSuggestion>

    data class Config(
        val minOccurrences: Int = DEFAULT_MIN_OCCURRENCES,     // 4
        val windowDays: Int = DEFAULT_WINDOW_DAYS,             // 14
        val minDistinctDays: Int = DEFAULT_MIN_DISTINCT_DAYS,  // 2
        val maxSuggestions: Int = DEFAULT_MAX_SUGGESTIONS,     // 3
    )
}

enum class RuleCoverage { UNCOVERED, APP_ONLY, CONDITIONS_MATCH } // ordinal = sort priority

data class RecurringSuggestion(
    val packageName: String,
    val appName: String,
    val normalizedTitleKey: String,
    val sampleTitle: String,          // raw title of the most recent member, for display
    val sampleNotificationId: String, // seeds Screen.RuleEditor(notificationId = …)
    val occurrences: Int,
    val distinctDays: Int,
    val coverage: RuleCoverage,
)
```

Sketch:

```
1. group notifications by (packageName, normalize(title)); drop null keys
2. keep groups where size >= minOccurrences
                  && distinct LocalDate(ts, zoneId) count >= minDistinctDays
                  && key !in dismissals
3. per group: rep = most recent member
     candidates = activeRules.filter { it.appliesToPackage(pkg) }
     coverage = when {
        candidates.isEmpty()                                          -> UNCOVERED
        candidates.any { RuleMatcher.matches(rep, it.conditions,
                          rep.timestamp.toLocalDateTime(zoneId),
                          it.conditionLogic) }                        -> CONDITIONS_MATCH
        else                                                          -> APP_ONLY
     }
4. drop CONDITIONS_MATCH
5. sortWith(compareBy<coverage.ordinal>.thenByDescending(occurrences)
                                       .thenByDescending(rep.timestamp))
6. take(maxSuggestions)
```

### Data layer additions

```kotlin
// domain/model/SuggestionDismissalKey.kt
data class SuggestionDismissalKey(val packageName: String, val normalizedTitleKey: String)

// core/data/local/entity — internal per architectureCheck rule 1
@Entity(
    tableName = "suggestion_dismissals",
    primaryKeys = ["package_name", "normalized_title_key"],
    indices = [Index(value = ["dismissed_at"])],
)
internal data class SuggestionDismissalEntity(
    @ColumnInfo("package_name") val packageName: String,
    @ColumnInfo("normalized_title_key") val normalizedTitleKey: String,
    @ColumnInfo("dismissed_at") val dismissedAt: Long,
)
```

`dismissed_at` is stored but unused in v1 (dismissal is permanent); it exists so a future "expire after 90 days" needs no schema change.

```kotlin
// domain/repository/SuggestionDismissalRepository.kt
interface SuggestionDismissalRepository {
    fun observeDismissals(): Flow<Set<SuggestionDismissalKey>>
    suspend fun dismiss(packageName: String, normalizedTitleKey: String): Result<Unit>
}

// domain/repository/RuleExecutionRepository.kt  — additive
fun observeExecutionCountSince(since: Long): Flow<Int>
fun observeRecentActivity(limit: Int): Flow<List<RecentActivity>>

// domain/repository/NotificationRepository.kt   — additive
fun observeActiveAppCountSince(since: Long): Flow<Int>
fun observeRecentSince(since: Long, limit: Int): Flow<List<Notification>>
```

Rule count reuses `observeAllRules()` (`.map { it.size }`) — the same flow already feeds coverage, so no new rule query. Monitored-app count reuses `observeEnabledApps().map { it.size }`.

New DAO queries land on the DAO owning the driving table — no new DAO (`getRecentExecutionsForPackageSince` is the existing precedent for a join on `RuleExecutionDao`):

```sql
-- RuleExecutionDao
SELECT COUNT(*) FROM rule_executions WHERE created_at >= :since AND was_dry_run = 0
-- RuleExecutionDao (RecentActivityRow POJO)
SELECT re.id, re.created_at, r.id AS rule_id, r.name AS rule_name,
       n.id AS notification_id, n.title, n.content, n.package_name, n.app_name
FROM rule_executions re
  INNER JOIN notifications n ON re.notification_id = n.id
  INNER JOIN rules r         ON re.rule_id = r.id
WHERE re.was_dry_run = 0 ORDER BY re.created_at DESC LIMIT :limit
-- NotificationDao
SELECT COUNT(DISTINCT package_name) FROM notifications WHERE timestamp >= :since
SELECT * FROM notifications WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit
```

All four hit existing indices (`rule_executions.created_at`, `notifications.timestamp`, PK joins). No new index needed.

### `features/home/contract/HomeContract.kt`

```kotlin
data class HomeUiState(
    val monitoring: MonitoringStatus = MonitoringStatus(),
    val weekStats: WeekStats = WeekStats(),
    val recentActivity: ImmutableList<RecentActivityUi> = persistentListOf(),
    val section: HomeSection = HomeSection.None,
    val isLoading: Boolean = true,
)

data class MonitoringStatus(val isListenerEnabled: Boolean = false, val monitoredAppCount: Int = 0, val ruleCount: Int = 0)
data class WeekStats(val records: Int = 0, val rulesFired: Int = 0, val appsActive: Int = 0)

sealed interface HomeSection {
    data class StarterRules(val templates: ImmutableList<RuleTemplateInfo>) : HomeSection
    data class Recurring(val suggestions: ImmutableList<RecurringSuggestionUi>) : HomeSection
    data object None : HomeSection
}
```

`RecurringSuggestionUi` is a **feature-owned** mapping of `core.notification.RecurringSuggestion`, mapped at the ViewModel boundary (architectureCheck rule 7 discipline). `RuleTemplateInfo` from `core.rulesharing` is used directly — existing precedent in `features/rules/ui/RuleTemplatePickerSheet.kt`, and rule 7 targets `core.extraction` only.

## Data Flow

```
RuleRepository.observeAllRules() ──┬──────────────────────────► ruleCount ──┐
                                   │                                        │
SelectedAppRepository.observeEnabledApps() ──► appCount ────────────────────┤
listenerRefresh (MutableStateFlow, re-read on HomeEvent.OnScreenResumed) ───┤
                                   │                              combineStatus ─┐
                                   │                                             │
RuleExecutionRepository.observeExecutionCountSince(weekStart) ──┐                │
NotificationRepository.observeActiveAppCountSince(weekStart) ───┤                │
DataBrowserRepository.statistics(...).thisWeek ─────────────────┤ combineStats ──┼─► HomeUiState
RuleExecutionRepository.observeRecentActivity(limit = 5) ───────┘                │
                                   │                                             │
NotificationRepository.observeRecentSince(now-14d, 500) ──┐                      │
SuggestionDismissalRepository.observeDismissals() ────────┤ combineSuggestions ──┘
   (+ activeRules from observeAllRules)                   │
        └──► RecurringNotificationSuggester.suggest(...) ─┘   [pure, no I/O]
```

`kotlinx.coroutines.combine` tops out at 5 typed sources, so this is a **two-stage combine**: three intermediate combines (status / stats / suggestions) feeding one terminal `combine(status, stats, suggestions)`. `RulesViewModel`'s existing 3-way `combine(...).collectLatest { setState { copy(...) } }` is the shape to mirror.

**Section selection** happens once, in the terminal combine — never in the UI:

| `ruleCount` | `suggestions` | `section` |
|---|---|---|
| `0` | any | `StarterRules(RuleTemplates.all.take(3))` |
| `> 0` | non-empty | `Recurring(suggestions)` |
| `> 0` | empty | `None` (section omitted; stats + activity carry the screen) |

Listener status: `NotificationListenerStatusProvider.isEnabled()` is a synchronous call, not a Flow. Home holds a `MutableStateFlow<Boolean>` re-read on `HomeEvent.OnScreenResumed`, matching the existing Inbox/Settings permission-banner pattern — **verify the exact existing idiom at apply time** and reuse it verbatim rather than inventing a variant.

## File Changes

| File | Action | Description |
|---|---|---|
| `core/notification/NotificationTitleNormalizer.kt` | Create | Pure normalizer (D3) |
| `core/notification/RecurringNotificationSuggester.kt` | Create | Pure grouping/ranking/coverage (D1, D4, D10) |
| `domain/model/RecurringSuggestion.kt`, `RuleCoverage.kt`, `SuggestionDismissalKey.kt`, `RecentActivity.kt` | Create | Domain models |
| `core/data/local/entity/SuggestionDismissalEntity.kt`, `dao/SuggestionDismissalDao.kt`, `mapper/SuggestionDismissalMapper.kt` | Create | `internal` (rule 1) |
| `domain/repository/SuggestionDismissalRepository.kt`, `core/data/repository/SuggestionDismissalRepositoryImpl.kt` | Create | `internal` impl, `Failure` mapping (rule 6) |
| `core/data/local/AppDatabase.kt` | Modify | Add entity + `suggestionDismissalDao()`, `CURRENT_VERSION` +1 (D9) |
| `core/data/local/dao/RuleExecutionDao.kt` | Modify | Windowed count + joined activity feed (D6) |
| `core/data/local/dao/NotificationDao.kt` | Modify | Distinct-app count + windowed recent list |
| `domain/repository/{RuleExecution,Notification}Repository.kt` + impls | Modify | Additive flow methods |
| `core/di/{Database,Repository}Module.kt` | Modify | DAO + repository wiring |
| `features/home/contract/HomeContract.kt`, `viewmodel/HomeViewModel.kt`, `ui/HomeScreen.kt` (+ section composables) | Create | MVI package |
| `core/ui/navigation/Screen.kt` | Modify | `data object Home : Screen()` |
| `core/ui/navigation/Routes.kt` | Modify | `fun home(): Screen = Screen.Home` |
| `core/ui/navigation/MainBottomNav.kt` | Modify | `INBOX` → `HOME("Home", Icons.Default.Home)`; **`RULES` must swap off `Icons.Default.Home`** (it currently uses it — would collide); `selectedDestination` → nullable (D7) |
| `MainActivity.kt` | Modify | `entry<Screen.Home>`; `AppFlowState.MAIN_APP -> Routes.home()` |
| `features/appselection/viewmodel/AppSelectionViewModel.kt` (+ its test) | Modify | `clearAndNavigate(Routes.home())` |
| `features/inbox/ui/InboxScreen.kt` | Modify | `selectedDestination = null` + back affordance (D7 — the 2-line amendment to "Inbox untouched") |
| `app/src/main/res/values*/strings.xml` | Modify | Home strings, English + existing locales (ADR 014) |
| `docs/capabilities.md` | Modify | See sync plan below |

## `docs/capabilities.md` Sync Plan

1. **Add `### Home Dashboard`** immediately before `### Notification Inbox & Detail`: monitoring status banner (listener state, monitored-app count, rule count); Starter Rules cards for zero-rule users; This Week row (records / rules fired / apps active); Recent Activity feed opening notification detail; "See all" pushing the Inbox. System trigger: "User opens the app."
2. **Add `### Recurring Notification Suggestions`**, stated in user terms: "When the same kind of notification arrives at least 4 times over 2 weeks on at least 2 different days from an app no rule covers, Home offers to turn it into a rule; 'Skip similar' hides that pattern permanently."
3. **Amend line ~98**: "User opens the app to the Inbox (its home screen)" → "User opens the app to the Home dashboard; the Inbox opens from Home's 'See all' or from a recent-activity row."
4. **Amend line ~128** (Data Browser): "bottom navigation, between Inbox and Rules" → "between Home and Rules".

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit — normalizer | Each token rule in isolation (currency/time/date/id/num/url/email); rule ordering (`$40.00` → `<amt>` not `$<num>`); case + NFKC folding; the <3-alpha-char null guard; null/blank input | Pure JVM, Kotest, table-driven |
| Unit — suggester | Below `minOccurrences` → excluded; same-day burst of 6 → excluded by `minDistinctDays`; dismissed key → excluded; `UNCOVERED` outranks `APP_ONLY`; `CONDITIONS_MATCH` suppressed; occurrence-count tiebreak; `maxSuggestions` cap; time-range coverage judged at the notification's own timestamp (D2) | Pure JVM, fixtures from `testutil/TestFixtures.kt`; no MockK |
| Unit — HomeViewModel | Section switching across all three rows of the selection table; dismiss event calls repo + suggestion disappears from the next emission; listener-status refresh on resume; effects (navigate to rule editor / notification detail / Inbox) | MockK repos + Turbine + injected `StandardTestDispatcher` (ADR 008) |
| Unit — dismissal repository | Round-trip mapping; re-dismiss replaces rather than duplicating (composite PK); DAO exception → `Result.failure(Failure…)` not a raw throwable (rule 6) | MockK DAO |
| Instrumented (Room) | `suggestion_dismissals` composite-PK REPLACE semantics; the three new windowed/joined queries return expected rows | Room in-memory DB test |
| Gates | `spotlessApply`, `detekt` (watch `HomeViewModel` `CyclomaticComplexMethod`/`LongMethod` on the terminal combine), `architectureCheck` (rules 1, 2, 3, 7), `test` | CI |

## Migration / Rollout

No data migration. `AppDatabase.CURRENT_VERSION` bumps by one with no `Migration` object — pre-launch destructive policy (CLAUDE.md); debug builds already use `fallbackToDestructiveMigration()`. Must not ship post-first-release without a real migration. Rollback per the proposal: revert `AppDestinations`/`Screen`/`Routes`/`MainActivity` to `Inbox` first, delete `features/home/` and the additive DAO/repository methods, drop `suggestion_dismissals` via another clean bump.

## Open Questions

- [ ] Exact `HomeEvent.OnScreenResumed` idiom — mirror whichever lifecycle hook Inbox/Settings already use for the permission banner; confirm at apply time rather than introducing a second pattern.
- [ ] Replacement icon for `AppDestinations.RULES` once `HOME` claims `Icons.Default.Home` (suggest `Icons.AutoMirrored.Filled.Rule` or `Icons.Default.Checklist`) — cosmetic, decide in tasks.
