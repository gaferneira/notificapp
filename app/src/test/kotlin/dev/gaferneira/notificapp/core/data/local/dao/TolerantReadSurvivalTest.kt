package dev.gaferneira.notificapp.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.mapper.RuleMapper
import dev.gaferneira.notificapp.core.extraction.RuleMatcher
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Rows written by another build (unknown condition/action types) must load without crashing,
 * and an unreadable condition must fail closed (never match).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TolerantReadSurvivalTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a rule with an unknown condition and action type loads, skips the action, and never matches`() = runBlocking<Unit> {
        val dao = database.ruleDao()
        dao.saveRuleWithRelatedData(
            rule = RuleEntity(id = "r1", name = "Future rule", description = null, category = null),
            fields = emptyList(),
            conditions = listOf(RuleConditionEntity(id = "c1", ruleId = "r1", payload = """{"type":"battery_level","id":"c1"}""")),
            actions = listOf(RuleActionEntity(id = "a1", ruleId = "r1", type = "REMOVED_ACTION")),
            apps = null,
        )

        val rule = RuleMapper.toDomain(
            entity = dao.getById("r1")!!,
            fieldEntities = emptyList(),
            conditionEntities = dao.getConditionsForRule("r1"),
            actionEntities = dao.getActionsForRule("r1"),
        )

        rule.actions.size shouldBe 0
        rule.conditions.size shouldBe 1
        val notification = createTestNotification()
        ConditionCombinator.entries.forEach { combinator ->
            RuleMatcher.matches(notification, rule.conditions, LocalDateTime.of(2024, 1, 15, 10, 0), combinator) shouldBe false
        }
    }

    @Test
    fun `hot queries use the composite indexes`() {
        val db = database.openHelper.writableDatabase
        fun plan(sql: String): String = db.query("EXPLAIN QUERY PLAN $sql").use { c ->
            buildString { while (c.moveToNext()) append(c.getString(c.getColumnIndexOrThrow("detail"))).append('\n') }
        }

        plan("SELECT COUNT(*) FROM rule_executions WHERE created_at >= 0 AND was_dry_run = 0") shouldContainIndex "was_dry_run"
        plan("SELECT * FROM rule_executions WHERE rule_id = 'x' ORDER BY created_at DESC") shouldContainIndex "rule_id"
        plan("SELECT * FROM notifications WHERE is_processed = 0 ORDER BY timestamp ASC") shouldContainIndex "is_processed"
    }

    private infix fun String.shouldContainIndex(column: String) {
        (contains("USING INDEX") || contains("USING COVERING INDEX")) shouldBe true
        contains(column) shouldBe true
    }
}
