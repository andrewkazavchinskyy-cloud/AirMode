package app.airmode.bluetooth

import app.airmode.domain.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Original Tandem codec, from published MDR documentation and filtered hardware captures. */
object SonyProtocol {
    enum class Generation { V1, V2 }
    data class Frame(val type: Int, val sequence: Int, val payload: ByteArray)
    const val DATA = 0x0c
    const val DATA_EXTRA = 0x0e
    const val ACK = 1
    private const val MAX_FRAME = 2048

    fun encode(frame: Frame): ByteArray {
        require(frame.type in setOf(ACK, DATA, DATA_EXTRA) && frame.sequence in 0..1)
        require(frame.payload.size <= MAX_FRAME - 9 && (frame.type != ACK || frame.payload.isEmpty()))
        val body = byteArrayOf(frame.type.toByte(), frame.sequence.toByte(), 0, 0,
            (frame.payload.size ushr 8).toByte(), frame.payload.size.toByte()) + frame.payload
        val checksum = body.sumOf { it.toInt() and 255 }.and(255).toByte()
        val wire = ByteArrayOutputStream()
        wire.write(0x3e)
        (body + checksum).forEach { byte ->
            val value = byte.toInt() and 255
            if (value in 0x3c..0x3e) { wire.write(0x3d); wire.write(value and 0xef) }
            else wire.write(value)
        }
        wire.write(0x3c)
        return wire.toByteArray().also { require(it.size <= MAX_FRAME) }
    }

    /** Bounded stream assembly handles both fragmented and coalesced RFCOMM reads. */
    class Decoder {
        private val body = ByteArrayOutputStream()
        private var inside = false
        private var escaped = false
        private var wireSize = 0
        fun feed(bytes: ByteArray): List<Frame> {
            val frames = mutableListOf<Frame>()
            for (byte in bytes) {
                val value = byte.toInt() and 255
                if (value == 0x3e) { reset(); inside = true; wireSize = 1; continue }
                if (!inside) continue
                if (++wireSize > MAX_FRAME) { reset(); continue }
                if (value == 0x3c) {
                    if (!escaped) decode(body.toByteArray())?.let(frames::add)
                    reset(); continue
                }
                if (escaped) {
                    if (value !in 0x2c..0x2e) { reset(); continue }
                    body.write(value or 0x10); escaped = false
                } else if (value == 0x3d) escaped = true
                else body.write(value)
            }
            return frames
        }
        private fun reset() { inside = false; escaped = false; wireSize = 0; body.reset() }
        private fun decode(raw: ByteArray): Frame? {
            if (raw.size < 7 || raw.u(0) !in setOf(ACK, DATA, DATA_EXTRA) || raw.u(1) !in 0..1) return null
            val length = ByteBuffer.wrap(raw, 2, 4).int
            if (length !in 0..MAX_FRAME - 9 || raw.size != length + 7 || (raw.u(0) == ACK && length != 0)) return null
            if (raw.dropLast(1).sumOf { it.toInt() and 255 }.and(255) != raw.last().toInt().and(255)) return null
            return Frame(raw.u(0), raw.u(1), raw.copyOfRange(6, 6 + length))
        }
    }

    fun generation(payload: ByteArray): Generation? {
        if (payload.size < 4 || payload.u(0) != 1 || payload.u(1) != 0) return null
        if (payload.size == 4 && ((payload.u(2) shl 8) or payload.u(3)) in
            setOf(0x1000, 0x2000, 0x3000, 0x4000, 0x4010, 0x5000, 0x6000, 0x7000, 0x7010)) return Generation.V1
        // Public WF-1000XM5 and LinkBuds Clip captures: 01 00 03 00 <revision:2> <table flags:2>.
        if (payload.size == 8 && payload.u(2) == 3 && payload.u(3) == 0 &&
            payload.u(6) in 0..1 && payload.u(7) in 0..1) return Generation.V2
        return null
    }

