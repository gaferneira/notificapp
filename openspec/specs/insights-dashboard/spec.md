# insights-dashboard Specification

## Purpose

Defines the Insights dashboard, the Data tab (bottom-nav label "Insights"): an at-a-glance, on-device summary of what rules did over a selectable period, with drill-downs to the Inbox, Rule Details, Webhooks, Rules and the Extracted data browser. All figures exclude dry-run executions and update live as data changes.

## Requirements

### Requirement: Period toggle

The screen SHALL offer a 7 / 30 day toggle (default 7). The period covers local calendar days, today included. Switching it SHALL recompute the period-based sections; selecting the already-selected period SHALL do nothing.

#### Scenario: Switching to 30 days
- GIVEN Insights shows the 7 day period
- WHEN the user selects 30 days
- THEN the overview tiles, activity chart, totals, top rules, action outcomes and top apps reflect the last 30 local days

### Requirement: Overview tiles

The screen SHALL show four tiles: Captured (notifications in the period), Handled by rules (processed notifications in the period), Actions run (actions that succeeded or failed) and Values extracted. Actions run SHALL show "N failed" when at least one action failed. Captured opens the Inbox with all statuses, Handled by rules opens the Inbox filtered to Processed, Values extracted opens the Extracted data browser; each destination is bounded from the period start for that visit (removable), so it lists exactly what the tile counted. Actions run is not tappable.

#### Scenario: Failed actions are surfaced
- GIVEN 10 actions ran in the period, 2 of them failed
- WHEN Insights is displayed
- THEN the Actions run tile shows 10 and "2 failed"

#### Scenario: Handled by rules drill-down
- GIVEN Insights is displayed
- WHEN the user taps Handled by rules
- THEN the Inbox opens with the Processed filter, bounded from the period start

### Requirement: Activity chart

The screen SHALL show a bar chart of daily rule runs (non-dry-run executions) with exactly one bar per day of the period, oldest first, zero-filled, with an accessibility description giving total and peak day. The section SHALL be hidden if there is no trend data.

#### Scenario: Zero-activity days appear
- GIVEN a 7 day period where one day had no rule runs
- WHEN the chart is displayed
- THEN 7 bars are shown and that day's bar has zero height

### Requirement: Extracted totals

The screen SHALL list, per numeric or currency extraction field, the sum and the number of values in the period, titled "Rule · Field". Sums are per field and never mixed across fields. Tapping a row opens the Extracted data browser pre-filtered to that rule and bounded from the period start. The section SHALL be hidden when empty.

#### Scenario: Totals drill-down
- GIVEN rule "Bank" has a currency field "Amount" with values in the period
- WHEN the user taps its row
- THEN the Extracted data browser opens filtered to rule "Bank"

### Requirement: Top rules and quiet rules

The screen SHALL list up to 5 rules by run count in the period (tap opens Rule Details) and, under "Quiet rules", every enabled rule with no non-dry-run execution in the last 30 days, independent of the period toggle. The section SHALL be hidden when both lists are empty.

#### Scenario: Quiet rule listed
- GIVEN an enabled rule has not run in 30 days
- WHEN Insights is displayed with either period
- THEN the rule appears under Quiet rules with "No runs in 30 days"

### Requirement: Action outcomes

The screen SHALL show, per action type that has outcomes in the period, the counts succeeded, failed and skipped (suppressed counts as skipped). The section SHALL be hidden when empty.

#### Scenario: Outcomes per type
- GIVEN a webhook action succeeded 3 times and failed once
- WHEN Insights is displayed
- THEN the Webhook row shows "3 succeeded · 1 failed · 0 skipped"

### Requirement: Webhook health

The screen SHALL show, per configured webhook, its last delivery status (Delivered, Config error, Unreachable, or No deliveries yet), the time of the last delivery when known, and the number of pending retries when above zero. It uses current webhook state only (no delivery history). Tapping the section opens the webhook list. The section SHALL be hidden when no webhook exists.

#### Scenario: Pending retries
- GIVEN a webhook has 2 queued failed deliveries
- WHEN Insights is displayed
- THEN its row shows the last status and "2 pending retries" in the error color

### Requirement: Top apps

The screen SHALL list up to 5 source apps by captured notifications in the period, hidden when empty.

#### Scenario: Top apps limited
- GIVEN notifications from 8 apps in the period
- WHEN Insights is displayed
- THEN the 5 apps with the most notifications are listed

### Requirement: Browse extracted data call to action

The screen SHALL end with a "Browse extracted data" button that opens the Extracted data browser without a filter.

#### Scenario: Browse button
- GIVEN Insights shows content
- WHEN the user taps "Browse extracted data"
- THEN the browser opens with no filter

### Requirement: Global empty state

When no rule exists and the period has no captured or handled notifications, actions or extracted values, the screen SHALL show an empty state ("No insights yet") with a "Go to rules" action that switches to the Rules tab, instead of the sections.

#### Scenario: Fresh install
- GIVEN no rules exist and nothing was captured
- WHEN the user opens Insights
- THEN the empty state is shown
- AND tapping "Go to rules" switches to the Rules tab

### Requirement: Loading and error states

Until the first result arrives the screen SHALL show a progress indicator. If loading fails the screen SHALL show a full-screen error with a Retry button that reloads the data; while a period switch or retry is loading, the previous snapshot is kept in state. The period toggle is not shown in the error state.

#### Scenario: Retry after failure
- GIVEN loading failed and the error is shown
- WHEN the user taps Retry
- THEN loading restarts and, on success, the dashboard replaces the error
