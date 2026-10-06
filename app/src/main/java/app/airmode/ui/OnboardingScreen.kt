package app.airmode.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
        Spacer(Modifier.height(12.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_airmode), contentDescription = null, Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.onboarding_text), style = MaterialTheme.typography.bodyLarge)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(stringResource(R.string.bluetooth_explanation), modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            !bluetoothGranted -> {
                if (permissionDenied) Text(stringResource(R.string.bluetooth_denied))
                Button(onClick = if (permissionDenied) onAppSettings else onBluetoothPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(if (permissionDenied) R.string.open_app_settings else R.string.allow_bluetooth))
                }
            }
            !bluetoothEnabled -> Button(onClick = onEnableBluetooth, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.enable_bluetooth)) }
            !notificationsGranted && !notificationsAsked -> {
                Text(stringResource(R.string.notification_explanation))
                Button(onClick = onNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.allow_notifications)) }
                TextButton(onClick = onSkipNotifications, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.skip_notifications)) }
            }
            else -> Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.done)) }
        }
    }
}
