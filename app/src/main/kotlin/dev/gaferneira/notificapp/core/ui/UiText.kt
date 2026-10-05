package dev.gaferneira.notificapp.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class StringResource(
        @StringRes val id: Int,
        val args: Array<Any> = arrayOf(),
    ) : UiText {
        override fun equals(other: Any?): Boolean = other is StringResource && other.id == id && other.args.contentEquals(args)

        override fun hashCode(): Int = 31 * id + args.contentHashCode()

        override fun toString(): String = "StringResource(id=$id, args=${args.contentToString()})"
    }

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(id = id, *args)
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(id, *args)
    }
}
