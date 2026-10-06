package app.airmode

import android.Manifest
import android.app.LocaleManager
import android.app.StatusBarManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.airmode.domain.Repository
import app.airmode.tile.NoiseTileService
import app.airmode.ui.*
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val repository by lazy { Repository.get(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository.start()
        setContent { AirModeContent() }
    }

    @Composable
    private fun AirModeContent() {
        val device by repository.state.collectAsStateWithLifecycle()
        val settings by repository.settings.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var permissionDenied by rememberSaveable { mutableStateOf(false) }
        var notificationsAsked by rememberSaveable { mutableStateOf(false) }
        var notificationDialog by remember { mutableStateOf(false) }
        var pendingNotification by remember { mutableStateOf<String?>(null) }
        var tileMessage by remember { mutableStateOf<Int?>(null) }
        var permissionsRefresh by remember { mutableIntStateOf(0) }
        val bluetoothGranted = permissionsRefresh.let { hasBluetoothPermission() }
        val notificationsGranted = permissionsRefresh.let { hasNotificationPermission() }
        val bluetoothRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionDenied = result.any { (permission, granted) ->
                !granted && !shouldShowRequestPermissionRationale(permission)
            }
            permissionsRefresh++
            repository.refresh()
        }
        val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsAsked = true
            permissionsRefresh++
            val pending = pendingNotification
            scope.launch {
                when (pending) {
                    "popup" -> repository.settings.setPopup(granted)
                    "persistent" -> repository.settings.setPersistent(granted)
                }
                repository.refresh()
            }
            pendingNotification = null
        }
        val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            repository.refresh()
        }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionsRefresh++
                    if (hasBluetoothPermission()) permissionDenied = false
                    repository.refresh()
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val localizedContext = remember(settings.language, LocalConfiguration.current) {
            val locales = when (settings.language) {
                "system" -> Resources.getSystem().configuration.locales
                else -> LocaleList(Locale.forLanguageTag(settings.language))
            }
            createConfigurationContext(Configuration(resources.configuration).apply { setLocales(locales) })
        }
        LaunchedEffect(settings.language) {
            if (Build.VERSION.SDK_INT >= 33) {
                val manager = getSystemService(LocaleManager::class.java)
                val desired = if (settings.language == "system") LocaleList.getEmptyLocaleList()
                    else LocaleList.forLanguageTags(settings.language)
                if (manager.applicationLocales != desired) manager.applicationLocales = desired
            }
        }
        CompositionLocalProvider(LocalContext provides localizedContext,
            LocalConfiguration provides localizedContext.resources.configuration) {
            AirModeTheme {
                val onPermission = {
                    bluetoothRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                }
                val onAppSettings = {
                    startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                val onBluetooth = {
                    if (!bluetoothGranted) onPermission()
                    else if (!device.bluetoothEnabled) enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    else startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS))
                }
                BackHandler(settingsOpen) { settingsOpen = false }
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        when {
                            !settings.onboarded -> OnboardingScreen(bluetoothGranted, device.bluetoothEnabled,
                                permissionDenied, notificationsGranted, notificationsAsked, onPermission, onAppSettings,
                                onBluetooth, {
                                    pendingNotification = "popup"
                                    if (Build.VERSION.SDK_INT >= 33) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else notificationsAsked = true
                                }, {
                                    notificationsAsked = true
                                    scope.launch { repository.settings.setPopup(false) }
                                }, { scope.launch { repository.settings.completeOnboarding(); repository.refresh() } })
                            settingsOpen -> SettingsScreen(settings, device.model?.anc != false, { settingsOpen = false },
                                { value ->
                                    if (value && !notificationsGranted) { pendingNotification = "popup"; notificationDialog = true }
                                    else scope.launch { repository.settings.setPopup(value); repository.refresh() }
                                }, { value ->
                                    if (value && !notificationsGranted) { pendingNotification = "persistent"; notificationDialog = true }
                                    else scope.launch { repository.settings.setPersistent(value); repository.refresh() }
                                }, { value -> scope.launch { repository.settings.setAutoStart(value) } },
                                { modes -> scope.launch { repository.settings.setTileModes(modes) } },
                                { language -> scope.launch { repository.settings.setLanguage(language) } },
                                { addTile { tileMessage = it } },
                                { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) })
                            else -> HomeScreen(device.copy(permissionGranted = bluetoothGranted), repository::switchMode,
                                { settingsOpen = true }, onBluetooth, onPermission, onAppSettings, permissionDenied)
                        }
                    }
                }
                if (notificationDialog) AlertDialog(onDismissRequest = { notificationDialog = false; pendingNotification = null },
                    title = { Text(stringResource(R.string.allow_notifications)) },
                    text = { Text(stringResource(R.string.notification_explanation)) },
                    confirmButton = { TextButton(onClick = {
                        notificationDialog = false
                        if (Build.VERSION.SDK_INT >= 33) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text(stringResource(R.string.continue_action)) } },
                    dismissButton = { TextButton(onClick = { notificationDialog = false; pendingNotification = null }) {
                        Text(stringResource(R.string.cancel))
                    } })
                tileMessage?.let { message -> AlertDialog(onDismissRequest = { tileMessage = null },
                    text = { Text(stringResource(message)) },
                    confirmButton = { TextButton(onClick = { tileMessage = null }) { Text(stringResource(R.string.done)) } }) }
            }
        }
    }

    private fun hasBluetoothPermission() = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun addTile(onResult: (Int) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) { onResult(R.string.add_tile_manual); return }
        try {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, NoiseTileService::class.java), getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_noise_anc), mainExecutor
            ) { result ->
                onResult(if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                    R.string.add_tile_done else R.string.add_tile_manual)
            }
        } catch (_: RuntimeException) { onResult(R.string.add_tile_manual) }
    }

    companion object { const val REPOSITORY_URL = "https://github.com/andrewkazavchinskyy-cloud/AirMode" }
}
