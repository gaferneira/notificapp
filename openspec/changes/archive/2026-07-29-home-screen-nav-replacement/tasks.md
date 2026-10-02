# Tasks: Home Dashboard replaces Inbox as first bottom-nav destination

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~1900-2400 (2 pure heuristic classes + tests, 1 new Room entity/dao, 4 additive repo methods, full MVI feature package, nav rewiring across 5 files, i18n) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1: domain+data+DI+DB bump; PR2: pure heuristic classes+unit tests; PR3: HomeViewModel+Contract+tests; PR4: UI+nav wiring; PR5: strings+docs+remaining tests+gates |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending — ask user before `sdd-apply` |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Domain models + `SuggestionDismissal{Entity,Dao,Mapper}` + repositories/impls + DI + `AppDatabase` version bump | PR 1 | No UI, no heuristic logic. Base = feature/tracker branch. |
| 2 | `NotificationTitleNormalizer` + `RecurringNotificationSuggester` + their unit tests | PR 2 | Pure JVM, depends on Unit 1's domain models only. Base = Unit 1 branch. |
| 3 | `HomeContract` + `HomeViewModel` + its unit tests | PR 3 | Depends on Units 1-2. Base = Unit 2 branch. |
| 4 | `HomeScreen` + nav wiring (`Screen`/`Routes`/`MainBottomNav`/`MainActivity`/`AppSelectionViewModel`/`InboxScreen`) | PR 4 | Depends on Unit 3. Base = Unit 3 branch. |
| 5 | Strings/i18n + `docs/capabilities.md` sync + dismissal-repo test + Room instrumented test + final gates | PR 5 | Depends on Units 1-4. Base = Unit 4 branch. |

## Phase 1: Domain Models

- [x] 1.1 Create `domain/model/SuggestionDismissalKey.kt`: `data class SuggestionDismissalKey(packageName, normalizedTitleKey)`.
- [x] 1.2 Create `domain/model/RuleCoverage.kt`: `enum class RuleCoverage { UNCOVERED, APP_ONLY, CONDITIONS_MATCH }` (ordinal = sort priority, D1).
- [x] 1.3 Create `domain/model/RecurringSuggestion.kt`: fields per design.md Interfaces section (packageName, appName, normalizedTitleKey, sampleTitle, sampleNotificationId, occurrences, distinctDays, coverage).
- [x] 1.4 Create `domain/model/RecentActivity.kt`: joined-row domain model (rule id/name, notification id/title/content/package/app name, executedAt).

## Phase 2: Data Layer

- [x] 2.1 Create `core/data/local/entity/SuggestionDismissalEntity.kt` (`internal`): composite PK `(package_name, normalized_title_key)`, `dismissed_at` column, index on `dismissed_at` (D8).
- [x] 2.2 Create `core/data/local/dao/SuggestionDismissalDao.kt` (`internal`): `INSERT OR REPLACE` dismiss, observe-all query.
- [x] 2.3 Create `core/data/local/mapper/SuggestionDismissalMapper.kt` (`internal object`): entity ↔ `SuggestionDismissalKey`.
- [x] 2.4 Modify `core/data/local/dao/RuleExecutionDao.kt`: add windowed `COUNT(*) WHERE created_at >= :since AND was_dry_run = 0` query + joined `RecentActivityRow` query (`rule_executions ⋈ notifications ⋈ rules`, `LIMIT :limit`, D6).
- [x] 2.5 Modify `core/data/local/dao/NotificationDao.kt`: add `COUNT(DISTINCT package_name) WHERE timestamp >= :since` query + windowed recent-notifications query (`ORDER BY timestamp DESC LIMIT :limit`).
- [x] 2.6 Modify `core/data/local/AppDatabase.kt`: read `CURRENT_VERSION`'s current committed value from source first (do not hardcode a number from another doc) and bump it by exactly one; register `SuggestionDismissalEntity` + `suggestionDismissalDao()`; no `Migration` object (D9, pre-launch destructive policy).

## Phase 3: Repository Layer

