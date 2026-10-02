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

### Requirement: Monitoring status banner
Home SHALL display a banner showing whether the notification listener is currently active, the count of monitored apps (`SelectedAppRepository`), and the count of configured rules (`RuleRepository`).

#### Scenario: Listener active with rules configured
- GIVEN the notification listener is active, 3 apps are monitored, and 5 rules exist
- WHEN Home renders
- THEN the banner shows an active status, "3" monitored apps, and "5" rules

#### Scenario: Listener inactive is surfaced distinctly
- GIVEN the notification listener is not active (permission revoked or service not running)
- WHEN Home renders
- THEN the banner shows an inactive/attention state distinguishable from the active state

### Requirement: Empty state offers Starter Rules
WHEN the user has zero rules, Home SHALL show a "Starter Rules" section built from the existing `core/rulesharing/RuleTemplates`, each offering "Create rule from this" (pre-fills the rule editor from the template) and "Create from scratch"; a "See more templates" action SHALL open the existing `RuleTemplatePickerSheet`.

#### Scenario: Zero-rule user sees starter templates
- GIVEN the user has no rules
- WHEN Home renders
- THEN a Starter Rules section is shown with cards sourced from `RuleTemplates`

#### Scenario: Creating from a starter template opens a pre-filled editor
- GIVEN the Starter Rules section is visible
- WHEN the user taps "Create rule from this" on a template card
- THEN the rule editor opens pre-filled with that template's conditions/actions

#### Scenario: Section is absent once a rule exists
- GIVEN the user has at least one rule
- WHEN Home renders
- THEN the Starter Rules section is not shown

### Requirement: "This Week" stats row
WHEN the user has at least one rule, Home SHALL show a stats row with: extracted-record count, rules-fired count, and distinct-apps-active count, each computed over the current calendar week in the device's local timezone, derived in-memory from existing repository flows (no new aggregate stored).

#### Scenario: Stats reflect current week's activity
- GIVEN 8 records were extracted, rules fired 12 times, and notifications arrived from 4 distinct apps this calendar week
- WHEN Home renders the stats row
- THEN it shows 8 records, 12 rules-fired, and 4 apps-active

#### Scenario: No activity this week yields a zero-value row, not an error
- GIVEN no rule executions occurred in the current calendar week
- WHEN Home renders
- THEN the stats row shows all zero values with no exception

### Requirement: Recent Activity feed
Home SHALL show a limited, reverse-chronological "Recent Activity" feed joined from `RuleExecutionDao`, the source notification, source app, and rule name. Tapping a row SHALL open that notification in `features/notificationdetail`. The feed SHALL always show a "See all" action; tapping it SHALL push Inbox as a new stacked navigation screen (not a bottom-nav destination).

#### Scenario: Recent row opens notification detail
- GIVEN the Recent Activity feed shows a row for a rule execution
- WHEN the user taps that row
- THEN `features/notificationdetail` opens for the row's source notification

#### Scenario: "See all" pushes Inbox as a stacked screen
- GIVEN the Recent Activity feed is visible
- WHEN the user taps "See all"
- THEN Inbox opens as a stacked navigation screen, and the bottom nav still shows Home as selected/available

#### Scenario: Empty feed is shown gracefully
- GIVEN no rule executions have occurred yet
- THEN the Recent Activity feed shows an empty state instead of an error, and "See all" remains visible

### Requirement: Recurring-notification suggestions are surfaced on Home
WHEN suggestions are available from the `recurring-notification-suggestions` capability, Home SHALL display them (in the active, non-empty state) offering "Create rule from this" and "Skip similar" per suggestion, per that capability's own requirements.

#### Scenario: Suggestions appear when available
- GIVEN the recurring-notification heuristic has produced at least one non-dismissed suggestion
- WHEN Home renders in its active (has-rules) state
- THEN the suggestion(s) are shown with both actions available
