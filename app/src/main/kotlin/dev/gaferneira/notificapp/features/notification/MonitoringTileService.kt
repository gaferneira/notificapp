package dev.gaferneira.notificapp.features.notification

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Quick Settings tile exposing a global monitoring pause/resume kill switch.
 *
 * Toggling the tile flips [UserPreferencesRepository]'s `monitoringPaused` flag, which
 * [dev.gaferneira.notificapp.core.notification.ProcessNotificationUseCase] checks at the top of
 * its `invoke()` entry point: while paused, freshly posted notifications are never captured or
 * processed, giving the user an instant, system-level privacy switch.
 */
@AndroidEntryPoint
class MonitoringTileService : TileService() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    @field:Dispatcher(DispatcherType.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @field:Dispatcher(DispatcherType.Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            val paused = userPreferencesRepository.observeMonitoringPaused().first()
            withContext(mainDispatcher) {
                renderTileState(paused)
            }
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val current = userPreferencesRepository.observeMonitoringPaused().first()
            val newValue = !current
            userPreferencesRepository.setMonitoringPaused(newValue)
                .onFailure { e -> Timber.e(e, "Failed to update monitoring paused flag") }
            withContext(mainDispatcher) {
                renderTileState(newValue)
            }
        }
    }

    /**
     * Renders the tile to reflect the current pause state.
     *
     * [Tile.STATE_ACTIVE] means monitoring is running (notifications are being captured);
     * [Tile.STATE_INACTIVE] means monitoring is paused (nothing is captured).
     *
     * Reads/writes to [userPreferencesRepository] happen on [ioDispatcher], but this method
     * itself must run on the main thread: [onStartListening] and [onClick] are called on the
     * service's main/binder thread per the `TileService` contract, and mutating [qsTile] is only
     * safe from that thread - hence the [mainDispatcher] hop back before calling it.
     */
    private fun renderTileState(paused: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.monitoring_tile_label)
        // Tile subtitle/contentDescription only exist on API 29/30+; minSdk is 26.
        val subtitle = if (paused) {
            getString(R.string.monitoring_tile_subtitle_paused)
        } else {
            getString(R.string.monitoring_tile_subtitle_active)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tile.contentDescription = subtitle
        tile.updateTile()
    }
}
