package app.airmode.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.airmode.R

@Composable
fun OnboardingScreen(bluetoothGranted: Boolean, bluetoothEnabled: Boolean, permissionDenied: Boolean,
                     notificationsGranted: Boolean, notificationsAsked: Boolean,
                     onBluetoothPermission: () -> Unit, onAppSettings: () -> Unit,
                     onEnableBluetooth: () -> Unit, onNotifications: () -> Unit,
                     onSkipNotifications: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.onboarding_text), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.bluetooth_explanation))
        when {
            !bluetoothGranted -> {
                if (permissionDenied) Text(stringResource(R.string.bluetooth_denied))
                Button(onClick = if (permissionDenied) onAppSettings else onBluetoothPermission) {
                    Text(stringResource(if (permissionDenied) R.string.open_app_settings else R.string.allow_bluetooth))
                }
            }
            !bluetoothEnabled -> Button(onClick = onEnableBluetooth) { Text(stringResource(R.string.enable_bluetooth)) }
            !notificationsGranted && !notificationsAsked -> {
                Text(stringResource(R.string.notification_explanation))
                Button(onClick = onNotifications) { Text(stringResource(R.string.allow_notifications)) }
                TextButton(onClick = onSkipNotifications) { Text(stringResource(R.string.skip_notifications)) }
            }
            else -> Button(onClick = onDone) { Text(stringResource(R.string.done)) }
        }
    }
}
