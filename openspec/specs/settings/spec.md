# settings Specification

## Purpose

Defines the Settings screen: a sectioned surface for monitoring controls, integrations, appearance, data management, and app information.

## Requirements

### Requirement: Sectioned layout

Settings SHALL group its content into the sections Monitoring, Integrations, Appearance, Data, and About, in that order, each with an accessibility heading. All user-visible text SHALL come from string resources (en + es).

### Requirement: Pause monitoring

The Monitoring section SHALL include a "Pause monitoring" switch bound to the persisted global `monitoringPaused` preference, the same state controlled by the Quick Settings tile and the Home banner. While paused, new notifications SHALL NOT be captured, processed, or acted on.

#### Scenario: Pausing from Settings stops capture
- GIVEN monitoring is active
- WHEN the user turns the switch on
- THEN `monitoringPaused` is persisted as true and freshly posted notifications are not saved or processed

#### Scenario: State stays in sync across surfaces
- GIVEN monitoring is paused from the Quick Settings tile
- WHEN Settings or Home is shown
- THEN the switch is on and Home shows the paused banner

### Requirement: Monitoring status rows

The Monitoring section SHALL show notification-access status (with an enable action when revoked), a battery-optimization row (opening the system exemption settings, status refreshed on resume), and the monitored-apps summary linking to app selection.

### Requirement: Appearance

Settings SHALL offer Theme (System / Light / Dark) and Language (System / English / Spanish); both SHALL be persisted and applied live.

### Requirement: Data management

Settings SHALL offer notification retention (30 days / 90 days / Forever), enforced on app start and immediately when the setting changes; a storage usage summary; and "Clear all data" (see `data-deletion`).

### Requirement: About

Settings SHALL show the app version, a Privacy Policy link, and an open-source licenses link.
