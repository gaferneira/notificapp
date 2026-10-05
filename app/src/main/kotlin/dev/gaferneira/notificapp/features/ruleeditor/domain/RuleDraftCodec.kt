package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec

/**
 * Serializes an in-progress [RuleUiModel] so it can survive process death (via `SavedStateHandle`).
 *
 * Reuses the shareable rule wire format ([RuleJsonCodec]) instead of inventing a second one. A
 * draft has two quirks the wire format does not model, handled here: a blank name is allowed
 * (decoded with `requireName = false`) and a new rule has `id == null`, encoded with a sentinel id.
 * As with saving, the name is trimmed on the way through.
 */
internal object RuleDraftCodec {

    private const val NEW_RULE_ID = "__draft__"

    fun encode(draft: RuleUiModel): String = RuleJsonCodec.encode(draft.toEntity().copy(id = draft.id ?: NEW_RULE_ID))

    /** Returns null when [text] cannot be parsed, so a corrupt saved state falls back to a normal load. */
    fun decode(text: String): RuleUiModel? = RuleJsonCodec.decode(text, requireName = false)
        .map { result ->
            val model = RuleUiModel.fromDomain(result.rule)
            if (model.id == NEW_RULE_ID) model.copy(id = null) else model
        }
        .getOrNull()
}