    fun model(payload: ByteArray): String? {
        if (payload.size < 4 || payload.u(0) != 5 || payload.u(1) != 1) return null
        val length = payload.u(2)
        if (length !in 3..64 || payload.size != length + 3) return null
        return runCatching {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload, 3, length)).toString()
        }.getOrNull()?.takeIf { ModelId.sony(it, emptySet(), BatteryForm.HEADPHONES) != null }
    }

    /** Values are retained as wire IDs; a brand or shared UUID alone never grants a feature. */
    fun features(payload: ByteArray, generation: Generation): Set<Int>? {
        if (payload.size < 3 || payload.u(0) != 7 || payload.u(1) != 0) return null
        val width = if (generation == Generation.V1) 1 else 2
        val count = payload.u(2)
        if (count > 128 || payload.size != 3 + count * width) return null
        val values = (0 until count).map { index ->
            val offset = 3 + index * width
            if (width == 1) payload.u(offset) else (payload.u(offset) shl 8) or payload.u(offset + 1)
        }
        return values.toSet().takeIf { it.size == count }
    }

    fun noiseSubtype(generation: Generation, model: String, features: Set<Int>): Int? = when (generation) {
        Generation.V1 -> 2.takeIf { features.any { it in 0x61..0x63 } }
        // This model's 0x15 layout is independently documented; generic 0x17 is unsafe on it.
        Generation.V2 -> if (model.equals("WF-1000XM4", true)) 0x15.takeIf { features.any { (it ushr 8) in setOf(0x62, 0x63, 0x64, 0x65, 0x68, 0x6a, 0x6b, 0x6c, 0x6d) } }
            else 0x17.takeIf { features.any { (it ushr 8) == 0x6b } }
    }

    fun listening(payload: ByteArray, generation: Generation, subtype: Int): Mode? {
        if (payload.uOrNull(0) !in setOf(0x67, 0x69) || payload.uOrNull(1) != subtype) return null
        return if (generation == Generation.V1) {
            if (payload.size != 8 || payload.u(2) !in 0..1 || payload.u(4) !in 0..2 ||
                payload.u(6) !in 0..1 || payload.u(7) !in 0..20) null
            else when { payload.u(2) == 0 -> Mode.OFF; payload.u(4) == 0 -> Mode.TRANSPARENCY; else -> Mode.ANC }
        } else {
            val size = if (subtype == 0x15) 8 else 7
            if (payload.size != size || payload.u(2) != 1 || payload.u(3) !in 0..1 || payload.u(4) !in 0..1 ||
                (subtype == 0x15 && (payload.u(5) !in 0..2 || payload.u(6) !in 0..1 || payload.u(7) !in 0..20)) ||
                (subtype == 0x17 && (payload.u(5) !in 0..2 || payload.u(6) !in 0..20))) null
            else when { payload.u(3) == 0 -> Mode.OFF; payload.u(4) == 1 -> Mode.TRANSPARENCY; else -> Mode.ANC }
        }
    }

    fun supportedModes(generation: Generation, model: String, features: Set<Int>, current: ByteArray?): Set<Mode> {
        val subtype = noiseSubtype(generation, model, features) ?: return emptySet()
        if (current == null || listening(current, generation, subtype) == null) return emptySet()
        if (generation == Generation.V2) {
            // ULT's documented eight-byte SET differs from the captured seven-byte family.
            if (isUlt(model) && (current.u(5) != 2 || current.u(6) !in 0..1)) return emptySet()
            return setOf(Mode.OFF, Mode.ANC, Mode.TRANSPARENCY)
        }
        return buildSet {
            add(Mode.OFF)
            if (0x61 in features || 0x62 in features) add(Mode.ANC)
            if (0x62 in features || 0x63 in features) add(Mode.TRANSPARENCY)
        }.takeIf { it.size >= 2 }.orEmpty()
    }

    fun writeMode(generation: Generation, model: String, features: Set<Int>, current: ByteArray, mode: Mode): ByteArray {
        require(mode in supportedModes(generation, model, features, current))
        if (generation == Generation.V2 && isUlt(model))
            return bytes(0x68, 0x17, 1, if (mode == Mode.OFF) 0 else 1,
                if (mode == Mode.TRANSPARENCY) 1 else 0, 2, current.u(6), 0)
        return current.copyOf().apply {
            this[0] = 0x68
            if (generation == Generation.V1) {
                this[2] = (if (mode == Mode.OFF) 0 else 1).toByte()
                if (mode != Mode.OFF) {
                    this[4] = (if (mode == Mode.ANC) 2 else 0).toByte()
                    this[7] = (if (mode == Mode.ANC) 0 else current.u(7).coerceIn(1, 20)).toByte()
                }
            } else {
                this[3] = (if (mode == Mode.OFF) 0 else 1).toByte()
                this[4] = (if (mode == Mode.TRANSPARENCY) 1 else 0).toByte()
                // Retain wind/voice/ambient level; switching noise mode must not edit other features.
            }
        }
    }

    fun batteryQueries(generation: Generation, form: BatteryForm, features: Set<Int>): List<ByteArray> =
        if (generation == Generation.V1) buildList {
            if (form == BatteryForm.HEADPHONES && 0x11 in features) add(bytes(0x10, 0))
            if (form == BatteryForm.EARBUDS && 0x15 in features) add(bytes(0x10, 2))
            if (form == BatteryForm.EARBUDS && 0x18 in features) add(bytes(0x10, 3))
        } else buildList {
            // V2 feature IDs are the first byte of each two-byte capability entry.
            val ids = features.map { it ushr 8 }.toSet()
            if (form == BatteryForm.HEADPHONES && 0x20 in ids) add(bytes(0x22, 0))
            if (form == BatteryForm.EARBUDS && 0x29 in ids) add(bytes(0x22, 9))
            if (form == BatteryForm.EARBUDS && 0x2a in ids) add(bytes(0x22, 10))
        }

    fun battery(payload: ByteArray, generation: Generation, form: BatteryForm, now: Long, features: Set<Int> = emptySet()): Battery? {
        val opcodes = if (generation == Generation.V1) setOf(0x11, 0x13) else setOf(0x23)
        if (payload.uOrNull(0) !in opcodes || payload.size < 2) return null
        val ids = features.map { it ushr 8 }.toSet()
        fun reading(offset: Int): BatteryReading? {
            val level = payload.u(offset); val charging = payload.u(offset + 1)
            if (charging !in 0..1 || (level !in 0..100 && level != 255)) return null
            return BatteryReading(level.takeIf { it <= 100 }, level <= 100 && charging == 1, now,
                available = level <= 100, source = BatterySource.PROTOCOL)
        }
        return when {
            payload.u(1) == 0 && payload.size == 4 && form == BatteryForm.HEADPHONES &&
                (generation == Generation.V1 || 0x20 in ids) ->
                reading(2)?.let { Battery(headset = it) }
            generation == Generation.V1 && payload.u(1) == 2 && payload.size == 6 && form == BatteryForm.EARBUDS -> {
                val left = reading(2) ?: return null; val right = reading(4) ?: return null
                Battery(left = left, right = right)
            }
            generation == Generation.V1 && payload.u(1) == 3 && payload.size == 4 && form == BatteryForm.EARBUDS ->
                reading(2)?.let { Battery(case = it) }
            // Public WF-XM5 and unequal LinkBuds Clip captures corroborate two level/charge
            // pairs followed by firmware-update thresholds. Percentages are not scaled by them.
            generation == Generation.V2 && payload.u(1) == 9 && payload.size == 8 &&
                form == BatteryForm.EARBUDS && 0x29 in ids && payload.u(6) <= 100 && payload.u(7) <= 100 -> {
                val left = reading(2) ?: return null; val right = reading(4) ?: return null
                Battery(left = left, right = right)
            }
            generation == Generation.V2 && payload.u(1) == 10 && payload.size == 5 &&
                form == BatteryForm.EARBUDS && 0x2a in ids && payload.u(4) <= 100 ->
                reading(2)?.let { Battery(case = it) }
            else -> null
        }
    }
    private fun isUlt(model: String) = model.equals("WH-ULT900N", true) || model.equals("ULT WEAR", true)
    fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ByteArray.u(index: Int) = this[index].toInt() and 255
    private fun ByteArray.uOrNull(index: Int) = getOrNull(index)?.toInt()?.and(255)
}
