# Delta for alarm-fullscreen-ui

## ADDED Requirements

### Requirement: User can configure the alarm background when full-screen is enabled

When configuring a `CREATE_ALARM` action, the rule editor SHALL show an "Alarm background" section only while the full-screen (call-style) option is enabled for that action; the section SHALL be hidden when full-screen is disabled. The section SHALL let the user choose between built-in preset color/gradient swatches and a custom image from the device gallery, stored via a background-type config key (`ALARM_BACKGROUND_TYPE_KEY`: `NONE` | `PRESET` | `IMAGE`, default `NONE`), a preset-id key (`ALARM_BACKGROUND_PRESET_KEY`) when type is `PRESET`, and a persisted image URI key (`ALARM_BACKGROUND_IMAGE_URI_KEY`) when type is `IMAGE`.

#### Scenario: Background section hidden when full-screen is off
- GIVEN a `CREATE_ALARM` action being configured with the full-screen option turned off
- WHEN the user views the alarm options
- THEN the "Alarm background" section is not shown

#### Scenario: Background section shown when full-screen is on
- GIVEN a `CREATE_ALARM` action being configured with the full-screen option turned on
- WHEN the user views the alarm options
- THEN the "Alarm background" section is shown with preset swatches and a custom-image option

#### Scenario: User selects a preset swatch
- GIVEN the "Alarm background" section is visible
- WHEN the user selects a preset color/gradient swatch
- THEN the background type is stored as `PRESET` with the selected preset id
- AND no image URI is persisted

### Requirement: Custom background image is picked via a persistable document contract

The system SHALL let the user pick a custom background image using `ActivityResultContracts.OpenDocument()` and SHALL call `takePersistableUriPermission` on the returned URI immediately after selection, so the image remains readable by the alarm at ring time even after the app process has been killed or the device rebooted. Transient-access picker contracts (e.g. `GetContent`, `PickVisualMedia`) SHALL NOT be used for this picker.

#### Scenario: User picks a custom gallery image
- GIVEN the "Alarm background" section is visible
- WHEN the user picks an image via the document picker
- THEN the background type is stored as `IMAGE` with the selected URI
- AND persistable read permission is taken on that URI

#### Scenario: Custom image survives app restart and reboot
- GIVEN a `CREATE_ALARM` action configured with a custom background image
- WHEN the app process is killed (or the device rebooted) and the alarm later fires
- THEN the full-screen alarm UI successfully loads and displays the previously selected image

## MODIFIED Requirements

### Requirement: Ringing alarm presents a full-screen call-style UI

While an alarm is ringing, the system SHALL present a full-screen, phone-call-style UI (`AlarmActivity`) launched via a full-screen intent attached to the ongoing alarm notification. The full-screen UI SHALL be shown in addition to — not instead of — the ongoing notification, so the notification remains the fallback surface. The `AlarmCallScreen`'s root `Surface` SHALL render the alarm action's configured background: the current app theme background when the type is `NONE` or unset, the selected preset color/gradient when `PRESET`, or the persisted custom image (via Coil `AsyncImage`) when `IMAGE`. If an `IMAGE` background fails to load (e.g. permission revoked, file deleted), the surface SHALL fall back to the theme background rather than failing to render.
(Previously: the surface always rendered the hardcoded `MaterialTheme.colorScheme.background` with no user-configurable background.)

#### Scenario: Full-screen alarm shown when the alarm rings
- GIVEN an alarm starts ringing and the full-screen intent can be presented
- WHEN the full-screen `AlarmActivity` displays
- THEN it is shown
- AND the ongoing alarm notification is still posted

#### Scenario: Full-screen alarm renders the configured preset background
- GIVEN a `CREATE_ALARM` action configured with a `PRESET` background
- WHEN the alarm rings and the full-screen UI is shown
- THEN the `AlarmCallScreen` surface renders the selected preset color/gradient

#### Scenario: Full-screen alarm renders the configured custom image background
- GIVEN a `CREATE_ALARM` action configured with an `IMAGE` background whose URI is still accessible
- WHEN the alarm rings and the full-screen UI is shown
- THEN the `AlarmCallScreen` surface renders the persisted image

#### Scenario: Image background fails to load
- GIVEN a `CREATE_ALARM` action configured with an `IMAGE` background whose URI is no longer accessible
- WHEN the alarm rings and the full-screen UI is shown
- THEN the surface falls back to the theme background
- AND the alarm still rings and remains stoppable via Dismiss/Snooze
