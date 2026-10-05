package dev.gaferneira.notificapp.features.notificationdetail.contract

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.ExtractionPreview
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField

/**
 * Contract for the Notification Detail screen.
 *
 * Shows a notification, the rules that matched it, and a read-only "test current rules" preview
 * that can optionally refresh the stored extracted data.
 */
object NotificationDetailContract {

    /** Where the screen is in loading the notification. Retry only makes sense for [ERROR]. */
    enum class LoadStatus {
        /** First load in progress. */
        LOADING,

        /** Notification loaded and being observed. */
        LOADED,

        /** There is no notification with the requested id. */
        NOT_FOUND,

        /** The notification existed but was deleted while the screen was open (retention, clear data). */
        DELETED,

        /** Reading from storage failed; offer Retry. */
        ERROR,
    }

    /** Why the rule test could not produce a preview. */
    enum class PreviewFailure {
        /** The raw content was removed by a rule, so there is nothing left to test against. */
        CONTENT_REDACTED,

        /** Loading or evaluating the rules failed. */
        EVALUATION_FAILED,
    }

    /** Transient feedback the UI shows as a snackbar/toast. */
    enum class Message {
        EXTRACTED_DATA_UPDATED,
        UPDATE_FAILED,
        DELETE_FAILED,
    }

    /** A rule referenced by id with its (possibly unknown) name. */
    data class RuleRef(val id: String, val name: String)

    /**
     * UI State for the notification detail screen.
     */
    data class UiState(
        val loadStatus: LoadStatus = LoadStatus.LOADING,
        /** The notification being viewed; `null` unless [loadStatus] is [LoadStatus.LOADED]. */
        val notification: Notification? = null,
        /** Stored rule executions for this notification, most recent first. */
        val executions: List<ExecutionWithDetails> = emptyList(),
        /** The rule that scrubbed this notification's content, when known. Only set if redacted. */
        val redactedByRule: RuleRef? = null,
        /** Inline progress of "Test current rules"; never replaces the screen. */
        val isPreviewLoading: Boolean = false,
        /** Result of the last "Test current rules", or `null` when none is showing. */
        val preview: ExtractionPreview? = null,
        val previewFailure: PreviewFailure? = null,
        /** Inline progress of "Update extracted data". */
        val isApplyingUpdate: Boolean = false,
    ) {
        val isLoading: Boolean get() = loadStatus == LoadStatus.LOADING

        /** Raw content was removed by a rule: text is gone, and testing/updating is unavailable. */
        val isContentRedacted: Boolean get() = notification?.isContentRedacted == true

        /** Absolute capture time (epoch millis) for the UI to format. */
        val postedAt: Long? get() = notification?.timestamp

        /** Package of the source app, for the "open app" action. */
        val sourcePackageName: String? get() = notification?.packageName

        /** Test current rules is available whenever content exists and nothing else is running. */
        val canTestRules: Boolean
            get() = loadStatus == LoadStatus.LOADED && !isContentRedacted && !isPreviewLoading && !isApplyingUpdate

        /** Update extracted data is enabled only for a preview that changes stored data. */
        val canApplyPreview: Boolean
            get() = loadStatus == LoadStatus.LOADED &&
                !isContentRedacted &&
                !isPreviewLoading &&
                !isApplyingUpdate &&
                preview?.hasUpdatableChanges == true
    }

    /**
     * A stored rule execution with names resolved from the rule as it is now.
     *
     * @property ruleName `null` when the rule was deleted; the UI shows a "deleted rule" fallback.
     * @property wasRedactionSource Whether this execution's rule is the one that scrubbed the content.
     */
    data class ExecutionWithDetails(
        val execution: RuleExecution,
        val ruleId: String,
        val ruleName: String?,
        val extractedFields: List<ExtractedFieldDisplay>,
        val triggeredActions: List<TriggeredActionDisplay>,
        val wasRedactionSource: Boolean = false,
    ) {
        val isRuleDeleted: Boolean get() = ruleName == null
    }

    /**
     * Display data for a stored extracted value.
     *
     * @property fieldName `null` when the field no longer exists on the rule; the UI shows a
     *   "removed field" fallback. [fieldType] is then [RuleField.FieldType.STRING].
     */
    data class ExtractedFieldDisplay(
        val fieldId: String,
        val fieldName: String?,
        val fieldType: RuleField.FieldType,
        val value: String,
    ) {
        val isFieldDeleted: Boolean get() = fieldName == null
    }

    /**
     * Display data for a triggered action and how it turned out.
     *
     * @property type `null` when the action was removed from the rule; the UI shows a generic
     *   fallback. Otherwise the UI maps it with `ActionType.ui()`.
     * @property outcome `null` for legacy rows recorded before outcomes were tracked (TD-5) -
     *   "no data", distinct from [ActionOutcome.SKIPPED] (the dispatcher decided not to run it).
     */
    data class TriggeredActionDisplay(
        val actionId: String,
        val type: ActionType?,
        val outcome: ActionOutcome?,
    )

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        data object OnBackClicked : UiEvent()

        /** Create a rule pre-populated from this notification. */
        data object OnCreateRuleClicked : UiEvent()

        /** Reload after a [LoadStatus.ERROR]; ignored in any other state. */
        data object OnRetryClicked : UiEvent()

        /** Evaluate the current active rules read-only; fills [UiState.preview]. Persists nothing. */
        data object OnTestRulesClicked : UiEvent()

        /** Hide the preview / preview failure. */
        data object OnDismissPreview : UiEvent()

        /** Write the previewed extractions into the existing executions (see [UiState.canApplyPreview]). */
        data object OnUpdateExtractedDataClicked : UiEvent()

        /** Delete this notification (the UI confirms first); navigates back on success. */
        data object OnDeleteNotificationClicked : UiEvent()

        /** Open the details screen of a rule that matched. */
        data class OnOpenRuleClicked(val ruleId: String) : UiEvent()

        /** Open the app that posted the notification. */
        data object OnOpenSourceAppClicked : UiEvent()
    }

    /**
     * One-time effects (navigation, actions).
     */
    sealed class UiEffect {
        /** Leave the screen (after the notification was deleted). */
        data object NavigateBack : UiEffect()

        /** Launch [packageName]; the UI resolves the launch intent and handles "not launchable". */
        data class OpenApp(val packageName: String) : UiEffect()

        /** Show transient feedback. */
        data class ShowMessage(val message: Message) : UiEffect()
    }
}
