package dev.gaferneira.notificapp.features.ruleeditor.domain

/** One piece of rule state shown in the collapsed header of the Advanced section. */
enum class AdvancedStateItem { TEST_MODE_ON, TEST_MODE_OFF, ORIGINAL_TEXT_DELETED }

/**
 * State summary for the collapsed Advanced header so hidden options are never invisible. Test mode
 * is always reported; deleting the original text is reported only when the option applies (the rule
 * has an Extract-data action) and is on.
 */
fun advancedStateSummary(
    isDryRun: Boolean,
    deleteRawContentVisible: Boolean,
    deleteRawContentEnabled: Boolean,
): List<AdvancedStateItem> = buildList {
    add(if (isDryRun) AdvancedStateItem.TEST_MODE_ON else AdvancedStateItem.TEST_MODE_OFF)
    if (deleteRawContentVisible && deleteRawContentEnabled) add(AdvancedStateItem.ORIGINAL_TEXT_DELETED)
}

/** The Advanced section opens by default only when it holds a non-default setting that is switched on. */
fun shouldExpandAdvancedInitially(
    isDryRun: Boolean,
    deleteRawContentVisible: Boolean,
    deleteRawContentEnabled: Boolean,
): Boolean = isDryRun || (deleteRawContentVisible && deleteRawContentEnabled)
