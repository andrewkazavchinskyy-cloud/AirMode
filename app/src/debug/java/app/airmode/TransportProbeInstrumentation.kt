package app.airmode

import android.app.Instrumentation
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Bundle
import app.airmode.bluetooth.ClassicTransport

/** DEBUG ONLY. Creates and closes an unconnected socket; sends no packets to any device. */
class TransportProbeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val adapter = targetContext.getSystemService(BluetoothManager::class.java).adapter
                ?: error("Bluetooth unavailable")
            check(adapter.isEnabled) { "Enable Bluetooth first" }
            val device = adapter.getRemoteDevice("02:00:00:00:00:01")
            ClassicTransport.open(device).use { check(it.connectionType == BluetoothSocket.TYPE_L2CAP) }
            result.putString("stream", "PASS: native classic L2CAP factory, API ${Build.VERSION.SDK_INT}, ${Build.ID}; no connection or packets attempted.\n")
            finish(-1, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.javaClass.simpleName}.\n")
            finish(0, result)
        }
    }
}
