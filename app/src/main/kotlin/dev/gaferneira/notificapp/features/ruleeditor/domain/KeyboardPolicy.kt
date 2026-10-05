package dev.gaferneira.notificapp.features.ruleeditor.domain

/** Window height (dp) under which an open keyboard leaves too little room for a sticky bottom bar. */
internal const val COMPACT_HEIGHT_DP = 480

/**
 * Whether the sticky bottom bar should step aside while the keyboard is open. On normal-height
 * windows the bar rides above the keyboard; on short ones (landscape phones) it would eat most of
 * the remaining space, so it hides until the keyboard closes.
 */
internal fun shouldHideBottomBarForIme(isImeVisible: Boolean, windowHeightDp: Int): Boolean = isImeVisible && windowHeightDp < COMPACT_HEIGHT_DP

/** The optional text field the name field hands focus to, in on-screen order. */
internal enum class NameNextField { DESCRIPTION, CATEGORY, NONE }

/** Next field after Name: the first optional field that is currently shown, or none (keyboard Done). */
internal fun nameNextField(showDescription: Boolean, showCategory: Boolean): NameNextField = when {
    showDescription -> NameNextField.DESCRIPTION
    showCategory -> NameNextField.CATEGORY
    else -> NameNextField.NONE
}

/**
 * Whether the guided Review step should focus the name field when it appears: only while the name is
 * still empty (so restoring a filled draft never pops the keyboard) and only once per visit.
 */
internal fun shouldAutoFocusName(name: String, alreadyHandled: Boolean): Boolean = !alreadyHandled && name.isBlank()
