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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Editing a rule must never wipe its execution history: `saveRuleWithRelatedData` has to update
 * rows in place (no REPLACE / delete-all-and-reinsert), otherwise FK cascades erase
 * `rule_executions` and `extracted_field_values`.
 *
 * Runs on the JVM via Robolectric (JUnit 4 runner, executed by the Vintage engine) against an
 * in-memory Room database on the plain framework SQLite helper (no SQLCipher).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuleDaoSaveTest {

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
    fun `saving an existing rule keeps its executions and extracted values`() = runBlocking<Unit> {
        seedRuleWithHistory(fields = listOf(FIELD_A))

        ruleDao.saveRuleWithRelatedData(
            rule = rule(name = "Renamed"),
            fields = listOf(field(FIELD_A)),
            conditions = listOf(condition()),
            actions = listOf(action()),
            apps = listOf("com.example"),
        )

        ruleDao.getById(RULE_ID)?.name shouldBe "Renamed"
        database.ruleExecutionDao().getExecutionsForRule(RULE_ID).map { it.id } shouldBe listOf(EXECUTION_ID)
        database.extractedFieldValueDao().getValuesForExecution(EXECUTION_ID).map { it.ruleFieldId } shouldBe listOf(FIELD_A)
    }

    @Test
    fun `removing one field deletes only that field and keeps the other field's values`() = runBlocking<Unit> {
        seedRuleWithHistory(fields = listOf(FIELD_A, FIELD_B))

        ruleDao.saveRuleWithRelatedData(
            rule = rule(name = "Renamed"),
            fields = listOf(field(FIELD_A)),
            conditions = listOf(condition()),
            actions = listOf(action()),
            apps = null,
        )

        ruleDao.getFieldsForAction(ACTION_ID).map { it.id } shouldBe listOf(FIELD_A)
        database.ruleExecutionDao().getExecutionsForRule(RULE_ID).map { it.id } shouldBe listOf(EXECUTION_ID)
        database.extractedFieldValueDao().getValuesForExecution(EXECUTION_ID).map { it.ruleFieldId } shouldBe listOf(FIELD_A)
    }

    private suspend fun seedRuleWithHistory(fields: List<String>) {
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
            rule = rule(name = "Original"),
            fields = fields.map(::field),
            conditions = listOf(condition()),
            actions = listOf(action()),
            apps = listOf("com.example"),
        )
        database.ruleExecutionDao().insert(
            RuleExecutionEntity(
                id = EXECUTION_ID,
                notificationId = NOTIFICATION_ID,
                ruleId = RULE_ID,
                extractedData = "{}",
                triggeredActions = "[]",
            ),
        )
        database.extractedFieldValueDao().insertAll(
            fields.map { fieldId ->
                ExtractedFieldValueEntity(
                    id = "value-$fieldId",
                    ruleExecutionId = EXECUTION_ID,
                    ruleFieldId = fieldId,
                    valueText = "x",
                    valueNumber = null,
                    valueDate = null,
                )
            },
        )
    }

    private fun rule(name: String) = RuleEntity(id = RULE_ID, name = name, description = null, category = null)

    private fun condition() = RuleConditionEntity(id = "condition-1", ruleId = RULE_ID, payload = "{}")

    private fun action() = RuleActionEntity(id = ACTION_ID, ruleId = RULE_ID, type = "SAVE_DATA")

    private fun field(id: String) = RuleFieldEntity(
        id = id,
        actionId = ACTION_ID,
        name = id,
        fieldType = "STRING",
        methodType = "REGEX",
        methodConfig = "{}",
    )

    private companion object {
        const val RULE_ID = "rule-1"
        const val ACTION_ID = "action-1"
        const val FIELD_A = "field-a"
        const val FIELD_B = "field-b"
        const val NOTIFICATION_ID = "notification-1"
        const val EXECUTION_ID = "execution-1"
    }
}
