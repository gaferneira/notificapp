# filtered-list-empty-state Specification

## Purpose

Defines how list screens (Inbox and Data) tell "there is no data at all" apart from "filters or search hide every result", and how the user recovers from the second case with one tap. The shared `FilterEmptyState` component lives in `core/ui/components` and takes plain parameters only.

## Requirements

### Requirement: Shared filtered-empty component

The system SHALL provide a `FilterEmptyState` component in `core/ui/components` that shows the title "Nothing matches your filters" and a "Clear filters" action. The component MUST accept plain parameters (no feature or domain types), MUST be styled through the Styles API, and MUST NOT use ad-hoc `.copy(alpha = ...)` color literals. All visible text MUST come from `strings.xml`.

#### Scenario: Component renders title and action
- GIVEN `FilterEmptyState` is composed with a clear-filters callback
- WHEN it is displayed
- THEN the text "Nothing matches your filters" and a "Clear filters" action are visible

#### Scenario: Clear action invokes the callback once
- GIVEN `FilterEmptyState` is displayed
- WHEN the user taps "Clear filters"
- THEN the supplied callback is invoked exactly once

### Requirement: Filtered-empty versus truly-empty distinction

Each list screen SHALL show `FilterEmptyState` only when the list has zero results AND at least one filter or a non-blank search query is active. When the list has zero results and no filter or search is active, the screen MUST show its own "no data" empty state instead. Neither empty state MUST be shown while the list is loading or refreshing.

#### Scenario: Inbox truly empty
- GIVEN the Inbox has no notifications and no filter or search is active
- WHEN the Inbox finishes loading
- THEN the Inbox "No notifications yet" empty state is shown
- AND `FilterEmptyState` is not shown

#### Scenario: Inbox filtered empty
- GIVEN the Inbox contains notifications
- AND the user has an active filter that matches none of them
- WHEN the Inbox finishes loading
- THEN `FilterEmptyState` is shown
- AND "No notifications yet" is not shown

#### Scenario: Inbox search-only empty
- GIVEN the Inbox contains notifications and no filter is active
- WHEN the user enters a search query that matches none of them
- THEN `FilterEmptyState` is shown

#### Scenario: No empty state flicker while loading
- GIVEN a list screen is refreshing after a filter change
- WHEN the paging load state is not `NotLoading`
- THEN neither the "no data" empty state nor `FilterEmptyState` is shown

### Requirement: Clear filters resets filters and search

Activating "Clear filters" SHALL reset every active filter dimension and the search query of that screen to their defaults, in a single action, and the list MUST then reload with the unfiltered scope.

#### Scenario: Clear filters on Inbox
- GIVEN the Inbox has active filters and a search query and shows `FilterEmptyState`
- WHEN the user taps "Clear filters"
- THEN all Inbox filters are removed
- AND the search query is empty
- AND the unfiltered list is displayed

#### Scenario: Clear filters on Data
- GIVEN the Data screen has a rule filter, an app filter, a date range, and a search query, and shows `FilterEmptyState`
- WHEN the user taps "Clear filters"
- THEN the rule, app, and date range filters are removed
- AND the search query is empty
- AND the unfiltered list is displayed

#### Scenario: Clear filters on an empty list that has no data
- GIVEN clearing filters leaves a screen with zero rows in total
- WHEN the list finishes reloading
- THEN the screen's "no data" empty state is shown, not `FilterEmptyState`
