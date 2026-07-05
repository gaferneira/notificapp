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

val APP_DATABASE_MIGRATIONS = arrayOf(MIGRATION_1_2)
