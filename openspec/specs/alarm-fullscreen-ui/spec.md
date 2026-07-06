# alarm-fullscreen-ui Specification

## Purpose
TBD - created by archiving change alarm-fullscreen-call-ui. Update Purpose after archive.
## Requirements
### Requirement: Ringing alarm presents a full-screen call-style UI
While an alarm is ringing, the system SHALL present a full-screen, phone-call-style UI (`AlarmActivity`) launched via a full-screen intent attached to the ongoing alarm notification. The full-screen UI SHALL be shown in addition to — not instead of — the ongoing notification, so the notification remains the fallback surface.

#### Scenario: Full-screen alarm shown when the alarm rings
- **WHEN** an alarm starts ringing and the full-screen intent can be presented
- **THEN** the full-screen call-style `AlarmActivity` is displayed
- **AND** the ongoing alarm notification is still posted

### Requirement: Full-screen alarm shows over the lock screen and wakes the screen
The full-screen alarm SHALL display over the lock screen and turn the screen on when it appears, so the user sees it without unlocking the device.

#### Scenario: Alarm rings while the device is locked with the screen off
- **WHEN** an alarm rings while the device is locked and the screen is off
- **THEN** the screen turns on
- **AND** the full-screen alarm is shown over the lock screen without requiring the user to unlock first

### Requirement: Full-screen alarm shows the triggering notification content and large controls
The full-screen alarm SHALL display the triggering notification's title, content text, and source app name, alongside prominent **Dismiss** and **Snooze** controls sized for a call-style screen.

#### Scenario: Full-screen alarm reflects what triggered it
- **WHEN** an alarm triggered by a notification with title "Payment received", text "You got $50", from app "Bank" is shown full-screen
- **THEN** the full-screen UI shows "Payment received", "You got $50", and identifies "Bank"
- **AND** shows large Dismiss and Snooze controls

### Requirement: Full-screen controls drive the same stop path
Dismiss and Snooze on the full-screen alarm SHALL reuse the existing `AlarmService` stop path (the same `ACTION_DISMISS` / `ACTION_SNOOZE` handling as the notification actions), with no separate stop logic. After acting, the full-screen UI SHALL close.

#### Scenario: Dismiss from the full-screen UI
- **WHEN** the user taps Dismiss on the full-screen alarm
- **THEN** the alarm sound and vibration stop via `AlarmService`
- **AND** the full-screen UI closes
- **AND** no re-ring is scheduled

#### Scenario: Snooze from the full-screen UI
- **WHEN** the user taps Snooze on the full-screen alarm
- **THEN** the alarm stops via `AlarmService` and a re-ring is scheduled
- **AND** the full-screen UI closes

### Requirement: Full-screen surface stays in sync with the alarm state
When a ringing alarm stops for any reason — dismissed or snoozed from the notification, or replaced by a new ring — an open full-screen `AlarmActivity` SHALL close itself so it never lingers over a stopped alarm.

#### Scenario: Alarm dismissed from the notification while full-screen is open
- **WHEN** the full-screen alarm is showing and the user dismisses the alarm from the ongoing notification instead
- **THEN** the alarm stops
- **AND** the open full-screen alarm UI closes

### Requirement: User can choose the alarm presentation style per alarm
When configuring a `CREATE_ALARM` action, the user SHALL be able to choose whether that alarm uses the full-screen, call-style UI or only the ongoing notification. The choice SHALL be persisted with the action's config and honored at ring time: a notification-only alarm SHALL NOT attach a full-screen intent.

#### Scenario: Alarm configured for notification only
- **WHEN** the user turns off the full-screen (call-style) option for an alarm action and the alarm later rings
- **THEN** only the ongoing notification is shown
- **AND** no full-screen UI is raised

#### Scenario: Alarm configured for full-screen
- **WHEN** the user leaves the full-screen (call-style) option on for an alarm action and the alarm later rings
- **THEN** the full-screen UI is presented (subject to the platform's full-screen-intent permission)

### Requirement: Degrade to the notification when full-screen intent is unavailable
On Android versions where `USE_FULL_SCREEN_INTENT` is a restricted permission (14+) and it has not been granted, the system SHALL still ring the alarm and SHALL still surface the ongoing Dismiss/Snooze notification; the full-screen UI is a best-effort enhancement and its absence SHALL NOT prevent the alarm from being seen or stopped.

#### Scenario: Full-screen permission not granted
- **WHEN** an alarm rings while `USE_FULL_SCREEN_INTENT` is not granted
- **THEN** the alarm still rings
- **AND** the ongoing notification with Dismiss and Snooze is still shown
- **AND** the app does not crash from the missing permission

