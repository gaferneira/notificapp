# ADR 015 – Database Evolution Policy

## Status
Accepted

## Context
The Room schema (`AppDatabase`, SQLCipher-encrypted) is at version 1 and the app has not shipped, so destructive schema changes are currently free. Once users have installed data, every change needs a migration, and several early pitfalls would silently destroy user data: `OnConflictStrategy.REPLACE` cascading deletes into child rows, hard FKs wiping execution history, strict JSON/enum decoding crashing on values written by a newer or older build.

## Decision

1. **v1 is frozen at the first public release.** After that, schema changes are additive (new tables, columns, indexes). Prefer `@AutoMigration`; otherwise write a `Migration` in `core/data/local/migration/DatabaseMigrations.kt` (`APP_DATABASE_MIGRATIONS`). Every version bump commits the exported `app/schemas/**/N.json` and adds a case to `DatabaseMigrationTest` (`MigrationTestHelper` through SQLCipher; a copy-paste template lives in the test).
2. **No destructive fallback in release builds.** `fallbackToDestructiveMigration` stays DEBUG-only (`DatabaseModule`) until release; a missing migration must crash loudly, not wipe data.
3. **Never `OnConflictStrategy.REPLACE` on tables with `ON DELETE CASCADE` children.** REPLACE is delete + insert, so it fires cascades and wipes children. Use `@Upsert` (in-place update). Leaf tables may opt out with `// replace-ok: reason`; `architectureCheck` rule 11 enforces this.
4. **Execution history is preserved:** history tables use `SET NULL` FKs plus snapshot columns (e.g. rule name) so deleting a rule or field does not erase what already happened.
5. **JSON columns use DTOs** with stable `@SerialName` values and a `"type"` discriminator, decoded tolerantly: an unknown condition becomes a never-matching condition; unknown actions/fields are skipped. Renaming a `@SerialName` is a breaking change.
6. **Enums are stored by name** with a tolerant fallback for unknown names (never throw on read).
7. **Time:** dates as epoch day, instants as UTC epoch milliseconds.

## Consequences

**Positive:**
- The first post-release migration is a small, tested change instead of a design exercise.
- Downgrades/forward-compat reads degrade gracefully rather than crash.
- The REPLACE pitfall is mechanically blocked.

**Negative:**
- Additive-only changes mean dead columns/tables linger until a deliberate table-rebuild migration.
- Tolerant decoding can hide corrupt data as "skipped"; it must be logged where it matters.
- Until release, DEBUG builds still wipe the DB on schema change, so migrations are only exercised by the instrumented test.
