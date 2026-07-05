package dev.gaferneira.notificapp.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gaferneira.notificapp.core.data.local.migration.MIGRATION_1_2
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
    fun migrate1To2_addsActionOutcomesColumn() {
        migrationTestHelper.createDatabase(TEST_DB, 1).apply {
            close()
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val cursor = migratedDb.query("PRAGMA table_info(rule_executions)")
        var foundActionOutcomes = false
        cursor.use {
            val nameColumnIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                if (it.getString(nameColumnIndex) == "action_outcomes") {
                    foundActionOutcomes = true
                }
            }
        }
        check(foundActionOutcomes) { "Expected 'action_outcomes' column after migrating 1 -> 2" }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
