package dev.gaferneira.notificapp.features.notificationdetail.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.RuleField.FieldType

@StringRes
internal fun FieldType.labelRes(): Int = when (this) {
    FieldType.STRING -> R.string.notification_detail_field_type_string
    FieldType.NUMBER -> R.string.notification_detail_field_type_number
    FieldType.DATE -> R.string.notification_detail_field_type_date
    FieldType.CURRENCY -> R.string.notification_detail_field_type_currency
    FieldType.BOOLEAN -> R.string.notification_detail_field_type_boolean
}

/** Visible outcome wording; `null` is a legacy row recorded before outcomes were tracked. */
@StringRes
internal fun ActionOutcome?.labelRes(): Int = when (this) {
    ActionOutcome.SUCCESS -> R.string.notification_detail_outcome_success
    ActionOutcome.FAILED -> R.string.notification_detail_outcome_failed
    ActionOutcome.SKIPPED -> R.string.notification_detail_outcome_skipped
    ActionOutcome.SUPPRESSED -> R.string.notification_detail_outcome_suppressed
    null -> R.string.notification_detail_outcome_unknown
}

internal fun ActionOutcome?.icon(): ImageVector = when (this) {
    ActionOutcome.SUCCESS -> Icons.Default.CheckCircle
    ActionOutcome.FAILED -> Icons.Default.Error
    ActionOutcome.SKIPPED -> Icons.Default.SkipNext
    ActionOutcome.SUPPRESSED -> Icons.Default.Block
    null -> Icons.AutoMirrored.Filled.HelpOutline
}
