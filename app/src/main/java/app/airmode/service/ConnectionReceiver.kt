package app.airmode.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.airmode.domain.Repository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                val repository = Repository.get(context)
                val settings = withTimeoutOrNull(5_000) { repository.settings.state.first { it.onboarded } }
                    ?: return@launch
                if (!settings.autoStart) return@launch
                repository.start()
                repository.refresh()
                val disconnected = intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED ||
                    (intent.hasExtra(BluetoothProfile.EXTRA_STATE) &&
                        intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_DISCONNECTED) ||
                    (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) != BluetoothAdapter.STATE_ON)
                if (!disconnected) {
                    withTimeoutOrNull(4_000) { repository.state.first { it.connected } }
                        ?.let { AirModeService.start(context) }
                }
            } finally { pending.finish() }
        }
    }
}
