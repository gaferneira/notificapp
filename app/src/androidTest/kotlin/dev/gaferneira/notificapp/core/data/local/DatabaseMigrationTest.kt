package dev.gaferneira.notificapp.core.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gaferneira.notificapp.core.data.local.migration.APP_DATABASE_MIGRATIONS
import io.kotest.matchers.shouldBe
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards database evolution (ADR 015). Runs against the schemas exported to `app/schemas` (wired as
 * androidTest assets in `app/build.gradle.kts`) and through SQLCipher, exactly like production.
 *
 * Adding a migration N -> N+1:
 *  1. Bump `AppDatabase.CURRENT_VERSION`, add the `@AutoMigration` or a `Migration` to
 *     `APP_DATABASE_MIGRATIONS`, and build once so `app/schemas/.../N+1.json` is exported (commit it).
 *  2. Copy the commented template below.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val passphrase = "migration-test-passphrase".toByteArray()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        SupportOpenHelperFactory(passphrase),
    )

    @Before
    fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
    }

    @Test
    fun exportedV1Schema_validatesAgainstCurrentMigrations() {
        helper.createDatabase(TEST_DB, 1).close()

        // With only v1 this verifies schema identity; later versions replay every migration.
        helper.runMigrationsAndValidate(TEST_DB, AppDatabase.CURRENT_VERSION, true, *APP_DATABASE_MIGRATIONS).close()
    }

    @Test
    fun v1Database_opensThroughRoomWithAllMigrations_atLatestVersion() {
        helper.createDatabase(TEST_DB, 1).close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(*APP_DATABASE_MIGRATIONS)
            .build()
        try {
            db.openHelper.writableDatabase.version shouldBe AppDatabase.CURRENT_VERSION
        } finally {
            db.close()
        }
    }

    // Template for the first post-release migration (copy, rename, adjust):
    //
    // @Test
    // fun migrate1To2_preservesExistingRows() {
    //     helper.createDatabase(TEST_DB, 1).use { db ->
    //         db.execSQL("INSERT INTO selected_apps (...) VALUES (...)")   // seed representative v1 rows
    //     }
    //
    //     helper.runMigrationsAndValidate(TEST_DB, 2, true, *APP_DATABASE_MIGRATIONS).use { db ->
    //         db.query("SELECT ... FROM selected_apps").use { c ->
    //             c.moveToFirst() shouldBe true
    //             // assert old rows survived and new columns hold their defaults
    //         }
    //     }
    // }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
