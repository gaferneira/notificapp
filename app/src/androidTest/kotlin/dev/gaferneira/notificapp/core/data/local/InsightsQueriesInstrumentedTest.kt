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
import dev.gaferneira.notificapp.domain.model.AppActivity
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
    fun observeDailyCountsSince_excludes_dry_runs() = runBlocking<Unit> {
        seedRule("r1", "Rule")
        seedNotification("n1", "com.a", "App", 1_000L)
        val dao = database.ruleExecutionDao()
        dao.upsert(execution("e1", "r1", "n1", createdAt = millisAt(DAY, 10), dryRun = false))
        dao.upsert(execution("e2", "r1", "n1", createdAt = millisAt(DAY, 11), dryRun = true))

        val daily = dao.observeDailyCountsSince(0L).first()

        daily.map { it.date to it.count } shouldBe listOf(DAY.toString() to 1)
    }

    @Test
    fun observeDailyCountsSince_buckets_by_local_day_across_the_midnight_boundary() = runBlocking<Unit> {
        seedRule("r1", "Rule")
        seedNotification("n1", "com.a", "App", 1_000L)
        val dao = database.ruleExecutionDao()
        dao.upsert(execution("e1", "r1", "n1", createdAt = millisAt(DAY, 23, 59)))
        dao.upsert(execution("e2", "r1", "n1", createdAt = millisAt(DAY.plusDays(1), 0, 1)))
        dao.upsert(execution("e3", "r1", "n1", createdAt = millisAt(DAY.plusDays(1), 12)))

        val daily = dao.observeDailyCountsSince(0L).first()

        daily.map { it.date to it.count } shouldBe listOf(DAY.toString() to 1, DAY.plusDays(1).toString() to 2)
    }

    @Test
    fun notification_daily_counts_bucket_by_local_day_and_sum_to_the_count_since() = runBlocking<Unit> {
        val dao = database.notificationDao()
        dao.upsert(seedNotificationEntity("n1", "com.a", "App", millisAt(DAY, 23, 59), processed = true))
        dao.upsert(seedNotificationEntity("n2", "com.a", "App", millisAt(DAY.plusDays(1), 0, 1)))
        dao.upsert(seedNotificationEntity("n3", "com.a", "App", millisAt(DAY.plusDays(1), 12), processed = true))
        dao.upsert(seedNotificationEntity("n4", "com.a", "App", millisAt(DAY.minusDays(5), 12))) // before the window
        val since = millisAt(DAY, 0)

        val captured = dao.observeDailyCountsSince(since).first()
        val handled = dao.observeDailyProcessedCountsSince(since).first()

        captured.map { it.date to it.count } shouldBe listOf(DAY.toString() to 1, DAY.plusDays(1).toString() to 2)
        handled.map { it.date to it.count } shouldBe listOf(DAY.toString() to 1, DAY.plusDays(1).toString() to 1)
        captured.sumOf { it.count } shouldBe dao.observeCountSince(since).first()
        handled.sumOf { it.count } shouldBe dao.observeProcessedCountSince(since).first()
    }

    @Test
    fun observeDailyActionOutcomesSince_keeps_only_live_executions_with_outcomes() = runBlocking<Unit> {
        seedRule("r1", "Rule")
        seedNotification("n1", "com.a", "App", 1_000L)
        val dao = database.ruleExecutionDao()
        dao.upsert(execution("e1", "r1", "n1", createdAt = millisAt(DAY, 10), outcomes = """{"a1":"FAILED"}"""))
        dao.upsert(execution("e2", "r1", "n1", createdAt = millisAt(DAY, 11), outcomes = """{"a1":"FAILED"}""", dryRun = true))
        dao.upsert(execution("e3", "r1", "n1", createdAt = millisAt(DAY, 12), outcomes = null))

        val rows = dao.observeDailyActionOutcomesSince(0L).first()

        rows.map { it.date to it.actionOutcomes } shouldBe listOf(DAY.toString() to """{"a1":"FAILED"}""")
    }

    @Test
    fun observeRuleActionOutcomesSince_joins_the_rule_name_and_keeps_deleted_rules_with_a_null_id() = runBlocking<Unit> {
        seedRule("r1", "Renamed")
        seedNotification("n1", "com.a", "App", 1_000L)
        val dao = database.ruleExecutionDao()
        dao.upsert(execution("e1", "r1", "n1", createdAt = 5_000L, outcomes = """{"a1":"SUCCESS"}"""))
        dao.upsert(execution("e2", null, "n1", createdAt = 6_000L, outcomes = """{"a1":"FAILED"}""", ruleName = "Gone"))
        dao.upsert(execution("e3", "r1", "n1", createdAt = 7_000L, outcomes = """{"a1":"SUCCESS"}""", dryRun = true))
        dao.upsert(execution("e4", "r1", "n1", createdAt = 100L, outcomes = """{"a1":"SUCCESS"}""")) // before the window

        val rows = dao.observeRuleActionOutcomesSince(1_000L).first()

        rows.map { Triple(it.ruleId, it.ruleName, it.actionOutcomes) }.toSet() shouldBe setOf(
            Triple("r1", "Renamed", """{"a1":"SUCCESS"}"""),
            Triple(null, "Gone", """{"a1":"FAILED"}"""),
        )
    }

    @Test
    fun observeNumericTotalsSince_sums_only_non_null_NUMBER_and_CURRENCY_values() = runBlocking<Unit> {
        seedRule("r1", "Rule")
        seedNotification("n1", "com.a", "App", 1_000L)
        database.ruleDao().upsertActions(listOf(RuleActionEntity(id = "a1", ruleId = "r1", type = "SAVE_DATA")))
        database.ruleDao().upsertFields(
            listOf(
                field("f-amount", "Amount", "CURRENCY"),
                field("f-text", "Label", "STRING"),
                field("f-empty", "Empty", "NUMBER"),
            ),
        )
        val executions = database.ruleExecutionDao()
        executions.upsert(execution("e1", "r1", "n1", createdAt = 5_000L))
        executions.upsert(execution("e2", "r1", "n1", createdAt = 6_000L))
        executions.upsert(execution("e3", "r1", "n1", createdAt = 7_000L, dryRun = true))
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
    fun observeQuietRulesSince_lists_only_active_rules_without_a_recent_real_execution() = runBlocking<Unit> {
        seedRule("busy", "Busy")
        seedRule("quiet", "Quiet")
        seedRule("dry-only", "Dry only")
        seedRule("old", "Old")
        seedRule("off", "Off", isActive = false)
        seedNotification("n1", "com.a", "App", 1_000L)
        val executions = database.ruleExecutionDao()
        executions.upsert(execution("e1", "busy", "n1", createdAt = 9_000L))
        executions.upsert(execution("e2", "dry-only", "n1", createdAt = 9_000L, dryRun = true))
        executions.upsert(execution("e3", "old", "n1", createdAt = 1_000L))

        val quiet = database.ruleDao().observeQuietRulesSince(5_000L).first()

        quiet.map { it.ruleId } shouldBe listOf("dry-only", "old", "quiet")
    }

    @Test
    fun observeTopRulesSince_breaks_ties_by_name_then_id() = runBlocking<Unit> {
        seedRule("r3", "Beta")
        seedRule("r2", "Beta")
        seedRule("r1", "Alpha")
        seedRule("r4", "Gamma")
        seedNotification("n1", "com.a", "App", 1_000L)
        val executions = database.ruleExecutionDao()
        listOf("r1", "r2", "r3").forEachIndexed { i, ruleId -> executions.upsert(execution("e$i", ruleId, "n1", createdAt = 5_000L)) }
        executions.upsert(execution("e-extra", "r4", "n1", createdAt = 5_000L))
        executions.upsert(execution("e-extra2", "r4", "n1", createdAt = 5_000L))

        val top = executions.observeTopRulesSince(0L, 10).first()

        top.map { it.ruleId } shouldBe listOf("r4", "r1", "r2", "r3")
    }

    @Test
    fun observeTopAppsSince_uses_the_latest_app_name_and_breaks_ties_by_package() = runBlocking<Unit> {
        val dao = database.notificationDao()
        dao.upsert(seedNotificationEntity("n1", "com.b", "Old B", 1_000L))
        dao.upsert(seedNotificationEntity("n2", "com.b", "New B", 3_000L))
        dao.upsert(seedNotificationEntity("n3", "com.b", "Mid B", 2_000L))
        dao.upsert(seedNotificationEntity("n4", "com.a", "App A", 2_000L))
        dao.upsert(seedNotificationEntity("n5", "com.a", "App A", 2_500L))
        dao.upsert(seedNotificationEntity("n6", "com.c", "Too old", 100L))
        dao.upsert(seedNotificationEntity("n7", "com.d", "App D", 2_000L))
        dao.upsert(seedNotificationEntity("n8", "com.d", "App D", 2_100L))

        val top = dao.observeTopAppsSince(500L, 5).first()

        // com.a and com.d tie at 2; package_name ASC decides. com.c is outside the window.
        top shouldBe listOf(AppActivity("com.b", "New B", 3), AppActivity("com.a", "App A", 2), AppActivity("com.d", "App D", 2))
    }

    private suspend fun seedRule(id: String, name: String, isActive: Boolean = true) {
        database.ruleDao().upsert(RuleEntity(id = id, name = name, description = null, category = null, isActive = isActive))
    }

    private suspend fun seedNotification(id: String, pkg: String, appName: String, timestamp: Long) {
        database.notificationDao().upsert(seedNotificationEntity(id, pkg, appName, timestamp))
    }

    private fun seedNotificationEntity(id: String, pkg: String, appName: String, timestamp: Long, processed: Boolean = false) = NotificationEntity(
        id = id,
        packageName = pkg,
        appName = appName,
        title = null,
        content = null,
        rawContent = "",
        timestamp = timestamp,
        isProcessed = processed,
    )

    @Suppress("LongParameterList") // test fixture builder with one defaulted knob per column
    private fun execution(
        id: String,
        ruleId: String?,
        notificationId: String,
        createdAt: Long,
        dryRun: Boolean = false,
        outcomes: String? = null,
        ruleName: String = "Rule $ruleId",
    ) = RuleExecutionEntity(
        id = id,
        ruleId = ruleId,
        ruleName = ruleName,
        actionOutcomes = outcomes,
        notificationId = notificationId,
        triggeredActions = "[]",
        wasDryRun = dryRun,
        createdAt = createdAt,
    )

    private fun field(id: String, name: String, type: String) = RuleFieldEntity(
        id = id,
        actionId = "a1",
        name = name,
        fieldType = type,
        methodConfig = "{}",
    )

    private fun value(id: String, executionId: String, fieldId: String, number: Double?) = ExtractedFieldValueEntity(
        id = id,
        ruleExecutionId = executionId,
        ruleFieldId = fieldId,
        fieldName = FIELD_SNAPSHOTS.getValue(fieldId).first,
        fieldType = FIELD_SNAPSHOTS.getValue(fieldId).second,
        valueText = null,
        valueNumber = number,
        valueEpochDay = null,
    )

    private fun millisAt(day: LocalDate, hour: Int, minute: Int = 0): Long = day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 3, 10)

        /** Field id to (name, type) snapshot, mirroring the `rule_fields` rows seeded above. */
        val FIELD_SNAPSHOTS = mapOf(
            "f-amount" to ("Amount" to "CURRENCY"),
            "f-text" to ("Label" to "STRING"),
            "f-empty" to ("Empty" to "NUMBER"),
        )
    }
}
