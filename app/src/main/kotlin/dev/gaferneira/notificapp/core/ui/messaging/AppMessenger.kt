package dev.gaferneira.notificapp.core.ui.messaging

import dev.gaferneira.notificapp.core.ui.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped, one-shot message channel for feedback that must outlive the screen that produced it
 * (e.g. "Rule saved" right before the editor pops itself).
 *
 * A screen-local snackbar is hosted by the screen being closed, so the message would be lost with
 * it. Messages posted here are buffered until the app-level host in `MainActivity` collects them
 * (lifecycle-gated, like [dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler]) and are
 * shown over whichever destination is on top.
 */
@Singleton
class AppMessenger @Inject constructor() {
    private val channel = Channel<UiText>(Channel.BUFFERED)

    /** Messages to show, delivered once each in posting order. */
    val messages: Flow<UiText> = channel.receiveAsFlow()

    /** Queue [message] for display. Never suspends or throws. */
    fun post(message: UiText) {
        channel.trySend(message)
    }
}
