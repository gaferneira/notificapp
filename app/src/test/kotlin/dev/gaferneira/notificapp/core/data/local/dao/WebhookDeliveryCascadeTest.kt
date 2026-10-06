package dev.gaferneira.notificapp.core.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.entity.WebhookDeliveryEntity
import dev.gaferneira.notificapp.core.data.local.entity.WebhookEntity
import dev.gaferneira.notificapp.core.data.repository.WebhookDeliveryRepositoryImpl
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `webhook_deliveries.webhook_id` is an `ON DELETE CASCADE` FK to `webhooks.id`: deleting a webhook
 * drops its queued deliveries, editing one (upsert) must not, and a delivery for an unknown webhook
 * is rejected as a mapped [Failure] at the repository boundary (ADR 006) instead of crashing.
 *
 * Runs on the JVM via Robolectric against an in-memory Room database (no SQLCipher).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebhookDeliveryCascadeTest {

    private lateinit var database: AppDatabase
    private lateinit var webhookDao: WebhookDao
    private lateinit var deliveryDao: WebhookDeliveryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        webhookDao = database.webhookDao()
        deliveryDao = database.webhookDeliveryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleting a webhook deletes its deliveries and keeps other webhooks' deliveries`() = runBlocking<Unit> {
        webhookDao.upsert(webhook(WEBHOOK_ID))
        webhookDao.upsert(webhook(OTHER_WEBHOOK_ID))
        deliveryDao.insert(delivery("d1", WEBHOOK_ID))
        deliveryDao.insert(delivery("d2", WEBHOOK_ID))
        deliveryDao.insert(delivery("d3", OTHER_WEBHOOK_ID))

        webhookDao.deleteById(WEBHOOK_ID)

        deliveryDao.getAllUnresolved().map { it.id } shouldBe listOf("d3")
    }

    @Test
    fun `editing a webhook keeps its queued deliveries`() = runBlocking<Unit> {
        webhookDao.upsert(webhook(WEBHOOK_ID))
        deliveryDao.insert(delivery("d1", WEBHOOK_ID))

        webhookDao.upsert(webhook(WEBHOOK_ID).copy(name = "Renamed"))

        webhookDao.getById(WEBHOOK_ID)?.name shouldBe "Renamed"
        deliveryDao.getAllUnresolved().map { it.id } shouldBe listOf("d1")
    }

    @Test
    fun `enqueueing a delivery for an unknown webhook returns a Failure instead of throwing`() = runBlocking<Unit> {
        val repository = WebhookDeliveryRepositoryImpl(deliveryDao, Dispatchers.Unconfined)

        val result = repository.enqueue(WebhookDelivery(id = "d1", webhookId = "missing", payload = "{}"))

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<Failure>()
        deliveryDao.getAllUnresolved() shouldBe emptyList()
    }

    private fun webhook(id: String) = WebhookEntity(
        id = id,
        name = "Hook",
        url = "https://example.com/hook",
        headers = emptyMap(),
        authType = "NONE",
        authHeaderName = null,
        authValue = null,
        method = "POST",
        queryParams = emptyMap(),
        createdAt = 1L,
        lastDeliveryStatus = "NEVER",
        lastDeliveryAt = null,
    )

    private fun delivery(id: String, webhookId: String) = WebhookDeliveryEntity(
        id = id,
        webhookId = webhookId,
        payload = "{}",
        failureType = null,
        attemptCount = 0,
        createdAt = 1L,
        lastAttemptAt = null,
    )

    private companion object {
        const val WEBHOOK_ID = "webhook-1"
        const val OTHER_WEBHOOK_ID = "webhook-2"
    }
}
