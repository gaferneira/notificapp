# data-browsing Specification

## Purpose

Defines paginated, filterable, searchable, sortable browsing of extracted rule data (`extracted_field_values` joined with their source `RuleField`, `RuleExecution`, `Notification`, `Rule`, and app). The browser is a pushed "Extracted data" route (back arrow, no bottom navigation) reached from the Insights dashboard.

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

The system SHALL support filtering the browse query by any combination of: rule, source app, date range, field type, and free-text search over extracted values, using an `IN` + `hasFilter` idiom (mirroring `NotificationDao.getFilteredPaged`) so each filter is optional and independently combinable. The user interface SHALL expose rule, source app, and date range (plus search); field type remains a domain-level filter only and is not exposed in the UI.

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
- THEN the query returns an empty page, not an error
- AND the UI shows `FilterEmptyState` (filtered empty), distinct from the "no data at all" state shown when no extracted data exists

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

### Requirement: Data filter sheet (rule, app, date range)

The Extracted data screen SHALL provide a filter bottom sheet that lets the user choose rules, apps, and a date range. The sheet MUST edit a draft copy of the filter; the draft MUST only take effect on Apply. The field-type filter MUST NOT be exposed in the UI. The date range MUST be chosen with the Material 3 `DateRangePicker`; no preset ranges are offered. The sheet MUST NOT display a live result count. The rule picker MUST list only rules that have at least one Extract-data field, so every offered rule can return results.

#### Scenario: Rule picker lists only extracting rules
- GIVEN rule A has an Extract-data field, rule B has no Extract-data action, and rule C has an Extract-data action with no fields
- WHEN the user opens the filter sheet
- THEN rule A is listed in the rule picker
- AND rules B and C are not listed

#### Scenario: Open sheet shows current filter as draft
- GIVEN the Extracted data screen has a rule filter active
- WHEN the user opens the filter sheet
- THEN the sheet lists rules, apps, and a date range control
- AND the active rule is preselected
- AND no field-type control is shown

#### Scenario: Apply commits the draft
- GIVEN the filter sheet is open
- WHEN the user selects an app and a date range and taps Apply
- THEN the sheet closes
- AND the Data list reloads with the app and date range filters applied
- AND matching chips are displayed

#### Scenario: Cancel discards the draft
- GIVEN the filter sheet is open and the user changed the draft selection
- WHEN the user dismisses the sheet without tapping Apply
- THEN the active filter is unchanged
- AND the list does not reload

#### Scenario: Date range is chosen via the picker only
- GIVEN the filter sheet is open
- WHEN the user edits the date range
- THEN a `DateRangePicker` is presented
- AND no preset options (such as last 7 or 30 days) are offered

#### Scenario: No live count in the sheet
- GIVEN the filter sheet is open with a draft selection
- WHEN the draft changes
- THEN no result count is shown or computed

### Requirement: Date range bounds are inclusive

A selected date range SHALL include every extracted value whose timestamp falls from the start of the first selected day through the end of the last selected day, in the device's local time zone.

#### Scenario: Values on the first and last selected days are included
- GIVEN extracted values at 00:00:00 on 2026-03-01, 23:59:59 on 2026-03-03, and 00:00:00 on 2026-03-04
- WHEN the user applies the date range 2026-03-01 to 2026-03-03
- THEN the values on 2026-03-01 and 2026-03-03 are returned
- AND the value on 2026-03-04 is not returned

#### Scenario: Single-day range
- GIVEN extracted values on 2026-03-01 and 2026-03-02
- WHEN the user applies a range whose start and end are both 2026-03-01
- THEN only the value on 2026-03-01 is returned

### Requirement: Active filter chips

The Extracted data screen SHALL display one removable chip per active filter value (each selected rule, each selected app, and the date range) while any such filter is active. Removing a chip MUST remove only that filter value and reload the list. When no filter is active, no chips are shown.

#### Scenario: Chips reflect active filters
- GIVEN the user applied rule A, app X, and a date range
- WHEN the Extracted data screen is displayed
- THEN a chip for rule A, a chip for app X, and a chip for the date range are visible

#### Scenario: Removing a chip removes only that filter
- GIVEN chips for rule A, app X, and a date range are visible
- WHEN the user removes the app X chip
- THEN the app X chip disappears
- AND the rule A and date range chips remain
- AND the list reloads with rule A and the date range applied

#### Scenario: Removing the last chip clears the scope
- GIVEN only one chip is visible
- WHEN the user removes it
- THEN no chips are shown
- AND the unfiltered list is displayed

### Requirement: Filter scope is visible for bulk actions

Export and bulk delete SHALL continue to act on the currently applied Data filter, and the active chips MUST be visible on the Extracted data screen whenever the filter is non-empty so the scope of these actions is apparent.

#### Scenario: Bulk delete targets the filtered scope shown by chips
- GIVEN the user applied a rule filter and its chip is visible
- WHEN the user starts "Delete filtered data"
- THEN only rows matching the filter are deleted
- AND the chip remains visible while the action is available

#### Scenario: Export follows the filter
- GIVEN the user applied an app filter and a date range
- WHEN the user exports Data
- THEN the export contains only rows matching both filters

### Requirement: Session-only Data filter lifetime

The Data filter and Data search query SHALL be held in `DataBrowserViewModel` state only. They MUST NOT be persisted to preferences or storage. The filter MUST be lost when the user leaves the browser and opens it again.

#### Scenario: Filter survives configuration change
- GIVEN the user applied a rule filter on the Extracted data screen
- WHEN the device is rotated
- THEN the rule filter and its chip are still active

