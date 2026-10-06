package app.airmode.domain

import app.airmode.bluetooth.ModelId

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object ConnectedNoSession : ConnectionState
    data object SessionReady : ConnectionState
    data class Switching(val target: Mode) : ConnectionState
    data object UnsupportedModel : ConnectionState
    data object ProtocolUnavailable : ConnectionState
}
enum class Problem { NO_REPLY, PROTOCOL_UNAVAILABLE, MODEL_PENDING, BATTERY_UNAVAILABLE, PERMISSION, SERVICE_UNAVAILABLE }
data class DeviceState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val name: String? = null,
    val model: ModelId? = null,
    val battery: Battery = Battery(),
    val mode: Mode? = null,
    val problem: Problem? = null,
    val bluetoothEnabled: Boolean = true,
    val permissionGranted: Boolean = false,
    val connectionId: Long = 0L,
    val failedMode: Mode? = null,
) {
    val connected get() = connection != ConnectionState.Disconnected
    val canSwitch get() = connection == ConnectionState.SessionReady && model?.anc == true
}
