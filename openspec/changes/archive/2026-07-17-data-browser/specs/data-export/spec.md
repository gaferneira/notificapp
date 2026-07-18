# data-export Specification

## Purpose

Defines on-demand export of the user's currently filtered/visible extracted data set as CSV or JSON, delivered via the Android share sheet. Export is manual and snapshot-based — no scheduling, no background jobs — consistent with the local-first, user-controlled data story.

## Requirements

### Requirement: Export the currently filtered/visible dataset

The system SHALL export exactly the dataset matching the currently applied filters (rule, app, date range, field type, text search) at the moment export is invoked — not the full unfiltered dataset.

#### Scenario: Export with no filters exports everything
- GIVEN no filters are applied
- WHEN the user exports
- THEN all extracted values are included in the export

#### Scenario: Export with active filters exports only the filtered subset
- GIVEN a rule filter is applied narrowing the browse view to 20 of 500 rows
- WHEN the user exports
- THEN the export file contains exactly those 20 rows

#### Scenario: Exporting a zero-result filter combination produces a valid, empty export
- GIVEN filters applied that match zero rows
- WHEN the user exports
- THEN a valid, well-formed CSV/JSON file is produced containing headers/structure but no data rows, not an error

### Requirement: CSV and JSON export formats

The system SHALL support exporting as CSV or as JSON, user-selectable, both containing the same logical fields (field name, value, source app, rule, timestamp).

#### Scenario: CSV export is well-formed
- GIVEN a non-empty filtered dataset
- WHEN CSV export is chosen
- THEN the resulting file is valid CSV with a header row and one row per extracted value, with values containing commas/quotes properly escaped

#### Scenario: JSON export is well-formed
- GIVEN a non-empty filtered dataset
- WHEN JSON export is chosen
- THEN the resulting file is valid JSON representing an array of extracted-value objects with consistent field names

### Requirement: Export is delivered via the Android share sheet, on-demand only

The system SHALL trigger export only in direct response to an explicit user action and SHALL deliver the resulting file through the Android share sheet (`ACTION_SEND` via `FileProvider`, mirroring the `RulesScreen` pattern). No scheduled or background export SHALL exist.

#### Scenario: Export action opens the share sheet
- GIVEN the user taps the export action with a chosen format
- WHEN export completes
- THEN the Android share sheet opens with the generated file attached

### Requirement: Export snapshots data independently of concurrent deletion

The system SHALL export a stable snapshot of the filtered dataset even if a delete operation is initiated concurrently; export SHALL NOT partially reflect a delete that started after the export began, and SHALL NOT block or be blocked by a concurrent delete beyond normal transactional isolation.

#### Scenario: Concurrent delete does not corrupt an in-progress export
- GIVEN an export is in progress reading the filtered dataset
- WHEN the user concurrently deletes a subset of that same data
- THEN the export completes using a consistent snapshot (either fully before or fully excluding the deleted rows, never a partially-corrupted mix) and does not throw

### Requirement: Dry-run executions are excluded from export by default

The system SHALL exclude any extracted value whose source `RuleExecution` has `wasDryRun = true` from export output by default, regardless of format.

#### Scenario: Dry-run extractions are not included in the exported file
- GIVEN a filtered dataset containing both real and dry-run extractions
- WHEN the user exports as CSV or JSON
- THEN the exported file contains only the real (non-dry-run) extractions

### Requirement: Large dataset export is bounded/chunked

The system SHALL export large filtered datasets without loading the entire result set into memory at once.

#### Scenario: Exporting tens of thousands of rows does not exhaust memory
- GIVEN a filtered dataset of 50,000+ rows
- WHEN the user exports as CSV or JSON
- THEN the export completes by streaming/chunking reads rather than materializing all rows simultaneously
