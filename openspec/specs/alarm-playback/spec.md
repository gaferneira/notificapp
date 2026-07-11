# alarm-playback Specification

## Purpose
TBD - created by archiving change persistent-stoppable-alarm. Update Purpose after archive.
## Requirements
### Requirement: Alarm rings until explicitly stopped
When a matched rule triggers an enabled `CREATE_ALARM` action, the system SHALL play the configured alarm sound in a loop and (when vibration is enabled) vibrate repeatedly, continuing until the alarm is explicitly dismissed or snoozed by the user or by a subsequent stop request. The alarm SHALL NOT stop on its own after a single playback.

#### Scenario: Alarm keeps ringing until dismissed
- **WHEN** a `CREATE_ALARM` action fires
- **THEN** the alarm sound plays in a loop
- **AND** the sound continues past the length of a single playthrough
- **AND** it stops only once a dismiss, snooze, or stop request is received

#### Scenario: Vibration repeats while ringing
- **WHEN** a `CREATE_ALARM` action with vibration enabled fires
- **THEN** the device vibrates in a repeating pattern for as long as the alarm rings

#### Scenario: Vibration disabled
- **WHEN** a `CREATE_ALARM` action with vibration disabled fires
- **THEN** the alarm sound loops but the device does not vibrate

### Requirement: Alarm sound can be disabled

The system SHALL support an optional sound-enabled flag on a `CREATE_ALARM` action (`ALARM_SOUND_ENABLED_KEY`, default `true`). When disabled, the alarm SHALL ring without audio playback while all other ringing behavior (vibration, ongoing notification, full-screen UI) proceeds unchanged.

#### Scenario: Sound enabled (default)
- GIVEN a `CREATE_ALARM` action with sound enabled (or unset, using the default)
- WHEN the alarm fires
- THEN the configured alarm sound plays in a loop

#### Scenario: Sound disabled
- GIVEN a `CREATE_ALARM` action with sound explicitly disabled
- WHEN the alarm fires
- THEN no alarm audio plays
- AND vibration (if enabled) and the ongoing notification still behave normally

### Requirement: Vibration uses a selectable named pattern

The system SHALL let the user select a vibration pattern from a built-in named set (e.g. Basic call, Pulse, Long) per `CREATE_ALARM` action, stored via a pattern-id config key (`ALARM_VIBRATION_PATTERN_KEY`, default the existing pattern). The device SHALL vibrate using the selected pattern's `LongArray` for as long as the alarm rings, MUST NOT alter behavior when vibration is disabled, and MUST fall back to the default pattern if the stored pattern id is unrecognized (e.g. an imported rule).

#### Scenario: Selected pattern is used while ringing
- GIVEN a `CREATE_ALARM` action with vibration enabled and pattern "Pulse" selected
- WHEN the alarm fires
- THEN the device vibrates using the "Pulse" pattern in a repeating loop for as long as the alarm rings

#### Scenario: Unrecognized pattern id falls back to default
- GIVEN a `CREATE_ALARM` action whose stored pattern id does not match any known pattern (e.g. malformed import)
- WHEN the alarm fires with vibration enabled
- THEN the device vibrates using the default pattern instead of crashing or vibrating silently

### Requirement: Foreground service owns the alarm lifecycle
The system SHALL run the ringing alarm inside a started foreground service so playback survives independently of the notification-listener coroutine that triggered it. The service SHALL request audio focus while ringing, release audio focus and all audio/vibration resources when it stops, and SHALL stop itself once no alarm is ringing.

#### Scenario: Alarm playback is not tied to the triggering pipeline
- **WHEN** a `CREATE_ALARM` action fires from the notification pipeline
- **THEN** a foreground service is started to own the ringing
- **AND** the alarm continues ringing after the triggering coroutine completes

#### Scenario: Resources released on stop
- **WHEN** the ringing alarm is stopped (dismiss or snooze)
- **THEN** the service releases audio focus, stops the sound, and cancels vibration
- **AND** the foreground service stops itself

### Requirement: Ongoing alarm notification with Dismiss and Snooze
While an alarm is ringing, the system SHALL post an ongoing, non-user-dismissable notification on a dedicated high-importance "Alarms" notification channel. The notification SHALL expose a **Dismiss** action and a **Snooze** action. The notification SHALL be removed when the alarm stops.

#### Scenario: Ongoing notification shown while ringing
- **WHEN** an alarm starts ringing
- **THEN** an ongoing notification appears on the "Alarms" channel
- **AND** it cannot be dismissed by swiping it away
- **AND** it shows a Dismiss action and a Snooze action

#### Scenario: Notification removed when alarm stops
- **WHEN** the alarm is dismissed or snoozed
- **THEN** the ongoing notification is removed

### Requirement: Ongoing notification reflects the triggering notification
The ongoing alarm notification SHALL display information from the notification that triggered the alarm — at least its title and content text, plus the source app name — rather than static placeholder text, so the user can tell what the alarm is for. The re-ring after a snooze SHALL preserve the same information.

