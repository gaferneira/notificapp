# Notificapp — Screen-by-Screen UX Audit

Scope: all screens in `dev.gaferneira.notificapp.features` (code-based audit).
Screens covered: Onboarding, App Selection, Inbox, Notification Detail, Rules, Rule Editor, Settings.

---

### Onboarding Screen Usability Breakdown

`features/onboarding/ui/OnboardingScreen.kt`

> **Implemented (2026-07-10):**
> - Replaced the fake `Switch`-shaped decorative toggle in `PermissionToggleCard` with a non-interactive "Enabled in system settings" status chip (icon + label), so nothing on the card looks tappable except the real "Grant Access" button.
> - Added `showPermissionDeniedHint` to `OnboardingContract.UiState`. `OnboardingViewModel` now tracks whether the user actually opened system settings (`hasRequestedNotificationAccess`) and, on the next `CheckPermission` (fired on `ON_RESUME`), surfaces an inline `PermissionDeniedHint` error card ("Access not enabled yet — tap Grant Access to try again.") above the Grant Access button if they returned without granting.
> - Removed the 120dp placeholder gear illustration in `PermissionToggleCard` — dead space between the toggle and the value bullets, replaced by tightening the card to a single row.
> - Unified the footer casing to sentence case ("Processed locally on your device.") on both onboarding steps (Value Statement previously used ALL CAPS).
> - Added merged semantics with a `contentDescription` combining title + subtitle on each `NotificationCardRow` in the preview cards group, so TalkBack announces the value proposition.
> - Verified `UiEffect.NavigateToMainApp`'s empty collector branch is intentional — navigation is actually performed by `OnboardingViewModel.navigateToMainApp()` via `NavigationHandler.clearAndNavigate`, not stranded.
> - New preview `OnboardingScreenPermissionDeniedPreview` added for the denied-hint state.
> - Not done: no automated tests exist yet for `OnboardingViewModel` (matches current project-wide test debt) — consider adding coverage for the `showPermissionDeniedHint` transition.

1. **Core Usability & Flow Assessment**

- **Primary action clarity:** Strong. The Value Statement step has a single full-width "Get Started" button anchored at the bottom, a bold headline, and no competing actions. The Permission step ends in one "Grant Access" CTA. Both steps pass the 2-second test.
- **Cognitive load:** Light overall, but the Permission step carries one dangerous element: `PermissionToggleCard` renders a **decorative toggle that looks fully interactive** (styled exactly like an "on" Material switch). Users will tap it, nothing happens, and the real switch is in system settings. A fake control is worse than no control — it teaches distrust right before the highest-friction moment of the whole app (granting `NotificationListener` access).
- **Eye flow:** Good vertical rhythm on both steps: header → visual → headline → body → CTA. The three `PermissionDetailItem` rows correctly front-load the "why" before the ask. The 120dp "illustration area" containing only a faded gear icon is dead space that interrupts the flow between the (fake) toggle and the real value bullets.

2. **Actionable Iterations**

- **High Impact:**
  - Replace the decorative switch in `PermissionToggleCard` with a non-interactive state representation: a small `AssistChip`/`Surface` reading "Enabled in system settings" with a settings glyph, or remove the toggle row entirely and let "Grant Access" be the only actionable element. Never render a `Switch`-shaped element that doesn't respond.
  - After returning from system settings without the permission granted (`ON_RESUME` + `CheckPermission` already exists), show explicit feedback — a `SnackbarHost` message or an inline error card ("Access not enabled yet — tap Grant Access to try again"). Today a failed grant returns the user to a screen that looks identical, with zero acknowledgment.
- **Medium Impact:**
  - Replace the 120dp placeholder gear `Surface` with either a short illustration of the actual system settings toggle the user is about to see (reduces disorientation in the settings deep-dive) or delete it and tighten the layout.
  - `UiEffect.NavigateToMainApp` is an empty branch in the collector — verify the ViewModel navigation actually fires; a silent no-op effect is a stranded user at the end of onboarding.
- **Low Impact:**
  - The "PROCESSED LOCALLY ON YOUR DEVICE" footer appears in ALL CAPS on step 1 and sentence case on step 2 ("Processed locally on your device."). Pick one treatment.
  - Add `contentDescription` on the notification preview cards group (currently all `null`) so TalkBack users get the value proposition too.

