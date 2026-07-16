package dev.gaferneira.notificapp.core.data.export

import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.RuleField
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Test
import java.io.StringWriter

class DataJsonSerializerTest {

    private fun row(id: String) = DataBrowserRow(
        valueId = id,
        executionId = "exec-$id",
        ruleName = "Rule",
        packageName = "com.test.app",
        appName = "Test App",
        fieldName = "Amount",
        fieldType = RuleField.FieldType.NUMBER,
        valueText = null,
        valueNumber = 42.0,
        valueDate = null,
        notificationTitle = "Title",
        notificationContent = "Content",
        createdAt = 1_000L,
    )

    @Test
    fun `an empty result set produces a valid, empty JSON array`() {
        val writer = StringWriter()

        DataJsonSerializer.writeArrayStart(writer)
        DataJsonSerializer.writeArrayEnd(writer)

        writer.toString() shouldBe "[]"
        Json.parseToJsonElement(writer.toString()).let { it is JsonArray && it.isEmpty() } shouldBe true
    }

    @Test
    fun `a single batch produces a well-formed array with one element`() {
        val writer = StringWriter()

        DataJsonSerializer.writeArrayStart(writer)
        DataJsonSerializer.writeRows(writer, listOf(row("v1")), isFirstBatch = true)
        DataJsonSerializer.writeArrayEnd(writer)

        val parsed = Json.parseToJsonElement(writer.toString()) as JsonArray
        parsed.size shouldBe 1
    }

    @Test
    fun `multiple streamed batches produce one well-formed array - no missing or extra commas`() {
        val writer = StringWriter()

        DataJsonSerializer.writeArrayStart(writer)
        DataJsonSerializer.writeRows(writer, listOf(row("v1"), row("v2")), isFirstBatch = true)
        DataJsonSerializer.writeRows(writer, listOf(row("v3")), isFirstBatch = false)
        DataJsonSerializer.writeArrayEnd(writer)

        val parsed = Json.parseToJsonElement(writer.toString()) as JsonArray
        parsed.size shouldBe 3
    }

    @Test
    fun `streamed multi-batch output equals a single-call encoding of the same rows`() {
        val streamed = StringWriter()
        DataJsonSerializer.writeArrayStart(streamed)
        DataJsonSerializer.writeRows(streamed, listOf(row("v1"), row("v2")), isFirstBatch = true)
        DataJsonSerializer.writeRows(streamed, listOf(row("v3")), isFirstBatch = false)
        DataJsonSerializer.writeArrayEnd(streamed)

        val single = StringWriter()
        DataJsonSerializer.writeArrayStart(single)
        DataJsonSerializer.writeRows(single, listOf(row("v1"), row("v2"), row("v3")), isFirstBatch = true)
        DataJsonSerializer.writeArrayEnd(single)

        streamed.toString() shouldBe single.toString()
    }
}
