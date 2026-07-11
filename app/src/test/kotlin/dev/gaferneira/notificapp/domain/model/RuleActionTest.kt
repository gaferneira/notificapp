package dev.gaferneira.notificapp.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleActionTest {

    @Test
    fun `flash count defaults when not configured`() {
        val action = RuleAction(id = "action-1", type = ActionType.FLASH_ALERT)

        action.getFlashCount() shouldBe DEFAULT_FLASH_COUNT
    }

    @Test
    fun `flash count is clamped to the safe maximum regardless of stored config`() {
        // Given: a maliciously or accidentally excessive flash count (e.g. an imported rule)
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_COUNT_KEY to "999"),
        )

        // Then: it is clamped to the safe maximum, not the raw stored value
        action.getFlashCount() shouldBe MAX_FLASH_COUNT
    }

    @Test
    fun `flash count is clamped to the safe minimum regardless of stored config`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_COUNT_KEY to "-5"),
        )

        action.getFlashCount() shouldBe MIN_FLASH_COUNT
    }

    @Test
    fun `flash duration is clamped to the photosensitivity-safe minimum`() {
        // Given: a duration fast enough to risk a photosensitive reaction
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_DURATION_MS_KEY to "10"),
        )

        // Then: it is clamped up to the safe floor
        action.getFlashDurationMs() shouldBe MIN_FLASH_DURATION_MS
    }

    @Test
    fun `flash duration is clamped to the safe maximum`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_DURATION_MS_KEY to "999999"),
        )

        action.getFlashDurationMs() shouldBe MAX_FLASH_DURATION_MS
    }

    @Test
    fun `createFlashAlert stores the requested count and duration`() {
        val action = RuleAction.createFlashAlert(id = "action-1", flashCount = 5, flashDurationMs = 400)

        action.type shouldBe ActionType.FLASH_ALERT
        action.getFlashCount() shouldBe 5
        action.getFlashDurationMs() shouldBe 400L
    }

    @Test
    fun `alarm sound-enabled defaults to true when not configured`() {
        val action = RuleAction(id = "action-1", type = ActionType.CREATE_ALARM)

        action.isAlarmSoundEnabled() shouldBe DEFAULT_ALARM_SOUND_ENABLED
    }

    @Test
    fun `createAlarm round-trips a disabled sound`() {
        val action = RuleAction.createAlarm(id = "action-1", options = AlarmOptionsConfig(soundEnabled = false))

        action.isAlarmSoundEnabled() shouldBe false
    }

    @Test
    fun `alarm vibration pattern defaults to BASIC_CALL when not configured`() {
        val action = RuleAction(id = "action-1", type = ActionType.CREATE_ALARM)

        action.getAlarmVibrationPattern() shouldBe VibrationPattern.BASIC_CALL
    }

    @Test
    fun `createAlarm round-trips the selected vibration pattern`() {
        val action = RuleAction.createAlarm(id = "action-1", options = AlarmOptionsConfig(vibrationPattern = VibrationPattern.PULSE))

        action.getAlarmVibrationPattern() shouldBe VibrationPattern.PULSE
    }

    @Test
    fun `alarm snooze duration is clamped to the safe maximum regardless of stored config`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.CREATE_ALARM,
            config = mapOf(ALARM_SNOOZE_DURATION_MINUTES_KEY to "999"),
        )

        action.getAlarmSnoozeDurationMinutes() shouldBe MAX_ALARM_SNOOZE_DURATION_MINUTES
    }

    @Test
    fun `alarm snooze duration is clamped to the safe minimum regardless of stored config`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.CREATE_ALARM,
            config = mapOf(ALARM_SNOOZE_DURATION_MINUTES_KEY to "-5"),
        )

        action.getAlarmSnoozeDurationMinutes() shouldBe MIN_ALARM_SNOOZE_DURATION_MINUTES
    }

    @Test
    fun `alarm snooze max count is clamped to the safe maximum regardless of stored config`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.CREATE_ALARM,
            config = mapOf(ALARM_SNOOZE_MAX_COUNT_KEY to "999"),
        )

        action.getAlarmSnoozeMaxCount() shouldBe MAX_ALARM_SNOOZE_MAX_COUNT
    }

    @Test
    fun `alarm snooze max count is clamped to the safe minimum regardless of stored config`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.CREATE_ALARM,
            config = mapOf(ALARM_SNOOZE_MAX_COUNT_KEY to "-5"),
        )

        action.getAlarmSnoozeMaxCount() shouldBe MIN_ALARM_SNOOZE_MAX_COUNT
    }

    @Test
    fun `legacy alarm rule with no snooze keys resolves snooze max count to the closed-loop default`() {
        // A rule persisted before this option existed: no ALARM_SNOOZE_MAX_COUNT_KEY in config at
        // all, not even an invalid value - the infinite-snooze bug this default closes.
        val action = RuleAction(id = "action-1", type = ActionType.CREATE_ALARM)

        action.getAlarmSnoozeMaxCount() shouldBe DEFAULT_ALARM_SNOOZE_MAX_COUNT
        action.getAlarmSnoozeMaxCount() shouldBe 3
    }

    @Test
    fun `alarm background type defaults to NONE when not configured`() {
        val action = RuleAction(id = "action-1", type = ActionType.CREATE_ALARM)

        action.getAlarmBackgroundType() shouldBe AlarmBackgroundType.NONE
    }

    @Test
    fun `createAlarm round-trips the background preset selection`() {
        val action = RuleAction.createAlarm(
            id = "action-1",
            options = AlarmOptionsConfig(
                background = AlarmBackgroundConfig(type = AlarmBackgroundType.PRESET, presetId = AlarmBackgroundPreset.OCEAN.id),
            ),
        )

        action.getAlarmBackgroundType() shouldBe AlarmBackgroundType.PRESET
        action.getAlarmBackgroundPresetId() shouldBe AlarmBackgroundPreset.OCEAN.id
    }

    @Test
    fun `createAlarm round-trips the background image uri`() {
        val action = RuleAction.createAlarm(
            id = "action-1",
            options = AlarmOptionsConfig(
                background = AlarmBackgroundConfig(type = AlarmBackgroundType.IMAGE, imageUri = "content://media/bg.jpg"),
            ),
        )

        action.getAlarmBackgroundType() shouldBe AlarmBackgroundType.IMAGE
        action.getAlarmBackgroundImageUri() shouldBe "content://media/bg.jpg"
    }
}
