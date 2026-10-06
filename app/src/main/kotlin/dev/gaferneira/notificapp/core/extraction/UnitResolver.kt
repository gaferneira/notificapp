package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.RuleField

/**
 * Pure helpers that turn currency markers found in extracted text into units, and resolve the final
 * unit stored with an extracted value. No I/O, no Android imports.
 */
object UnitResolver {

    private val MARKER_REGEX = Regex(FieldExtractor.CURRENCY_MARKER)

    private val ISO_CODE = Regex("[A-Za-z]{3}")

    /** Symbols that identify exactly one currency. `$` and `kr` are deliberately absent (many currencies share them). */
    private val UNAMBIGUOUS_SYMBOLS = mapOf(
        "€" to "EUR",
        "£" to "GBP",
        "¥" to "JPY",
        "₹" to "INR",
        "₩" to "KRW",
        "₽" to "RUB",
        "₺" to "TRY",
        "₪" to "ILS",
        "₱" to "PHP",
        "฿" to "THB",
        "R$" to "BRL",
    )

    /** First currency marker (symbol, ISO code or `kr`) in [text], or null. */
    fun detectMarker(text: String): String? = MARKER_REGEX.find(text)?.value

    /** ISO 4217 code for an unambiguous [marker]; null for ambiguous markers (`$`, `kr`) or unknown input. */
    fun resolveMarker(marker: String?): String? {
        val trimmed = marker?.trim().orEmpty()
        return UNAMBIGUOUS_SYMBOLS[trimmed] ?: trimmed.takeIf { ISO_CODE.matches(it) }?.uppercase()
    }

    /**
     * Final unit for a value of [field] extracted as [valueText]:
     * - CURRENCY: an unambiguous marker in the text wins, otherwise the field's default unit, otherwise null.
     * - NUMBER: the field's default unit.
     * - Any other type: null.
     * Currency codes are uppercased and trimmed; other units are trimmed as typed; blank becomes null.
     */
    fun resolveUnit(field: RuleField, valueText: String): String? = when (field.fieldType) {
        RuleField.FieldType.CURRENCY ->
            resolveMarker(detectMarker(valueText)) ?: normalizeUnit(field.defaultUnit, field.fieldType)
        RuleField.FieldType.NUMBER -> normalizeUnit(field.defaultUnit, field.fieldType)
        else -> null
    }

    /** Normalizes a user-typed unit for [fieldType]; null when blank or when the type carries no unit. */
    fun normalizeUnit(raw: String?, fieldType: RuleField.FieldType): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (fieldType) {
            RuleField.FieldType.CURRENCY -> trimmed.uppercase()
            RuleField.FieldType.NUMBER -> trimmed
            else -> null
        }
    }
}
