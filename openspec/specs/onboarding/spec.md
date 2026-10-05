# onboarding Specification

## Purpose

Defines the first-run setup flow: a value statement, a notification-access permission step, and an opt-in App Selection step, plus the launch routing that decides whether a user sees setup or the main app, and how the flow behaves under back navigation and process death.

## Requirements

### Requirement: Three-step setup flow
Initial setup SHALL consist of three steps shown with a consistent progress indicator exposing the description "Step X of 3": (1) Value Statement, (2) Permission Explanation, (3) App Selection. Steps 1 and 2 SHALL be sub-states of a single Onboarding screen; step 3 SHALL be the App Selection screen opened as initial setup. Step 1 MAY additionally show a feature carousel whose position is exposed as "Page X of Y".

#### Scenario: First launch shows step 1
- GIVEN a first-time user opens the app
- WHEN the first frame resolves
- THEN the Value Statement is shown with step 1 of 3 active

#### Scenario: Get started advances to the permission step
- GIVEN the Value Statement is shown
- WHEN the user taps "Get started"
- THEN the Permission Explanation is shown with step 2 of 3 active

### Requirement: Permission step
The Permission step SHALL explain why notification access is needed, state that notification content (including messages and codes) from selected apps is stored only on the device, that there are no analytics, and that data leaves the device only through webhooks the user sets up, and SHALL link to the Privacy Policy with a touch target of at least 48dp. The access status label SHALL read "Turn on in system settings" (it is not a toggle). "Grant access" SHALL open this app's notification-listener detail screen on API 30+, falling back to the notification-listener list and then general settings if a screen cannot be launched.

#### Scenario: Grant access opens the app's own settings screen
- GIVEN the Permission step is shown on API 30 or higher
- WHEN the user taps "Grant access"
- THEN the system notification-access detail screen for Notificapp's listener opens

#### Scenario: Detail screen unavailable
- GIVEN the detail settings screen cannot be resolved on the device
- WHEN the user taps "Grant access"
- THEN the generic notification-listener settings open, and if that also fails, general settings open, without crashing

#### Scenario: Returning without granting
- GIVEN the user opened system settings from "Grant access"
- WHEN the app resumes and access is still off
- THEN an inline "Access not enabled yet" hint is shown above the "Grant access" button

### Requirement: App Selection is opt-in
Initial setup SHALL start App Selection with no apps selected. Setup SHALL be complete only when notification access is enabled AND at least one app is saved.

#### Scenario: Access granted moves to App Selection
- GIVEN the user is on the Permission step
- WHEN notification access becomes enabled and the app resumes
- THEN App Selection opens as initial setup with nothing selected and step 3 of 3 active

#### Scenario: Quitting before saving apps
- GIVEN access is enabled and zero apps are saved
- WHEN the user relaunches the app
- THEN App Selection (initial setup) is shown, not Home

### Requirement: Launch routing
`MainViewModel` SHALL derive the flow state on resume until the main app is reached, and SHALL be the single owner of the transition between Onboarding, App Selection and Home; Onboarding SHALL NOT navigate by itself. The back stack SHALL contain exactly one entry for the resolved start route.

#### Scenario: First-time user
- GIVEN no apps are saved and access is off
- WHEN the app launches
- THEN Onboarding is shown

#### Scenario: Access granted, nothing saved
- GIVEN no apps are saved and access is on
- WHEN the app resumes
- THEN App Selection (initial setup) becomes the only back-stack entry, exactly once

#### Scenario: Returning user with revoked access
- GIVEN at least one app is saved and access has since been revoked
- WHEN the app launches
- THEN Home is shown and its access-off banner offers to re-enable access; onboarding is not replayed

#### Scenario: Setup complete
- GIVEN access is on and at least one app is saved
- WHEN the app launches
- THEN Home is shown

### Requirement: Back navigation
System and predictive back on the Permission step SHALL return to the Value Statement and SHALL NOT exit the app. Back on the Value Statement SHALL follow default behavior.

#### Scenario: Back on step 2
- GIVEN the Permission step is shown
- WHEN the user triggers system back
- THEN the Value Statement is shown and the app stays open

### Requirement: Process death
The current onboarding step and whether the user opened system settings SHALL be persisted in `SavedStateHandle` and restored after process death. An unrecognized saved step SHALL fall back to the Value Statement.

#### Scenario: Process killed while in system settings
- GIVEN the user is on the Permission step and tapped "Grant access"
- WHEN the process is killed and the user returns
- THEN the Permission step is restored and the denied hint is shown if access is still off

### Requirement: Accessibility
Headlines SHALL expose heading semantics, the progress indicator SHALL expose "Step X of 3", the carousel dots SHALL expose a single "Page X of Y" state, and no spinner SHALL be shown for the synchronous resume permission check.

#### Scenario: TalkBack on the progress indicator
- GIVEN TalkBack is on and step 2 is shown
- WHEN focus lands on the progress indicator
- THEN "Step 2 of 3" is announced
