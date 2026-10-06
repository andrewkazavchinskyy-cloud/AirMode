package app.airmode.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import app.airmode.BuildConfig
import app.airmode.bluetooth.ProtocolDiagnostics
import app.airmode.MainActivity
import app.airmode.R
import app.airmode.data.Settings
import app.airmode.domain.ConnectionState
import app.airmode.domain.Battery
import app.airmode.domain.BatteryReading
import app.airmode.domain.Mode
import app.airmode.widget.AirModeWidgetProvider
import app.airmode.domain.DeviceState
import app.airmode.domain.Repository
import app.airmode.tile.NoiseTileService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.Locale

class AirModeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { Repository.get(this) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var stopping: Job? = null
    private var popupShown = false
    private var popupDeadline = 0L
    private var connectionId: Long? = null
    private var foreground = false
    private var starting = true
    private var widgetCommand: Job? = null
    private var staleUpdate: Job? = null
    private var lastForegroundContent: List<Any?>? = null
    private var lastPopupContent: List<Any?>? = null
    private var lastTileContent: List<Any?>? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        if (!ensureForeground(repository.state.value, repository.settings.state.value)) return
        scope.launch {
            combine(repository.state, repository.settings.state) { state, settings -> state to settings }
                .collect { (state, settings) -> update(state, settings) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        starting = false
        if (intent?.action == ACTION_WIDGET_MODE) {
            val mode = Mode.fromCode(intent.getIntExtra(EXTRA_MODE, 0)) ?: return START_NOT_STICKY
            if (!foreground || !hasBluetoothPermission()) {
                val context = localized(this, repository.settings.state.value)
                AirModeWidgetProvider.update(this, repository.state.value, repository.settings.state.value,
                    context.getString(if (hasBluetoothPermission()) R.string.service_unavailable else R.string.permission_needed))
                stopSelf()
                return START_NOT_STICKY
            }
            if (widgetCommand?.isActive == true) return START_NOT_STICKY
            widgetCommand = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    // Never authorize a command from the launcher view or a persisted model.
                    repository.retryControl()
                    val ready = withTimeoutOrNull(5_000) {
                        repository.state.first { it.canSwitch || it.connection == ConnectionState.UnsupportedModel ||
                            it.connection == ConnectionState.ProtocolUnavailable || it.model?.anc == false }
                    }
                    if (ready?.canSwitch == true && hasBluetoothPermission()) repository.switchMode(mode)
                } finally {
                    widgetCommand = null
                    update(repository.state.value, repository.settings.state.value)
                }
            }
            widgetCommand?.start()
            return START_NOT_STICKY
        }
        val state = repository.state.value
        if (!state.connected || state.connection == ConnectionState.UnsupportedModel) {
            update(state, repository.settings.state.value)
            return START_NOT_STICKY
        }
        if (!foreground && !ensureForeground(state, repository.settings.state.value)) return START_NOT_STICKY
        repository.refresh()
        return START_NOT_STICKY
    }

    private fun hasBluetoothPermission() = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun visualBattery(state: DeviceState): List<Any?> {
        val now = SystemClock.elapsedRealtime()
        return listOf(state.battery.left, state.battery.case, state.battery.right).flatMap {
            listOf(it.percent, it.available, it.charging, it.stale(now))
        }
    }

    private fun update(state: DeviceState, settings: Settings) {
        val tileContent = listOf(state.connected, state.connection == ConnectionState.UnsupportedModel,
            state.connection == ConnectionState.ProtocolUnavailable, state.canSwitch, state.model?.anc, state.mode, settings.language)
        if (tileContent != lastTileContent) { lastTileContent = tileContent; NoiseTileService.refresh(this) }
        AirModeWidgetProvider.update(this, state, settings)
        staleUpdate?.cancel()
        val now = SystemClock.elapsedRealtime()
        val nextStale = listOf(state.battery.left, state.battery.case, state.battery.right)
            .filter { it.percent != null && it.available && !it.stale(now) }
            .minOfOrNull { it.updatedAt + 120_001 }
        if (state.connected && nextStale != null) staleUpdate = scope.launch {
            delay((nextStale - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            update(repository.state.value, repository.settings.state.value)
        }
        if (!state.connected || state.connection == ConnectionState.UnsupportedModel) {
            popupShown = false
            popupDeadline = 0L
            connectionId = null
            lastPopupContent = null
            notifications.cancel(POPUP_ID)
            // A widget PendingIntent cold-starts the FGS before Bluetooth profile discovery.
            if (foreground && !starting && widgetCommand?.isActive != true) {
                stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; foregroundReady = false; lastForegroundContent = null
            }
            if (stopping == null) stopping = scope.launch {
                delay(30_000)
                stopSelf()
            }
            return
        }
        stopping?.cancel()
        stopping = null
        if (connectionId != state.connectionId) {
            popupShown = false; popupDeadline = 0L; connectionId = state.connectionId; lastPopupContent = null
        }
        val foregroundContent = listOf(state.name, settings.persistent, settings.language) +
            if (settings.persistent) visualBattery(state) else emptyList()
        if (!foreground) { if (!ensureForeground(state, settings)) return }
        else if (foregroundContent != lastForegroundContent) notifications.notify(FOREGROUND_ID, notification(state, settings))
        lastForegroundContent = foregroundContent
        if (!settings.popup) { notifications.cancel(POPUP_ID); popupDeadline = 0L; lastPopupContent = null }
        // Existing metadata may arrive before model identity; no artificial delay is needed.
        if (settings.popup && (state.model != null || state.battery.known) && canNotify(this)) {
            val firstPopup = !popupShown
            if (firstPopup) { popupShown = true; popupDeadline = now + 8_000 }
            val popupContent = listOf(state.name, state.model?.generation, settings.language) + visualBattery(state)
            // Respect dismissal and the original eight-second deadline when later data arrives.
            if (now < popupDeadline && (firstPopup || notifications.activeNotifications.any { it.id == POPUP_ID }) &&
                popupContent != lastPopupContent) {
                notifications.notify(POPUP_ID, popupNotification(localized(this, settings), state, popupDeadline - now))
                ProtocolDiagnostics.note("connection popup ${if (firstPopup) "shown" else "updated"}; remaining=${popupDeadline - now}ms")
                lastPopupContent = popupContent
            }
        }
    }

    private fun ensureForeground(state: DeviceState, settings: Settings): Boolean = try {
        startForeground(FOREGROUND_ID, notification(state, settings))
        foreground = true
        foregroundReady = true
        true
    } catch (_: SecurityException) { foregroundFailure(); false }
      catch (_: IllegalStateException) { foregroundFailure(); false }

    private fun foregroundFailure() {
        foregroundReady = false
        val settings = repository.settings.state.value
        val context = localized(this, settings)
        AirModeWidgetProvider.update(this, repository.state.value, settings,
            context.getString(if (hasBluetoothPermission()) R.string.service_unavailable else R.string.permission_needed))
        stopSelf()
    }

    private fun createChannels() {
        val context = localized(this, repository.settings.state.value)
        createPopupChannel(context)
        notifications.createNotificationChannel(NotificationChannel(
            CONNECTION_CHANNEL, context.getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW
        ).apply { setSound(null, null); enableVibration(false) })
    }

    private fun notification(state: DeviceState, settings: Settings): Notification {
        val context = localized(this, settings)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (settings.persistent) batteryText(context, state) else
            if (context.resources.configuration.locales[0].language == "ru") {
                if (state.connected) "AirPods подключены" else "Подключение AirPods…"
            } else if (state.connected) "AirPods connected" else "Connecting AirPods…"
        return Notification.Builder(this, CONNECTION_CHANNEL)
            .setSmallIcon(R.drawable.ic_airmode)
            .setContentTitle(state.name ?: "AirMode")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        foregroundReady = false
        scope.cancel()
        notifications.cancel(POPUP_ID)
        repository.serviceStopped()
        super.onDestroy()
    }

    companion object {
        @Volatile var foregroundReady = false
            private set
        const val ACTION_WIDGET_MODE = "app.airmode.action.WIDGET_MODE"
        const val EXTRA_MODE = "mode"
        private const val POPUP_CHANNEL = "charge_popup_v2"
        private const val CONNECTION_CHANNEL = "connection"
        private const val FOREGROUND_ID = 1
        private const val POPUP_ID = 2
        private const val TEST_POPUP_ID = 3
        private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        private fun createPopupChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
                POPUP_CHANNEL, context.getString(R.string.charge_channel), NotificationManager.IMPORTANCE_HIGH
            ).apply { setSound(null, null); enableVibration(false) })
        }
        private fun popupNotification(context: Context, state: DeviceState, timeout: Long): Notification {
            val content = RemoteViews(context.packageName, R.layout.notification_battery)
            content.setTextViewText(R.id.popup_device_name, state.name ?: state.model?.generation ?: "AirMode")
            val now = SystemClock.elapsedRealtime()
            fun component(column: Int, label: Int, value: Int, status: Int, labelKey: Int, battery: BatteryReading) {
                val name = context.getString(labelKey)
                val percentage = battery.percent?.takeIf { battery.available }?.let { "$it%" } ?: "—"
                val detail = listOfNotNull(
                    context.getString(R.string.widget_last_known, battery.percent).takeIf { !battery.available && battery.percent != null },
                    context.getString(R.string.charging).takeIf { battery.available && battery.charging },
                    context.getString(R.string.stale).takeIf { battery.available && battery.stale(now) },
                ).joinToString(" · ")
                content.setTextViewText(label, name)
                content.setTextViewText(value, percentage)
                content.setTextViewText(status, detail)
                content.setContentDescription(column, "$name $percentage $detail".trim())
            }
            component(R.id.popup_left, R.id.popup_left_label, R.id.popup_left_value, R.id.popup_left_status,
                R.string.battery_left, state.battery.left)
            component(R.id.popup_case, R.id.popup_case_label, R.id.popup_case_value, R.id.popup_case_status,
                R.string.battery_case, state.battery.case)
            component(R.id.popup_right, R.id.popup_right_label, R.id.popup_right_value, R.id.popup_right_status,
                R.string.battery_right, state.battery.right)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(context, POPUP_CHANNEL)
                .setSmallIcon(R.drawable.ic_airmode)
                .setContentTitle(state.name ?: "AirMode")
                .setContentText(batteryText(context, state))
                .setContentIntent(open)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomBigContentView(content)
                .setCustomHeadsUpContentView(content)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setTimeoutAfter(timeout)
                .build()
        }
        /** Explicit notification preview; synthetic readings never enter device state. */
        fun testPopup(context: Context) {
            if (!BuildConfig.DEBUG || !canNotify(context)) return
            createPopupChannel(context)
            val now = SystemClock.elapsedRealtime()
            val title = if (context.resources.configuration.locales[0].language == "ru") "Проверка AirMode" else "AirMode test"
            val preview = DeviceState(name = title, battery = Battery(
                BatteryReading(80, updatedAt = now), BatteryReading(76, updatedAt = now), BatteryReading(54, updatedAt = now)))
            context.getSystemService(NotificationManager::class.java).notify(TEST_POPUP_ID,
                popupNotification(context, preview, 8_000))
        }
        fun start(context: Context): Boolean {
            val state = Repository.get(context).state.value
            if (!state.connected || state.connection == ConnectionState.UnsupportedModel) return false
            return try {
                context.startForegroundService(Intent(context, AirModeService::class.java))
                true
            } catch (_: IllegalStateException) { false }
              catch (_: SecurityException) { false }
        }
        fun stop(context: Context) { context.stopService(Intent(context, AirModeService::class.java)) }
        fun localized(context: Context, settings: Settings): Context {
            val configuration = Configuration(context.resources.configuration)
            if (settings.language == "system") configuration.setLocales(Resources.getSystem().configuration.locales)
            else configuration.setLocale(Locale.forLanguageTag(settings.language))
            return context.createConfigurationContext(configuration)
        }
        private fun batteryText(context: Context, state: DeviceState): String {
            fun value(reading: BatteryReading): String {
                val percentage = reading.percent?.takeIf { reading.available }?.let { "$it%" } ?: "—"
                return if (!reading.available && reading.percent != null) "$percentage (${context.getString(R.string.widget_last_known, reading.percent)})"
                    else percentage
            }
            return "${context.getString(R.string.battery_left)} ${value(state.battery.left)} · " +
                "${context.getString(R.string.battery_right)} ${value(state.battery.right)} · " +
                "${context.getString(R.string.battery_case)} ${value(state.battery.case)}"
        }
    }
}
