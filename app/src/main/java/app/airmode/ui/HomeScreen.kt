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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.airmode.bluetooth.ModelId
import app.airmode.bluetooth.BatteryForm
import app.airmode.bluetooth.DeviceVendor
import app.airmode.R
import app.airmode.domain.*
import kotlinx.coroutines.delay

fun Mode.label() = when (this) {
    Mode.OFF -> R.string.mode_off
    Mode.ANC -> R.string.mode_anc
    Mode.TRANSPARENCY -> R.string.mode_transparency
    Mode.ADAPTIVE -> R.string.mode_adaptive
}
fun Mode.icon() = when (this) {
    Mode.OFF -> R.drawable.ic_noise_off
    Mode.ANC -> R.drawable.ic_noise_anc
    Mode.TRANSPARENCY -> R.drawable.ic_noise_transparency
    Mode.ADAPTIVE -> R.drawable.ic_noise_adaptive
}

@Composable
fun HomeScreen(state: DeviceState, onMode: (Mode) -> Unit, onSettings: () -> Unit,
               onBluetooth: () -> Unit, onPermission: () -> Unit, onAppSettings: () -> Unit,
               permissionDenied: Boolean, onRefreshBattery: () -> Unit = {}) {
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
        val headphones = state.model?.batteryForm == BatteryForm.HEADPHONES
        val supportedModes = Mode.entries.filter { it in (state.model?.supportedModes ?: emptySet()) }
        val status = when {
            !state.permissionGranted -> R.string.permission_needed
            !state.bluetoothEnabled -> R.string.enable_bluetooth
            !state.connected -> R.string.device_disconnected
            state.connection == ConnectionState.UnsupportedModel -> R.string.device_connected
            state.model == null -> R.string.model_pending
            else -> R.string.device_connected
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (headphones) R.drawable.ic_headset else R.drawable.ic_airmode),
                        contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                state.model?.takeIf { it.generation != state.name }?.let {
                    Text(it.generation, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(stringResource(status), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            if (headphones) {
                BatteryValue(R.string.battery_headset, state.battery.headset, now, Modifier.fillMaxWidth(), large = true)
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BatteryValue(R.string.battery_left, state.battery.left, now, Modifier.weight(1f))
                    BatteryValue(R.string.battery_case, state.battery.case, now, Modifier.weight(1f))
                    BatteryValue(R.string.battery_right, state.battery.right, now, Modifier.weight(1f))
                }
            }
        }
        if (state.connected && state.connection != ConnectionState.UnsupportedModel) {
            if (!headphones && !state.battery.case.available) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.case_unavailable), style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!state.battery.known) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.battery_waiting), style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRefreshBattery, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.refresh_battery))
            }
        }
        Spacer(Modifier.height(20.dp))
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
            state.model != null && supportedModes.isEmpty() -> Text(stringResource(
                if (state.model.vendor == DeviceVendor.SONY) R.string.sony_no_control else R.string.no_anc))
            state.model == null -> Text(stringResource(R.string.model_pending))
            else -> {
                val switching = state.connection as? ConnectionState.Switching
                Text(stringResource(R.string.noise_modes), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    supportedModes.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            row.forEachIndexed { index, mode ->
                                ToggleButton(checked = state.mode == mode, onCheckedChange = { checked ->
                                    if (checked) onMode(mode)
                                },
                                    enabled = state.canSwitch,
                                    shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    modifier = Modifier.weight(1f).heightIn(min = 80.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (switching?.target == mode) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary)
                                    } else Icon(painterResource(mode.icon()), contentDescription = null, Modifier.size(22.dp))
                                    Text(stringResource(mode.label()), modifier = Modifier.fillMaxWidth(), maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(when {
                    state.awaitingConfirmation -> stringResource(R.string.awaiting_confirmation)
                    switching != null -> stringResource(R.string.mode_switching, stringResource(switching.target.label()))
                    state.mode != null -> stringResource(R.string.mode_current, stringResource(state.mode.label()))
                    else -> stringResource(R.string.control_connecting)
                }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.mode == Mode.ADAPTIVE || switching?.target == Mode.ADAPTIVE) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.adaptive_hint), style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val protocolHelp = if (state.model?.vendor == DeviceVendor.SONY) R.string.sony_protocol_help else R.string.protocol_help
        val message = when (state.problem) {
            Problem.NO_REPLY -> if (state.failedMode == Mode.ADAPTIVE) R.string.adaptive_no_reply else R.string.no_reply
            Problem.PROTOCOL_UNAVAILABLE -> protocolHelp
            Problem.MODEL_PENDING -> null // The model/session explanation is already beside its controls.
            Problem.BATTERY_UNAVAILABLE -> R.string.battery_unavailable
            Problem.SERVICE_UNAVAILABLE -> R.string.service_unavailable
            else -> if (state.connection == ConnectionState.ProtocolUnavailable) protocolHelp else null
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
private fun BatteryValue(label: Int, reading: BatteryReading, now: Long, modifier: Modifier, large: Boolean = false) {
    val charging = reading.available && reading.charging
    val lastKnown = reading.percent?.takeUnless { reading.available }
    Column(modifier.padding(horizontal = if (large) 24.dp else 8.dp, vertical = 22.dp).heightIn(min = 146.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(when (label) { R.string.battery_case -> R.drawable.ic_popup_case; R.string.battery_headset -> R.drawable.ic_headset; else -> R.drawable.ic_popup_earbud }),
                    contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (reading.available) reading.percent?.let { "$it%" } ?: "—" else "—",
                modifier = Modifier.fillMaxWidth(), maxLines = 1,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.displayLarge,
                autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = if (large) 72.sp else 52.sp))
            if (reading.available && reading.percent != null) {
                LinearProgressIndicator(progress = { reading.percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
            } else Spacer(Modifier.height(4.dp))
            if (charging) Text(stringResource(R.string.charging), style = MaterialTheme.typography.bodySmall)
            if (lastKnown != null) Text(stringResource(R.string.last_known, lastKnown), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (reading.stale(now)) Text(stringResource(R.string.stale), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