---

### App Selection Screen Usability Breakdown

`features/appselection/ui/AppSelectionScreen.kt`

> **Implemented (2026-07-10):**
> - `PageIndicator` now takes `currentStep`/`totalSteps` parameters instead of hardcoding 4 dots with the 3rd active; call site passes the real onboarding shape (`ONBOARDING_TOTAL_STEPS = 3`, `ONBOARDING_STEP_APP_SELECTION = 2` — Value Statement → Permission Explanation → App Selection).
> - Wired `UiEffect.ShowError` to a `SnackbarHostState` in the `Scaffold`, so app-list load/selection failures are now visible instead of silently swallowed.
> - Fixed the selection-count banner layout jump: it now lives inside a fixed-height `Box` (`SELECTION_BANNER_HEIGHT = 40.dp`) and cross-fades with `AnimatedVisibility` instead of collapsing to zero height, so toggling the first app no longer pushes the search field and list up/down.
> - Added a "Select at least one app to continue" helper line under the Continue/Save button when nothing is selected, instead of a silently disabled button.
> - Removed the stale KDoc on `AppSelectionScreen` documenting `onNavigateToMainApp`/`onNavigateBack`/`onShowError` params that no longer exist on the composable.
> - Not done: async icon loading via Coil (requires a custom `Fetcher` for `PackageManager` app icons — larger lift, deferred) and checkbox toggle semantics on `AppListItem` — both low-impact, left for a follow-up pass.

1. **Core Usability & Flow Assessment**

- **Primary action clarity:** Good. "Select Data Sources" headline + tappable app cards + bottom "Continue" button communicate the task immediately in initial-setup mode. Settings mode ("Save (N selected)") is equally clear.
- **Cognitive load:** The heaviest issue is the `PageIndicator`: it hardcodes **4 dots with the 3rd active**, but onboarding only has Value → Permission → App Selection. If the flow is 3 steps, this lies about progress; either way, hardcoded progress state is a maintenance trap and a trust leak. Second: the "N apps selected" banner appears/disappears above the search field, pushing the entire list down and up as the user toggles the first app — layout jump exactly where their finger is.
- **Eye flow:** Solid: title → count → search → list → CTA. The disabled Continue button gives no hint about *why* it's disabled when nothing is selected.

2. **Actionable Iterations**

- **High Impact:**
  - Fix `PageIndicator` to receive `currentStep`/`totalSteps` as parameters driven by real navigation state, or remove it. Wrong progress indicators are worse than none.
  - `UiEffect.ShowError` is an empty `when` branch — selection persistence failures are currently invisible. Wire a `SnackbarHost` into the `Scaffold` and surface it.
- **Medium Impact:**
  - Reserve fixed vertical space for the selection-count banner (`AnimatedVisibility` with a fixed-height slot, or move the count into the Continue button label as already done in settings mode: "Continue (3 selected)") to kill the layout jump.
  - When Continue is disabled, show a one-line helper under it ("Select at least one app to continue") instead of a silent dead button.
  - The KDoc documents `onNavigateToMainApp`/`onNavigateBack`/`onShowError` params that no longer exist on the composable — stale contract documentation misleads the next navigation change.
- **Low Impact:**
  - App icons load synchronously via `packageManager.getApplicationIcon(...)` inside `remember` on the composition path — with many apps this can jank the first frame of each row. Move to Coil (already a project dependency) or `produceState` on a background dispatcher.
  - The custom checkbox `Surface` has no toggle semantics; add `Modifier.semantics { role = Role.Checkbox }` / `toggleable` on the row for accessibility.

---

### Inbox Screen Usability Breakdown

`features/inbox/ui/InboxScreen.kt`

