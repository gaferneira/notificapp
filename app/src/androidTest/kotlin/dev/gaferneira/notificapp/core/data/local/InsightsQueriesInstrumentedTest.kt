package dev.gaferneira.notificapp.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Instrumented Room tests for the Insights DAO queries (dry-run exclusion, local-day bucketing,
 * numeric field totals, quiet rules, tie-break ordering, top apps). In-memory DB, no SQLCipher.
 */
@RunWith(AndroidJUnit4::class)
class InsightsQueriesInstrumentedTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeDailyCountsSince_excludes_dry_runs() = runBlocking {
        seedRule("r1", "Rule")
        seedNotification("n1", "com.a", "App", 1_000L)
        val dao = database.ruleExecutionDao()
        dao.insert(execution("e1", "r1", "n1", createdAt = millisAt(DAY, 10), dryRun = false))
        dao.insert(execution("e2", "r1", "n1", createdAt = millisAt(DAY, 11), dryRun = true))

        val daily = dao.observeDailyCountsSince(0L).first()

        daily.map { it.date to it.count } shouldBe listOf(DAY.toString() to 1)
    }

    @Test
    fun observeDailyCountsSince_buckets_by_local_day_across_the_midnight_boundary() = runBlocking {
        seedRule("r1", "Rule")
        seedNotification("n1", "com.a", "App", 1_000L)
        val dao = database.ruleExecutionDao()
        dao.insert(execution("e1", "r1", "n1", createdAt = millisAt(DAY, 23, 59)))
        dao.insert(execution("e2", "r1", "n1", createdAt = millisAt(DAY.plusDays(1), 0, 1)))
        dao.insert(execution("e3", "r1", "n1", createdAt = millisAt(DAY.plusDays(1), 12)))

        val daily = dao.observeDailyCountsSince(0L).first()

        daily.map { it.date to it.count } shouldBe listOf(DAY.toString() to 1, DAY.plusDays(1).toString() to 2)
    }

    @Test
    fun observeNumericTotalsSince_sums_only_non_null_NUMBER_and_CURRENCY_values() = runBlocking {
        seedRule("r1", "Rule")
        seedNotification("n1", "com.a", "App", 1_000L)
        database.ruleDao().insertActions(listOf(RuleActionEntity(id = "a1", ruleId = "r1", type = "SAVE_DATA")))
        database.ruleDao().insertFields(
            listOf(
                field("f-amount", "Amount", "CURRENCY"),
                field("f-text", "Label", "STRING"),
                field("f-empty", "Empty", "NUMBER"),
            ),
        )
        val executions = database.ruleExecutionDao()
        executions.insert(execution("e1", "r1", "n1", createdAt = 5_000L))
        executions.insert(execution("e2", "r1", "n1", createdAt = 6_000L))
        executions.insert(execution("e3", "r1", "n1", createdAt = 7_000L, dryRun = true))
        database.extractedFieldValueDao().insertAll(
            listOf(
                value("v1", "e1", "f-amount", number = 10.0),
                value("v2", "e2", "f-amount", number = 5.5),
                value("v3", "e2", "f-amount", number = null),
                value("v4", "e3", "f-amount", number = 100.0), // dry run: excluded
                value("v5", "e1", "f-text", number = 3.0), // wrong field type: excluded
                value("v6", "e1", "f-empty", number = null), // only NULLs: no group
            ),
        )

        val totals = database.extractedFieldValueDao().observeNumericTotalsSince(0L).first()

        totals.size shouldBe 1
        totals.single().fieldId shouldBe "f-amount"
        totals.single().sum shouldBe 15.5
        totals.single().count shouldBe 2
    }

    @Test
    fun observeQuietRulesSince_lists_only_active_rules_without_a_recent_real_execution() = runBlocking {
        seedRule("busy", "Busy")
        seedRule("quiet", "Quiet")
        seedRule("dry-only", "Dry only")
        seedRule("old", "Old")
        seedRule("off", "Off", isActive = false)
        seedNotification("n1", "com.a", "App", 1_000L)
        val executions = database.ruleExecutionDao()
        executions.insert(execution("e1", "busy", "n1", createdAt = 9_000L))
        executions.insert(execution("e2", "dry-only", "n1", createdAt = 9_000L, dryRun = true))
        executions.insert(execution("e3", "old", "n1", createdAt = 1_000L))

        val quiet = database.ruleDao().observeQuietRulesSince(5_000L).first()

        quiet.map { it.ruleId } shouldBe listOf("dry-only", "old", "quiet")
    }

    @Test
    fun observeTopRulesSince_breaks_ties_by_name_then_id() = runBlocking {
        seedRule("r3", "Beta")
        seedRule("r2", "Beta")
        seedRule("r1", "Alpha")
        seedRule("r4", "Gamma")
        seedNotification("n1", "com.a", "App", 1_000L)
        val executions = database.ruleExecutionDao()
        listOf("r1", "r2", "r3").forEachIndexed { i, ruleId -> executions.insert(execution("e$i", ruleId, "n1", createdAt = 5_000L)) }
        executions.insert(execution("e-extra", "r4", "n1", createdAt = 5_000L))
        executions.insert(execution("e-extra2", "r4", "n1", createdAt = 5_000L))

        val top = executions.observeTopRulesSince(0L, 10).first()

        top.map { it.ruleId } shouldBe listOf("r4", "r1", "r2", "r3")
    }

    @Test
    fun observeTopAppsSince_uses_the_latest_app_name_and_breaks_ties_by_package() = runBlocking {
        val dao = database.notificationDao()
        dao.insert(seedNotificationEntity("n1", "com.b", "Old B", 1_000L))
        dao.insert(seedNotificationEntity("n2", "com.b", "New B", 3_000L))
        dao.insert(seedNotificationEntity("n3", "com.b", "Mid B", 2_000L))
        dao.insert(seedNotificationEntity("n4", "com.a", "App A", 2_000L))
        dao.insert(seedNotificationEntity("n5", "com.a", "App A", 2_500L))
        dao.insert(seedNotificationEntity("n6", "com.c", "Too old", 100L))
        dao.insert(seedNotificationEntity("n7", "com.d", "App D", 2_000L))
        dao.insert(seedNotificationEntity("n8", "com.d", "App D", 2_100L))

        val top = dao.observeTopAppsSince(500L, 5).first()

        // com.a and com.d tie at 2; package_name ASC decides. com.c is outside the window.
        top.map { it.label to it.count } shouldBe listOf("New B" to 3, "App A" to 2, "App D" to 2)
    }

    private suspend fun seedRule(id: String, name: String, isActive: Boolean = true) {
        database.ruleDao().insert(RuleEntity(id = id, name = name, description = null, category = null, isActive = isActive))
    }

    private suspend fun seedNotification(id: String, pkg: String, appName: String, timestamp: Long) {
        database.notificationDao().insert(seedNotificationEntity(id, pkg, appName, timestamp))
    }

    private fun seedNotificationEntity(id: String, pkg: String, appName: String, timestamp: Long) = NotificationEntity(
        id = id,
        packageName = pkg,
        appName = appName,
        title = null,
        content = null,
        rawContent = "",
        timestamp = timestamp,
        isProcessed = false,
    )

    private fun execution(id: String, ruleId: String, notificationId: String, createdAt: Long, dryRun: Boolean = false) = RuleExecutionEntity(
        id = id,
        ruleId = ruleId,
        notificationId = notificationId,
        extractedData = "{}",
        triggeredActions = "[]",
        wasDryRun = dryRun,
        createdAt = createdAt,
    )

    private fun field(id: String, name: String, type: String) = RuleFieldEntity(
        id = id,
        actionId = "a1",
        name = name,
        fieldType = type,
        methodType = "REGEX",
        methodConfig = "{}",
    )

    private fun value(id: String, executionId: String, fieldId: String, number: Double?) = ExtractedFieldValueEntity(
        id = id,
        ruleExecutionId = executionId,
        ruleFieldId = fieldId,
        valueText = null,
        valueNumber = number,
        valueDate = null,
    )

    private fun millisAt(day: LocalDate, hour: Int, minute: Int = 0): Long = day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 3, 10)
    }
}
