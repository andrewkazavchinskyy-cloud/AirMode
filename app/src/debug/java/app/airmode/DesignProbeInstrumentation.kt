package app.airmode

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.airmode.bluetooth.BatteryForm
import app.airmode.bluetooth.ModelId
import app.airmode.domain.*
import app.airmode.ui.AirModeTheme
import app.airmode.ui.HomeScreen

/** ADB-only labelled layout check. Fixtures never enter Repository or the release build. */
class DesignProbeInstrumentation : Instrumentation() {
    private var fixture = "buds"
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        fixture = arguments?.getString("fixture") ?: "buds"
        start()
    }
    override fun onStart() {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val now = SystemClock.elapsedRealtime()
        val model = when (fixture) {
            "max" -> ModelId.fromNumber("A2096")
            "sony" -> ModelId.sony("WH-1000XM5", setOf(Mode.OFF, Mode.ANC, Mode.TRANSPARENCY), BatteryForm.HEADPHONES)
            "noanc" -> ModelId.fromNumber("A1523")
            else -> ModelId.fromNumber("A3532")
        }
        val state = DeviceState(connection = ConnectionState.SessionReady, name = model?.generation,
            model = model, mode = Mode.ANC, permissionGranted = true,
            pendingMode = Mode.ADAPTIVE.takeIf { fixture == "waiting" },
            battery = Battery(BatteryReading(17, updatedAt = now), BatteryReading(31, updatedAt = now),
                headset = BatteryReading(76, updatedAt = now)))
        runOnMainSync {
            activity.setContent {
                AirModeTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().statusBarsPadding()) {
                            Text("ТЕСТ ДИЗАЙНА • тестовые данные / Design test • sample data",
                                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp, 8.dp))
                            HomeScreen(state, {}, {}, {}, {}, {}, false)
                        }
                    }
                }
            }
        }
        waitForIdleSync()
        // Keep the labelled window available for a screenshot, then end the probe.
        Thread.sleep(20_000)
        finish(-1, Bundle().apply { putString("stream", "PASS: labelled $fixture layout; no Bluetooth commands.\n") })
    }
}
