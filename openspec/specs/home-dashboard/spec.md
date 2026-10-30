# home-dashboard Specification

## Purpose

Defines the Home launch screen that replaces Inbox as the first bottom-nav destination: it surfaces monitoring health, a path to the first rule, last-7-days activity, and recent rule executions, so the app answers "is this working, what should I do next?" on open — without touching Inbox's own code or reachability.

## Requirements

### Requirement: Home replaces Inbox in the first bottom-nav slot
`AppDestinations.HOME` SHALL occupy the first bottom-nav slot previously held by `AppDestinations.INBOX`; only Inbox's nav-bar entry point moves. Reached without route arguments, Inbox SHALL behave as before (its saved filters apply); the optional per-visit `initialStatus` and `since` route arguments are used only by the stats tiles.

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

#### Scenario: Paused monitoring shows a paused banner with a resume action
- GIVEN global monitoring is paused (`monitoringPaused` is true)
- WHEN Home renders outside first run
- THEN the banner shows "Monitoring paused" in place of "Monitoring active" and offers a "Resume" action that sets `monitoringPaused` to false; a failed resume is logged without crashing

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
WHEN the user has zero rules, Home SHALL show a "Get started" checklist INSTEAD of the monitoring banner, with three steps: notification access, monitored apps, and first rule. Steps 1 and 2 SHALL be derived from the current monitoring status (no persisted state), show completion, and offer an action (enable access / choose apps) while incomplete. Step 3 SHALL be the current step, SHALL NOT be gated on steps 1-2, and SHALL host curated starter templates from `core/rulesharing/RuleTemplates` (first three), a "See more templates" action, and a "Create from scratch" action. A progress indicator SHALL show completed steps out of 3. First run is defined as zero rules, zero last-7-days stats, and no recent activity; in first run the stats row and recent activity SHALL be hidden.

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

### Requirement: "Last 7 days" stats row
Outside first run, Home SHALL show a "Last 7 days" stats row with two tappable tiles, each computed over a rolling window starting exactly 7 days before the moment the window was computed (not a calendar week, so the tiles never reset to zero on Monday):
- "Captured": the number of captured notifications with a timestamp at or after the window start.
- "Handled by rules": the number of those notifications marked processed (`is_processed = 1`). This counts notifications, not rule executions, so a notification matched by several rules counts once.

Each count SHALL use the same predicate as the Inbox list its tile opens, so the tile's number equals the size of that list. A one-line summary ("N notifications handled by your rules in the last 7 days") SHALL be shown only when the handled count is greater than zero. The window SHALL be recomputed on resume once the calendar day changes. Home SHALL NOT show an apps-active tile.

Tapping a tile SHALL push Inbox as a stacked screen with a per-visit status and the exact window start used for the counts as its `since` bound: "Captured" opens the All status, "Handled by rules" opens the Processed status. Neither value SHALL be persisted to the user's saved Inbox filters.

#### Scenario: Stats reflect the last 7 days
- GIVEN 8 notifications were captured in the last 7 days and 5 of them were processed by rules
- WHEN Home renders the stats row
- THEN it shows 8 for Captured and 5 for Handled by rules, and the summary line reads "5 notifications handled by your rules in the last 7 days"

#### Scenario: Several rules on one notification count once
- GIVEN one notification in the window was matched by two rules
- WHEN Home renders the stats row
- THEN Handled by rules counts it as 1

#### Scenario: Monday morning does not reset the tiles
- GIVEN notifications were captured on the previous Friday and it is now Monday 08:00
- WHEN Home renders the stats row
- THEN those notifications are still counted, because the window starts 7 days before now

#### Scenario: No activity in the window yields zero values, not an error
- GIVEN no notifications were captured in the last 7 days and the user has at least one rule
- WHEN Home renders
- THEN both tiles show 0 and no summary line is shown

#### Scenario: Window rolls forward on a new day
- GIVEN Home was opened on one day
- WHEN Home resumes on a later calendar day
- THEN the stats are re-observed with a window starting 7 days before the new current time; a resume on the same day does not re-observe

#### Scenario: Tile count matches the opened list
- GIVEN the Handled by rules tile shows 5
- WHEN the user taps it
- THEN Inbox opens on the Processed status bounded to the same window start (shown as a removable "Since" chip) and lists exactly 5 notifications, and the user's saved Inbox filters are unchanged

### Requirement: Recent Activity feed
Outside first run, Home SHALL show a "Recent Activity" feed of at most the 5 most recent rule executions in reverse-chronological order, joined from rule executions, the source notification, source app, and rule name. Each row SHALL show the source app's icon, the rule name, a relative time for when the rule ran (e.g. "5 min ago"), and the notification content (subtitle) and app name when available. Tapping a row SHALL open that notification in `features/notificationdetail`. The feed SHALL always show a "See all" action; tapping it SHALL push Inbox as a new stacked navigation screen (not a bottom-nav destination) with no status override and no date bound, so the user's saved Inbox filters apply. When there are no executions, the feed SHALL show an empty state with a hint instead of an error.

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
