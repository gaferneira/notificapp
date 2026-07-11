package dev.gaferneira.notificapp.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AlarmBackgroundTest {

    @Test
    fun `preset fromId resolves a known id to its preset`() {
        AlarmBackgroundPreset.fromId("ocean") shouldBe AlarmBackgroundPreset.OCEAN
        AlarmBackgroundPreset.fromId("midnight") shouldBe AlarmBackgroundPreset.MIDNIGHT
        AlarmBackgroundPreset.fromId("sunrise") shouldBe AlarmBackgroundPreset.SUNRISE
    }

    @Test
    fun `preset fromId falls back to the default preset for a null id`() {
        AlarmBackgroundPreset.fromId(null) shouldBe AlarmBackgroundPreset.DEFAULT
    }

    @Test
    fun `preset fromId falls back to the default preset for an unrecognized id`() {
        // Stale id from an old export, or a preset removed in a later version.
        AlarmBackgroundPreset.fromId("retired-preset") shouldBe AlarmBackgroundPreset.DEFAULT
    }

    @Test
    fun `background type PRESET with no preset ever picked resolves to the default preset via fromId`() {
        // The action has backgroundType == PRESET but never got an ALARM_BACKGROUND_PRESET_KEY.
        val action = RuleAction(
            id = "action-1",
            type = ActionType.CREATE_ALARM,
            config = mapOf(ALARM_BACKGROUND_TYPE_KEY to AlarmBackgroundType.PRESET.name),
        )

        action.getAlarmBackgroundType() shouldBe AlarmBackgroundType.PRESET
        AlarmBackgroundPreset.fromId(action.getAlarmBackgroundPresetId()) shouldBe AlarmBackgroundPreset.DEFAULT
    }

    @Test
    fun `type fromName resolves a known name to its type`() {
        AlarmBackgroundType.fromName("IMAGE") shouldBe AlarmBackgroundType.IMAGE
        AlarmBackgroundType.fromName("PRESET") shouldBe AlarmBackgroundType.PRESET
    }

    @Test
    fun `type fromName falls back to NONE for a null or unrecognized name`() {
        AlarmBackgroundType.fromName(null) shouldBe AlarmBackgroundType.NONE
        AlarmBackgroundType.fromName("not-a-real-type") shouldBe AlarmBackgroundType.NONE
    }
}