#### Scenario: Filter is lost when the browser is reopened
- GIVEN the user applied a rule filter on the Extracted data screen
- WHEN the user navigates back and opens the browser again without a rule pre-filter
- THEN no filter is active
- AND no chips are shown

#### Scenario: Filter is not persisted
- GIVEN the user applied a filter on the Extracted data screen
- WHEN the app process is restarted
- THEN no Data filter is active
- AND no `UserPreferencesRepository` value was written for it

### Requirement: Data empty states are distinct

The Extracted data screen SHALL show one of two empty states when the list has zero rows after loading. If no filter and no search query is active and no extracted data exists, it MUST show a "no data" state that explains how Data gets filled (fields declared with Extract data in a rule) and offers a "Go to Rules" action that navigates to the Rules screen. If a filter or search is active, it MUST show `FilterEmptyState`.

#### Scenario: No data at all
- GIVEN no extracted values exist
- AND no filter or search is active
- WHEN the Extracted data screen finishes loading
- THEN the explanation of Extract data fields is shown
- AND a "Go to Rules" action is shown
- AND `FilterEmptyState` is not shown

#### Scenario: Go to Rules action
- GIVEN the "no data" state is shown
- WHEN the user taps "Go to Rules"
- THEN the app navigates to the Rules screen

#### Scenario: Filters hide every row
- GIVEN extracted values exist
- AND the active filter matches none of them
- WHEN the Extracted data screen finishes loading
- THEN `FilterEmptyState` is shown
- AND the "Go to Rules" explanation is not shown

#### Scenario: Clear filters from the Data empty state
- GIVEN `FilterEmptyState` is shown on Data with filters and a search query active
- WHEN the user taps "Clear filters"
- THEN the filters, chips, and search query are all cleared
- AND the unfiltered rows are listed

### Requirement: Pushed route with optional rule pre-filter

The browser SHALL be a pushed route (`Screen.DataBrowser(ruleId, since)`) titled "Extracted data" with a back arrow and no bottom navigation. When opened with a `ruleId`, the list SHALL start filtered to that rule, shown as a removable chip. When opened with a `since` (inclusive epoch-millis lower bound, e.g. an Insights period start), the list SHALL start with an open-ended date range from that instant, shown as a removable "Since <date>" chip. Both are seeded only on first creation of the screen's state: a chip the user removed SHALL stay removed after process death and recreation, and neither value is persisted beyond that visit.

#### Scenario: Opened from the Insights browse button
- GIVEN the user is on Insights
- WHEN the user taps "Browse extracted data"
- THEN the browser opens with no filter and shows a back arrow and no bottom navigation

#### Scenario: Opened pre-filtered to a rule
- GIVEN the user taps an extracted-totals row for rule A on Insights
- WHEN the browser opens
- THEN only rule A's rows are listed and a chip for rule A is visible
- AND removing the chip shows all rows

#### Scenario: Opened with a since window
- GIVEN the user taps an Insights tile that passes a period start as `since`
- WHEN the browser opens
- THEN only data from that instant on is listed and a "Since <date>" chip is visible
- AND removing the chip shows all rows

#### Scenario: Open-ended date range
- GIVEN a date range with only a lower bound (no upper bound)
- WHEN the browser lists rows
- THEN rows at or after the lower bound are included with no upper limit

#### Scenario: Removed chips stay removed after process death
- GIVEN the browser was opened with a rule and a since and the user removed both chips
- WHEN the screen state is restored after process death with the same route arguments
- THEN neither filter is re-applied

### Requirement: Row tap opens notification details

#### Scenario: Tapping a row
- GIVEN the list shows rows
- WHEN the user taps a row
- THEN the details of the row's source notification are opened

### Requirement: Swipe delete with Undo

Swiping a row SHALL hide it and offer Undo; the repository delete SHALL be deferred until the Undo window ends (snackbar dismissed, or a ViewModel-owned timeout elapsed so a rotation never deletes early), another delete starts, an export or bulk-delete preview begins (so the hidden row is neither exported nor counted), or the screen is cleared.

#### Scenario: Undo restores the row
- GIVEN the user swiped a row away
- WHEN the user taps Undo before the snackbar ends
- THEN the row reappears and nothing is deleted

#### Scenario: Undo window ends
- GIVEN the user swiped a row away
- WHEN the snackbar is dismissed without Undo
- THEN the value is deleted

### Requirement: Overflow menu actions

The overflow menu SHALL offer sort options and "Delete filtered data" (bulk delete behind a confirmation dialog with the exact affected count).

#### Scenario: Bulk delete from the overflow menu
- GIVEN a filter is active
- WHEN the user chooses "Delete filtered data" and confirms
- THEN only the previewed matching rows are deleted

### Requirement: CURRENCY values keep their original text

For CURRENCY fields the list SHALL show the original extracted text (with its unit) when it is non-blank, and otherwise the locale-formatted number with two decimals.

#### Scenario: Currency with unit
- GIVEN a CURRENCY value with text "249.90 kr" and number 249.9
- WHEN the row is displayed
- THEN it shows "249.90 kr"

### Requirement: Browsing degrades gracefully under large datasets

The system SHALL remain responsive (paged, not loaded eagerly) regardless of total extracted-value volume.

#### Scenario: Large dataset still pages correctly
- GIVEN tens of thousands of extracted values
- WHEN the user scrolls through the paginated list
- THEN each page loads independently and the UI does not attempt to materialize the full dataset in memory
