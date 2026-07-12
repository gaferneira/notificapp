package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.DEFAULT_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.DEFAULT_SNOOZE_THROTTLE_RESET_AT
import dev.gaferneira.notificapp.domain.model.DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.SnoozeMode
import dev.gaferneira.notificapp.domain.model.SnoozeSchedule
import dev.gaferneira.notificapp.domain.model.getSnoozeDurationMinutes
import dev.gaferneira.notificapp.domain.model.getSnoozeMode
import dev.gaferneira.notificapp.domain.model.getSnoozeSchedule
import dev.gaferneira.notificapp.domain.model.getThrottleResetAt
import dev.gaferneira.notificapp.domain.model.getThrottleWindowMinutes
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.BatchAtTimeConfig
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.BatchAtTimeSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.DigestScheduleConfig
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.DigestScheduleSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.SnoozeDurationSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.SnoozeOutcome
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.SnoozeOutcomeSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ThrottleWindowSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.WORKDAYS
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.WeekdayMode
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.defaultBatchAtTimeConfig
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.defaultDigestScheduleConfig
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.isDigestScheduleConfigValid
import java.util.UUID

/**
 * Type-scoped sheet for the Snooze action. Owns its own duration/schedule state; on confirm it
 * builds a `SNOOZE_NOTIFICATION` [RuleAction] and hands it back. No shared action ViewModel is
 * involved.
 *
 * @param initial The action being edited, or null when adding a new one
 */
