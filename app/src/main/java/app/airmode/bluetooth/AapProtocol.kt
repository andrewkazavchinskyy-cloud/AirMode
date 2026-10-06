package app.airmode.bluetooth

import app.airmode.domain.Battery
import app.airmode.domain.BatteryReading
import app.airmode.domain.BatterySource
import app.airmode.domain.Mode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

sealed interface ProtocolEvent {
    data class Model(val number: String, val name: String?) : ProtocolEvent
    data class Identity(val model: ModelId, val name: String? = null) : ProtocolEvent
    // Deliberately not a data class: keys have no generated content-based debug representation.
    class ProximityKeys(val keys: AppleProximity) : ProtocolEvent
    data class Batteries(val battery: Battery) : ProtocolEvent
    data class Listening(val mode: Mode) : ProtocolEvent
    data class Ears(val primary: Int, val secondary: Int) : ProtocolEvent
    data class ControlBusy(val busy: Boolean) : ProtocolEvent
    data object Ready : ProtocolEvent
}

/** Original decoder of the documented wire format; no raw packets or serials are logged. */
object AapProtocol {
    fun handshake() = bytes(0, 0, 4, 0, 1, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    fun notifications() = bytes(4, 0, 4, 0, 0x0F, 0, 0xFF, 0xFF, 0xFE, 0xFF)
    fun allNotifications() = bytes(4, 0, 4, 0, 0x0F, 0, 0xFF, 0xFF, 0xFF, 0xFF)
    fun listening(mode: Mode) = bytes(4, 0, 4, 0, 9, 0, 0x0D, mode.code, 0, 0, 0)
    // Publicly documented capability negotiation. Sent only after an explicit Adaptive
    // request on a proven ANC session; the mask is not a noise-mode acknowledgement.
    fun adaptiveCapabilities() = bytes(4, 0, 4, 0, 0x4D, 0, 0xFF, 0, 0, 0, 0, 0, 0, 0)
    fun requestProximityKeys() = bytes(4, 0, 4, 0, 0x30, 0, 5, 0)

    // CONNECT_RSP: msgType=1, service=4, result LE16=0. Never accept a truncated refusal field.
    fun handshakeAcknowledged(packet: ByteArray): Boolean = packet.size in 6..64 &&
        packet.u(0) == 1 && packet.u(1) == 0 && packet.u(2) == 4 && packet.u(3) == 0 &&
        packet.u(4) == 0 && packet.u(5) == 0

    fun parse(packet: ByteArray, now: Long): ProtocolEvent? {
        if (packet.size < 6 || packet.u(0) != 4 || packet.u(1) != 0 ||
            packet.u(2) != 4 || packet.u(3) != 0 || packet.u(5) != 0) return null
        return when (packet.u(4)) {
            4 -> battery(packet, now)
            6 -> if (packet.size == 8 && packet.u(6) in 0..2 && packet.u(7) in 0..2)
                ProtocolEvent.Ears(packet.u(6), packet.u(7)) else null
            9 -> if (packet.size == 11 && packet.u(6) == 0x0D &&
                packet.sliceArray(8..10).all { it == 0.toByte() })
                Mode.fromCode(packet.u(7))?.let(ProtocolEvent::Listening) else null
            0x1D -> metadata(packet)
            0x31 -> proximityKeys(packet)
            else -> null
        }
    }

    private fun battery(packet: ByteArray, now: Long): ProtocolEvent? {
        if (packet.size < 7) return null
        val count = packet.u(6)
        if (count !in 1..3 || packet.size != 7 + count * 5) return null
        var result = Battery()
        val seen = mutableSetOf<Int>()
        for (i in 0 until count) {
            val offset = 7 + i * 5
            val component = packet.u(offset)
            val level = packet.u(offset + 2)
            val status = packet.u(offset + 3)
            if (component !in listOf(1, 2, 4, 8) || !seen.add(component) ||
                packet.u(offset + 1) != 1 || packet.u(offset + 4) != 1 ||
                status !in listOf(0, 1, 2, 4) || (status in 1..2 && level > 100)) return null
            // Explicitly unavailable is distinct from a component missing from this packet.
            val reading = if (status in 1..2) BatteryReading(level, status == 1, now,
                source = BatterySource.PROTOCOL) else BatteryReading(source = BatterySource.PROTOCOL, observedAt = now)
            result = when (component) {
                4 -> result.copy(left = reading)
                2 -> result.copy(right = reading)
                1 -> result.copy(headset = reading)
                else -> result.copy(case = reading)
            }
        }
        return ProtocolEvent.Batteries(result)
    }

    private fun proximityKeys(packet: ByteArray): ProtocolEvent? {
        // Public l2cap.md: count at 6, four-byte entry header, length at header offset 2.
        // Unknown header fields remain uninterpreted; no key material enters diagnostics.
        if (packet.size !in 12..551 || packet.u(6) !in 1..8) return null
        var offset = 7
        var irk: ByteArray? = null
        var encryption: ByteArray? = null
        val seen = mutableSetOf<Int>()
        try {
            repeat(packet.u(6)) {
                if (offset + 4 > packet.size) return null
                val type = packet.u(offset)
                val length = packet.u(offset + 2)
                if (!seen.add(type) || length !in 1..64 || offset + 4 + length > packet.size ||
                    (type in listOf(1, 4) && length != 16)) return null
                when (type) {
                    1 -> irk = packet.copyOfRange(offset + 4, offset + 4 + length)
                    4 -> encryption = packet.copyOfRange(offset + 4, offset + 4 + length)
                }
                offset += 4 + length
            }
            if (offset != packet.size || (irk == null && encryption == null)) return null
            return ProtocolEvent.ProximityKeys(AppleProximity(irk, encryption))
        } finally { irk?.fill(0); encryption?.fill(0) }
    }

    private fun metadata(packet: ByteArray): ProtocolEvent? {
        // Captured metadata variant: 1d 00 02 [body length LE16] 04 00 [NUL strings].
        if (packet.size < 12 || packet.size > 4096 || packet.u(6) != 2 ||
            packet.u(9) != 4 || packet.u(10) != 0 ||
            packet.size != 9 + packet.u(7) + (packet.u(8) shl 8)) return null
        val fields = ArrayList<String>(3)
        var start = 11
        repeat(3) {
            val end = (start until packet.size).firstOrNull { packet[it] == 0.toByte() } ?: return null
            if (end - start !in 1..256) return null
            val value = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(packet, start, end - start)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) { return null }
            if (value.any { it.isISOControl() }) return null
            fields.add(value)
            start = end + 1
        }
        if (!fields[1].matches(Regex("A[0-9]{4}")) || fields[2] != "Apple Inc.") return null
        return ProtocolEvent.Model(fields[1], fields[0])
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
