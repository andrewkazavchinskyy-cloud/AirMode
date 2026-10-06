package app.airmode.domain

import app.airmode.bluetooth.ModelId
import org.junit.Assert.*
import org.junit.Test

class DeviceStateTest {
    @Test fun noiseWritesRequireConfirmedAncAndReadyConnection() {
        val anc = requireNotNull(ModelId.fromNumber("A3056"))
        val nonAnc = requireNotNull(ModelId.fromNumber("A3053"))
        val ready = DeviceState(connection = ConnectionState.SessionReady, model = anc)
        assertTrue(ready.canSwitch)
        assertFalse(ready.copy(model = null).canSwitch)
        assertFalse(ready.copy(model = nonAnc).canSwitch)
        listOf(
            ConnectionState.Disconnected, ConnectionState.ConnectedNoSession,
            ConnectionState.UnsupportedModel, ConnectionState.ProtocolUnavailable,
            ConnectionState.Switching(Mode.ANC),
        ).forEach { connection -> assertFalse(ready.copy(connection = connection).canSwitch) }
        assertFalse(DeviceState(name = "AirPods 5", connection = ConnectionState.SessionReady).canSwitch)
    }
}
