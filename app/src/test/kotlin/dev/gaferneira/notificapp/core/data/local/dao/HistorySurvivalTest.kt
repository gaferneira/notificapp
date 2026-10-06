package dev.gaferneira.notificapp.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.domain.model.RuleField
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Execution history must survive deleting a rule or removing a field: `rule_executions.rule_id`
 * and `extracted_field_values.rule_field_id` are `ON DELETE SET NULL`, with name/type snapshots
 * keeping the rows displayable, while deleting the *notification* still cascades (retention).
 *
 * Runs on the JVM via Robolectric against an in-memory Room database (no SQLCipher).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistorySurvivalTest {

    private lateinit var database: AppDatabase
    private lateinit var ruleDao: RuleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ruleDao = database.ruleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleting a rule keeps its executions with a null rule id and the name snapshot`() = runBlocking<Unit> {
        seed()

        ruleDao.deleteRuleWithRelatedData(RULE_ID)

        val execution = database.ruleExecutionDao().getAll().single()
        execution.id shouldBe EXECUTION_ID
        execution.ruleId shouldBe null
        execution.ruleName shouldBe "Bank purchases"
    }

    @Test
    fun `deleting a rule keeps its extracted values with a null field id and the snapshots`() = runBlocking<Unit> {
        seed()

        ruleDao.deleteRuleWithRelatedData(RULE_ID)

        val value = database.extractedFieldValueDao().getValuesForExecution(EXECUTION_ID).first { it.id == "value-amount" }
        value.ruleFieldId shouldBe null
        value.fieldName shouldBe "Amount"
        value.fieldType shouldBe "CURRENCY"
        value.valueNumber shouldBe 42.5
    }

    @Test
    fun `removing a field keeps its values with a null field id and intact snapshots`() = runBlocking<Unit> {
        seed()

        // Re-save the rule without FIELD_AMOUNT (keeps only FIELD_LABEL).
        ruleDao.saveRuleWithRelatedData(
            rule = rule(),
            fields = listOf(field(FIELD_LABEL, "Label", "STRING")),
            conditions = listOf(condition()),
            actions = listOf(action()),
            apps = null,
        )

        database.ruleExecutionDao().getExecutionsForRule(RULE_ID).map { it.id } shouldBe listOf(EXECUTION_ID)
        val values = database.extractedFieldValueDao().getValuesForExecution(EXECUTION_ID).associateBy { it.id }
        values.getValue("value-amount").ruleFieldId shouldBe null
        values.getValue("value-amount").fieldName shouldBe "Amount"
        values.getValue("value-amount").fieldType shouldBe "CURRENCY"
        values.getValue("value-label").ruleFieldId shouldBe FIELD_LABEL
    }

    @Test
    fun `deleting the notification still cascades to its executions and values`() = runBlocking<Unit> {
        seed()

        database.notificationDao().deleteById(NOTIFICATION_ID)

        database.ruleExecutionDao().getAll() shouldBe emptyList()
        database.extractedFieldValueDao().getCount() shouldBe 0
    }

    @Test
    fun `deleting a rule leaves executions that were already orphaned untouched`() = runBlocking<Unit> {
        seed()
        database.ruleExecutionDao().insert(execution("execution-2", ruleId = null, ruleName = "Gone rule"))

        ruleDao.deleteRuleWithRelatedData(RULE_ID)

        database.ruleExecutionDao().getCount() shouldBe 2
    }

    @Test
    fun `data browser counts and lists values of a deleted rule and removed field`() = runBlocking<Unit> {
        seed()
        ruleDao.deleteRuleWithRelatedData(RULE_ID)
        val dao = database.dataBrowserDao()

        dao.getCount(NO_IDS, false, NO_IDS, false, NO_IDS, false, 0L, false, 0L, false) shouldBe 2
        // Field-type filter reads the snapshot type, so orphaned values are still matched.
        dao.getCount(NO_IDS, false, NO_IDS, false, listOf("CURRENCY"), true, 0L, false, 0L, false) shouldBe 1
        dao.getPerRuleCounts(NO_IDS, false, NO_IDS, false, NO_IDS, false, 0L, false, 0L, false)
            .map { it.label to it.count } shouldBe listOf("Bank purchases" to 2)
        dao.getMostActiveRule(NO_IDS, false, NO_IDS, false, NO_IDS, false, 0L, false, 0L, false)?.ruleName shouldBe "Bank purchases"

        val ids = dao.getMatchingIds(NO_IDS, false, NO_IDS, false, NO_IDS, false, 0L, false, 0L, false, "", false)
        val rows = dao.getMatchingBatch(ids)
        rows.map { it.ruleName }.distinct() shouldBe listOf("Bank purchases")
        rows.associate { it.fieldName to it.fieldType } shouldBe mapOf(
            "Amount" to RuleField.FieldType.CURRENCY,
            "Label" to RuleField.FieldType.STRING,
        )
    }

    @Test
    fun `insights totals and top rules still include a deleted rule`() = runBlocking<Unit> {
        seed()
        ruleDao.deleteRuleWithRelatedData(RULE_ID)

        val totals = database.extractedFieldValueDao().observeNumericTotalsSince(0L).first()
        totals.size shouldBe 1
        totals.single().ruleId shouldBe null
        totals.single().ruleName shouldBe "Bank purchases"
        totals.single().fieldId shouldBe null
        totals.single().fieldName shouldBe "Amount"
        totals.single().fieldType shouldBe RuleField.FieldType.CURRENCY
        totals.single().sum shouldBe 42.5
        totals.single().count shouldBe 1

        val topRules = database.ruleExecutionDao().observeTopRulesSince(0L, 10).first()
        topRules.map { it.ruleId to it.ruleName } shouldBe listOf(null to "Bank purchases")

        val recent = database.ruleExecutionDao().observeRecentActivity(10).first()
        recent.map { it.ruleId to it.ruleName } shouldBe listOf(null to "Bank purchases")
    }

    @Test
    fun `a live rule renamed after execution shows its current name in joined queries`() = runBlocking<Unit> {
        seed()
        ruleDao.upsert(rule().copy(name = "Renamed"))

        database.ruleExecutionDao().observeTopRulesSince(0L, 10).first()
            .map { it.ruleId to it.ruleName } shouldBe listOf(RULE_ID to "Renamed")
    }

    private suspend fun seed() {
        database.notificationDao().insert(
            NotificationEntity(
                id = NOTIFICATION_ID,
                packageName = "com.example",
                appName = "Example",
                title = "t",
                content = "c",
                rawContent = "raw",
                timestamp = 1L,
            ),
        )
        ruleDao.saveRuleWithRelatedData(
            rule = rule(),
            fields = listOf(field(FIELD_AMOUNT, "Amount", "CURRENCY"), field(FIELD_LABEL, "Label", "STRING")),
            conditions = listOf(condition()),
            actions = listOf(action()),
            apps = listOf("com.example"),
        )
        database.ruleExecutionDao().insert(execution(EXECUTION_ID, ruleId = RULE_ID, ruleName = "Bank purchases"))
        database.extractedFieldValueDao().insertAll(
            listOf(
                value("value-amount", FIELD_AMOUNT, "Amount", "CURRENCY", number = 42.5),
                value("value-label", FIELD_LABEL, "Label", "STRING", number = null),
            ),
        )
    }

    private fun execution(id: String, ruleId: String?, ruleName: String) = RuleExecutionEntity(
        id = id,
        notificationId = NOTIFICATION_ID,
        ruleId = ruleId,
        ruleName = ruleName,
        extractedData = "{}",
        triggeredActions = "[]",
        createdAt = 1_000L,
    )

    private fun value(id: String, fieldId: String, name: String, type: String, number: Double?) = ExtractedFieldValueEntity(
        id = id,
        ruleExecutionId = EXECUTION_ID,
        ruleFieldId = fieldId,
        fieldName = name,
        fieldType = type,
        valueText = "x",
        valueNumber = number,
        valueDate = null,
    )

    private fun rule() = RuleEntity(id = RULE_ID, name = "Bank purchases", description = null, category = null)

    private fun condition() = RuleConditionEntity(id = "condition-1", ruleId = RULE_ID, payload = "{}")

    private fun action() = RuleActionEntity(id = ACTION_ID, ruleId = RULE_ID, type = "SAVE_DATA")

    private fun field(id: String, name: String, type: String) = RuleFieldEntity(
        id = id,
        actionId = ACTION_ID,
        name = name,
        fieldType = type,
        methodType = "REGEX",
        methodConfig = "{}",
    )

    private companion object {
        const val RULE_ID = "rule-1"
        const val ACTION_ID = "action-1"
        const val FIELD_AMOUNT = "field-amount"
        const val FIELD_LABEL = "field-label"
        const val NOTIFICATION_ID = "notification-1"
        const val EXECUTION_ID = "execution-1"
        val NO_IDS = emptyList<String>()
    }
}
