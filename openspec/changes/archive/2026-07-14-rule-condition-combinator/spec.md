# rule-conditions Specification — Delta: Root-Level Condition Combinator

## Change Reference
`openspec/changes/rule-condition-combinator/proposal.md`. References CLAUDE.md "Development Status" for the pre-launch policies that allow destructive schema bumps — this delta installs the root-level combinator on the `rules` table.

## Purpose of This Delta

Today `RuleMatcher.matches` combines a rule's flat `conditions` list with AND only (`.all`). This delta adds an explicit, persisted, per-rule combinator — `ALL` (existing AND behavior, default) or `ANY` (OR) — without changing anything else about how individual conditions evaluate. It amends the `rule-conditions` spec's "Multiple conditions on a rule combine with AND" requirement, which becomes conditional on the new `conditionLogic` field.

This is a **domain + storage + wire + matcher** change only. No UI/editor surface is added; the rule editor continues to only ever construct `ALL` rules.

## Domain Model Changes

### New: `ConditionCombinator` enum
`domain/model/ConditionCombinator.kt` — new sealed/plain enum with exactly two members: `ALL`, `ANY`.

### Changed: `Rule`
`domain/model/Rule.kt` gains a new field:
- `conditionLogic: ConditionCombinator = ConditionCombinator.ALL`

Default preserves pre-change behavior for every existing in-memory `Rule` construction site that does not explicitly set it.

## Modified Requirement: Multiple conditions on a rule combine per `conditionLogic`

**Supersedes** the `rule-conditions` spec requirement "Multiple conditions on a rule combine with AND". A rule's `conditionLogic` (`ALL` or `ANY`) SHALL determine how its `conditions` list combines:
- `ALL` — every condition in the list SHALL match for the rule to match (unchanged AND semantics; this is the default).
- `ANY` — at least one condition in the list SHALL match for the rule to match (OR semantics).
- An empty `conditions` list SHALL match regardless of `conditionLogic` (global rule; matches today's `RuleMatcher.matches` empty-list short-circuit, which fires before the combinator is consulted).

`RuleMatcher.matches` SHALL accept the rule's `conditionLogic` (or an equivalent combinator parameter) and dispatch to `.all { }` for `ALL` or `.any { }` for `ANY` after the empty-list short-circuit. `RuleEngine` SHALL pass `rule.conditionLogic` from the domain model at the call site.

#### Scenario: ALL matches only when every condition matches
- **WHEN** a rule has `conditionLogic = ALL` and three conditions, all of which match the current notification/time
- **THEN** the rule matches
- **WHEN** any one of those three conditions instead fails to match
- **THEN** the rule does not match

#### Scenario: ANY matches when at least one condition matches
- **WHEN** a rule has `conditionLogic = ANY` and three conditions, and only one of them matches the current notification/time
- **THEN** the rule matches

#### Scenario: Empty conditions list matches under both combinators
- **WHEN** a rule has an empty `conditions` list and `conditionLogic = ALL`
- **THEN** the rule matches (global rule)
- **WHEN** the same rule instead has `conditionLogic = ANY`
- **THEN** the rule still matches

#### Scenario: ANY with every condition failing does not match
- **WHEN** a rule has `conditionLogic = ANY` and none of its conditions match
- **THEN** the rule does not match

#### Scenario: ANY short-circuits correctly regardless of match order
- **WHEN** a rule has `conditionLogic = ANY`, its first condition fails to match, and a later condition in the list matches
- **THEN** the rule matches

#### Scenario: Default combinator is ALL
- **WHEN** a `Rule` is constructed without explicitly setting `conditionLogic`
- **THEN** its `conditionLogic` is `ALL` and it evaluates with pre-change AND semantics

## New Requirement: Combinator persists through storage round-trip

The Room entity for a rule SHALL persist `conditionLogic` (e.g. a `condition_logic` column) with a default equal to `ALL`, and the entity-to-domain mapper (and its inverse) SHALL round-trip the value losslessly.

#### Scenario: Persisted rule round-trips its combinator
- **WHEN** a `Rule` with `conditionLogic = ANY` is mapped to its Room entity and back to a domain `Rule`
- **THEN** the resulting `Rule.conditionLogic` is `ANY`

#### Scenario: Pre-existing rows default to ALL
- **WHEN** a Room entity row has no explicit combinator value persisted (schema default applies)
- **THEN** it maps to domain `conditionLogic = ALL`

## New Requirement: Wire format encodes the combinator and defaults absent field to ALL

`RuleExportDto`'s `RuleDto` SHALL include the combinator field. Per CLAUDE.md's pre-launch policy, `RULE_EXPORT_SCHEMA_VERSION` is not bumped for this change — no shipped app has exported rules yet, so there is no compatibility contract for the version number to protect, and the codec's import guard (`export.schemaVersion <= RULE_EXPORT_SCHEMA_VERSION`) is unaffected by leaving it at `1`. Decoding a rule JSON payload that has no combinator field (any export predating this change, or any hand-authored file omitting it) SHALL default the decoded `Rule.conditionLogic` to `ALL`.

#### Scenario: Exporting a rule includes the combinator field
- **WHEN** a rule with `conditionLogic = ANY` is exported via the rule wire codec
- **THEN** the resulting JSON includes the combinator field set to `ANY`

#### Scenario: Importing a payload without the combinator field defaults to ALL
- **WHEN** a rule JSON payload lacking the combinator field is imported
- **THEN** the decoded `Rule.conditionLogic` is `ALL`

#### Scenario: Round-trip through export then import preserves the combinator
- **WHEN** a rule with `conditionLogic = ANY` is exported and the resulting JSON is immediately re-imported
- **THEN** the re-imported rule has `conditionLogic = ANY`

## Non-Goals (recorded, not to be implemented in this change)

- **No editor/UI exposure.** The rule editor continues to construct only `ALL` rules; there is no user-facing control to choose `ANY`. This is a schema/matching-semantics change only.
- **No nested `ConditionGroup` / boolean condition tree.** A future extension could let combinators apply to sub-groups of conditions rather than only at the rule root; this change explicitly does not build that — the sealed `RuleCondition` + JSON `payload` column design makes a future `ConditionGroup` sealed subtype a code-only additive change (no migration, no wire break beyond a version bump).
- **No hand-written Room `Migration`.** Per CLAUDE.md's pre-launch "Development Status" policy, the schema bump for the new column relies on the destructive-migration fallback (no installed user base to preserve); a real `Migration` object is not written for this change.

## Out of Scope Note for the Base Spec

This delta does not change: individual condition-family evaluation (`ContentMatchCondition`, `DayOfWeekCondition`, `TimeRangeCondition` semantics, all unchanged and covered by the base `rule-conditions` spec), fail-closed/no-throw evaluation guarantees, or any UI/editor behavior.
