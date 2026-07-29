package dev.gaferneira.notificapp.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.core.data.local.entity.SuggestionDismissalEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First instrumented Room test in this repo, covering this change's new schema pieces:
 * `suggestion_dismissals`' composite-PK REPLACE semantics, and the three new windowed/joined DAO
 * queries added to `RuleExecutionDao`/`NotificationDao`. Uses an in-memory DB (no SQLCipher
 * passphrase needed - production's `DatabaseModule.provideDatabase` encryption setup is
 * intentionally not exercised here).
 */
@RunWith(AndroidJUnit4::class)
class HomeQueriesInstrumentedTest {

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
    fun suggestionDismissalDao_replaces_on_repeated_dismissal_of_the_same_key() = runBlocking {
        val dao = database.suggestionDismissalDao()
        dao.dismiss(SuggestionDismissalEntity(packageName = "com.a", normalizedTitleKey = "key-1", dismissedAt = 1_000L))

        dao.dismiss(SuggestionDismissalEntity(packageName = "com.a", normalizedTitleKey = "key-1", dismissedAt = 2_000L))

        val all = dao.observeAll().first()
        all.size shouldBe 1
        all.single().dismissedAt shouldBe 2_000L
    }

    @Test
    fun suggestionDismissalDao_keeps_distinct_keys_for_the_same_package_separate() = runBlocking {
        val dao = database.suggestionDismissalDao()
        dao.dismiss(SuggestionDismissalEntity(packageName = "com.a", normalizedTitleKey = "key-1", dismissedAt = 1_000L))
        dao.dismiss(SuggestionDismissalEntity(packageName = "com.a", normalizedTitleKey = "key-2", dismissedAt = 1_000L))

        val all = dao.observeAll().first()
        all.size shouldBe 2
    }

    @Test
    fun ruleExecutionDao_observeExecutionCountSince_excludes_dry_run_and_out_of_window_rows() = runBlocking {
        insertMinimalRuleAndNotification(ruleId = "r1", notificationId = "n1")
        val ruleExecutionDao = database.ruleExecutionDao()
        ruleExecutionDao.insert(testExecution(id = "e1", ruleId = "r1", notificationId = "n1", createdAt = 5_000L, wasDryRun = false))
        ruleExecutionDao.insert(testExecution(id = "e2", ruleId = "r1", notificationId = "n1", createdAt = 5_000L, wasDryRun = true))
        ruleExecutionDao.insert(testExecution(id = "e3", ruleId = "r1", notificationId = "n1", createdAt = 1_000L, wasDryRun = false))

        val count = ruleExecutionDao.observeExecutionCountSince(4_000L).first()

        count shouldBe 1
    }

    @Test
    fun ruleExecutionDao_observeRecentActivity_returns_joined_rows_respecting_limit_and_order() = runBlocking {
        insertMinimalRuleAndNotification(ruleId = "r1", notificationId = "n1")
        val ruleExecutionDao = database.ruleExecutionDao()
        ruleExecutionDao.insert(testExecution(id = "e1", ruleId = "r1", notificationId = "n1", createdAt = 1_000L, wasDryRun = false))
        ruleExecutionDao.insert(testExecution(id = "e2", ruleId = "r1", notificationId = "n1", createdAt = 2_000L, wasDryRun = false))

        val rows = ruleExecutionDao.observeRecentActivity(limit = 1).first()

        rows.size shouldBe 1
        rows.single().executionId shouldBe "e2"
        rows.single().ruleName shouldBe "Test Rule"
    }

    @Test
    fun notificationDao_observeActiveAppCountSince_and_observeRecentSince_respect_the_window() = runBlocking {
        val notificationDao = database.notificationDao()
        notificationDao.insert(testNotification(id = "n1", packageName = "com.a", timestamp = 5_000L))
        notificationDao.insert(testNotification(id = "n2", packageName = "com.b", timestamp = 1_000L))

        val appCount = notificationDao.observeActiveAppCountSince(4_000L).first()
        val recent = notificationDao.observeRecentSince(4_000L, limit = 10).first()

        appCount shouldBe 1
        recent.size shouldBe 1
        recent.single().id shouldBe "n1"
    }

    private suspend fun insertMinimalRuleAndNotification(ruleId: String, notificationId: String) {
        database.ruleDao().insert(testRule(id = ruleId))
        database.notificationDao().insert(testNotification(id = notificationId, packageName = "com.a", timestamp = 1_000L))
    }
}

private fun testRule(id: String, name: String = "Test Rule") = RuleEntity(
    id = id,
    name = name,
    description = null,
    category = null,
)

private fun testNotification(id: String, packageName: String, timestamp: Long) = NotificationEntity(
    id = id,
    packageName = packageName,
    appName = "Test App",
    title = null,
    content = null,
    rawContent = "",
    timestamp = timestamp,
)

private fun testExecution(
    id: String,
    ruleId: String,
    notificationId: String,
    createdAt: Long,
    wasDryRun: Boolean,
) = RuleExecutionEntity(
    id = id,
    ruleId = ruleId,
    notificationId = notificationId,
    extractedData = "{}",
    triggeredActions = "[]",
    wasDryRun = wasDryRun,
    createdAt = createdAt,
)
