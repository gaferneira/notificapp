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
import java.time.LocalDate

/**
 * `extracted_field_values.value_epoch_day` stores a zone-independent calendar day, so range queries
 * take epoch-day bounds and the Data Browser projection exposes a [LocalDate].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DateValueEpochDayTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `date range query is inclusive on epoch-day bounds and skips null dates`() = runBlocking<Unit> {
        seed(
            "jan-14" to LocalDate.of(2024, 1, 14),
            "jan-15" to LocalDate.of(2024, 1, 15),
            "jan-16" to LocalDate.of(2024, 1, 16),
            "none" to null,
        )

        val ids = database.extractedFieldValueDao()
            .getDateValuesInRange(FIELD_ID, LocalDate.of(2024, 1, 15).toEpochDay(), LocalDate.of(2024, 1, 16).toEpochDay())
            .map { it.id }
            .sorted()

        ids shouldBe listOf("jan-15", "jan-16")
    }

    @Test
    fun `data browser projection exposes the stored epoch day as a LocalDate`() = runBlocking<Unit> {
        seed("jan-15" to LocalDate.of(2024, 1, 15), "none" to null)

        val rows = database.dataBrowserDao().getMatchingBatch(listOf("jan-15", "none")).associateBy { it.valueId }

        rows.getValue("jan-15").valueDate shouldBe LocalDate.of(2024, 1, 15)
        rows.getValue("none").valueDate shouldBe null
    }

    private suspend fun seed(vararg values: Pair<String, LocalDate?>) {
        database.notificationDao().upsert(
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
        database.ruleDao().saveRuleWithRelatedData(
            rule = RuleEntity(id = RULE_ID, name = "Rule", description = null, category = null),
            fields = listOf(RuleFieldEntity(id = FIELD_ID, actionId = ACTION_ID, name = "When", fieldType = "DATE", methodConfig = "{}")),
            conditions = listOf(RuleConditionEntity(id = "condition-1", ruleId = RULE_ID, payload = "{}")),
            actions = listOf(RuleActionEntity(id = ACTION_ID, ruleId = RULE_ID, type = "SAVE_DATA")),
            apps = listOf("com.example"),
        )
        database.ruleExecutionDao().upsert(
            RuleExecutionEntity(
                id = EXECUTION_ID,
                notificationId = NOTIFICATION_ID,
                ruleId = RULE_ID,
                ruleName = "Rule",
                triggeredActions = "[]",
                createdAt = 1_000L,
            ),
        )
        database.extractedFieldValueDao().insertAll(
            values.map { (id, date) ->
                ExtractedFieldValueEntity(
                    id = id,
                    ruleExecutionId = EXECUTION_ID,
                    ruleFieldId = FIELD_ID,
                    fieldName = "When",
                    fieldType = "DATE",
                    valueText = id,
                    valueNumber = null,
                    valueEpochDay = date?.toEpochDay(),
                )
            },
        )
    }

    private companion object {
        const val NOTIFICATION_ID = "notification-1"
        const val EXECUTION_ID = "execution-1"
        const val RULE_ID = "rule-1"
        const val ACTION_ID = "action-1"
        const val FIELD_ID = "field-when"
    }
}