@Composable
fun SnoozeBottomSheet(
    initial: RuleAction?,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var outcome by remember { mutableStateOf(initial.toInitialOutcome()) }
    var minutes by remember {
        mutableIntStateOf(initial?.getSnoozeDurationMinutes() ?: DEFAULT_SNOOZE_DURATION_MINUTES)
    }
    var batchAtTimeConfig by remember { mutableStateOf(initial.toInitialBatchAtTimeConfig()) }
    var digestConfig by remember { mutableStateOf(initial.toInitialDigestConfig()) }
    var throttleWindowMinutes by remember {
        mutableIntStateOf(initial?.getThrottleWindowMinutes() ?: DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES)
    }

    val canConfirm = outcome != SnoozeOutcome.BATCH_INTO_DIGEST || isDigestScheduleConfigValid(digestConfig)

    val configs = SnoozeActionConfigs(minutes, batchAtTimeConfig, digestConfig, throttleWindowMinutes)

    ActionConfigSheet(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        title = "Snooze notification",
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = if (canConfirm) {
            { onSave(buildSnoozeAction(initial, outcome, configs)) }
        } else {
            null
        },
        onDismiss = onDismiss,
    ) {
        ActionSheetDescription(ActionType.SNOOZE_NOTIFICATION.ui().description)

        SnoozeOutcomeSelector(
            selected = outcome,
            onOutcomeSelected = { outcome = it },
        ) { selectedOutcome ->
            SnoozeOutcomeConfig(
                outcome = selectedOutcome,
                minutes = minutes,
                onMinutesChange = { minutes = it },
                batchAtTimeConfig = batchAtTimeConfig,
                onBatchAtTimeConfigChange = { batchAtTimeConfig = it },
                digestConfig = digestConfig,
                onDigestConfigChange = { digestConfig = it },
                throttleWindowMinutes = throttleWindowMinutes,
                onThrottleWindowChange = { throttleWindowMinutes = it },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Renders the inline configuration for whichever [SnoozeOutcome] card is currently selected. */
@Composable
private fun SnoozeOutcomeConfig(
    outcome: SnoozeOutcome,
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    batchAtTimeConfig: BatchAtTimeConfig,
    onBatchAtTimeConfigChange: (BatchAtTimeConfig) -> Unit,
    digestConfig: DigestScheduleConfig,
    onDigestConfigChange: (DigestScheduleConfig) -> Unit,
    throttleWindowMinutes: Int,
    onThrottleWindowChange: (Int) -> Unit,
) {
    when (outcome) {
        SnoozeOutcome.DELAY_EACH_ONE -> SnoozeDurationSelector(
            selectedMinutes = minutes,
            onDurationChange = onMinutesChange,
        )
        SnoozeOutcome.BATCH_AT_TIME -> BatchAtTimeSelector(
            config = batchAtTimeConfig,
            onConfigChange = onBatchAtTimeConfigChange,
        )
        SnoozeOutcome.BATCH_INTO_DIGEST -> DigestScheduleSelector(
            config = digestConfig,
            onConfigChange = onDigestConfigChange,
        )
        SnoozeOutcome.THROTTLE -> ThrottleWindowSelector(
            selectedMinutes = throttleWindowMinutes,
            onWindowChange = onThrottleWindowChange,
        )
    }
}

/** Determines which [SnoozeOutcome] card an existing action (or a fresh one) starts on. */
private fun RuleAction?.toInitialOutcome(): SnoozeOutcome = when {
    this == null || getSnoozeMode() == SnoozeMode.DURATION -> SnoozeOutcome.DELAY_EACH_ONE
    getSnoozeMode() == SnoozeMode.THROTTLE -> SnoozeOutcome.THROTTLE
    getSnoozeSchedule()?.intervalMinutes != null -> SnoozeOutcome.BATCH_INTO_DIGEST
    else -> SnoozeOutcome.BATCH_AT_TIME
}

/** Infers the [WeekdayMode] a persisted weekday set was configured with. */
private fun Set<java.time.DayOfWeek>.toWeekdayMode(): WeekdayMode = when {
    isEmpty() -> WeekdayMode.EVERYDAY
    this == WORKDAYS -> WeekdayMode.WORKDAYS
    else -> WeekdayMode.CUSTOM
}

/** The "Batch at time" config an existing non-repeating scheduled action starts on, or the default. */
private fun RuleAction?.toInitialBatchAtTimeConfig(): BatchAtTimeConfig = this?.getSnoozeSchedule()
    ?.takeIf { it.intervalMinutes == null }
    ?.let { schedule ->
        BatchAtTimeConfig(
            times = if (schedule.times.isNotEmpty()) schedule.times else listOf(schedule.startHour to schedule.startMinute),
            weekdays = schedule.weekdays,
            weekdayMode = schedule.weekdays.toWeekdayMode(),
        )
    }
    ?: defaultBatchAtTimeConfig()

/** The "Batch into a digest" config an existing repeating scheduled action starts on, or the default. */
private fun RuleAction?.toInitialDigestConfig(): DigestScheduleConfig {
    val schedule = this?.getSnoozeSchedule()
    val intervalMinutes = schedule?.intervalMinutes ?: return defaultDigestScheduleConfig()
    val defaults = defaultDigestScheduleConfig()
    val weekdays = schedule.weekdays
    val weekdayMode = weekdays.toWeekdayMode()
    return defaults.copy(
        startHour = schedule.startHour,
        startMinute = schedule.startMinute,
        intervalMinutes = intervalMinutes,
        windowEndHour = schedule.windowEndHour ?: defaults.windowEndHour,
        windowEndMinute = schedule.windowEndMinute ?: defaults.windowEndMinute,
        weekdays = weekdays,
        weekdayMode = weekdayMode,
    )
}

/**
 * Whether editing a throttle action from [initial] to [outcome]/[throttleWindowMinutes] should
 * stamp a fresh reset watermark (D5): the window duration changed, or the action is entering
 * throttle mode from a different (or absent) prior mode. Otherwise the prior watermark is
 * preserved, so an unrelated save on an already-throttling action doesn't reopen its window.
 *
 * `internal` (rather than `private`) so [dev.gaferneira.notificapp.features.ruleeditor.ui.SnoozeBottomSheetTest]
 * can unit-test the window-duration-change half of D5 directly, without standing up a
 * Compose-UI/androidTest harness for a single pure decision function.
 */
internal fun shouldResetThrottleWatermark(initial: RuleAction?, throttleWindowMinutes: Int): Boolean {
    if (initial == null || initial.getSnoozeMode() != SnoozeMode.THROTTLE) return true
    return initial.getThrottleWindowMinutes() != throttleWindowMinutes
}

/**
 * Bundle of all per-outcome configuration state hoisted in [SnoozeBottomSheet], grouped so
 * [buildSnoozeAction] stays under detekt's `LongParameterList` function threshold (6).
 */
private data class SnoozeActionConfigs(
    val minutes: Int,
    val batchAtTimeConfig: BatchAtTimeConfig,
    val digestConfig: DigestScheduleConfig,
    val throttleWindowMinutes: Int,
)

/** Builds the [RuleAction] for the currently selected outcome and its config. */
private fun buildSnoozeAction(initial: RuleAction?, outcome: SnoozeOutcome, configs: SnoozeActionConfigs): RuleAction {
    val id = initial?.id ?: UUID.randomUUID().toString()
    val isEnabled = initial?.isEnabled ?: true
    return when (outcome) {
        SnoozeOutcome.DELAY_EACH_ONE -> RuleAction.createSnooze(
            id = id,
            durationMinutes = configs.minutes,
            isEnabled = isEnabled,
        )
        SnoozeOutcome.BATCH_AT_TIME -> RuleAction.createScheduledSnooze(
            id = id,
            schedule = SnoozeSchedule(
                startHour = configs.batchAtTimeConfig.times.first().first,
                startMinute = configs.batchAtTimeConfig.times.first().second,
                times = configs.batchAtTimeConfig.times,
                weekdays = configs.batchAtTimeConfig.weekdays,
            ),
            isEnabled = isEnabled,
        )
        SnoozeOutcome.BATCH_INTO_DIGEST -> RuleAction.createScheduledSnooze(
            id = id,
            schedule = SnoozeSchedule(
                startHour = configs.digestConfig.startHour,
                startMinute = configs.digestConfig.startMinute,
                intervalMinutes = configs.digestConfig.intervalMinutes,
                windowEndHour = configs.digestConfig.windowEndHour,
                windowEndMinute = configs.digestConfig.windowEndMinute,
                weekdays = configs.digestConfig.weekdays,
            ),
            isEnabled = isEnabled,
        )
        SnoozeOutcome.THROTTLE -> RuleAction.createThrottleSnooze(
            id = id,
            windowMinutes = configs.throttleWindowMinutes,
            resetAt = if (shouldResetThrottleWatermark(initial, configs.throttleWindowMinutes)) {
                System.currentTimeMillis()
            } else {
                initial?.getThrottleResetAt() ?: DEFAULT_SNOOZE_THROTTLE_RESET_AT
            },
            isEnabled = isEnabled,
        )
    }
}
