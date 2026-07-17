# data-statistics Specification

## Purpose

Defines the aggregate statistics computed over extracted rule data: total volume, recent activity, per-rule and per-app breakdowns, and a day-bucketed trend series. This phase computes and exposes these values from the data layer only — no chart rendering — so a future visualization phase can consume them without re-deriving aggregation logic.

## Requirements

### Requirement: Core summary counts

The system SHALL compute total extraction count, extraction count for the current calendar week (user's local timezone), and the single most-active rule (by extraction count) over all extracted values.

#### Scenario: Summary reflects current data
- GIVEN 100 total extracted values, 12 of which occurred within the current calendar week
- WHEN the summary is computed
- THEN total count is 100 and this-week count is 12

#### Scenario: No data yields a zero-value summary, not an error
- GIVEN no extracted values exist
- WHEN the summary is computed
- THEN total count is 0, this-week count is 0, and most-active-rule is absent/null (no exception)

#### Scenario: Tie in most-active-rule resolves deterministically
- GIVEN two rules with an equal, highest extraction count
- WHEN the most-active rule is computed
- THEN the system SHALL deterministically pick one (e.g. by stable secondary sort such as rule ID) rather than returning an arbitrary or unstable result

### Requirement: Per-rule and per-app extraction counts

The system SHALL compute extraction counts grouped by rule and, separately, grouped by source app.

#### Scenario: Per-rule counts sum to total
- GIVEN extractions distributed across 3 rules
- WHEN per-rule counts are computed
- THEN the sum of all per-rule counts equals the total extraction count

#### Scenario: Per-app counts reflect distinct source apps
- GIVEN extractions from 2 distinct source apps
- WHEN per-app counts are computed
- THEN exactly 2 app entries are returned, each with its correct count

### Requirement: Day-bucketed trend series (7/30 days) in local timezone

The system SHALL compute a trend series of daily extraction counts for the last 7 and last 30 days, bucketed by calendar day in the device's local timezone (not UTC). A day with zero extractions SHALL appear in the series with a count of 0, not be omitted.

#### Scenario: Extractions near local midnight bucket to the correct local day
- GIVEN an extraction timestamp that is 11:30 PM local time but already past midnight UTC
- WHEN the trend series is computed
- THEN the extraction is bucketed under the local calendar day, not the UTC day

#### Scenario: Days with no activity still appear as zero-count buckets
- GIVEN a 7-day window where day 3 has no extractions
- WHEN the trend series is computed
- THEN the series contains exactly 7 entries, with day 3's count equal to 0

#### Scenario: 30-day trend spans a daylight-saving transition without dropping or duplicating a day
- GIVEN a 30-day window that crosses a DST transition in the device's local timezone
- WHEN the trend series is computed
- THEN the series still contains exactly 30 daily buckets with no duplicated or missing calendar day

### Requirement: Dry-run executions are excluded from statistics by default

The system SHALL exclude any extraction whose source `RuleExecution` has `wasDryRun = true` from all counts, per-rule/per-app breakdowns, and trend series by default.

#### Scenario: Dry-run extractions do not inflate summary counts
- GIVEN 100 real extracted values and 10 dry-run extracted values (`wasDryRun = true`)
- WHEN the summary is computed
- THEN total count is 100, not 110, and the dry-run entries are excluded from per-rule/per-app counts and the trend series

### Requirement: Statistics are computed but not rendered as charts (Phase 1)

The system SHALL expose statistics as structured data (counts, series) consumable by a UI layer. It SHALL NOT render charts/graphs in this phase.

#### Scenario: Statistics are available as plain data for a future consumer
- GIVEN the statistics query results
- WHEN a UI layer requests them
- THEN it receives structured numeric/series data with no chart-rendering dependency required
