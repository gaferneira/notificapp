package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundConfig
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundPreset
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.AlarmOptionsConfig
import dev.gaferneira.notificapp.domain.model.AlarmSnoozeConfig
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.VibrationPattern
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.AlarmContract
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val ruleRepository: RuleRepository = mockk()
    private lateinit var viewModel: AlarmViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AlarmViewModel(ruleRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class InitializeTests {

        @Test
        fun `initialize with null resets to defaults`() {
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(null))

            val state = viewModel.uiState.value
            state.initialIsEnabled shouldBe true
            state.initialImageUri shouldBe null
            state.initialBackgroundType shouldBe AlarmBackgroundType.NONE
            state.soundUri shouldBe null
            state.soundEnabled shouldBe true
            state.vibrationEnabled shouldBe true
            state.snoozeEnabled shouldBe false
            state.backgroundType shouldBe AlarmBackgroundType.NONE
            state.backgroundImageUri shouldBe null
        }

        @Test
        fun `initialize with an existing alarm action round-trips all fields`() {
            val action = RuleAction.createAlarm(
                id = "alarm-1",
                soundUri = "content://sound",
                vibrationEnabled = false,
                isEnabled = false,
                options = AlarmOptionsConfig(
                    soundEnabled = false,
                    vibrationPattern = VibrationPattern.LONG,
                    fullScreenEnabled = false,
                    snooze = AlarmSnoozeConfig(enabled = false, durationMinutes = 10, maxCount = 2),
                    background = AlarmBackgroundConfig(
                        type = AlarmBackgroundType.IMAGE,
                        imageUri = "content://image",
                    ),
                ),
            )

            viewModel.onEvent(AlarmContract.UiEvent.Initialize(action))

            val state = viewModel.uiState.value
            state.actionId shouldBe "alarm-1"
            state.initialIsEnabled shouldBe false
            state.initialImageUri shouldBe "content://image"
            state.initialBackgroundType shouldBe AlarmBackgroundType.IMAGE
            state.soundUri shouldBe "content://sound"
            state.soundEnabled shouldBe false
            state.vibrationEnabled shouldBe false
            state.vibrationPattern shouldBe VibrationPattern.LONG
            state.fullScreenEnabled shouldBe false
            state.snoozeEnabled shouldBe false
            state.snoozeDurationMinutes shouldBe 10
            state.snoozeMaxCount shouldBe 2
            state.backgroundType shouldBe AlarmBackgroundType.IMAGE
            state.backgroundImageUri shouldBe "content://image"
        }
    }

    @Nested
    inner class SetterTests {

        @Test
        fun `each setter event updates its corresponding field`() {
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(null))

            viewModel.onEvent(AlarmContract.UiEvent.OnSoundToggle(false))
            viewModel.onEvent(AlarmContract.UiEvent.OnSoundChange("content://custom-sound"))
            viewModel.onEvent(AlarmContract.UiEvent.OnVibrationToggle(false))
            viewModel.onEvent(AlarmContract.UiEvent.OnVibrationPatternChange(VibrationPattern.LONG))
            viewModel.onEvent(AlarmContract.UiEvent.OnFullScreenToggle(false))
            viewModel.onEvent(AlarmContract.UiEvent.OnSnoozeToggle(false))
            viewModel.onEvent(AlarmContract.UiEvent.OnSnoozeDurationChange(15))
            viewModel.onEvent(AlarmContract.UiEvent.OnSnoozeMaxCountChange(5))
            viewModel.onEvent(AlarmContract.UiEvent.OnBackgroundPresetSelected(AlarmBackgroundPreset.entries.first()))

            val state = viewModel.uiState.value
            state.soundEnabled shouldBe false
            state.soundUri shouldBe "content://custom-sound"
            state.vibrationEnabled shouldBe false
            state.vibrationPattern shouldBe VibrationPattern.LONG
            state.fullScreenEnabled shouldBe false
            state.snoozeEnabled shouldBe false
            state.snoozeDurationMinutes shouldBe 15
            state.snoozeMaxCount shouldBe 5
            state.backgroundType shouldBe AlarmBackgroundType.PRESET
            state.backgroundPresetId shouldBe AlarmBackgroundPreset.entries.first().id
        }

        @Test
        fun `OnBackgroundImageIsDarkToggle updates backgroundImageIsDark`() {
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(null))

            viewModel.onEvent(AlarmContract.UiEvent.OnBackgroundImageIsDarkToggle(false))

            viewModel.uiState.value.backgroundImageIsDark shouldBe false
        }
    }

    @Nested
    inner class ImagePickedTests {

        @Test
        fun `first pick does not emit a release effect`() = runTest(testDispatcher) {
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(null))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnImagePicked("content://first"))
                testDispatcher.scheduler.advanceUntilIdle()
                expectNoEvents()
            }
            viewModel.uiState.value.backgroundImageUri shouldBe "content://first"
            viewModel.uiState.value.backgroundType shouldBe AlarmBackgroundType.IMAGE
        }

        @Test
        fun `replacing a stale unsaved pick emits a release effect for the old uri`() = runTest(testDispatcher) {
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(null))
            viewModel.onEvent(AlarmContract.UiEvent.OnImagePicked("content://first"))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnImagePicked("content://second"))
                val effect = awaitItem()
                effect.shouldBeInstanceOf<AlarmContract.UiEffect.ReleaseImageUri>()
                (effect as AlarmContract.UiEffect.ReleaseImageUri).uri shouldBe "content://first"
            }
            viewModel.uiState.value.backgroundImageUri shouldBe "content://second"
        }

        @Test
        fun `replacing the already-persisted image does not emit a release effect`() = runTest(testDispatcher) {
            // The persisted uri isn't "picked-but-unsaved" from this session - releasing it (if
            // unreferenced elsewhere) is decided later, at confirm time.
            val action = RuleAction.createAlarm(
                id = "alarm-1",
                options = AlarmOptionsConfig(
                    background = AlarmBackgroundConfig(type = AlarmBackgroundType.IMAGE, imageUri = "content://persisted"),
                ),
            )
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(action))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnImagePicked("content://new"))
                testDispatcher.scheduler.advanceUntilIdle()
                expectNoEvents()
            }
            viewModel.uiState.value.backgroundImageUri shouldBe "content://new"
        }
    }

    @Nested
    inner class ConfirmClickedTests {

        @Test
        fun `confirm with a changed uri requests a referenced check for the old uri`() = runTest(testDispatcher) {
            val action = RuleAction.createAlarm(
                id = "alarm-1",
                options = AlarmOptionsConfig(
                    background = AlarmBackgroundConfig(type = AlarmBackgroundType.IMAGE, imageUri = "content://old"),
                ),
            )
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(action))
            viewModel.onEvent(AlarmContract.UiEvent.OnImagePicked("content://new"))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnConfirmClicked)
                val effect = awaitItem()
                effect.shouldBeInstanceOf<AlarmContract.UiEffect.ConfirmSave>()
                (effect as AlarmContract.UiEffect.ConfirmSave).oldUriToCheck shouldBe "content://old"
            }
        }

        @Test
        fun `confirm after leaving IMAGE type requests a referenced check for the old uri`() = runTest(testDispatcher) {
            val action = RuleAction.createAlarm(
                id = "alarm-1",
                options = AlarmOptionsConfig(
                    background = AlarmBackgroundConfig(type = AlarmBackgroundType.IMAGE, imageUri = "content://old"),
                ),
            )
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(action))
            viewModel.onEvent(AlarmContract.UiEvent.OnBackgroundPresetSelected(AlarmBackgroundPreset.entries.first()))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnConfirmClicked)
                val effect = awaitItem()
                effect.shouldBeInstanceOf<AlarmContract.UiEffect.ConfirmSave>()
                (effect as AlarmContract.UiEffect.ConfirmSave).oldUriToCheck shouldBe "content://old"
            }
        }

        @Test
        fun `confirm with nothing changed requests no referenced check`() = runTest(testDispatcher) {
            val action = RuleAction.createAlarm(
                id = "alarm-1",
                options = AlarmOptionsConfig(
                    background = AlarmBackgroundConfig(type = AlarmBackgroundType.IMAGE, imageUri = "content://old"),
                ),
            )
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(action))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnConfirmClicked)
                val effect = awaitItem()
                effect.shouldBeInstanceOf<AlarmContract.UiEffect.ConfirmSave>()
                (effect as AlarmContract.UiEffect.ConfirmSave).oldUriToCheck shouldBe null
            }
        }
    }

    @Nested
    inner class SheetDismissedTests {

        @Test
        fun `dismiss with an unsaved picked uri emits a release effect`() = runTest(testDispatcher) {
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(null))
            viewModel.onEvent(AlarmContract.UiEvent.OnImagePicked("content://unsaved"))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnSheetDismissed)
                val effect = awaitItem()
                effect.shouldBeInstanceOf<AlarmContract.UiEffect.ReleaseImageUri>()
                (effect as AlarmContract.UiEffect.ReleaseImageUri).uri shouldBe "content://unsaved"
            }
        }

        @Test
        fun `dismiss without picking a new image emits no effect`() = runTest(testDispatcher) {
            val action = RuleAction.createAlarm(
                id = "alarm-1",
                options = AlarmOptionsConfig(
                    background = AlarmBackgroundConfig(type = AlarmBackgroundType.IMAGE, imageUri = "content://persisted"),
                ),
            )
            viewModel.onEvent(AlarmContract.UiEvent.Initialize(action))

            viewModel.effect.test {
                viewModel.onEvent(AlarmContract.UiEvent.OnSheetDismissed)
                testDispatcher.scheduler.advanceUntilIdle()
                expectNoEvents()
            }
        }
    }

    @Nested
    inner class ImageUriReferenceCheckTests {

        @Test
        fun `delegates to the repository and returns its result when the uri is still referenced`() = runTest(testDispatcher) {
            // Given: the repository reports the uri is still referenced by another alarm action
            coEvery {
                ruleRepository.isImageUriReferencedByOtherAlarmAction("content://media/bg.jpg", "alarm-1")
            } returns true

            // When: the ViewModel's suspend wrapper is called (as AlarmBottomSheet would)
            val result = viewModel.isImageUriReferencedByOtherAlarmAction("content://media/bg.jpg", "alarm-1")

            // Then: the repository's result is returned unchanged
            result shouldBe true
            coVerify(exactly = 1) { ruleRepository.isImageUriReferencedByOtherAlarmAction("content://media/bg.jpg", "alarm-1") }
        }

        @Test
        fun `returns false when the repository reports the uri is no longer referenced`() = runTest(testDispatcher) {
            // Given: no other alarm action references the uri
            coEvery {
                ruleRepository.isImageUriReferencedByOtherAlarmAction("content://media/bg.jpg", "alarm-1")
            } returns false

            // When: checking
            val result = viewModel.isImageUriReferencedByOtherAlarmAction("content://media/bg.jpg", "alarm-1")

            // Then: false is returned, so the caller is free to release the grant
            result shouldBe false
        }
    }
}
