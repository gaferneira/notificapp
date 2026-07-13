# rule-storage Specification

## Purpose

Defines how a rule's `RuleCondition` list is persisted and how the persisted shape relates to the rule-sharing wire format. Scoped to condition storage only — `rule_fields`, `rule_actions`, `rule_target_apps`, and `rules` metadata stay on the pre-existing normalized-table shape (ADR 011 unchanged for those).

## Requirements

### Requirement: Condition persistence uses a JSON polymorphic column
The system SHALL persist a rule's `RuleCondition` list as a single JSON-serialized, polymorphically-discriminated column (kotlinx.serialization, `@SerialName` per subtype) rather than one row per condition in typed columns. Mappers SHALL round-trip every condition subtype (`ContentMatchCondition`, `DayOfWeekCondition`, `TimeRangeCondition`) without data loss.

#### Scenario: Content condition round-trips through storage
- **WHEN** a rule with a `ContentMatchCondition` is saved and then loaded
- **THEN** the loaded condition has the same field, operator, and value as before saving

#### Scenario: Day-of-week condition round-trips through storage
- **WHEN** a rule with a `DayOfWeekCondition` is saved and then loaded
- **THEN** the loaded condition has the same `days` set as before saving

#### Scenario: Time-range condition round-trips through storage
- **WHEN** a rule with a `TimeRangeCondition` is saved and then loaded
- **THEN** the loaded condition has the same `start` and `end` values as before saving

#### Scenario: Mixed-family condition list round-trips in order
- **WHEN** a rule has a `DayOfWeekCondition`, a `TimeRangeCondition`, and a `ContentMatchCondition`, and is saved then loaded
- **THEN** all three conditions are present after loading with their original types and values preserved

### Requirement: New condition types require no schema migration
Adding a new `RuleCondition` subtype (including a future device-state condition) SHALL require only a new sealed subtype, its evaluator, and a new `@SerialName` discriminator — no Room schema migration and no new entity/mapper.

#### Scenario: Storage schema is subtype-agnostic
- **WHEN** a new `RuleCondition` subtype is added to the sealed hierarchy
- **THEN** the `rule_conditions` JSON column's schema requires no migration to store it

### Requirement: Rule wire format supports polymorphic conditions
The rule-sharing wire format (`ConditionDto` / `RuleWireMapper`) SHALL represent each condition subtype with its own discriminated DTO shape, so exported rules preserve condition family and round-trip on import without loss.

#### Scenario: Exported rule with typed conditions re-imports identically
- **WHEN** a rule containing a `DayOfWeekCondition` and a `TimeRangeCondition` is exported to the wire format and re-imported
- **THEN** the re-imported rule's conditions match the originals in type and value
