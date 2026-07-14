# Delta for rule-app-scope

No prior `openspec/specs/rule-app-scope/spec.md` exists — app targeting via `Rule.targetApps` was previously unspec'd. This delta introduces the capability's first formal requirements: the app-scope model (all / include-listed / exclude-listed), its enforcement in live matching and backtest, its effect on the Rules-list app filter, and its wire-format representation.

## ADDED Requirements

### Requirement: Include-mode app scope (unchanged behavior)
WHEN a rule has a non-null `targetApps` list and `isIncludeMode = true`, the system SHALL fire the rule only for notifications from apps present in that list.

#### Scenario: Rule fires for a listed app in include mode
- **GIVEN** a rule with `targetApps = [AppA, AppB]` and `isIncludeMode = true`
- **WHEN** a notification arrives from AppA
- **THEN** the rule is evaluated against the notification

#### Scenario: Rule does not fire for an unlisted app in include mode
- **GIVEN** a rule with `targetApps = [AppA, AppB]` and `isIncludeMode = true`
- **WHEN** a notification arrives from AppC
- **THEN** the rule is not evaluated against the notification

### Requirement: Exclude-mode app scope
WHEN a rule has a non-null `targetApps` list and `isIncludeMode = false`, the system SHALL fire the rule for every app NOT present in that list, and SHALL skip the rule for apps present in the list. This SHALL hold identically for live matching (`RuleDao`) and backtest (`NotificationDao`) — both are independent "does this rule apply" checks and MUST agree on the same rule.

#### Scenario: Rule fires for a non-listed app in exclude mode
- **GIVEN** a rule with `targetApps = [AppA]` and `isIncludeMode = false`
- **WHEN** a notification arrives from AppB
- **THEN** the rule is evaluated against the notification

#### Scenario: Rule does not fire for a listed app in exclude mode
- **GIVEN** a rule with `targetApps = [AppA]` and `isIncludeMode = false`
- **WHEN** a notification arrives from AppA
- **THEN** the rule is not evaluated against the notification

#### Scenario: Backtest agrees with live match for exclude mode
- **GIVEN** an exclude-mode rule with `targetApps = [AppA]`
- **WHEN** the rule is run against a stored notification from AppB via backtest, and separately via live matching
- **THEN** both checks report the rule as applicable

#### Scenario: Empty non-null list collapses to all apps in exclude mode
- **GIVEN** a rule with `targetApps = []` (empty, non-null) and `isIncludeMode = false`
- **WHEN** a notification arrives from any app
- **THEN** the rule is evaluated against the notification, matching current null-or-empty "all apps" semantics

### Requirement: Null targetApps is unconditionally global
WHEN a rule has `targetApps == null`, the system SHALL treat the rule as applying to all apps regardless of the value of `isIncludeMode`; the flag SHALL have no effect.

#### Scenario: Null targetApps fires for any app irrespective of the mode flag
- **GIVEN** a rule with `targetApps = null` and `isIncludeMode = false`
- **WHEN** a notification arrives from any app
- **THEN** the rule is evaluated against the notification, identically to `isIncludeMode = true`

### Requirement: Rules-list app filter surfaces exclude-mode rules by effective scope
The Rules-list "filter by app" facet SHALL include a rule under an app's facet based on the rule's effective app scope (whether it would fire for that app), not on literal membership in `targetApps`. An exclude-mode rule that does not list App X SHALL appear under the App X facet.

#### Scenario: Filtering by an app surfaces an exclude-mode rule that omits it
- **GIVEN** an exclude-mode rule with `targetApps = [AppA]` and an include-mode rule with `targetApps = [AppA]`
- **WHEN** the user filters the Rules list by AppB
- **THEN** the exclude-mode rule appears in the filtered results
- **AND** the include-mode rule does not appear in the filtered results

#### Scenario: Filtering by a listed app excludes the exclude-mode rule
- **GIVEN** an exclude-mode rule with `targetApps = [AppA]`
- **WHEN** the user filters the Rules list by AppA
- **THEN** the exclude-mode rule does not appear in the filtered results

### Requirement: Wire format round-trips the app-scope mode without a schema-version bump
The rule-sharing wire format (`RuleExportDto` / `RuleWireMapper`) SHALL carry `isIncludeMode` alongside `targetApps` so exported rules preserve app scope and round-trip on import without loss. `RULE_EXPORT_SCHEMA_VERSION` SHALL remain unchanged (project is pre-launch; no compatibility shim is required for this shape change per project policy). The existing golden-file wire-format test SHALL be updated to reflect the new field in its fixture, since the JSON shape changes even though the version number does not.

#### Scenario: Exported exclude-mode rule re-imports identically
- **WHEN** an exclude-mode rule with `targetApps = [AppA]` is exported to the wire format and re-imported
- **THEN** the re-imported rule has `isIncludeMode = false` and `targetApps = [AppA]`, matching the original

#### Scenario: Golden fixture reflects the new field at the unchanged schema version
- **GIVEN** the golden-file wire-format test asserting the exact JSON shape of an exported rule
- **WHEN** a rule containing `isIncludeMode` is exported
- **THEN** the golden fixture includes the `isIncludeMode` field in its expected JSON
- **AND** `schemaVersion` in the fixture remains `2`
