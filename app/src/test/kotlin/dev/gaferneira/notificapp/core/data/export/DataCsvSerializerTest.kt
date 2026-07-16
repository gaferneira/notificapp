package dev.gaferneira.notificapp.core.data.export

import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.RuleField
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.StringWriter

class DataCsvSerializerTest {

    @Suppress("LongParameterList") // test fixture builder covering every escapable/nullable DataBrowserRow field
    private fun row(
        id: String = "v1",
        fieldName: String = "Amount",
        fieldType: RuleField.FieldType = RuleField.FieldType.NUMBER,
        valueText: String? = null,
        valueNumber: Double? = 42.5,
        valueDate: Long? = null,
        appName: String = "Test App",
        ruleName: String = "Rule",
        createdAt: Long = 1_000L,
    ) = DataBrowserRow(
        valueId = id,
        executionId = "exec-$id",
        ruleName = ruleName,
        packageName = "com.test.app",
        appName = appName,
        fieldName = fieldName,
        fieldType = fieldType,
        valueText = valueText,
        valueNumber = valueNumber,
        valueDate = valueDate,
        notificationTitle = "Title",
        notificationContent = "Content",
        createdAt = createdAt,
    )

    @Test
    fun `writeHeader writes exactly one header row`() {
        val writer = StringWriter()

        DataCsvSerializer.writeHeader(writer)

        writer.toString() shouldBe "Field Name,Field Type,Value,Source App,Rule Name,Timestamp\r\n"
    }

    @Test
    fun `an empty result set produces header-only, still valid CSV`() {
        val writer = StringWriter()

        DataCsvSerializer.writeHeader(writer)
        DataCsvSerializer.writeRows(writer, emptyList())

        writer.toString() shouldBe "Field Name,Field Type,Value,Source App,Rule Name,Timestamp\r\n"
    }

    @Test
    fun `writeRows writes one row per entry with the numeric value column`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = 42.5)))

        writer.toString() shouldBe "Amount,NUMBER,42.5,Test App,Rule,1000\r\n"
    }

    @Test
    fun `a value containing a comma is quoted`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = "Acme, Inc")))

        writer.toString() shouldBe "Amount,NUMBER,\"Acme, Inc\",Test App,Rule,1000\r\n"
    }

    @Test
    fun `a value containing a double quote is escaped by doubling it`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = """He said "hi"""")))

        writer.toString() shouldBe "Amount,NUMBER,\"He said \"\"hi\"\"\",Test App,Rule,1000\r\n"
    }

    @Test
    fun `a value containing a newline is quoted`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = "line1\nline2")))

        writer.toString() shouldBe "Amount,NUMBER,\"line1\nline2\",Test App,Rule,1000\r\n"
    }

    @Test
    fun `writeRows across multiple streamed batches produces the same output as one call`() {
        val streamed = StringWriter()
        DataCsvSerializer.writeRows(streamed, listOf(row("v1"), row("v2")))
        DataCsvSerializer.writeRows(streamed, listOf(row("v3")))

        val single = StringWriter()
        DataCsvSerializer.writeRows(single, listOf(row("v1"), row("v2"), row("v3")))

        streamed.toString() shouldBe single.toString()
    }

    @Test
    fun `a value starting with an equals sign is prefixed with a literal quote to prevent formula injection`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = "=cmd|'/c calc'!A1")))

        writer.toString() shouldBe "Amount,NUMBER,'=cmd|'/c calc'!A1,Test App,Rule,1000\r\n"
    }

    @Test
    fun `a value starting with a plus sign is prefixed with a literal quote to prevent formula injection`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = "+1234567890")))

        writer.toString() shouldBe "Amount,NUMBER,'+1234567890,Test App,Rule,1000\r\n"
    }

    @Test
    fun `a value starting with a minus sign is prefixed with a literal quote to prevent formula injection`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = "-1+1")))

        writer.toString() shouldBe "Amount,NUMBER,'-1+1,Test App,Rule,1000\r\n"
    }

    @Test
    fun `a value starting with an at sign is prefixed with a literal quote to prevent formula injection`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = "@SUM(1,2)")))

        writer.toString() shouldBe "Amount,NUMBER,\"'@SUM(1,2)\",Test App,Rule,1000\r\n"
    }

    @Test
    fun `a normal value is not prefixed with a literal quote`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(writer, listOf(row(valueNumber = null, valueText = "Acme Inc")))

        writer.toString() shouldBe "Amount,NUMBER,Acme Inc,Test App,Rule,1000\r\n"
    }

    @Test
    fun `a DATE field falls back to the raw epoch-millis value column`() {
        val writer = StringWriter()

        DataCsvSerializer.writeRows(
            writer,
            listOf(row(fieldType = RuleField.FieldType.DATE, valueNumber = null, valueDate = 999_999L)),
        )

        writer.toString() shouldBe "Amount,DATE,999999,Test App,Rule,1000\r\n"
    }
}
