package dev.gaferneira.notificapp.core.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the nullable `action_outcomes` column to `rule_executions`
 * (per-action execution results introduced alongside pluggable ActionExecutors).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rule_executions ADD COLUMN action_outcomes TEXT")
    }
}

/**
 * Adds the `is_dry_run` column to `rules` and the `was_dry_run` column to `rule_executions`,
 * for per-rule dry-run mode (matches logged, actions never execute).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rules ADD COLUMN is_dry_run INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rule_executions ADD COLUMN was_dry_run INTEGER NOT NULL DEFAULT 0")
    }
}

val APP_DATABASE_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