- [x] 3.1 Create `domain/repository/SuggestionDismissalRepository.kt`: `observeDismissals(): Flow<Set<SuggestionDismissalKey>>`, `suspend fun dismiss(packageName, normalizedTitleKey): Result<Unit>`.
- [x] 3.2 Create `core/data/repository/SuggestionDismissalRepositoryImpl.kt` (`internal`): map DAO exceptions to `Failure` (architectureCheck rule 6), never leak raw throwables.
- [x] 3.3 Modify `domain/repository/RuleExecutionRepository.kt` + impl: add `observeExecutionCountSince(since): Flow<Int>`, `observeRecentActivity(limit): Flow<List<RecentActivity>>`.
- [x] 3.4 Modify `domain/repository/NotificationRepository.kt` + impl: add `observeActiveAppCountSince(since): Flow<Int>`, `observeRecentSince(since, limit): Flow<List<Notification>>`.

## Phase 4: DI Wiring

- [x] 4.1 Modify `core/di/DatabaseModule.kt`: `provideSuggestionDismissalDao`.
- [x] 4.2 Modify `core/di/RepositoryModule.kt`: `bindSuggestionDismissalRepository`.

## Phase 5: Pure Heuristic Classes

- [x] 5.1 Create `core/notification/NotificationTitleNormalizer.kt` (D3): pure `object`, NFKC/trim/lowercase, ordered regex replacements (url → email → currency/amount → time → date → id → residual digits, table in design.md), punctuation/whitespace collapse, `<3`-alpha-char guard returning `null`; compiled `Regex` as `private val` fields, never per-call.
- [x] 5.2 Write `NotificationTitleNormalizerTest`: each token rule in isolation, rule ordering (`$40.00` → `<amt>` not `$<num>`), case + NFKC folding, `<3`-alpha guard, null/blank input.
- [x] 5.3 Create `core/notification/RecurringNotificationSuggester.kt` (D1, D2, D4, D5, D10): pure `@Inject constructor()`, no DB access; `Config` data class with `internal const val` defaults (`minOccurrences=4`, `windowDays=14`, `minDistinctDays=2`, `maxSuggestions=3`); groups by `(packageName, normalize(title))`; gates on count + window + distinct-calendar-days per D4; classifies `RuleCoverage` via `Rule.appliesToPackage()` + `RuleMatcher.matches()` evaluated at the representative notification's own timestamp (D2), never wall-clock now; drops `CONDITIONS_MATCH`; sorts `coverage.ordinal` then occurrences desc then recency desc; caps at `maxSuggestions`.
- [x] 5.4 Write `RecurringNotificationSuggesterTest`: below-`minOccurrences` excluded; same-day burst excluded by `minDistinctDays`; dismissed key excluded; `UNCOVERED` outranks `APP_ONLY`; `CONDITIONS_MATCH` suppressed entirely (not just deprioritized); occurrence-count tiebreak; `maxSuggestions` cap; time-range coverage judged at the notification's own timestamp, not wall-clock now.

## Phase 6: HomeViewModel + Contract

- [x] 6.1 Create `features/home/contract/HomeContract.kt`: `HomeUiState` (monitoring, weekStats, recentActivity, section, isLoading), `MonitoringStatus`, `WeekStats`, `HomeSection` sealed interface (`StarterRules`, `Recurring`, `None`), `RecurringSuggestionUi` (feature-owned mapping of `core.notification.RecurringSuggestion`, mapped at the ViewModel boundary per architectureCheck rule 7), events including a screen-resume event.
- [x] 6.2 **Confirm and reuse, do not invent**: before implementing the screen-resume event, read how Inbox/Settings currently refresh their permission banner (`NotificationListenerStatusProvider.isEnabled()` + resume hook) and mirror that exact idiom for `HomeEvent`'s resume handling — this is an explicitly open design question, resolve it against existing code, not a new pattern.
- [x] 6.3 Create `features/home/viewmodel/HomeViewModel.kt`: three intermediate `combine()`s (status / stats / suggestions — `kotlinx.coroutines.combine` caps at 5 typed sources) feeding one terminal `combine(status, stats, suggestions)`; section-selection table (`ruleCount == 0` → `StarterRules`; `ruleCount > 0 && suggestions non-empty` → `Recurring`; else → `None`) computed once in the terminal combine, never in UI; dismiss-suggestion event calls `SuggestionDismissalRepository.dismiss(...)`.
- [x] 6.4 Write `HomeViewModelTest`: section switching across all three rows of the selection table; dismiss event calls repo and suggestion disappears from the next emission; listener-status refresh on resume; effects (navigate to rule editor / notification detail / Inbox). MockK repos + Turbine + injected `StandardTestDispatcher` (ADR 008).

