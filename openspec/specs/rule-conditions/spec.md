# rule-conditions Specification

## Purpose

Defines the typed `RuleCondition` families a rule can be gated on — notification-content matching, day-of-week, and time-range — how each family evaluates against a notification/point in time, and how multiple conditions on the same rule combine. The sealed shape is designed so future condition families (e.g. device-state) add a new subtype without changing this evaluation contract.

## Requirements

### Requirement: Sealed condition evaluation is total and fail-closed
`RuleCondition` SHALL be a sealed interface with at least three implementations: `ContentMatchCondition`, `DayOfWeekCondition`, `TimeRangeCondition`. `RuleMatcher` SHALL dispatch each condition to a per-family evaluator and every evaluator SHALL resolve to `true` or `false` for any input — it SHALL NOT throw. A condition that cannot be evaluated SHALL resolve to `false` (fail-closed), never `true`.

#### Scenario: Evaluation never throws
- **WHEN** `RuleMatcher.matches` evaluates any condition in the sealed hierarchy
- **THEN** it returns a boolean and does not throw

### Requirement: Day-of-week matching
`DayOfWeekCondition(days: Set<DayOfWeek>)` SHALL match the current evaluation time when its `DayOfWeek` is a member of `days`. An empty `days` set SHALL match no day (fail-closed, not "match every day").

#### Scenario: Single day matches
- **WHEN** a `DayOfWeekCondition` contains only `MONDAY` and the current day is Monday
- **THEN** the condition matches

#### Scenario: Multi-day set matches any listed day
- **WHEN** a `DayOfWeekCondition` contains `SATURDAY` and `SUNDAY` and the current day is Sunday
- **THEN** the condition matches

#### Scenario: Non-listed day does not match
- **WHEN** a `DayOfWeekCondition` contains only weekday values and the current day is Saturday
- **THEN** the condition does not match

#### Scenario: Empty day set never matches
- **WHEN** a `DayOfWeekCondition` has an empty `days` set
- **THEN** the condition does not match, regardless of the current day

#### Scenario: Every individual day of week is a valid selector
- **WHEN** a `DayOfWeekCondition` contains exactly one of `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`, `SATURDAY`, or `SUNDAY`
- **THEN** it matches only when the current day equals that value

### Requirement: Time-range matching, including overnight wrap
`TimeRangeCondition(start: LocalTime, end: LocalTime)` SHALL match when the current time falls within `[start, end]` inclusive of both boundaries. When `start` is after `end`, the range SHALL be treated as wrapping across midnight, matching any time that is `>= start` OR `<= end`. When `start == end`, the range SHALL match only the exact instant equal to `start`.

#### Scenario: Same-day range matches within bounds
- **WHEN** a `TimeRangeCondition` is `09:00`–`17:00` and the current time is `12:00`
- **THEN** the condition matches

#### Scenario: Boundaries are inclusive
- **WHEN** a `TimeRangeCondition` is `09:00`–`17:00` and the current time is exactly `09:00` or exactly `17:00`
- **THEN** the condition matches in both cases

#### Scenario: Time outside a same-day range does not match
- **WHEN** a `TimeRangeCondition` is `09:00`–`17:00` and the current time is `08:59`
- **THEN** the condition does not match

#### Scenario: Overnight range matches both sides of midnight
- **WHEN** a `TimeRangeCondition` is `22:00`–`06:00` and the current time is `23:30`
- **THEN** the condition matches
- **WHEN** the current time is instead `05:00`
- **THEN** the condition also matches

#### Scenario: Overnight range excludes the daytime gap
- **WHEN** a `TimeRangeCondition` is `22:00`–`06:00` and the current time is `12:00`
- **THEN** the condition does not match

#### Scenario: Degenerate zero-duration range matches only its exact instant
- **WHEN** a `TimeRangeCondition` has `start == end == 08:00`
- **THEN** the condition matches only when the current time is exactly `08:00`

### Requirement: Multiple conditions on a rule combine per `conditionLogic`
A rule's conditions combine according to its `conditionLogic` field: `ALL` (AND semantics, the default) or `ANY` (OR semantics). When `conditionLogic = ALL`, all conditions SHALL match for the rule to match. When `conditionLogic = ANY`, at least one condition SHALL match for the rule to match. An empty `conditions` list SHALL match regardless of `conditionLogic` (global rule).

#### Scenario: ALL combinator — rule matches only when every condition matches
- **WHEN** a rule has `conditionLogic = ALL` and three conditions (day, time, and content), all of which match the current notification/time
- **THEN** the rule matches
- **WHEN** any one of those three conditions fails to match
- **THEN** the rule does not match

#### Scenario: ANY combinator — rule matches when at least one condition matches
- **WHEN** a rule has `conditionLogic = ANY` and three conditions, and exactly one of them matches
- **THEN** the rule matches
- **WHEN** none of the conditions match
- **THEN** the rule does not match

#### Scenario: Empty conditions list matches under both combinators
- **WHEN** a rule has an empty `conditions` list, regardless of whether `conditionLogic` is `ALL` or `ANY`
- **THEN** the rule matches (global rule)

#### Scenario: Default combinator is ALL
- **WHEN** a `Rule` is constructed without explicitly setting `conditionLogic`
- **THEN** its `conditionLogic` defaults to `ALL` and it evaluates with AND semantics (preserving pre-change behavior)

### Requirement: Content-match condition preserves existing behavior
`ContentMatchCondition` SHALL carry the same `field` + `operator` + `value` shape and evaluation semantics that `RuleCondition` had before this change (all existing `MatchingCondition` fields and `MatchingOperator` operators, unchanged). Rules that previously used only content conditions SHALL match identically to their pre-change behavior.

#### Scenario: Content-only rule matches exactly as before
- **WHEN** a rule has only `ContentMatchCondition`s and a notification satisfies the same field/operator/value combination it satisfied before this change
- **THEN** the rule matches, with the same result as the pre-change implementation

#### Scenario: All existing matching operators keep working
- **WHEN** a `ContentMatchCondition` uses any pre-existing `MatchingOperator` (e.g. `CONTAINS`, `EQUALS`, `STARTS_WITH`)
- **THEN** it evaluates using that operator's unchanged semantics
