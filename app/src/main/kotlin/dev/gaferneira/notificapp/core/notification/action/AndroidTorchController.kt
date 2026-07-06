package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

/**
 * Controls the device's camera torch via [CameraManager.setTorchMode], which (unlike opening a
 * camera session) requires no runtime permission.
 */
class AndroidTorchController @Inject constructor(
    @ApplicationContext private val context: Context,
) : TorchController {

    private val cameraManager: CameraManager
        get() = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val torchCameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.onFailure { e ->
            Timber.w(e, "Failed to enumerate cameras for torch capability")
        }.getOrNull()
    }

    override fun hasFlash(): Boolean = torchCameraId != null

    override fun isPowerSaveMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isPowerSaveMode
    }

    override suspend fun blink(count: Int, phaseDurationMs: Long) {
        val cameraId = torchCameraId ?: return
        repeat(count) {
            cameraManager.setTorchMode(cameraId, true)
            delay(phaseDurationMs)
            cameraManager.setTorchMode(cameraId, false)
            delay(phaseDurationMs)
        }
    }
}
