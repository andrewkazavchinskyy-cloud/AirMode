@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.airmode.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.airmode.bluetooth.ModelId
import app.airmode.R
import app.airmode.domain.*
import kotlinx.coroutines.delay

fun Mode.label() = when (this) {
    Mode.OFF -> R.string.mode_off
    Mode.ANC -> R.string.mode_anc
    Mode.TRANSPARENCY -> R.string.mode_transparency
    Mode.ADAPTIVE -> R.string.mode_adaptive
}

@Composable
fun HomeScreen(state: DeviceState, onMode: (Mode) -> Unit, onSettings: () -> Unit,
               onBluetooth: () -> Unit, onPermission: () -> Unit, onAppSettings: () -> Unit,
               permissionDenied: Boolean) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.battery) { while (true) { now = SystemClock.elapsedRealtime(); delay(5_000) } }
    Scaffold(bottomBar = {
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings))
            }
        }
    }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(state.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        state.model?.takeIf { it.generation != state.name }?.let { Text(it.generation, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(40.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatteryValue(R.string.battery_left, state.battery.left, now, Modifier.weight(1f))
            BatteryValue(R.string.battery_case, state.battery.case, now, Modifier.weight(1f))
            BatteryValue(R.string.battery_right, state.battery.right, now, Modifier.weight(1f))
        }
        Spacer(Modifier.height(32.dp))
        when {
            !state.permissionGranted -> {
                Text(stringResource(if (permissionDenied) R.string.bluetooth_denied else R.string.permission_needed))
                Button(onClick = if (permissionDenied) onAppSettings else onPermission) {
                    Text(stringResource(if (permissionDenied) R.string.open_app_settings else R.string.allow_bluetooth))
                }
            }
            !state.bluetoothEnabled -> Button(onClick = onBluetooth) { Text(stringResource(R.string.enable_bluetooth)) }
            !state.connected -> {
                Text(stringResource(R.string.not_connected))
                Button(onClick = onBluetooth) { Text(stringResource(R.string.open_bluetooth)) }
            }
            state.connection == ConnectionState.UnsupportedModel -> Text(stringResource(R.string.unsupported))
            state.model?.anc == false -> Text(stringResource(R.string.no_anc))
            state.model == null -> Text(stringResource(R.string.model_pending))
            else -> {
                val switching = state.connection as? ConnectionState.Switching
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Mode.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            row.forEachIndexed { index, mode ->
                                ToggleButton(checked = state.mode == mode, onCheckedChange = { onMode(mode) },
                                    enabled = state.canSwitch,
                                    shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                                    if (switching?.target == mode) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(stringResource(mode.label()))
                                }
                            }
                        }
                    }
                }
            }
        }
        val message = when (state.problem) {
            Problem.NO_REPLY -> R.string.no_reply
            Problem.PROTOCOL_UNAVAILABLE -> R.string.protocol_help
            Problem.MODEL_PENDING -> if (state.model != null) R.string.model_pending else null
            Problem.BATTERY_UNAVAILABLE -> R.string.battery_unavailable
            Problem.SERVICE_UNAVAILABLE -> R.string.service_unavailable
            else -> if (state.connection == ConnectionState.ProtocolUnavailable) R.string.protocol_help else null
        }
        message?.let {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun BatteryValue(label: Int, reading: BatteryReading, now: Long, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 20.dp).heightIn(min = 130.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(reading.percent?.let { "$it%" } ?: "—", modifier = Modifier.fillMaxWidth(), maxLines = 1,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.displayLarge,
                autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 57.sp))
            if (reading.charging) Text(stringResource(R.string.charging), style = MaterialTheme.typography.bodySmall)
            if (reading.stale(now)) Text(stringResource(R.string.stale), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    AirModeTheme {
        HomeScreen(DeviceState(connection = ConnectionState.SessionReady, name = "AirPods 5",
            model = ModelId.fromNumber("A3531"), mode = Mode.ANC, battery = Battery(
            BatteryReading(80, true, SystemClock.elapsedRealtime()),
            BatteryReading(76, false, SystemClock.elapsedRealtime()),
            BatteryReading(54, false, SystemClock.elapsedRealtime())), permissionGranted = true),
            {}, {}, {}, {}, {}, false)
    }
}
