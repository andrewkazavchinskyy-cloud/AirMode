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
import app.airmode.MainActivity
import app.airmode.R
import app.airmode.data.Settings
import app.airmode.domain.ConnectionState
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
        if (connectionId != state.connectionId) { popupShown = false; connectionId = state.connectionId }
        if (!foreground) { if (!ensureForeground(state, settings)) return }
        else notifications.notify(FOREGROUND_ID, notification(state, settings))
        if (!settings.popup) notifications.cancel(POPUP_ID)
        if (!popupShown && settings.popup && state.battery.known && canNotify()) {
            notifications.notify(POPUP_ID, notification(state, settings, popup = true))
            popupShown = true
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
        notifications.createNotificationChannel(NotificationChannel(
            CHARGE_CHANNEL, context.getString(R.string.charge_channel), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { setSound(null, null); enableVibration(false) })
        notifications.createNotificationChannel(NotificationChannel(
            CONNECTION_CHANNEL, context.getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW
        ).apply { setSound(null, null); enableVibration(false) })
    }

    private fun notification(state: DeviceState, settings: Settings, popup: Boolean = false): Notification {
        val context = localized(this, settings)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (popup || settings.persistent) batteryText(context, state) else
            if (context.resources.configuration.locales[0].language == "ru") "AirPods подключены" else "AirPods connected"
        return Notification.Builder(this, if (popup) CHARGE_CHANNEL else CONNECTION_CHANNEL)
            .setSmallIcon(R.drawable.ic_airmode)
            .setContentTitle(state.name ?: "AirMode")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(!popup)
            .setAutoCancel(popup)
            .apply { if (popup) setTimeoutAfter(8_000) }
            .build()
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        notifications.cancel(POPUP_ID)
        repository.serviceStopped()
        super.onDestroy()
    }

    companion object {
        private const val CHARGE_CHANNEL = "charge"
        private const val CONNECTION_CHANNEL = "connection"
        private const val FOREGROUND_ID = 1
        private const val POPUP_ID = 2
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
