package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.domain.model.ActionType
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Locks two guarantees about the curated starter rule templates ([RuleTemplates.all]):
 * every asset actually decodes via [RuleJsonCodec] (catches a typo or a wire-format drift before
 * it ships), and the whole set together exercises every [ActionType] at least once (catches a
 * curation gap - e.g. someone adding a seventh finance template instead of covering a missing
 * action type).
 */
class RuleTemplatesTest {

    private fun readTemplateAsset(assetFileName: String): String {
        val resource = checkNotNull(javaClass.classLoader?.getResourceAsStream("rules/$assetFileName")) {
            "Template asset not found on classpath: rules/$assetFileName"
        }
        return resource.bufferedReader().use { it.readText() }
    }

    @Test
    fun `every template asset decodes successfully via RuleJsonCodec`() {
        RuleTemplates.all.forEach { template ->
            val json = readTemplateAsset(template.assetFileName)

            val result = RuleJsonCodec.decode(json)

            result.isSuccess shouldBe true
        }
    }

    @Test
    fun `templates together cover every action type at least once`() {
        val actionTypesInTemplates = RuleTemplates.all
            .map { readTemplateAsset(it.assetFileName) }
            .map { json -> RuleJsonCodec.decode(json).getOrThrow() }
            .flatMap { it.rule.actions }
            .map { it.type }
            .toSet()

        ActionType.entries.forEach { actionType ->
            actionTypesInTemplates shouldContain actionType
        }
    }
}
