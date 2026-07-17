# data-browsing Specification

## Purpose

Defines paginated, filterable, searchable, sortable browsing of extracted rule data (`extracted_field_values` joined with their source `RuleField`, `RuleExecution`, `Notification`, `Rule`, and app). This is the read surface a future visualization phase builds on, so its query contract (filters, sort, empty behavior) must be stable and fully tested up front.

## Requirements

### Requirement: Paginated joined browse query

The system SHALL expose a Paging3 source that returns `DataBrowserRow` (field name, value, source notification summary, rule name, source app, timestamp) joined from `ExtractedFieldValue`, `RuleField`, `RuleExecution`, `Notification`, and `Rule`. The query MUST NOT require loading the full result set into memory.

#### Scenario: Browsing returns joined rows in reverse-chronological order by default
- GIVEN extracted field values exist across multiple rules and apps
- WHEN the user opens the data browser with no filters or explicit sort applied
- THEN rows are returned page by page, each populated with field name (joined from `RuleField`), value, source app, rule name, and timestamp, newest first

#### Scenario: Empty dataset yields an empty page, not an error
- GIVEN no `ExtractedFieldValue` rows exist yet
- WHEN the browse query is executed
- THEN it returns an empty page (zero items) with no exception

### Requirement: Multi-filter combination (rule, app, date range, field type, text search)

The system SHALL support filtering the browse query by any combination of: rule, source app, date range, field type, and free-text search over extracted values, using an `IN` + `hasFilter` idiom (mirroring `NotificationDao.getFilteredPaged`) so each filter is optional and independently combinable.

#### Scenario: Single filter narrows results
- GIVEN extracted values from rules A and B
- WHEN the user filters by rule A only
- THEN only rows produced by rule A are returned

#### Scenario: Combined filters intersect
- GIVEN extracted values across several apps, rules, and dates
- WHEN the user applies a rule filter, an app filter, and a date range simultaneously
- THEN only rows satisfying all three constraints are returned

#### Scenario: Filter combination yields zero results
- GIVEN a rule filter and a date range that share no matching rows
- WHEN both filters are applied together
- THEN the query returns an empty page, not an error, and the UI SHALL be able to distinguish "no data at all" from "no data matches this filter combination"

### Requirement: Free-text search across extracted values

The system SHALL support free-text search over `value_text` using an FTS4 virtual table (mirroring `notifications_fts`) with input sanitized via the existing `FtsQuerySanitizer`.

#### Scenario: Text search matches partial value content
- GIVEN an extracted value containing "INV-2024-001"
- WHEN the user searches "2024"
- THEN the row is included in the results

#### Scenario: Search input with FTS-reserved characters does not crash the query
- GIVEN the user enters a search term containing FTS special syntax characters (e.g. `"`, `*`, `-`)
- WHEN the search is executed
- THEN the sanitizer neutralizes the syntax and the query executes without throwing

### Requirement: Sorting by date, rule, app, or field name

The system SHALL support sorting browse results by extraction date, rule name, source app, or field name, in ascending or descending order, applied together with any active filters.

#### Scenario: Sort changes result ordering without changing result set
- GIVEN a filtered result set of N rows
- WHEN the sort key changes from date to rule name
- THEN the same N rows are returned, reordered by rule name

### Requirement: Dry-run executions are excluded from browse results by default

The system SHALL exclude any `ExtractedFieldValue` whose source `RuleExecution` has `wasDryRun = true` from all browse and search results by default.

#### Scenario: Dry-run extractions do not appear in the browse list
- GIVEN a rule has produced 5 real extractions and 2 dry-run extractions (`wasDryRun = true`)
- WHEN the user browses with no filters applied
- THEN only the 5 real extractions are returned; the 2 dry-run extractions are excluded

### Requirement: Browsing degrades gracefully under large datasets

The system SHALL remain responsive (paged, not loaded eagerly) regardless of total extracted-value volume.

#### Scenario: Large dataset still pages correctly
- GIVEN tens of thousands of extracted values
- WHEN the user scrolls through the paginated list
- THEN each page loads independently and the UI does not attempt to materialize the full dataset in memory