> **Implemented (2026-07-10):**
> - Re-mapped status semantics: unprocessed notifications (the normal state for a new user) no longer get a red bar/dot. Only processed notifications get a positive `tertiary`-colored "N rules" badge with a `CheckCircle` icon; the hardcoded `Color(0x802B962B)` green and the 2dp left status bar are both gone (one status encoding per card instead of two).
> - Wired `InboxEffect.ShowError` to a `SnackbarHostState` in the `Scaffold`.
> - Replaced `InboxEffect.ShowPermissionRequired` (a one-shot effect that couldn't model a persistent condition) with `InboxUiState.isNotificationListenerActive`, checked in `InboxViewModel` on `init` and on a new `InboxEvent.OnResume` fired from `ON_RESUME` (mirrors the Onboarding/App Selection pattern). When false, a persistent `PermissionRequiredBanner` ("Notification access disabled — Notificapp can't see notifications") appears above the list with an "Enable" button that opens system notification-listener settings.
> - Made the empty state actionable and honest: it now distinguishes "no notifications yet" (listener active, nothing captured) from "notification access is disabled" (with an "Enable access" button), instead of one static two-line message that always suggested checking settings.
> - Added a filter-active `Badge` on the `Tune` icon (`BadgedBox`), matching the pattern already used on `RulesScreen`.
> - Extracted a shared `dev.gaferneira.notificapp.util.isNotificationListenerEnabled(context)` / `openNotificationListenerSettings(context)` utility — this exact check was previously duplicated three times (`MainActivity`, `OnboardingViewModel`, `SettingsViewModel`); all three now call the shared function. `InboxViewModel` uses the same helper.
> - Split `InboxScreenContent` (was 101 lines, over the detekt `LongMethod` budget) into `InboxTopBar` and `InboxSearchField` sub-composables, and dropped its `modifier` parameter (unused after extraction) to stay under the `LongParameterList` threshold — required by the project's boy-scout baseline policy (TD-16) since this PR meaningfully touches the file. `config/detekt/baseline.xml` shrank accordingly.
> - Not done: collapsing the always-visible search field into the app bar, and moving app-icon loading to Coil (both Medium/Low impact, larger redesigns) — deferred.
> - Found but out of scope: `app/src/test/kotlin/.../ruleeditor/viewmodel/ExtractDataViewModelTest.kt` fails to compile on a clean `main` checkout (unrelated to this audit, pre-existing), which currently blocks running the full unit test suite via `./gradlew test`. Flagged separately for the team; `./gradlew detekt` and `:app:compileDebugKotlin` both pass clean.

1. **Core Usability & Flow Assessment**

- **Primary action clarity:** Moderate. "Inbox / Live Notification Feed" plus the list reads instantly as "here are your captured notifications." But the screen's *purpose in the product* — "tap a notification to inspect it and build a rule from it" — is not signaled anywhere. Cards look informational, not actionable.
- **Cognitive load:** The status color language is the heaviest problem. Unprocessed notifications get `colorScheme.error` (red) on both the left bar and the status dot. **A notification with no matching rule is the normal state, not an error** — a new user's inbox is a wall of red, which reads as "everything is broken." The processed state uses a hardcoded `Color(0x802B962B)` that ignores the theme and dark mode. Each card also carries two status encodings (left bar + right circle icon) saying the same thing.
- **Eye flow:** Search → list → card is fine. Time headers help. But the filter (`Tune`) icon in the top bar gives no signal when filters are active, so a filtered-down list can silently look like "no notifications."

2. **Actionable Iterations**

- **High Impact:**
  - Re-map status semantics: unprocessed = neutral (`onSurfaceVariant` dot or simply *no* indicator), processed = positive accent (theme `tertiary`/`primary`) with the matched-rule count. Reserve red for actual failures. Replace `Color(0x802B962B)` with a theme color.
  - `InboxEffect.ShowError` and `ShowPermissionRequired` are `TODO` no-ops. The permission one is critical: if listener access is revoked, the inbox silently stops filling and the empty state lies. Add a persistent inline warning banner (a `Card` above the list) with a "Enable access" button when permission is missing.
  - Make the empty state actionable: replace the two-line text with icon + copy + a `Button` that deep-links to notification-access settings (or to App Selection if permission is fine but no apps are monitored — distinguish the two causes).
- **Medium Impact:**
  - Add a filter-active `Badge` on the `Tune` icon (`BadgedBox`, exactly as `RulesScreen` already does) plus a dismissible "Filtered" chip row above the list so filtered emptiness is explained.
  - Drop one of the two per-card status encodings. Keep the compact right-side dot with rule count; remove the 2dp left bar (at 2dp it's nearly invisible anyway).
  - Collapse the search field into the app bar (`SearchBar` or an expanding icon) — permanent search costs ~72dp of prime viewport on a screen whose value is list density.
- **Low Impact:**
  - Same synchronous icon-loading concern as App Selection — jank on fast scroll; move to Coil with `AsyncImage`.
  - `EmptyState` text uses `\n` inside a centered `Text` without `textAlign = TextAlign.Center`, so the second line left-aligns awkwardly.
  - Show the matched-rule count visibly (e.g., "2 rules" micro-label) rather than only in `contentDescription`.

---

### Notification Detail Screen Usability Breakdown

`features/notificationdetail/ui/NotificationDetailScreen.kt`

> **Implemented (2026-07-10):**
> - The error-state Retry button was a literal no-op (`onRetry = { /* Reload */ }`). Added `UiEvent.OnRetryClicked` → `NotificationDetailViewModel.loadNotificationAndExecutions()` (a plain reload) and wired it — kept distinct from `OnRefreshClicked`, which actually deletes and re-evaluates executions (confirmed by reading `refreshExecutions()`), so a load-failure retry no longer silently re-runs rules.
> - Renamed the title from "Executions - {AppName}" to just the app name (falling back to "Notification"), and the section header from "RULE EXECUTIONS" to "MATCHED RULES" — both were leaking internal rule-engine vocabulary.
> - "Refresh Rules" was a bare icon in the top bar performing a real destructive action (delete + re-run). Promoted it to a labeled `TextButton("Re-run rules")` next to the "Matched rules" section header, disabled while a refresh is in flight (`isRefreshing = uiState.isLoading`, true only once the notification has already loaded).
> - `EmptyExecutionsState` now includes "Create a rule to extract data from notifications like this" plus its own `TextButton("Create Rule")`, instead of leaving the fix (the FAB) visually disconnected from the empty message.
> - Raw content is no longer always suffixed with "…" even when under 200 chars. Extracted `RawContentSection`: shows the full text under 200 chars, and adds a "Show more"/"Show less" toggle above that length instead of a fixed unreadable truncation.
> - Fixed the app icon overflowing its container (56dp `Image` inside a 48dp `Surface`) — now 40dp, matching the icon-in-container pattern used elsewhere.
> - Added a merged `contentDescription` ("{action name} succeeded/failed/skipped/no outcome data") on the ✓/✗/— action-outcome glyphs in `ActionChip`, which previously relied on color + symbol alone.
> - Not done: demoting the field-type chips (STRING/CURRENCY/…) to a subtler badge — left as-is since it's a visual-only nice-to-have, not a functional bug.

1. **Core Usability & Flow Assessment**

- **Primary action clarity:** Good, thanks to the `ExtendedFloatingActionButton` "Create Rule" — the single most important CTA in the product's core loop (see notification → build rule) is unmistakable. This is the best-focused screen in the app.
- **Cognitive load:** The title "Executions - {AppName}" leads with internal engine vocabulary. A user navigating from a WhatsApp message expects "Notification" or the app name; "Executions" only makes sense after they've internalized the rule engine. The "RULE EXECUTIONS / N matched" section header repeats the jargon. The Refresh action ("Refresh Rules") is ambiguous — does it reload the screen or re-run rules against this notification? If it re-evaluates, that's a meaningful action hidden behind a generic icon.
- **Eye flow:** Correct: notification card first, then per-rule execution cards with extracted fields and action chips. Field type chips (STRING/CURRENCY/…) add developer-facing noise to every row.

2. **Actionable Iterations**

- **High Impact:**
  - The error state's Retry button is a no-op: `onRetry = { /* Reload */ }`. A visible button that does nothing is a hard trust breaker — wire it to re-emit the load event.
  - Rename the title to the app name (with "Notification" fallback) and the section to "Matched rules". If Refresh re-runs rules, promote it to a labeled `TextButton`/`OutlinedButton` "Re-run rules" near the section header instead of a bare icon in the top bar.
- **Medium Impact:**
  - Improve `EmptyExecutionsState`: it says "No rules matched" but offers no next step, while the fix (Create Rule) floats disconnected in the FAB. Add a short "Create a rule to extract data from notifications like this" line pointing at the primary action.
  - The raw-content preview always appends "..." (`rawContent.take(200) + "..."`) even when content is under 200 chars, and long raw payloads are unreadable inline. Use an expandable section (`Text` with `maxLines` + "Show more" toggle).
- **Low Impact:**
  - App icon `Image` is 56dp inside a 48dp `Surface` — it overflows its rounded container; align the sizes.
  - Demote the field-type chips to a subdued single-letter/short badge or show type only on tap; name + value is what users scan.
  - Action outcome glyphs (✓ ✗ —) rely on color+symbol only; add `contentDescription` for the outcome.

---

### Rules Screen Usability Breakdown

`features/rules/ui/RulesScreen.kt`

> **Implemented (2026-07-10):**
> - The error-state "retry" was `IconButton(Icons.Default.Add)` — a plus icon meaning "retry loading rules." Replaced with `Button(onClick = onRetry) { Text("Retry") }`, matching every other screen.
> - Replaced the hand-rolled circular `IconButton` FAB with `ExtendedFloatingActionButton(icon = Add, text = "New rule")` — proper elevation, standard pressed states, and a label for the app's core creation action.
> - "Import rule" moved out from behind an opaque `FileDownload` icon: the top-bar entry point is now `Icons.Default.MoreVert` ("More options"), a universally recognized affordance, opening the existing labeled menu ("Import from file" / "Import from clipboard").
> - Removed `RulesEffect.ShowDeleteConfirmation` — verified there is no rule-delete feature anywhere in this codebase (`rg` for `deleteRule`/`OnRuleDelete` turned up nothing), so the effect was dead scaffolding with an empty `// Could show a confirmation dialog here` handler, not a live data-loss bug. Deleted rather than implementing a dialog for a feature that doesn't exist yet.
> - Migrated `ShowError`/`ShowSuccess` from `Toast.makeText(...)` to a `SnackbarHostState` in the `Scaffold`, for consistency/theming with the rest of the app.
> - Fixed the double-label bug: multi-app rules rendered "App: Apps: X, Y, Z" (`RuleCardInfo` prefixes `"App: "` onto a value that already started with `"Apps: "`). Now computes a single label — the app name for 1 app, `"All apps"` for none, and `"N apps"` (not a raw joined list) for multiple — fixing the duplicate prefix and the audit's separate "ellipsize long lists" ask in one change.
> - Split `RulesTopBar`, `RulesImportMenu`, `RulesBody`, and `RulesImportDialogs` out of `RulesScreenContent` (had grown past the detekt `LongMethod`/`LongParameterList` budget after adding the snackbar host) — same boy-scout-baseline cleanup applied to the other screens.
> - Not done: moving per-card export off the primary tap target (Low impact) and switching previews from `MaterialTheme` to `NotificappTheme` (cosmetic, doesn't affect the real app) — deferred.

1. **Core Usability & Flow Assessment**

- **Primary action clarity:** Mostly good — the circular "+" bottom-right reads as "create rule," and the empty state explicitly says "Tap + to create your first rule." However the FAB is hand-rolled as an `IconButton` with a circular background rather than a real `FloatingActionButton`: no elevation/shadow, weaker discoverability, and non-standard pressed states.
- **Cognitive load:** The top bar packs three icon-only actions (filter, import, plus the per-card share/switch below). The import icon (`FileDownload`) is particularly opaque — importing a *rule* is a rare, conceptual action hidden behind an ambiguous glyph. Rule cards are clean; the dry-run badge plus import preview dialog copy is genuinely excellent safety UX.
- **Eye flow:** Search → grouped list → FAB is coherent. Group headers (category/status) with counts work well. One sharp defect: the **error state's "retry" button renders `Icons.Default.Add`** — a plus icon that retries loading is actively misleading.

2. **Actionable Iterations**

- **High Impact:**
  - Replace the error-state `IconButton(Icons.Default.Add)` with `Button(onClick = onRetry) { Text("Retry") }` (the pattern every other screen already uses).
  - Swap the hand-rolled FAB for `ExtendedFloatingActionButton(text = { Text("New rule") }, icon = { Icon(Icons.Default.Add, …) })` — for a rules app, rule creation deserves a labeled FAB, matching NotificationDetail.
- **Medium Impact:**
  - Move "Import rule" out of the icon-only top bar: either an overflow menu item labeled with text, or an entry in the empty state ("…or import a rule"). Rare actions deserve words, not glyphs.
  - `RulesEffect.ShowDeleteConfirmation` is an empty branch — if any path deletes without confirmation, that's data loss; either implement the dialog or remove the dead effect.
  - Errors/successes arrive as `Toast`; migrate to `SnackbarHost` in the `Scaffold` for consistency, theming, and testability.
- **Low Impact:**
  - Multi-app rules render "App: Apps: X, Y, Z" (`RuleCardInfo` prefixes "App: " onto an `appName` that already starts with "Apps: "). Fix the double label and ellipsize long lists ("3 apps").
  - Per-card `Switch` + share icon + card click gives three targets in one row; consider moving export into a long-press/overflow on the card to reduce accidental taps next to the toggle.
  - Previews use `MaterialTheme` instead of `NotificappTheme`, so they don't reflect real branding.

---

### Rule Editor Screen Usability Breakdown

`features/ruleeditor/ui/RuleEditorScreen.kt` (+ `WhenSection`, `DoSection`, bottom sheets)

> **Implemented (2026-07-10):**
> - Wired `UiEffect.ShowSuccess`/`ShowError` to a `SnackbarHostState` (both were empty collector branches — a successful or failed Save produced zero visible feedback). Confirmed via `RuleEditorViewModel` that navigation-back on success already happens through `navigationHandler.goBack()` independent of the effect, so this was purely a missing-feedback bug, not a missing-navigation one.
> - Added the existing (previously unused) `StepIndicator` component under the top bar, so Continue → Save reads as a 2-step wizard instead of an unexplained navigation.
> - Retitled step 2 from the verb "Save" to "Name your rule". Step 2's bottom-left button was labeled "Cancel" but fired `OnBackToLogicClicked` (navigates back to step 1, does not abandon the rule) — relabeled to "Back", and moved Save itself into the top bar as a `TextButton` (Material convention for editors), removing the redundant bottom Save button.
> - Added an unsaved-changes confirmation `AlertDialog` ("Discard this rule?") when abandoning step 1 via Cancel or the top-bar back arrow while any conditions or actions are configured — mirrors the existing delete-confirmation pattern, previously step 1 could be abandoned instantly with configured conditions/actions and no warning.
> - Gated "Test against history": now `enabled = !isBacktesting && (triggers.isNotEmpty() || targetApps.isNotEmpty())`, with a "Add a condition or app to test against history" helper line when disabled — previously the first tap could test an empty rule against the entire notification history.
> - Fixed the `AppsCard` trailing icon (`WhenSection.kt`): was `Icons.Default.Add` with `contentDescription = "Edit apps"` next to condition cards whose own trailing icon (×) means "remove" — now `KeyboardArrowRight`, matching the actual tap-to-edit behavior.
> - Changed `RuleUiModel.isDryRun` default from `false` to `true` for new rules, matching the existing default for imported rules (consistency of the safety story) — updated the one test (`RuleEditorViewModelTest`) that asserted the old default.
> - Split `RuleEditorTopBar`, `RuleEditorSteps`, `RuleEditorDialogs`, `TestAgainstHistoryButton`, and `LogicStepBottomActions` out of `RuleEditorScreenContent`/`LogicStep` (both had grown past the detekt `LongMethod`/`CyclomaticComplexMethod` budget after wiring the snackbar and dialogs) — same boy-scout-baseline cleanup as the other screens; `config/detekt/baseline.xml` shrank by 9 entries net.
> - Not done: moving Save's top-bar position relative to Cancel/Continue weight balance on step 1 (already differentiated via filled vs. outlined button, judged sufficient) — no change made there.

1. **Core Usability & Flow Assessment**

- **Primary action clarity:** The When/Do mental model with plain-language section descriptions is strong — the user immediately understands "define trigger, define effect." But the *flow* framing is muddled: this is a 2-step wizard with **no visible step indicator** (a `StepIndicator` component exists in `ui/components/` but is not used here), and the step-2 top-bar title is the word **"Save"** — a verb as a screen title, which reads like a button.
- **Cognitive load:** Heaviest around exits and labels on step 2: the bottom-left button says **"Cancel" but fires `OnBackToLogicClicked`** — it navigates back to step 1, it doesn't cancel the rule. Meanwhile the top-bar back arrow does the same thing. On step 1, "Cancel" (bottom) vs back arrow (top) both abandon the rule with no unsaved-changes guard visible in this layer. Also, `UiEffect.ShowSuccess`/`ShowError` collectors are empty — saving a rule produces **no visible confirmation and no visible failure**.
- **Eye flow:** Step 1 flows well: When → conditions → Do → actions → "Test against history" → Continue. "Test against history" with backtest results in a sheet is a standout feature and is placed exactly where confidence is needed. In `WhenSection`, the apps card's trailing icon is a "+" (`Icons.Default.Add`) but tapping *edits* the app selection — wrong affordance next to condition cards whose trailing icon (×) means *remove*.

2. **Actionable Iterations**

- **High Impact:**
  - Wire `ShowSuccess`/`ShowError` to visible feedback (snackbar on failure; on success, navigate back with a confirmation). A silent Save is the single biggest trust gap in the core creation flow.
  - Relabel step 2's bottom-left button from "Cancel" to "Back" (and make top-bar behavior consistent). Retitle step 2 to "Name your rule" or "Details"; never a verb.
  - Add the existing `StepIndicator` (or a "Step 1 of 2" label) under the top bar so Continue → Save reads as a wizard, not a mystery navigation.
- **Medium Impact:**
  - Change the `AppsCard` trailing icon from `Add` to `Icons.Default.Edit` or `ChevronRight` — the affordance should say "opens picker," matching the `ConditionCard` tap-to-edit pattern.
  - Add an unsaved-changes confirmation (`AlertDialog`) when abandoning step 1 with any conditions/actions configured — the delete flow already has one; discarding an unsaved rule deserves the same.
  - "Test against history" is disabled-less when the rule has no conditions; gate it (`enabled = conditions.isNotEmpty() || targetApps.isNotEmpty()`) with helper text, otherwise the first tap tests an empty rule against everything.
- **Low Impact:**
  - Move Save into the top bar as a `TextButton` on step 2 (Material convention for editors) so thumb and eye don't split between top title and bottom buttons.
  - `DryRunToggle` defaulting deserves a nudge for new users: consider defaulting dry-run ON for newly created rules (imports already do this) — consistency of the safety story.
  - On step 1, Cancel and Continue have equal visual weight (both `weight(1f)`); make Continue visually dominant (filled vs. text button) to guide the next step.

---

### Settings Screen Usability Breakdown

`features/settings/ui/SettingsScreen.kt`

> **Implemented (2026-07-10):**
> - Added `ListenerStatusCard` as the first item on the screen: a green "Notification access active" card when granted, or a red/amber "Notification access disabled" card with an "Enable" button (deep-links to `ACTION_NOTIFICATION_LISTENER_SETTINGS` via the shared `openNotificationListenerSettings` util) when not. `SettingsContract.UiState.isNotificationListenerActive` already existed but was never rendered — this was pure UI wiring plus a new `UiEvent.OnResume` / `ON_RESUME` lifecycle hook (matching Inbox/Onboarding) so the status re-checks after the user returns from system settings.
> - Removed the unused `SettingsContract.UiEffect.NavigateToNotificationSettings` — it was declared but never sent anywhere; the screen now calls the shared util directly instead of routing through a dead effect.
> - `MonitoredAppsCard` now restyles to a primary-tinted call-to-action ("No apps monitored — Select apps to start") when `monitoredAppsCount == 0`, instead of always looking like a neutral settings row.
> - Fixed the double-fire risk on `ToggleSettingItem`: the row now uses `Modifier.toggleable(role = Role.Switch)` as the single source of truth, and the inner `Switch` has `onCheckedChange = null` so it no longer has its own independent tap target overlapping the row's.
> - "Version 1.0.0" is now read from `BuildConfig.VERSION_NAME`.
> - `AboutCard`'s icon tint changed from a hardcoded `Color.White` to `MaterialTheme.colorScheme.onPrimary`.
> - Split `GeneralSettingsCard` out of `SettingsList` (which had grown over the detekt `LongMethod` budget) as part of the same boy-scout-baseline cleanup applied to the other screens this session.
> - Not done: unifying the custom `Surface`-based top app bar here with the `TopAppBar` pattern used on Inbox/Rules — a cross-screen consistency pass, left for a follow-up.

1. **Core Usability & Flow Assessment**

- **Primary action clarity:** Good. "Settings / Manage your app preferences" + sectioned cards (Monitored Apps → General → About) is instantly legible. The Monitored Apps card with count + "Tap to manage apps" + chevron is a textbook navigation row.
- **Cognitive load:** Low — but there is one critical *information* gap: `UiState` carries `isNotificationListenerActive`, and **the UI never renders it**. For an app whose entire function dies when the listener is revoked or killed by the OS, listener health is the most important status on this screen, and it's invisible.
- **Eye flow:** Correct priority order. "Data Collection" toggle subtitle ("Active - monitoring…" / "Paused - not collecting data") is a great state-explaining pattern.

2. **Actionable Iterations**

- **High Impact:**
  - Surface listener status as the first card: green "Notification access active" or a red/amber warning card ("Notification access disabled — Notificapp can't see notifications") with a button that deep-links to `ACTION_NOTIFICATION_LISTENER_SETTINGS`. The state already exists in the ViewModel; this is pure UI wiring with outsized user value.
- **Medium Impact:**
  - When `monitoredAppsCount == 0`, restyle the Monitored Apps card as a call-to-action ("No apps monitored — select apps to start") with primary-container coloring; zero monitored apps means the app is doing nothing.
  - The `ToggleSettingItem` row has both `Modifier.clickable { onCheckedChange(!checked) }` and an inner `Switch` with its own `onCheckedChange` — tapping the switch can double-fire through overlapping targets on some devices. Use `Modifier.toggleable(value = checked, role = Role.Switch, …)` on the row and render the `Switch` with `onCheckedChange = null`.
- **Low Impact:**
  - "Version 1.0.0" is hardcoded; read it from `BuildConfig.VERSION_NAME`.
  - The About icon uses `Color.White` tint on `primary` — use `MaterialTheme.colorScheme.onPrimary` for theme correctness.
  - Non-standard headline-in-surface top bar here vs. `TopAppBar` on Inbox/Rules — align on one app-bar pattern across tabs so tab switches don't feel like app switches.

---

## Cross-Screen Priority Checklist

Ordered for design-iteration planning; items above the line block user success.

1. **Silent effects epidemic (High):** Empty effect collectors across Onboarding (`NavigateToMainApp` fallback), App Selection (`ShowError`), Inbox (`ShowError`, `ShowPermissionRequired`), Rule Editor (`ShowSuccess`, `ShowError`). Adopt one pattern — `SnackbarHost` in each `Scaffold` — and wire every effect. Rule Editor save feedback first.
2. **Dead/wrong buttons (High):** NotificationDetail retry no-op; Rules error-state retry rendered as a "+" icon; Onboarding's fake permission toggle.
3. **Status color semantics in Inbox (High):** unprocessed ≠ error; remove hardcoded green; one status encoding per card.
4. **Listener health visibility (High):** Settings status card + Inbox warning banner, both deep-linking to system settings.
5. **Rule Editor wizard legibility (Medium):** step indicator, "Back" vs "Cancel" labels, non-verb step-2 title, save-in-top-bar.
6. **Progress honesty (Medium):** parameterize or delete the hardcoded App Selection page dots.
7. **Icon-affordance sweep (Medium):** apps-card "+" → edit/chevron; import behind labeled menu; labeled FAB on Rules.
8. **Layout stability & polish (Low):** selection-banner jump, async icon loading via Coil, empty-state text alignment, theme-token consistency (`onPrimary`, no hardcoded colors), toggle-row semantics.
