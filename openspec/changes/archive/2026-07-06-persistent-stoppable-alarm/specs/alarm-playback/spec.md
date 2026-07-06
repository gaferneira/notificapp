## ADDED Requirements

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
The system SHALL stop the ringing alarm when the user invokes the Snooze action and SHALL re-ring the same alarm after a snooze delay. Snooze SHALL stop the current sound, vibration, and ongoing notification immediately, then start a fresh ringing alarm after the delay elapses.

#### Scenario: User snoozes a ringing alarm
- **WHEN** the user taps Snooze on the ongoing alarm notification
- **THEN** the sound and vibration stop immediately
- **AND** the ongoing notification is removed
- **AND** the alarm rings again after the snooze delay elapses

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
