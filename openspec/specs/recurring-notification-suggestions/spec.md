# recurring-notification-suggestions Specification

## Purpose

Defines a pure-Kotlin, non-ML heuristic that detects notifications repeating for the same app with a similar title, and surfaces them on Home as "you could automate this" suggestions — ranked toward genuinely un-automated repetition, dismissible per suggestion.

## Requirements

### Requirement: Repetition is detected per app via title normalization
The system SHALL detect a notification as "repeating" when multiple notifications from the same app normalize to the same title key, using a normalization strategy that folds superficial differences (e.g. casing, dynamic tokens) so near-identical titles group together. The exact normalization rules (case-folding, dynamic-token stripping, similarity threshold) are a design-phase decision; this requirement constrains only the observable grouping behavior.

#### Scenario: Notifications with identical titles group together
- GIVEN 3 notifications from the same app, all with the exact same title text
- WHEN the heuristic runs
- THEN all 3 are grouped under one repeated-notification candidate for that app

#### Scenario: Titles differing only in a dynamic value still group together
- GIVEN 2 notifications from the same app whose titles differ only in an incidental value (e.g. an amount, a name, a timestamp fragment)
- WHEN the heuristic runs
- THEN the two are grouped under the same repeated-notification candidate

#### Scenario: Unrelated titles from the same app do not group together
- GIVEN 2 notifications from the same app with clearly unrelated titles
- WHEN the heuristic runs
- THEN they are NOT grouped into the same candidate

### Requirement: A repeat count, time window, and day-spread gate candidacy
A grouped title key SHALL only become a suggestion candidate once its repeat count within a rolling day-window meets a configured threshold AND those occurrences span at least a configured minimum number of distinct calendar days — a same-day burst from a single event does not qualify no matter how many notifications it produces. The specific repeat-count (N), window-length-in-days (M), and minimum-distinct-days values are a design-phase decision; this requirement constrains only that all three gates exist and are applied together.

#### Scenario: Below-threshold repetition does not surface
- GIVEN a title key has repeated fewer times than the configured threshold within the configured window
- WHEN suggestions are computed
- THEN no suggestion is produced for that title key

#### Scenario: Repetition outside the window does not count
- GIVEN a title key's occurrences are spread further apart than the configured window allows
- WHEN suggestions are computed
- THEN occurrences outside the window are not counted toward the threshold

#### Scenario: A same-day burst does not surface as recurring
- GIVEN a title key meets the repeat-count threshold, but all of its occurrences fall on a single calendar day (e.g. one event emitting several rapid updates)
- WHEN suggestions are computed
- THEN no suggestion is produced for that title key, because the distinct-days minimum is not met

### Requirement: Suggestions are ranked by rule-coverage first, repeat count second
The system SHALL classify each candidate's coverage into three states: not covered by any rule targeting its app; covered by a rule targeting its app whose conditions do not currently match this notification pattern; and covered by a rule whose conditions already match this notification pattern. The system SHALL rank the first state above the second, using per-app repeat count as the tiebreaker among candidates of equal coverage state. The third state — a rule already firing on this exact pattern — is not a suggestion candidate at all and SHALL be excluded, since offering to "automate" something already automated would be incorrect, not merely low-priority. The exact precision of the coverage classification (e.g. how a rule's conditions are evaluated against a candidate's notifications) is a design-phase decision.

#### Scenario: Uncovered candidate ranks above an app-covered-but-not-matching candidate
- GIVEN one candidate whose app has no rule targeting it, and one candidate whose app is targeted by a rule but that rule's conditions do not match this notification pattern
- WHEN suggestions are ranked
- THEN the uncovered candidate ranks first, and the app-covered candidate still appears, ranked below it

#### Scenario: Equal coverage status breaks tie by repeat count
- GIVEN two candidates with the same coverage state
- WHEN suggestions are ranked
- THEN the candidate with the higher repeat count ranks first

#### Scenario: A pattern already matched by an existing rule is not suggested
- GIVEN a candidate whose notifications are already matched by an existing rule's conditions
- WHEN suggestions are computed
- THEN no suggestion is produced for that candidate, regardless of its repeat count

### Requirement: Per-suggestion actions
Each surfaced suggestion SHALL offer "Create rule from this" (opens the rule editor pre-filled with the suggestion's app/title pattern) and "Skip similar" (dismisses this and future occurrences of the same normalized-title key for that app).

#### Scenario: Creating from a suggestion opens a pre-filled editor
- GIVEN a suggestion for a specific app and normalized title
- WHEN the user taps "Create rule from this"
- THEN the rule editor opens pre-filled with a condition scoped to that app and title pattern

#### Scenario: Skipping dismisses the suggestion
- GIVEN a suggestion is visible
- WHEN the user taps "Skip similar"
- THEN the suggestion is removed from the current view

### Requirement: Dismissals persist in a purpose-built store
Dismissals SHALL persist in a new, minimal Room table scoped to this feature only, keyed by app package and normalized-title-key, storing the dismissal timestamp. This table SHALL NOT be designed as a generic/reusable dismissal mechanism for other features.

#### Scenario: Dismissed key excluded from future suggestion runs
- GIVEN the user dismissed a suggestion for a given app package and normalized-title-key
- WHEN the heuristic runs again on a later launch, and new notifications matching that same app/title-key have arrived since
- THEN no suggestion is produced for that app/title-key combination

#### Scenario: Dismissal is scoped to its exact key, not the whole app
- GIVEN the user dismissed a suggestion for one normalized-title-key on an app
- WHEN a different, unrelated normalized-title-key later repeats for the same app
- THEN a suggestion is produced for that different title key