## Phase 7: UI

- [x] 7.1 Create `features/home/ui/HomeScreen.kt` + section composables: monitoring status banner, Starter Rules cards (reusing `RuleTemplatePickerSheet` for "See more templates"), This Week stats row, Recent Activity feed (rows open `features/notificationdetail`, "See all" pushes stacked Inbox), Recurring suggestions section ("Create rule from this" / "Skip similar").

## Phase 8: Navigation Wiring

- [x] 8.1 Modify `core/ui/navigation/Screen.kt`: add `data object Home : Screen()`.
- [x] 8.2 Modify `core/ui/navigation/Routes.kt`: add `fun home(): Screen = Screen.Home`.
- [x] 8.3 Modify `core/ui/navigation/MainBottomNav.kt`: replace `INBOX` entry with `HOME("Home", Icons.Default.Home)`. **`AppDestinations.RULES` currently also uses `Icons.Default.Home` — this is an icon collision, not cosmetic-only debt.** Pick a replacement icon for `RULES` (design.md suggests `Icons.AutoMirrored.Filled.Rule` or `Icons.Default.Checklist`) and confirm it renders sensibly next to the other three tab icons before committing to it. Also relax `selectedDestination` to nullable (D7).
- [x] 8.4 Modify `features/inbox/ui/InboxScreen.kt`: pass `selectedDestination = null` (fixes the compile break caused by `AppDestinations.INBOX` disappearing in 8.3) and add a back affordance, since Inbox is now a stacked screen with no current tab.
- [x] 8.5 Modify `MainActivity.kt`: add `entry<Screen.Home>`; change `AppFlowState.MAIN_APP -> Routes.inbox()` (currently line ~158) to `Routes.home()`.
- [x] 8.6 Modify `features/appselection/viewmodel/AppSelectionViewModel.kt`: change `navigateToMainApp()`'s `clearAndNavigate(Routes.inbox())` to `clearAndNavigate(Routes.home())`.
- [x] 8.7 Modify `AppSelectionViewModelTest.kt` (`features/appselection/viewmodel/`): update the existing `coVerify { navigationHandler.clearAndNavigate(Routes.inbox()) }` assertion to `Routes.home()`.

## Phase 9: Strings (ADR 014)

- [x] 9.1 Add Home-screen strings (banner states, Starter Rules CTAs, stats row labels, Recent Activity labels/empty state, suggestion CTAs) to `app/src/main/res/values/strings.xml` and every existing locale variant per `docs/guides/common-patterns.md`'s string-adding process.

## Phase 10: Remaining Tests

- [x] 10.1 Write `SuggestionDismissalRepositoryImplTest`: round-trip mapping; re-dismiss replaces rather than duplicating (composite PK); DAO exception maps to `Result.failure(Failure...)`, never a raw throwable (rule 6).
- [x] 10.2 Write a Room instrumented test (in-memory DB): `suggestion_dismissals` composite-PK `REPLACE` semantics; the three new windowed/joined DAO queries (2.4, 2.5) return expected rows.

## Phase 11: Docs Sync (`docs/capabilities.md`)

- [x] 11.1 Add `### Home Dashboard` section immediately before `### Notification Inbox & Detail`: monitoring status banner, Starter Rules cards for zero-rule users, This Week row, Recent Activity feed, "See all" pushing Inbox. System trigger: "User opens the app."
- [x] 11.2 Add `### Recurring Notification Suggestions` section, stated in user terms per design.md's exact wording (4 times over 2 weeks, 2 different days, no covering rule, "Skip similar" hides permanently).
- [x] 11.3 Amend line ~98: "User opens the app to the Inbox (its home screen)" → "User opens the app to the Home dashboard; the Inbox opens from Home's 'See all' or from a recent-activity row."
- [x] 11.4 Amend line ~128 (Data Browser nav description): "between Inbox and Rules" → "between Home and Rules".

## Phase 12: Final Gate

- [x] 12.1 Run `./gradlew spotlessApply detekt architectureCheck test` and confirm all green — watch `HomeViewModel`'s terminal combine for detekt `CyclomaticComplexMethod`/`LongMethod`, and architectureCheck rules 1 (visibility), 2 (dispatcher injection), 3 (effect collection), 7 (contract purity).
