package app.airmode.bluetooth

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.airmode.BuildConfig
import app.airmode.domain.Repository

/** Debug-only bounded memory, never Logcat/disk/network. Identity-bearing packet bodies are excluded. */
object ProtocolDiagnostics {
    private val lines = ArrayDeque<String>()
    private val types = mutableMapOf<Int, Int>()
    private var advertisements = 0
    private var matchedAdvertisements = 0
    private var acceptedAdvertisements = 0
    private var metadataReads = 0
    private var metadataKnown = false

    @Synchronized fun note(message: String) {
        if (!BuildConfig.DEBUG) return
        if (lines.size == 128) lines.removeFirst()
        lines.addLast(message)
    }

    @Synchronized fun packet(packet: ByteArray, event: ProtocolEvent?) {
        if (!BuildConfig.DEBUG) return
        val type = packet.getOrNull(4)?.toInt()?.and(255) ?: -1
        types[type] = (types[type] ?: 0) + 1
        if (type == 4 || event is ProtocolEvent.Model || event is ProtocolEvent.Listening)
            note(packetSummary(packet, event))
    }

    internal fun packetSummary(packet: ByteArray, event: ProtocolEvent?): String {
        val header = packet.take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
        val label = when (event) {
            is ProtocolEvent.Batteries -> "battery accepted L=${event.battery.left.percent} R=${event.battery.right.percent} C=${event.battery.case.percent}"
            is ProtocolEvent.Model -> "model=${event.number}"
            is ProtocolEvent.Listening -> "mode=${event.mode.code}"
            else -> "unrecognized"
        }
        // Only the documented battery message has its bounded body recorded; never metadata/serials.
        val battery = packet.size in 7..64 && header == "040004000400"
        val body = if (battery) " battery=${packet.joinToString("") { "%02x".format(it.toInt() and 255) }}" else ""
        return "RX header=$header bytes=${packet.size} $label$body"
    }

    @Synchronized fun advertisement(matched: Boolean, accepted: Boolean) {
        if (!BuildConfig.DEBUG) return
        advertisements++
        if (matched) matchedAdvertisements++
        if (accepted) acceptedAdvertisements++
    }

    @Synchronized fun metadata(known: Boolean) {
        if (!BuildConfig.DEBUG) return
        metadataReads++
        metadataKnown = known
    }

    @Synchronized fun report(context: Context): String {
        if (!BuildConfig.DEBUG) return ""
        val repository = Repository.get(context)
        val state = repository.state.value
        val settings = repository.settings.state.value
        val notifications = context.getSystemService(NotificationManager::class.java)
        fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return buildString {
            appendLine("AirMode ${BuildConfig.VERSION_NAME} diagnostics")
            appendLine("Phone=${Build.MODEL}; SDK=${Build.VERSION.SDK_INT}; build=${Build.DISPLAY}")
            appendLine("Bluetooth connect=${granted(Manifest.permission.BLUETOOTH_CONNECT)} scan=${granted(Manifest.permission.BLUETOOTH_SCAN)}")
            appendLine("Notifications enabled=${notifications.areNotificationsEnabled()}; permission=${Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)}; popup=${settings.popup}")
            appendLine("Popup channel importance=${notifications.getNotificationChannel("charge_popup_v2")?.importance}; old channel=${notifications.getNotificationChannel("charge")?.importance}")
            appendLine("Connection=${state.connection}; problem=${state.problem}; model=${state.model?.number}; mode=${state.mode?.code}")
            appendLine("Battery L=${state.battery.left.percent} R=${state.battery.right.percent} C=${state.battery.case.percent}")
            appendLine("Metadata reads=$metadataReads known=$metadataKnown")
            appendLine("Apple adverts=$advertisements matched=$matchedAdvertisements accepted=$acceptedAdvertisements")
            appendLine("RX type counts=${types.toSortedMap()}")
            appendLine("No Bluetooth addresses, names or serial numbers are included.")
            lines.forEach { appendLine(it) }
        }
    }
}