#### Scenario: Alarm notification shows the source notification content
- **WHEN** an alarm is triggered by a notification with title "Payment received" and text "You got $50"
- **THEN** the ongoing alarm notification shows the title "Payment received" and text "You got $50"
- **AND** identifies the source app

#### Scenario: Snoozed alarm re-rings with the same content
- **WHEN** an alarm triggered by a given notification is snoozed and later re-rings
- **THEN** the re-rung notification shows the same title, text, and source app

### Requirement: Dismiss stops the alarm
The system SHALL stop the ringing alarm when the user invokes the Dismiss action from the ongoing notification. Dismiss SHALL stop the sound and vibration, remove the ongoing notification, and stop the foreground service without scheduling any re-ring.

#### Scenario: User dismisses a ringing alarm
- **WHEN** the user taps Dismiss on the ongoing alarm notification
- **THEN** the sound and vibration stop
- **AND** the ongoing notification is removed
- **AND** no further alarm is scheduled

### Requirement: Snooze stops the alarm and re-rings later
The system SHALL stop the ringing alarm when the user invokes the Snooze action and SHALL re-ring the same alarm after a configurable snooze delay, subject to a configurable maximum snooze count. Snooze SHALL stop the current sound, vibration, and ongoing notification immediately, then start a fresh ringing alarm after the delay elapses. Snooze availability, duration, and max count are configured per `CREATE_ALARM` action via `ALARM_SNOOZE_ENABLED_KEY` (default `true`), `ALARM_SNOOZE_DURATION_MINUTES_KEY` (default 5, clamped `MIN_ALARM_SNOOZE_DURATION_MINUTES`..`MAX_ALARM_SNOOZE_DURATION_MINUTES`), and `ALARM_SNOOZE_MAX_COUNT_KEY` (default 3, clamped `MIN_ALARM_SNOOZE_MAX_COUNT`..`MAX_ALARM_SNOOZE_MAX_COUNT`, following the `FLASH_COUNT`/`FLASH_DURATION_MS` coercion pattern). The system SHALL track the number of snoozes already used for the current ringing alarm and SHALL persist that count across re-ring so it is not reset by rebuilding the alarm request. When the snooze count reaches the configured max, the Snooze action SHALL no longer be offered.

#### Scenario: User snoozes a ringing alarm
- **WHEN** the user taps Snooze on the ongoing alarm notification
- **THEN** the sound and vibration stop immediately
- **AND** the ongoing notification is removed
- **AND** the alarm rings again after the configured snooze delay elapses

#### Scenario: Snooze exhausted at max count
- **GIVEN** a `CREATE_ALARM` action with a configured max snooze count and the user has already snoozed that many times
- **WHEN** the alarm re-rings
- **THEN** the Snooze action is not offered on the ongoing notification or full-screen UI
- **AND** only Dismiss remains available

#### Scenario: Snooze disabled
- **GIVEN** a `CREATE_ALARM` action with snooze disabled
- **WHEN** the alarm fires
- **THEN** the ongoing notification and full-screen UI do not offer a Snooze action

#### Scenario: Legacy rule defaults to 3 max snoozes
- **GIVEN** a `CREATE_ALARM` action persisted before this change, with no snooze-related config keys present
- **WHEN** the rule is loaded and the alarm fires
- **THEN** snooze is enabled with the existing 5-minute delay behavior
- **AND** the max snooze count defaults to 3

### Requirement: Single active alarm
The system SHALL keep at most one alarm ringing at a time. When a `CREATE_ALARM` action fires while an alarm is already ringing, the system SHALL restart the ringing for the new request rather than stacking overlapping, independently unstoppable sounds.

#### Scenario: Second alarm while one is ringing
- **WHEN** a `CREATE_ALARM` action fires while an alarm is already ringing
- **THEN** the currently ringing sound is stopped before the new one starts
- **AND** only one alarm sound is audible

### Requirement: Alarm requires notification permission
The alarm's Dismiss and Snooze controls live only on the ongoing notification, so an alarm that cannot post its notification would ring with no way to stop it. The system SHALL therefore require notification permission for the alarm rather than ringing without it:

- When the user configures a `CREATE_ALARM` action, the system SHALL request the `POST_NOTIFICATIONS` runtime permission (Android 13+) and SHALL warn the user that the alarm will not ring without it.
- When a `CREATE_ALARM` action fires while app notifications are disabled, the system SHALL NOT start ringing (no sound, no vibration) and SHALL record the action outcome as `SKIPPED`.

#### Scenario: Permission requested when configuring an alarm action
- **WHEN** the user selects the Create Alarm action type while `POST_NOTIFICATIONS` is not granted (Android 13+)
- **THEN** the app requests the notification permission
- **AND** warns that the alarm will not ring without it

#### Scenario: Alarm does not ring when notifications are disabled
- **WHEN** a `CREATE_ALARM` action fires while the app's notifications are disabled
- **THEN** no alarm sound or vibration starts
- **AND** the action outcome is recorded as `SKIPPED`

