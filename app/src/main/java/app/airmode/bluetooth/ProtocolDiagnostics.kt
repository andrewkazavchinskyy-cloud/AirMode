package app.airmode.bluetooth

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import app.airmode.BuildConfig
import app.airmode.domain.BatteryReading
import app.airmode.domain.Battery
import app.airmode.domain.Mode
import app.airmode.domain.Repository

/** Debug-only bounded memory, never Logcat/disk/network. Identity-bearing packet bodies are excluded. */
object ProtocolDiagnostics {
    private val lines = ArrayDeque<String>()
    private val types = mutableMapOf<Int, Int>()
    private var advertisements = 0
    private var matchedAdvertisements = 0
    private var acceptedAdvertisements = 0
    private var caseAdvertisements = 0
    private var acceptedCaseAdvertisements = 0
    private var metadataReads = 0
    private var metadataKnown = false
    private var metadataBattery = Battery()

    @Synchronized fun note(message: String) {
        if (!BuildConfig.DEBUG) return
        if (lines.size == 128) lines.removeFirst()
        lines.addLast("t=${SystemClock.elapsedRealtime()}ms $message")
    }

    fun txListening(mode: Mode) = note("TX listening mode=${mode.code}")

    fun txCapabilities(mask: Int) {
        // This logs only the bounded declared capability byte, never an arbitrary packet body.
        if (mask in 0..255) note("TX capabilities mask=%02x".format(mask))
    }

    @Synchronized fun packet(packet: ByteArray, event: ProtocolEvent?) {
        if (!BuildConfig.DEBUG) return
        val type = packet.getOrNull(4)?.toInt()?.and(255) ?: -1
        types[type] = (types[type] ?: 0) + 1
        if (type == 4 || event is ProtocolEvent.Model || event is ProtocolEvent.Listening || event is ProtocolEvent.Ears)
            note(packetSummary(packet, event))
    }

    internal fun packetSummary(packet: ByteArray, event: ProtocolEvent?): String {
        val validHeader = packet.size >= 6 && packet.take(4) == listOf<Byte>(4, 0, 4, 0) && packet[5] == 0.toByte()
        val header = if (validHeader) packet.take(6).joinToString("") { "%02x".format(it.toInt() and 255) } else "invalid"
        val label = when (event) {
            is ProtocolEvent.Batteries -> "battery accepted L=${readingSummary(event.battery.left)} R=${readingSummary(event.battery.right)} C=${readingSummary(event.battery.case)} H=${readingSummary(event.battery.headset)}"
            is ProtocolEvent.Model -> "model=${event.number}"
            is ProtocolEvent.Listening -> "mode=${event.mode.code}"
            is ProtocolEvent.Ears -> "ears primary=${event.primary} secondary=${event.secondary}"
            else -> "unrecognized"
        }
        // Only the documented battery message has its bounded body recorded; never metadata/serials.
        val battery = event is ProtocolEvent.Batteries && packet.size in 12..22 && header == "040004000400"
        val body = if (battery) " battery=${packet.joinToString("") { "%02x".format(it.toInt() and 255) }}" else ""
        return "RX header=$header bytes=${packet.size} $label$body"
    }

    internal fun readingSummary(reading: BatteryReading): String =
        "${reading.percent ?: "unknown"}(source=${reading.source},available=${reading.available},updated=${reading.updatedAt},observed=${reading.observedAt})"

    @Synchronized fun advertisement(matched: Boolean, accepted: Boolean, caseFormat: Boolean = false) {
        if (!BuildConfig.DEBUG) return
        advertisements++
        if (matched) matchedAdvertisements++
        if (accepted) acceptedAdvertisements++
        if (caseFormat) {
            caseAdvertisements++
            if (accepted) acceptedCaseAdvertisements++
        }
    }

    @Synchronized fun metadata(battery: Battery) {
        if (!BuildConfig.DEBUG) return
        metadataReads++
        metadataKnown = battery.known
        metadataBattery = battery
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
            appendLine("Connection=${state.connection}; problem=${state.problem}; model=${state.model?.number}; mode=${state.mode?.code}; pending=${state.pendingMode?.code}; controlBusy=${state.controlBusy}")
            appendLine("Capabilities vendor=${state.model?.vendor} form=${state.model?.batteryForm} modes=${state.model?.supportedModes?.map { it.code }}")
            appendLine("Battery L=${readingSummary(state.battery.left)} R=${readingSummary(state.battery.right)} C=${readingSummary(state.battery.case)} H=${readingSummary(state.battery.headset)}")
            appendLine("Metadata reads=$metadataReads known=$metadataKnown")
            appendLine("Metadata cache L=${readingSummary(metadataBattery.left)} R=${readingSummary(metadataBattery.right)} C=${readingSummary(metadataBattery.case)} H=${readingSummary(metadataBattery.headset)}")
            appendLine("Apple adverts=$advertisements matched=$matchedAdvertisements accepted=$acceptedAdvertisements")
            appendLine("Encrypted case adverts=$caseAdvertisements accepted=$acceptedCaseAdvertisements")
            appendLine("RX type counts=${types.toSortedMap()}")
            appendLine("No Bluetooth addresses, names or serial numbers are included.")
            lines.forEach { appendLine(it) }
        }
    }
}
