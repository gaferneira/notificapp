# notification-detail Specification

## Purpose

Defines the Notification Detail screen: the full captured notification, the history of rules that matched it (extracted data and action outcomes), a read-only "test current rules" preview that can optionally refresh stored extracted data, and the actions available on the notification (open source app, delete, create rule). All user-visible text is localized (EN/ES).

## Requirements

### Requirement: Load states
The screen SHALL show a centered progress indicator (with an accessibility description) while loading. IF the notification does not exist (NOT_FOUND) or was deleted while the screen was open (DELETED), it SHALL show an explanatory message with a "Go back" action and no Retry. IF reading fails (ERROR), it SHALL show a message with a "Retry" action that reloads. Create rule, the overflow menu and the matched-rules list SHALL only be available when the notification is loaded.

#### Scenario: Missing notification
- GIVEN the requested notification id does not exist
- WHEN the screen renders
- THEN a "Notification not found" message and a "Go back" button are shown, with no Retry

#### Scenario: Deleted while open
- GIVEN the notification is shown and is then removed (retention, clear data)
- WHEN the observation emits no notification
- THEN the Deleted message and "Go back" are shown

#### Scenario: Read failure
- GIVEN reading the notification fails
- WHEN the screen renders
- THEN an error message and "Retry" are shown and Retry reloads

### Requirement: Notification card
The card SHALL show the source app icon (loaded asynchronously, letter avatar fallback), app name, the absolute localized date and time plus a relative time, the title and the content. Text SHALL be selectable. Content longer than six lines SHALL collapse with a "Show more"/"Show less" toggle. The raw payload SHALL NOT be shown. IF the content was removed by a rule after extraction, the card SHALL say "Content removed by rule <name> after extraction" (generic wording when the rule is unknown) instead of text.

#### Scenario: Long content
- GIVEN content that exceeds six lines
- WHEN the card renders
- THEN it is truncated with "Show more", and "Show less" appears once expanded

#### Scenario: Redacted content
- GIVEN a rule removed the content after extraction
- WHEN the card renders
- THEN the explanatory message replaces title and content

### Requirement: Matched rules history
A "Matched rules" section (heading semantics, pluralized count) SHALL list the stored executions, most recent first. Each card SHALL open that rule's details when tapped, except a deleted rule ("Deleted rule"), which is not tappable. A dry-run execution SHALL show a dry-run badge and note. Extracted fields SHALL show name (fallback "Removed field"), a neutral type chip and the value below the name so long values wrap. Actions SHALL show their localized label (fallback "Removed action") and an outcome with icon and text: Succeeded, Failed, Skipped, Suppressed, or "No result recorded" for legacy rows (omitted for dry runs). Color SHALL NOT be the only carrier of meaning.

#### Scenario: Open a matched rule
- GIVEN an execution of an existing rule
- WHEN the user taps its card
- THEN the rule details screen opens

#### Scenario: Deleted rule
- GIVEN an execution whose rule no longer exists
- WHEN the card renders
- THEN it shows "Deleted rule" and is not tappable

### Requirement: Empty state and Create rule
WHEN no rule matched, the screen SHALL show "No rules matched" with a "Create rule" button. Once at least one execution exists, a floating "Create rule" button SHALL be shown and the list SHALL reserve bottom space so it never covers the last card. At any time exactly one Create rule entry point is visible.

#### Scenario: Single entry point
- GIVEN a loaded notification
- WHEN it has no executions
- THEN only the empty-state button is shown, and when it has executions only the floating button is shown

### Requirement: Test current rules
A "Test current rules" button SHALL run the read-only preview, showing inline progress in the button (never a full-screen loader). It SHALL be disabled while a preview or update is running. The result SHALL appear in a modal bottom sheet stating that nothing is saved and actions are not run, listing for every currently matching rule its name, a dry-run badge when applicable, each field as Unchanged, Changed (was/now), New or Removed, and the actions it would run (informational). A match with no stored execution SHALL be labeled "Matches now, not recorded". Recorded rules that no longer match SHALL be listed under "No longer matching". IF the content was removed, or evaluation fails, the sheet SHALL show the corresponding message (with "Try again" for failures). Dismissing the sheet SHALL clear the preview.

#### Scenario: Preview with changes
- GIVEN the current rules extract different values than those stored
- WHEN the user taps "Test current rules"
- THEN a sheet shows each field's change and the sheet's primary button is enabled

#### Scenario: Redacted content
- GIVEN the content was removed by a rule
- WHEN the user taps "Test current rules"
- THEN the sheet explains there is nothing to test against

### Requirement: Update extracted data
"Update extracted data" SHALL be enabled only when applying the preview would change stored data. Tapping it SHALL open a confirmation stating how many stored values will be replaced and that actions will not run. On confirmation it SHALL show progress, then close the preview and show "Extracted data updated"; on failure it SHALL show an error message and keep the preview open.

#### Scenario: Confirm update
- GIVEN a preview with two changed fields on a recorded execution
- WHEN the user taps "Update extracted data" and confirms
- THEN the confirmation said two values would be replaced and no actions run, and the stored values are updated

### Requirement: Top bar menu and effects
The top bar SHALL have a back button and an overflow menu with "Open app" (only when the source app has a launcher activity) and "Delete notification" (confirmation dialog; on success the screen navigates back). Open-app failure and update/delete failures SHALL be reported with a localized snackbar. Touch targets SHALL be at least 48dp and icons SHALL have content descriptions where they carry meaning.

#### Scenario: Delete
- GIVEN the overflow menu
- WHEN the user chooses Delete and confirms
- THEN the notification is deleted and the screen closes

### Requirement: Localization
All visible text SHALL come from string resources (EN and ES), enforced by `architectureCheck` rule 9 over `features/notificationdetail/ui`. Relative times SHALL be formatted with the app language.
