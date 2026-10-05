# home-dashboard Specification

## Purpose

Defines the Home launch screen that replaces Inbox as the first bottom-nav destination: it surfaces monitoring health, a path to the first rule, weekly activity, and recent rule executions, so the app answers "is this working, what should I do next?" on open — without touching Inbox's own code or reachability.

## Requirements

### Requirement: Home replaces Inbox in the first bottom-nav slot
`AppDestinations.HOME` SHALL occupy the first bottom-nav slot previously held by `AppDestinations.INBOX`. Inbox's screen, ViewModel, and contract code SHALL remain unmodified; only its nav-bar entry point moves.

#### Scenario: App launches to Home
- GIVEN a user opens the app
- WHEN the initial screen renders
- THEN Home is shown and Home occupies the first bottom-nav slot

#### Scenario: Inbox remains fully functional when reached
- GIVEN Inbox is no longer a bottom-nav destination
- WHEN the user navigates to Inbox via any remaining entry point
- THEN Inbox renders and behaves exactly as before this change

### Requirement: Loading and error states
Home SHALL show a centered progress indicator (with an accessibility description) until its first data emission, and SHALL render no banner, stats, or activity while loading. IF observing the underlying data fails, Home SHALL show an error message with a "Retry" action instead of content; "Retry" SHALL return Home to the loading state and re-observe the data.

#### Scenario: Loading hides content
- GIVEN Home has not yet received its first data emission
- WHEN Home renders
- THEN only the progress indicator is shown, with no access-off banner, stats, or activity

#### Scenario: Observation failure shows error with retry
- GIVEN observing Home data fails
- WHEN Home renders
- THEN an error title, message, and "Retry" button are shown and no content is shown

#### Scenario: Retry recovers
- GIVEN Home shows the error state
- WHEN the user taps "Retry" and the data emits successfully
- THEN Home shows a loading indicator and then its normal content with no error

### Requirement: Monitoring status banner
Outside the first-run checklist, Home SHALL display a monitoring banner whose state is derived from the notification listener status, the enabled monitored apps (`SelectedAppRepository`), and the rule count (`RuleRepository`). The listener status SHALL be refreshed each time Home resumes, and a failure to read it (e.g. `SecurityException`) SHALL be treated as "access off" without crashing.

#### Scenario: Access off shows a warning with an enable action
- GIVEN notification access is not granted
- WHEN Home renders outside first run
- THEN a warning banner explains access is off and offers an "Enable access" action that opens the system notification-access settings

#### Scenario: Access on with zero monitored apps shows a warning
- GIVEN notification access is granted and zero apps are enabled for monitoring
- WHEN Home renders outside first run
- THEN a warning banner explains nothing is being captured and offers a "Choose apps" action that opens app selection

#### Scenario: Access on with apps shows active monitoring
- GIVEN access is granted, 3 apps are monitored, and 5 rules exist
- WHEN Home renders
- THEN the banner shows "Monitoring active" with "3 apps monitored" and "5 rules", and tapping it opens app selection

#### Scenario: Rules part omitted until a rule exists
- GIVEN access is granted, apps are monitored, and zero rules exist
- WHEN the active banner summary is rendered
- THEN only the monitored-app count is shown

### Requirement: Battery-optimization hint
Home SHALL show a battery-optimization hint card when notification access is on, the app is NOT exempt from battery optimizations, and Home is past first run. The exemption state SHALL be read in the UI layer and refreshed on each resume. The hint's action SHALL open the system battery-optimization settings list.

#### Scenario: Hint shown when optimized
- GIVEN access is on, the app is subject to battery optimization, and the user is past first run
- WHEN Home renders
- THEN the hint card is shown with an action opening the battery-optimization settings

#### Scenario: Hint hidden otherwise
- GIVEN the app is exempt from battery optimization, OR access is off, OR Home is in first run
- WHEN Home renders
- THEN the hint card is not shown

### Requirement: First-run checklist replaces the banner and offers templates
WHEN the user has zero rules, Home SHALL show a "Get started" checklist INSTEAD of the monitoring banner, with three steps: notification access, monitored apps, and first rule. Steps 1 and 2 SHALL be derived from the current monitoring status (no persisted state), show completion, and offer an action (enable access / choose apps) while incomplete. Step 3 SHALL be the current step, SHALL NOT be gated on steps 1-2, and SHALL host curated starter templates from `core/rulesharing/RuleTemplates` (first three), a "See more templates" action, and a "Create from scratch" action. A progress indicator SHALL show completed steps out of 3. First run is defined as zero rules, zero weekly stats, and no recent activity; in first run the stats row and recent activity SHALL be hidden.

#### Scenario: Zero-rule user sees the checklist
- GIVEN the user has no rules
- WHEN Home renders
- THEN the checklist is shown instead of the monitoring banner, with starter template cards sourced from `RuleTemplates`

#### Scenario: Checklist reflects access and apps status
- GIVEN access is granted and no app is monitored
- WHEN the checklist renders
- THEN step 1 is shown as done, step 2 shows a "choose apps" action, and progress reads 1 of 3

