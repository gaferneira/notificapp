package dev.gaferneira.notificapp.core.data.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class StorageStatsRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var notificationDao: NotificationDao
    private lateinit var ruleDao: RuleDao
    private lateinit var ruleExecutionDao: RuleExecutionDao
    private lateinit var extractedFieldValueDao: ExtractedFieldValueDao
    private lateinit var selectedAppDao: SelectedAppDao
    private lateinit var dbFile: File

    @BeforeEach
    fun setUp() {
        dbFile = Files.createTempFile("storage-stats-test", ".db").toFile().apply {
            writeBytes(ByteArray(SIZE_BYTES))
        }
        context = mockk()
        every { context.getDatabasePath(any()) } returns dbFile

        notificationDao = mockk()
        ruleDao = mockk()
        ruleExecutionDao = mockk()
        extractedFieldValueDao = mockk()
        selectedAppDao = mockk()

        database = mockk()
        every { database.notificationDao() } returns notificationDao
        every { database.ruleDao() } returns ruleDao
        every { database.ruleExecutionDao() } returns ruleExecutionDao
        every { database.extractedFieldValueDao() } returns extractedFieldValueDao
        every { database.selectedAppDao() } returns selectedAppDao
    }

    @AfterEach
    fun tearDown() {
        dbFile.delete()
    }

    private fun repository() = StorageStatsRepositoryImpl(
        context = context,
        database = database,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `getStorageStats aggregates db file size and row counts from every dao`() = runTest(testDispatcher) {
        coEvery { notificationDao.getCount() } returns 5
        coEvery { ruleDao.getCount() } returns 2
        coEvery { ruleExecutionDao.getCount() } returns 3
        coEvery { extractedFieldValueDao.getCount() } returns 4
        coEvery { selectedAppDao.getCount() } returns 1

        val result = repository().getStorageStats()

        result.isSuccess shouldBe true
        val stats = result.getOrNull()!!
        stats.databaseSizeBytes shouldBe SIZE_BYTES.toLong()
        stats.notificationCount shouldBe 5
        stats.ruleCount shouldBe 2
        stats.ruleExecutionCount shouldBe 3
        stats.extractedFieldValueCount shouldBe 4
        stats.selectedAppCount shouldBe 1
    }

    @Test
    fun `getStorageStats maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { notificationDao.getCount() } throws SQLiteException("db error")

        val result = repository().getStorageStats()

        result.isFailure shouldBe true
    }

    private companion object {
        const val SIZE_BYTES = 2048
    }
}
