package app.airmode.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.airmode.MainActivity
import app.airmode.R
import app.airmode.data.Settings
import app.airmode.domain.*
import app.airmode.service.AirModeService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

class NoiseTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { Repository.get(this) }
    private var listening: Job? = null
    private var switching: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        repository.start()
        repository.refresh()
        update(repository.state.value, repository.settings.state.value)
        // Active tiles do not bind when the panel opens; initialize through a listening request.
        refresh(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        repository.start()
        repository.refresh()
        listening?.cancel()
        listening = scope.launch {
            combine(repository.state, repository.settings.state) { state, settings -> state to settings }
                .collect { (state, settings) -> update(state, settings) }
        }
    }

    private fun update(state: DeviceState, settings: Settings) {
        val tile = qsTile ?: return
        val context = AirModeService.localized(this, settings)
        val label = when {
            !state.connected || state.connection == ConnectionState.UnsupportedModel -> context.getString(R.string.no_airpods)
            state.model?.anc == false -> context.getString(R.string.tile_no_anc)
            state.connection == ConnectionState.ProtocolUnavailable -> context.getString(R.string.protocol_unavailable)
            state.mode != null -> context.getString(labelFor(state.mode))
            else -> context.getString(R.string.model_pending)
        }
        tile.label = label
        tile.state = if (state.canSwitch) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        tile.icon = Icon.createWithResource(this, iconFor(state.mode ?: Mode.OFF))
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (switching?.isActive == true) return
        unlockAndRun {
            switching = scope.launch {
                val deadline = SystemClock.elapsedRealtime() + 5_000
                repository.retryControl()
                if (!repository.state.value.permissionGranted) { openApp(); return@launch }
                val connected = withTimeoutOrNull(4_000) { repository.state.first { it.connected } }
                if (connected == null || !AirModeService.start(this@NoiseTileService)) {
                    openApp()
                    return@launch
                }
                val ready = withTimeoutOrNull((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1)) {
                    repository.state.first { it.canSwitch || it.connection == ConnectionState.ProtocolUnavailable ||
                        it.connection == ConnectionState.UnsupportedModel || it.model?.anc == false }
                }
                if (ready?.canSwitch != true) { openApp(); return@launch }
                val modes = Mode.entries.filter { it in repository.settings.state.value.tileModes }
                val next = modes[(modes.indexOf(ready.mode) + 1) % modes.size]
                repository.switchMode(next)
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        // Android 31–33 support the Intent overload; the target-SDK restriction starts on OS 34.
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }

    override fun onStopListening() { listening?.cancel(); super.onStopListening() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        fun refresh(context: Context) { requestListeningState(context, ComponentName(context, NoiseTileService::class.java)) }
        private fun labelFor(mode: Mode) = when (mode) {
            Mode.OFF -> R.string.mode_off
            Mode.ANC -> R.string.mode_anc
            Mode.TRANSPARENCY -> R.string.mode_transparency
            Mode.ADAPTIVE -> R.string.mode_adaptive
        }
        private fun iconFor(mode: Mode) = when (mode) {
            Mode.OFF -> R.drawable.ic_noise_off
            Mode.ANC -> R.drawable.ic_noise_anc
            Mode.TRANSPARENCY -> R.drawable.ic_noise_transparency
            Mode.ADAPTIVE -> R.drawable.ic_noise_adaptive
        }
    }
}
