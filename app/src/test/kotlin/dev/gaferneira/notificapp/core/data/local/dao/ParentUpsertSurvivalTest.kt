package dev.gaferneira.notificapp.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Re-saving an existing parent row must update it in place: `notifications` is the parent of
 * `rule_executions` and `rule_executions` of `extracted_field_values`, both `ON DELETE CASCADE`,
 * so `OnConflictStrategy.REPLACE` (delete + insert) would wipe the children. Also checks that the
 * `notifications_fts` external-content index follows an in-place update.
 *
 * Runs on the JVM via Robolectric against an in-memory Room database (no SQLCipher).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParentUpsertSurvivalTest {

    private lateinit var database: AppDatabase
    private lateinit var notificationDao: NotificationDao
    private lateinit var executionDao: RuleExecutionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notificationDao = database.notificationDao()
        executionDao = database.ruleExecutionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `re-upserting a notification keeps its executions and extracted values`() = runBlocking<Unit> {
        seed()

        notificationDao.upsert(notification(title = "Changed title"))

        executionDao.getExecutionsForNotification(NOTIFICATION_ID).map { it.id } shouldBe listOf(EXECUTION_ID)
        database.extractedFieldValueDao().getValuesForExecution(EXECUTION_ID).map { it.id } shouldBe listOf(VALUE_ID)
    }

    @Test
    fun `re-upserting a batch of notifications keeps their executions`() = runBlocking<Unit> {
        seed()

        notificationDao.upsertAll(listOf(notification(title = "Changed title")))

        executionDao.getExecutionsForNotification(NOTIFICATION_ID).map { it.id } shouldBe listOf(EXECUTION_ID)
    }

    @Test
    fun `re-upserting an execution keeps its extracted values`() = runBlocking<Unit> {
        seed()

        executionDao.upsert(execution(ruleName = "Renamed"))

        executionDao.getExecutionsForNotification(NOTIFICATION_ID).single().ruleName shouldBe "Renamed"
        database.extractedFieldValueDao().getValuesForExecution(EXECUTION_ID).map { it.id } shouldBe listOf(VALUE_ID)
    }

    @Test
    fun `re-upserting a batch of executions keeps their extracted values`() = runBlocking<Unit> {
        seed()

        executionDao.upsertAll(listOf(execution(ruleName = "Renamed")))

        database.extractedFieldValueDao().getValuesForExecution(EXECUTION_ID).map { it.id } shouldBe listOf(VALUE_ID)
    }

    @Test
    fun `re-upserting a notification updates the full-text index to the new title only`() = runBlocking<Unit> {
        notificationDao.upsert(notification(title = "Zebra"))
        notificationDao.search("Zebra").map { it.id } shouldBe listOf(NOTIFICATION_ID)

        notificationDao.upsert(notification(title = "Giraffe"))

        notificationDao.search("Giraffe").map { it.id } shouldBe listOf(NOTIFICATION_ID)
        notificationDao.search("Zebra") shouldBe emptyList()
        notificationDao.getCount() shouldBe 1
    }

    private suspend fun seed() {
        notificationDao.upsert(notification(title = "Original title"))
        executionDao.upsert(execution(ruleName = "Bank purchases"))
        database.extractedFieldValueDao().insertAll(
            listOf(
                ExtractedFieldValueEntity(
                    id = VALUE_ID,
                    ruleExecutionId = EXECUTION_ID,
                    ruleFieldId = null,
                    fieldName = "Amount",
                    fieldType = "STRING",
                    valueText = "x",
                    valueNumber = null,
                    valueDate = null,
                ),
            ),
        )
    }

    private fun notification(title: String) = NotificationEntity(
        id = NOTIFICATION_ID,
        packageName = "com.example",
        appName = "Example",
        title = title,
        content = "c",
        rawContent = "raw",
        timestamp = 1L,
    )

    private fun execution(ruleName: String) = RuleExecutionEntity(
        id = EXECUTION_ID,
        notificationId = NOTIFICATION_ID,
        ruleId = null,
        ruleName = ruleName,
        extractedData = "{}",
        triggeredActions = "[]",
        createdAt = 1_000L,
    )

    private companion object {
        const val NOTIFICATION_ID = "notification-1"
        const val EXECUTION_ID = "execution-1"
        const val VALUE_ID = "value-1"
    }
}
