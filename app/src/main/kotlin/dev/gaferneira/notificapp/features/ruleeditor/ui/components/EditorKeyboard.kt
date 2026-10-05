package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/*
 * Keyboard conventions shared by every text field of the rule editor: prose gets sentence
 * capitalisation, machine-ish input (regex, anchors, JSON paths, match values) gets no
 * capitalisation or autocorrect, numbers get the number pad. The last field of a form uses Done,
 * which clears focus so the keyboard hides and the primary button is reachable again.
 */

/** Free prose (names, descriptions, categories, labels). */
internal fun proseKeyboardOptions(imeAction: ImeAction = ImeAction.Next): KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction)

/** Text that is matched or parsed literally: no capitalisation, no autocorrect. */
internal fun literalKeyboardOptions(imeAction: ImeAction = ImeAction.Next): KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    imeAction = imeAction,
)

/** Whole-number input. */
internal fun numberKeyboardOptions(imeAction: ImeAction = ImeAction.Next): KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction)

/** Done and Search both end the editing session: clear focus, which also hides the keyboard. */
@Composable
internal fun rememberClearFocusKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    return remember(focusManager) { focusManager.clearFocusKeyboardActions() }
}

private fun FocusManager.clearFocusKeyboardActions(): KeyboardActions = KeyboardActions(
    onDone = { clearFocus() },
    onSearch = { clearFocus() },
)

/**
 * Lifts a sheet's body above the navigation bar and, while the keyboard is open, above the
 * keyboard. Apply before `verticalScroll` so the scroll viewport shrinks with the keyboard and the
 * focused field is scrolled into view. `imePadding` runs second so the navigation-bar part of the
 * keyboard inset is not counted twice.
 */
internal fun Modifier.sheetInsetsPadding(): Modifier = navigationBarsPadding().imePadding()
