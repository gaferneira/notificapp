# data-deletion Specification

## Purpose

Defines safe deletion of extracted data: removing a single entry, and bulk-deleting the currently filtered set with a mandatory preview/confirmation step, since bulk delete resolves a filtered-ID set transactionally rather than re-running a duplicated WHERE-clause DELETE.

## Requirements

### Requirement: Single-entry deletion

The system SHALL support deleting one `ExtractedFieldValue` entry by ID.

#### Scenario: Deleting a single entry removes only that entry
- GIVEN 10 extracted values exist
- WHEN the user deletes one specific entry
- THEN exactly 9 entries remain and the deleted entry is no longer returned by browse queries

#### Scenario: Deleting an already-deleted entry does not error
- GIVEN an entry has already been deleted (e.g. by a concurrent bulk delete)
- WHEN the user attempts to delete the same entry ID again
- THEN the operation completes as a no-op without throwing

### Requirement: Bulk delete resolves the filtered ID set transactionally

The system SHALL implement bulk delete by first resolving the set of IDs matching the currently applied filters, then deleting exactly those IDs inside a single transaction — not by re-issuing the filter's WHERE clause as a DELETE statement, so the deleted set is guaranteed to match what was previewed.

#### Scenario: Bulk delete removes exactly the previewed set
- GIVEN a filter matching 25 entries, previewed to the user as "25 entries will be deleted"
- WHEN the user confirms bulk delete
- THEN exactly those 25 entries are removed, even if new entries arrive after the preview but before confirmation

#### Scenario: New data arriving between preview and confirmation is not deleted
- GIVEN a bulk-delete preview computed a filtered ID set of N entries
- WHEN a new extraction matching the same filter arrives after the preview but before the user confirms
- THEN the new entry is NOT included in the delete (only the originally-resolved ID set is deleted)

### Requirement: Bulk delete requires a preview showing the affected count before committing

The system SHALL require the UI to display the count (and SHOULD allow inspecting the affected rows) of entries that will be deleted before the user can confirm a bulk delete.

#### Scenario: User sees affected count before confirming
- GIVEN the user has applied filters and initiates bulk delete
- WHEN the confirmation step is shown
- THEN it displays the exact number of entries that will be deleted

#### Scenario: Zero-result filter blocks or no-ops a bulk delete confirmation
- GIVEN a filter combination matching zero entries
- WHEN the user initiates bulk delete
- THEN the preview shows 0 entries and confirming performs no deletion (not an error)

### Requirement: Deletion is safe while the browse list is paginating

The system SHALL ensure that deleting (single or bulk) while the user is actively scrolling/paginating the browse list does not corrupt pagination state; Paging3 invalidation SHALL refresh the list to reflect the deletion.

#### Scenario: Deleting a visible row mid-scroll refreshes the list without crashing
- GIVEN the user has scrolled several pages into the browse list
- WHEN an entry in an already-loaded page is deleted
- THEN the Paging3 source invalidates and the list reloads reflecting the deletion, without an index-out-of-bounds or crash

#### Scenario: Bulk delete during active pagination does not leave stale rows visible
- GIVEN the user is mid-scroll through a filtered list
- WHEN a bulk delete removes a large portion of the currently loaded pages
- THEN subsequent scrolling reflects the updated (smaller) dataset, not stale cached pages

### Requirement: Dry-run executions are excluded from deletion targeting by default

The system SHALL exclude any extracted value whose source `RuleExecution` has `wasDryRun = true` from bulk-delete filter matching by default (single-entry deletion by explicit ID is unaffected, since the user is targeting a specific already-visible row).

#### Scenario: Bulk delete does not target dry-run extractions
- GIVEN a filter matching 10 real extractions and 3 dry-run extractions
- WHEN the user previews and confirms bulk delete
- THEN only the 10 real extractions are deleted; the 3 dry-run extractions remain untouched

### Requirement: Deletion is unaffected by a concurrent export snapshot

The system SHALL allow deletion to proceed independently of an in-progress export; deletion SHALL NOT be blocked waiting on an export snapshot beyond normal transactional isolation.

#### Scenario: Delete proceeds while an export is in progress
- GIVEN an export of the filtered dataset is currently reading data
- WHEN the user deletes an entry from that same filtered set
- THEN the delete completes successfully and does not deadlock or wait indefinitely on the export
