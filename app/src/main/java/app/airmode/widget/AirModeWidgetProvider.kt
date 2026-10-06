package app.airmode.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import android.view.View
import app.airmode.bluetooth.BatteryForm
import app.airmode.bluetooth.DeviceVendor
import app.airmode.MainActivity
import app.airmode.R
import app.airmode.data.Settings
import app.airmode.domain.*
import app.airmode.service.AirModeService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect

/** Launcher presentation only: every mode action is validated by the live repository. */
class AirModeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val repository = Repository.get(context)
        update(context, repository.state.value, repository.settings.state.value, force = true)
        repository.start()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                // Observe discovery while the connected-device service takes ownership.
                withTimeoutOrNull(5_000) {
                    combine(repository.state, repository.settings.state) { state, settings -> state to settings }
                        .collect { (state, settings) -> update(context, state, settings) }
                }
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val repository = Repository.get(context)
        update(context, repository.state.value, repository.settings.state.value, force = true)
    }

    companion object {
        private var lastContent: List<Any?>? = null

        fun update(context: Context, state: DeviceState, settings: Settings, statusOverride: String? = null, force: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AirModeWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val localized = AirModeService.localized(context, settings)
            val now = SystemClock.elapsedRealtime()
            fun detail(reading: BatteryReading): String = when {
                !reading.available && reading.percent != null -> localized.getString(R.string.widget_last_known, reading.percent)
                !reading.available -> ""
                reading.stale(now) -> localized.getString(R.string.stale)
                reading.charging -> localized.getString(R.string.charging)
                else -> ""
            }
            val status = statusOverride ?: when {
                !state.permissionGranted -> localized.getString(R.string.permission_needed)
                !state.connected -> localized.getString(R.string.no_airpods)
                state.connection == ConnectionState.UnsupportedModel -> localized.getString(R.string.unsupported)
                state.model?.supportedModes?.isEmpty() == true -> localized.getString(
                    if (state.model.vendor == DeviceVendor.SONY) R.string.sony_no_control else R.string.no_anc)
                state.problem == Problem.SERVICE_UNAVAILABLE -> localized.getString(R.string.service_unavailable)
                state.problem == Problem.NO_REPLY -> localized.getString(R.string.no_reply)
                state.connection == ConnectionState.ProtocolUnavailable -> localized.getString(R.string.protocol_unavailable)
                state.connection is ConnectionState.Switching -> localized.getString(R.string.widget_switching)
                state.awaitingConfirmation -> localized.getString(R.string.awaiting_confirmation)
                !state.canSwitch -> localized.getString(R.string.model_pending)
                else -> state.mode?.let { localized.getString(labelFor(it)) }
                    ?: localized.getString(R.string.control_connecting)
            }
            val headphones = state.model?.batteryForm == BatteryForm.HEADPHONES
            val supported = state.model?.supportedModes ?: Mode.entries.toSet()
            val batteries = if (headphones) listOf(state.battery.headset)
                else listOf(state.battery.left, state.battery.case, state.battery.right)
            val permission = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            // A stale launcher view may remain clickable after process death. It grants no authority.
            val actionable = permission && supported.isNotEmpty() && state.connection != ConnectionState.UnsupportedModel
            val pendingMode = state.pendingMode != null || state.connection is ConnectionState.Switching
            val content = listOf(state.name, state.mode, state.canSwitch, actionable, pendingMode, headphones, supported, status,
                settings.language, localized.resources.configuration.uiMode) +
                batteries.flatMap { listOf(it.percent, it.available, detail(it)) }
            if (!force && content == lastContent) return
            lastContent = content
            val views = RemoteViews(context.packageName, R.layout.widget_airmode)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_header, open)
            views.setOnClickPendingIntent(R.id.widget_title, open)
            views.setOnClickPendingIntent(R.id.widget_status, open)
            views.setTextViewText(R.id.widget_title, state.name ?: "AirMode")
            views.setTextViewText(R.id.widget_status, status)
            views.setImageViewResource(R.id.widget_device_icon, if (headphones) R.drawable.ic_headset else R.drawable.ic_airmode)
            views.setViewVisibility(R.id.widget_buds, if (headphones) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_headset, if (headphones) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.widget_buds, open)
            views.setOnClickPendingIntent(R.id.widget_headset, open)
            views.setViewVisibility(R.id.widget_modes_top, if (supported.any { it == Mode.OFF || it == Mode.ANC }) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_modes_bottom, if (supported.any { it == Mode.TRANSPARENCY || it == Mode.ADAPTIVE }) View.VISIBLE else View.GONE)
            fun component(label: Int, value: Int, info: Int, name: Int, reading: BatteryReading) {
                val caption = localized.getString(name)
                val percentage = reading.percent?.takeIf { reading.available }?.let { "$it%" } ?: "—"
                val description = detail(reading)
                views.setTextViewText(label, caption)
                views.setTextViewText(value, percentage)
                views.setTextViewText(info, description)
                views.setContentDescription(value, "$caption $percentage $description".trim())
            }
            component(R.id.widget_left_label, R.id.widget_left_value, R.id.widget_left_info, R.string.battery_left, state.battery.left)
            component(R.id.widget_case_label, R.id.widget_case_value, R.id.widget_case_info, R.string.battery_case, state.battery.case)
            component(R.id.widget_right_label, R.id.widget_right_value, R.id.widget_right_info, R.string.battery_right, state.battery.right)
            component(R.id.widget_headset_label, R.id.widget_headset_value, R.id.widget_headset_info, R.string.battery_headset, state.battery.headset)
            val buttons = listOf(R.id.widget_off, R.id.widget_anc, R.id.widget_transparency, R.id.widget_adaptive)
            Mode.entries.forEachIndexed { index, mode ->
                val id = buttons[index]
                val label = localized.getString(labelFor(mode))
                views.setViewVisibility(id, if (mode in supported) View.VISIBLE else View.GONE)
                views.setTextViewText(id, label)
                views.setContentDescription(id, if (state.mode == mode) localized.getString(R.string.mode_current, label) else label)
                views.setInt(id, "setBackgroundResource", if (state.mode == mode) R.drawable.widget_mode_selected else R.drawable.widget_mode)
                views.setTextColor(id, localized.getColor(if (state.mode == mode) R.color.widget_on_primary else R.color.widget_text))
                views.setFloat(id, "setAlpha", if (actionable && state.canSwitch) 1f else 0.45f)
                // Current requests stay disabled; cold launcher actions still revalidate the live session.
                views.setBoolean(id, "setEnabled", !pendingMode)
                val action = Intent(context, AirModeService::class.java).setAction(AirModeService.ACTION_WIDGET_MODE)
                    .putExtra(AirModeService.EXTRA_MODE, mode.code)
                val click = if (actionable && mode in supported) PendingIntent.getForegroundService(context, 100 + mode.code, action,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) else open
                views.setOnClickPendingIntent(id, click)
            }
            manager.updateAppWidget(ids, views)
        }

        private fun labelFor(mode: Mode) = when (mode) {
            Mode.OFF -> R.string.mode_off
            Mode.ANC -> R.string.mode_anc
            Mode.TRANSPARENCY -> R.string.mode_transparency
            Mode.ADAPTIVE -> R.string.mode_adaptive
        }
    }
}
