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
import app.airmode.MainActivity
import app.airmode.R
import app.airmode.data.Settings
import app.airmode.domain.ConnectionState
import app.airmode.domain.Battery
import app.airmode.domain.BatteryReading
import app.airmode.domain.DeviceState
import app.airmode.domain.Repository
import app.airmode.tile.NoiseTileService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
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
        val state = repository.state.value
        if (!state.connected || state.connection == ConnectionState.UnsupportedModel) {
            update(state, repository.settings.state.value)
            return START_NOT_STICKY
        }
        if (!foreground && !ensureForeground(state, repository.settings.state.value)) return START_NOT_STICKY
        repository.start()
        repository.refresh()
        return START_NOT_STICKY
    }

    private fun update(state: DeviceState, settings: Settings) {
        NoiseTileService.refresh(this)
        if (!state.connected || state.connection == ConnectionState.UnsupportedModel) {
            popupShown = false
            popupDeadline = 0L
            connectionId = null
            notifications.cancel(POPUP_ID)
            if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
            if (stopping == null) stopping = scope.launch {
                delay(30_000)
                stopSelf()
            }
            return
        }
        stopping?.cancel()
        stopping = null
        if (connectionId != state.connectionId) { popupShown = false; popupDeadline = 0L; connectionId = state.connectionId }
        if (!foreground) { if (!ensureForeground(state, settings)) return }
        else notifications.notify(FOREGROUND_ID, notification(state, settings))
        if (!settings.popup) { notifications.cancel(POPUP_ID); popupDeadline = 0L }
        // A confirmed supported model may honestly show unknown levels while its data arrives.
        if (settings.popup && state.model != null && canNotify(this)) {
            val now = SystemClock.elapsedRealtime()
            val firstPopup = !popupShown
            if (firstPopup) { popupShown = true; popupDeadline = now + 8_000 }
            // Respect a user's dismissal instead of posting a second alert when data arrives.
            if (now < popupDeadline && (firstPopup || notifications.activeNotifications.any { it.id == POPUP_ID })) notifications.notify(POPUP_ID,
                popupNotification(localized(this, settings), state, popupDeadline - now))
        }
    }

    private fun ensureForeground(state: DeviceState, settings: Settings): Boolean = try {
        startForeground(FOREGROUND_ID, notification(state, settings))
        foreground = true
        true
    } catch (_: SecurityException) { stopSelf(); false }
      catch (_: IllegalStateException) { stopSelf(); false }

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
            if (context.resources.configuration.locales[0].language == "ru") "AirPods подключены" else "AirPods connected"
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
        scope.cancel()
        notifications.cancel(POPUP_ID)
        repository.serviceStopped()
        super.onDestroy()
    }

    companion object {
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
                val percentage = battery.percent?.let { "$it%" } ?: "—"
                val detail = listOfNotNull(
                    context.getString(R.string.charging).takeIf { battery.charging },
                    context.getString(R.string.stale).takeIf { battery.stale(now) },
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
            fun value(percent: Int?) = percent?.let { "$it%" } ?: "—"
            return "${context.getString(R.string.battery_left)} ${value(state.battery.left.percent)} · " +
                "${context.getString(R.string.battery_right)} ${value(state.battery.right.percent)} · " +
                "${context.getString(R.string.battery_case)} ${value(state.battery.case.percent)}"
        }
    }
}
