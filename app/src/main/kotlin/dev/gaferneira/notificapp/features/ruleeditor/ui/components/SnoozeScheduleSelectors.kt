package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.util.formatDurationMinutes
import java.time.DayOfWeek

private val INTERVAL_PRESETS = listOf(30, 60, 120)
private const val DEFAULT_INTERVAL_MINUTES = 60
private const val DEFAULT_WINDOW_END_HOUR = 18
private const val DEFAULT_START_HOUR = 9

enum class WeekdayMode {
    EVERYDAY,
    WORKDAYS,
    CUSTOM,
}

val WORKDAYS = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

/** Times a user is editing for batch-at-time snooze: one or more delivery times per day. */
data class BatchAtTimeConfig(
    val times: List<Pair<Int, Int>> = listOf(DEFAULT_START_HOUR to 0),
    val weekdays: Set<DayOfWeek> = emptySet(),
    val weekdayMode: WeekdayMode = WeekdayMode.EVERYDAY,
)

/** Default configuration a new "Batch at time" snooze action starts from. */
fun defaultBatchAtTimeConfig(): BatchAtTimeConfig = BatchAtTimeConfig(
    times = listOf(DEFAULT_START_HOUR to 0),
)

/**
 * Composable for configuring a batch-at-time snooze: one or more specific times per day,
 * repeating on the configured weekdays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchAtTimeSelector(
    config: BatchAtTimeConfig,
    onConfigChange: (BatchAtTimeConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        SectionLabel("Delivery times")
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            config.times.forEachIndexed { index, (hour, minute) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimePickerButton(
                        hour = hour,
                        minute = minute,
                        onTimePicked = { newHour, newMinute ->
                            val updatedTimes = config.times.toMutableList()
                            updatedTimes[index] = newHour to newMinute
                            onConfigChange(config.copy(times = updatedTimes))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (config.times.size > 1) {
                        OutlinedButton(
                            onClick = {
                                val updatedTimes = config.times.toMutableList()
                                updatedTimes.removeAt(index)
                                onConfigChange(config.copy(times = updatedTimes))
                            },
                            modifier = Modifier.padding(0.dp),
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val newTime = DEFAULT_START_HOUR to 0
                onConfigChange(config.copy(times = config.times + newTime))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Add time")
        }

        Spacer(modifier = Modifier.height(16.dp))

        WeekdaySelector(
            weekdayMode = config.weekdayMode,
            selectedDays = config.weekdays,
            onSelectionChange = { mode, weekdays -> onConfigChange(config.copy(weekdayMode = mode, weekdays = weekdays)) },
        )
    }
}

/** The recurring-digest configuration a user is editing: a start time, cadence, window end, and optional weekday filter. */
data class DigestScheduleConfig(
    val startHour: Int,
    val startMinute: Int,
    val intervalMinutes: Int,
    val windowEndHour: Int,
    val windowEndMinute: Int,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val weekdayMode: WeekdayMode = WeekdayMode.EVERYDAY,
)

/** Default configuration a new "Batch into a digest" snooze action starts from. */
fun defaultDigestScheduleConfig(): DigestScheduleConfig = DigestScheduleConfig(
    startHour = DEFAULT_START_HOUR,
    startMinute = 0,
    intervalMinutes = DEFAULT_INTERVAL_MINUTES,
    windowEndHour = DEFAULT_WINDOW_END_HOUR,
    windowEndMinute = 0,
)

/** Whether the configured window end is strictly after the start time. */
fun isDigestScheduleConfigValid(config: DigestScheduleConfig): Boolean {
    val startMinutes = config.startHour * 60 + config.startMinute
    val endMinutes = config.windowEndHour * 60 + config.windowEndMinute
    return endMinutes > startMinutes
}

/**
 * Composable for configuring a repeating scheduled snooze: a start time, a repeat interval, and
 * a window end time bounding the last delivery.
 *
 * @param config Current schedule configuration
 * @param onConfigChange Callback when any part of the configuration changes
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DigestScheduleSelector(
    config: DigestScheduleConfig,
    onConfigChange: (DigestScheduleConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        SectionLabel("Start delivering at")
        Spacer(modifier = Modifier.height(8.dp))
        TimePickerButton(
            hour = config.startHour,
            minute = config.startMinute,
            onTimePicked = { hour, minute -> onConfigChange(config.copy(startHour = hour, startMinute = minute)) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Every",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            INTERVAL_PRESETS.forEach { preset ->
                FilterChip(
                    selected = config.intervalMinutes == preset,
                    onClick = { onConfigChange(config.copy(intervalMinutes = preset)) },
                    label = { Text(formatDurationMinutes(preset)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionLabel("Until")
        Spacer(modifier = Modifier.height(8.dp))
        TimePickerButton(
            hour = config.windowEndHour,
            minute = config.windowEndMinute,
            onTimePicked = { hour, minute ->
                onConfigChange(config.copy(windowEndHour = hour, windowEndMinute = minute))
            },
        )

        if (!isDigestScheduleConfigValid(config)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The end time must be after the start time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        WeekdaySelector(
            weekdayMode = config.weekdayMode,
            selectedDays = config.weekdays,
            onSelectionChange = { mode, weekdays -> onConfigChange(config.copy(weekdayMode = mode, weekdays = weekdays)) },
        )
    }
}

/**
 * Composable for selecting which days of the week a digest schedule applies to.
 * Shows presets (Everyday, Workdays) and a Custom option with individual day selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdaySelector(
    weekdayMode: WeekdayMode,
    selectedDays: Set<DayOfWeek>,
    onSelectionChange: (mode: WeekdayMode, days: Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("Days")
        Spacer(modifier = Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = weekdayMode == WeekdayMode.EVERYDAY,
                onClick = { onSelectionChange(WeekdayMode.EVERYDAY, emptySet()) },
                label = { Text("Everyday") },
                shape = RoundedCornerShape(8.dp),
            )
            SegmentedButton(
                selected = weekdayMode == WeekdayMode.WORKDAYS,
                onClick = { onSelectionChange(WeekdayMode.WORKDAYS, WORKDAYS) },
                label = { Text("Workdays") },
                shape = RoundedCornerShape(8.dp),
            )
            SegmentedButton(
                selected = weekdayMode == WeekdayMode.CUSTOM,
                onClick = { onSelectionChange(WeekdayMode.CUSTOM, WORKDAYS) },
                label = { Text("Custom") },
                shape = RoundedCornerShape(8.dp),
            )
        }

        if (weekdayMode == WeekdayMode.CUSTOM) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Select days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in selectedDays,
                        onClick = {
                            val updated = selectedDays.toMutableSet()
                            if (day in updated) {
                                updated.remove(day)
                            } else {
                                updated.add(day)
                            }
                            onSelectionChange(WeekdayMode.CUSTOM, updated)
                        },
                        label = { Text(day.name.substring(0, 3)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerButton(
    hour: Int,
    minute: Int,
    onTimePicked: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("%02d:%02d".format(hour, minute))
    }

    if (showDialog) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimePicked(state.hour, state.minute)
                        showDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
}