#### Scenario: Creating from a starter template opens a pre-filled editor
- GIVEN the checklist is visible
- WHEN the user taps a template card
- THEN the rule editor opens pre-filled from that template's asset

#### Scenario: "See more templates" opens the templates screen
- GIVEN the checklist is visible
- WHEN the user taps "See more templates"
- THEN the templates screen (`Routes.ruleTemplates()`) opens as a stacked screen

#### Scenario: Create from scratch opens an empty editor
- GIVEN the checklist is visible
- WHEN the user taps "Create from scratch"
- THEN the rule editor opens with no rule id and no source notification, via a single `NavigateToRuleEditor` effect

#### Scenario: Checklist is absent once a rule exists
- GIVEN the user has at least one rule
- WHEN Home renders
- THEN the checklist is not shown and the monitoring banner is shown instead

### Requirement: "New rule" button
Home SHALL show a persistent "New rule" floating button that opens the templates screen (`Routes.ruleTemplates()`, where "start from scratch" is also available). The button SHALL be hidden while loading, in the error state, and while the first-run checklist (StarterRules section) is shown, since the checklist already hosts the create actions.

#### Scenario: Button visible for users with rules
- GIVEN Home has loaded without error and the user has at least one rule
- WHEN Home renders
- THEN the "New rule" button is shown and opens the templates screen when tapped

#### Scenario: Button hidden while loading, on error, or during first run
- GIVEN Home is loading, OR in the error state, OR showing the first-run checklist
- WHEN Home renders
- THEN the "New rule" button is not shown

### Requirement: "This Week" stats row
Outside first run, Home SHALL show a stats row with two tiles: extracted-record count ("Records") and rules-fired count ("Rules fired"), each computed over the current calendar week (Monday start) in the device's local timezone, derived in-memory from existing repository flows (no new aggregate stored). A one-line weekly summary SHALL be shown only when rules fired is greater than zero. The week window SHALL be recomputed on resume once the calendar day changes. Home SHALL NOT show an apps-active tile.

#### Scenario: Stats reflect current week's activity
- GIVEN 8 records were extracted and rules fired 12 times this calendar week
- WHEN Home renders the stats row
- THEN it shows 8 for Records and 12 for Rules fired

#### Scenario: No activity this week yields zero values, not an error
- GIVEN no rule executions occurred in the current calendar week and the user has at least one rule
- WHEN Home renders
- THEN both tiles show 0 and no weekly summary line is shown

#### Scenario: Week window rolls over
- GIVEN Home is open across a Monday boundary
- WHEN Home resumes on the new week
- THEN the stats are re-observed with the new week start

### Requirement: Recent Activity feed
Outside first run, Home SHALL show a "Recent Activity" feed of at most the 5 most recent rule executions in reverse-chronological order, joined from rule executions, the source notification, source app, and rule name. Each row SHALL show the rule name, and the notification content (subtitle) and app name when available. Tapping a row SHALL open that notification in `features/notificationdetail`. The feed SHALL always show a "See all" action; tapping it SHALL push Inbox as a new stacked navigation screen (not a bottom-nav destination). When there are no executions, the feed SHALL show an empty state with a hint instead of an error.

#### Scenario: Recent row opens notification detail
- GIVEN the Recent Activity feed shows a row for a rule execution
- WHEN the user taps that row
- THEN `features/notificationdetail` opens for the row's source notification

#### Scenario: Feed is limited to 5 rows
- GIVEN more than 5 rule executions exist
- WHEN Home renders
- THEN at most 5 rows are shown

#### Scenario: "See all" pushes Inbox as a stacked screen
- GIVEN the Recent Activity feed is visible
- WHEN the user taps "See all"
- THEN Inbox opens as a stacked navigation screen, and the bottom nav still shows Home as selected/available

#### Scenario: Empty feed is shown gracefully
- GIVEN no rule executions have occurred yet and the user has at least one rule
- WHEN Home renders
- THEN the Recent Activity feed shows an empty state instead of an error, and "See all" remains visible

### Requirement: Recurring-notification suggestions are surfaced on Home
WHEN suggestions are available from the `recurring-notification-suggestions` capability, Home SHALL display them (in the active, non-empty state) offering "Create rule from this" and "Skip similar" per suggestion, per that capability's own requirements.

#### Scenario: Suggestions appear when available
- GIVEN the recurring-notification heuristic has produced at least one non-dismissed suggestion
- WHEN Home renders in its active (has-rules) state
- THEN the suggestion(s) are shown with both actions available

#### Scenario: Skipping a suggestion dismisses it
- GIVEN a suggestion is shown
- WHEN the user taps "Skip similar"
- THEN the dismissal is persisted and the suggestion disappears on the next emission

#### Scenario: Dismiss failure is surfaced
- GIVEN persisting a dismissal fails
- WHEN the user taps "Skip similar"
- THEN a transient error message is shown and Home does not crash

#### Scenario: Create rule from a suggestion
- GIVEN a suggestion is shown
- WHEN the user taps "Create rule from this"
- THEN the rule editor opens pre-filled from the suggestion's sample notification
