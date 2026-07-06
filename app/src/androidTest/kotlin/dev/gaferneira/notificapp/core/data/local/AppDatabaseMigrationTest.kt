package dev.gaferneira.notificapp.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gaferneira.notificapp.core.data.local.migration.MIGRATION_1_2
import dev.gaferneira.notificapp.core.data.local.migration.MIGRATION_2_3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesExistingDataAndAddsActionOutcomesColumn() {
        migrationTestHelper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO notifications (id, package_name, app_name, title, content, raw_content, " +
                    "timestamp, is_processed, applied_rules_count, sbn_key) VALUES " +
                    "('notif-1', 'com.test.app', 'Test App', 'Title', 'Content', 'raw', 0, 0, 1, NULL)",
            )
            execSQL(
                "INSERT INTO rules (id, name, description, category, is_active, is_global, created_at, " +
                    "updated_at) VALUES ('rule-1', 'Test Rule', NULL, NULL, 1, 1, 0, 0)",
            )
            execSQL(
                "INSERT INTO rule_executions (id, notification_id, rule_id, extracted_data, " +
                    "triggered_actions, created_at) VALUES " +
                    "('exec-1', 'notif-1', 'rule-1', '{}', '[]', 0)",
            )
            close()
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val cursor = migratedDb.query("SELECT notification_id, rule_id, action_outcomes FROM rule_executions WHERE id = 'exec-1'")
        cursor.use {
            check(it.moveToFirst()) { "Expected the pre-migration rule_executions row to survive the migration" }
            val notificationIdIndex = it.getColumnIndex("notification_id")
            val ruleIdIndex = it.getColumnIndex("rule_id")
            val actionOutcomesIndex = it.getColumnIndex("action_outcomes")
            check(it.getString(notificationIdIndex) == "notif-1") { "notification_id was not preserved" }
            check(it.getString(ruleIdIndex) == "rule-1") { "rule_id was not preserved" }
            check(it.isNull(actionOutcomesIndex)) { "Expected the new action_outcomes column to be NULL for pre-existing rows" }
        }
    }

    @Test
    fun migrate2To3_preservesExistingDataAndDefaultsDryRunColumnsToFalse() {
        migrationTestHelper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO notifications (id, package_name, app_name, title, content, raw_content, " +
                    "timestamp, is_processed, applied_rules_count, sbn_key) VALUES " +
                    "('notif-1', 'com.test.app', 'Test App', 'Title', 'Content', 'raw', 0, 0, 1, NULL)",
            )
            execSQL(
                "INSERT INTO rules (id, name, description, category, is_active, is_global, created_at, " +
                    "updated_at) VALUES ('rule-1', 'Test Rule', NULL, NULL, 1, 1, 0, 0)",
            )
            execSQL(
                "INSERT INTO rule_executions (id, notification_id, rule_id, extracted_data, " +
                    "triggered_actions, action_outcomes, created_at) VALUES " +
                    "('exec-1', 'notif-1', 'rule-1', '{}', '[]', NULL, 0)",
            )
            close()
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        val ruleCursor = migratedDb.query("SELECT name, is_dry_run FROM rules WHERE id = 'rule-1'")
        ruleCursor.use {
            check(it.moveToFirst()) { "Expected the pre-migration rules row to survive the migration" }
            check(it.getString(it.getColumnIndex("name")) == "Test Rule") { "name was not preserved" }
            check(it.getInt(it.getColumnIndex("is_dry_run")) == 0) { "Expected is_dry_run to default to false (0) for pre-existing rules" }
        }

        val executionCursor = migratedDb.query("SELECT rule_id, was_dry_run FROM rule_executions WHERE id = 'exec-1'")
        executionCursor.use {
            check(it.moveToFirst()) { "Expected the pre-migration rule_executions row to survive the migration" }
            check(it.getString(it.getColumnIndex("rule_id")) == "rule-1") { "rule_id was not preserved" }
            check(it.getInt(it.getColumnIndex("was_dry_run")) == 0) { "Expected was_dry_run to default to false (0) for pre-existing executions" }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
