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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Insights totals are grouped per (field, unit) so currencies and units are never summed together. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnitTotalsTest {

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
    fun `same field with two currencies yields one total per currency`() = runBlocking<Unit> {
        seed(
            value("v1", FIELD_A, "EUR", 10.0),
            value("v2", FIELD_A, "EUR", 5.0),
            value("v3", FIELD_A, "GBP", 7.0),
        )

        val totals = database.extractedFieldValueDao().observeNumericTotalsSince(0L).first()

        totals.map { Triple(it.fieldId, it.unit, it.sum) }.toSet() shouldBe setOf(
            Triple(FIELD_A, "EUR", 15.0),
            Triple(FIELD_A, "GBP", 7.0),
        )
        totals.first { it.unit == "EUR" }.count shouldBe 2
    }

    @Test
    fun `dollar field with default USD and kr field with default SEK stay separate`() = runBlocking<Unit> {
        seed(
            value("v1", FIELD_A, "USD", 20.0),
            value("v2", FIELD_B, "SEK", 200.0),
        )

        val totals = database.extractedFieldValueDao().observeNumericTotalsSince(0L).first()

        totals.map { Triple(it.fieldId, it.unit, it.sum) }.toSet() shouldBe setOf(
            Triple(FIELD_A, "USD", 20.0),
            Triple(FIELD_B, "SEK", 200.0),
        )
    }

    @Test
    fun `values without a unit form their own total next to unit totals`() = runBlocking<Unit> {
        seed(value("v1", FIELD_A, null, 3.0), value("v2", FIELD_A, "SEK", 4.0))

        val totals = database.extractedFieldValueDao().observeNumericTotalsSince(0L).first()

        totals.map { it.unit to it.sum }.toSet() shouldBe setOf(null to 3.0, "SEK" to 4.0)
    }

    @Test
    fun `data browser rows expose the stored unit`() = runBlocking<Unit> {
        seed(value("v1", FIELD_A, "EUR", 10.0))
        val dao = database.dataBrowserDao()

        val ids = dao.getMatchingIds(emptyList(), false, emptyList(), false, emptyList(), false, 0L, false, 0L, false, "", false)

        dao.getMatchingBatch(ids).single().unit shouldBe "EUR"
    }

    private suspend fun seed(vararg values: ExtractedFieldValueEntity) {
        database.notificationDao().upsert(
            NotificationEntity(
                id = "n1",
                packageName = "com.example",
                appName = "Example",
                title = "t",
                content = "c",
                rawContent = "raw",
                timestamp = 1L,
            ),
        )
        val ruleDao = database.ruleDao()
        ruleDao.saveRuleWithRelatedData(
            rule = RuleEntity(id = "rule-1", name = "Bank", description = null, category = null),
            fields = listOf(field(FIELD_A, "Amount A"), field(FIELD_B, "Amount B")),
            conditions = listOf(RuleConditionEntity(id = "c1", ruleId = "rule-1", payload = "{}")),
            actions = listOf(RuleActionEntity(id = "action-1", ruleId = "rule-1", type = "SAVE_DATA")),
            apps = listOf("com.example"),
        )
        database.ruleExecutionDao().upsert(
            RuleExecutionEntity(
                id = "e1",
                notificationId = "n1",
                ruleId = "rule-1",
                ruleName = "Bank",
                triggeredActions = "[]",
                createdAt = 1_000L,
            ),
        )
        database.extractedFieldValueDao().insertAll(values.toList())
    }

    private fun field(id: String, name: String) = RuleFieldEntity(
        id = id,
        actionId = "action-1",
        name = name,
        fieldType = "CURRENCY",
        methodConfig = "{}",
    )

    private fun value(id: String, fieldId: String, unit: String?, number: Double) = ExtractedFieldValueEntity(
        id = id,
        ruleExecutionId = "e1",
        ruleFieldId = fieldId,
        fieldName = fieldId,
        fieldType = "CURRENCY",
        valueText = "x",
        valueNumber = number,
        valueEpochDay = null,
        valueUnit = unit,
    )

    private companion object {
        const val FIELD_A = "field-a"
        const val FIELD_B = "field-b"
    }
}
