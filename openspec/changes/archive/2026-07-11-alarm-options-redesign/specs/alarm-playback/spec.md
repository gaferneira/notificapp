# Delta for alarm-playback

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Snooze stops the alarm and re-rings later

The system SHALL stop the ringing alarm when the user invokes the Snooze action and SHALL re-ring the same alarm after a configurable snooze delay, subject to a configurable maximum snooze count. Snooze SHALL stop the current sound, vibration, and ongoing notification immediately, then start a fresh ringing alarm after the delay elapses. Snooze availability, duration, and max count are configured per `CREATE_ALARM` action via `ALARM_SNOOZE_ENABLED_KEY` (default `true`), `ALARM_SNOOZE_DURATION_MINUTES_KEY` (default 5, clamped `MIN_ALARM_SNOOZE_DURATION_MINUTES`..`MAX_ALARM_SNOOZE_DURATION_MINUTES`), and `ALARM_SNOOZE_MAX_COUNT_KEY` (default 3, clamped `MIN_ALARM_SNOOZE_MAX_COUNT`..`MAX_ALARM_SNOOZE_MAX_COUNT`, following the `FLASH_COUNT`/`FLASH_DURATION_MS` coercion pattern). The system SHALL track the number of snoozes already used for the current ringing alarm and SHALL persist that count across re-ring so it is not reset by rebuilding the alarm request. When the snooze count reaches the configured max, the Snooze action SHALL no longer be offered.
(Previously: hardcoded 5-min delay, no max-count / infinite snooze bug.)

#### Scenario: User snoozes a ringing alarm
- GIVEN a `CREATE_ALARM` action with snooze enabled and snoozes remaining below the max count
- WHEN the user taps Snooze on the ongoing alarm notification
- THEN the sound and vibration stop immediately
- AND the ongoing notification is removed
- AND the alarm rings again after the configured snooze delay elapses

#### Scenario: Snooze exhausted at max count
- GIVEN a `CREATE_ALARM` action with a configured max snooze count and the user has already snoozed that many times
- WHEN the alarm re-rings
- THEN the Snooze action is not offered on the ongoing notification or full-screen UI
- AND only Dismiss remains available

#### Scenario: Snooze disabled
- GIVEN a `CREATE_ALARM` action with snooze disabled
- WHEN the alarm fires
- THEN the ongoing notification and full-screen UI do not offer a Snooze action

#### Scenario: Legacy rule defaults to 3 max snoozes
- GIVEN a `CREATE_ALARM` action persisted before this change, with no snooze-related config keys present
- WHEN the rule is loaded and the alarm fires
- THEN snooze is enabled with the existing 5-minute delay behavior
- AND the max snooze count defaults to 3
