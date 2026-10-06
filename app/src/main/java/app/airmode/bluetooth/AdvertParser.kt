package app.airmode.bluetooth

import app.airmode.domain.Battery
import app.airmode.domain.BatteryReading
import app.airmode.domain.BatterySource

/** Apple manufacturer payload, excluding the company ID. This does not establish device identity. */
object AdvertParser {
    const val COMPANY_ID = 0x004C

    fun parse(data: ByteArray, now: Long): Battery? {
        if (data.size != 27 || data.u(0) != 0x07 || data.u(1) != 0x19 ||
            data.u(2) != 0x01 || data.u(4) != 0x20) return null
        val primaryLeft = data.u(5) and 0x20 != 0
        val levels = data.u(6)
        val flags = data.u(7)
        val primary = reading(levels and 0x0F, flags and 0x10 != 0, now)
        val secondary = reading(levels ushr 4, flags and 0x20 != 0, now)
        return Battery(
            left = if (primaryLeft) primary else secondary,
            right = if (primaryLeft) secondary else primary,
            case = reading(flags and 0x0F, flags and 0x40 != 0, now),
        )
    }

    private fun reading(nibble: Int, charging: Boolean, now: Long): BatteryReading =
        if (nibble in 0..10) BatteryReading(nibble * 10, charging, now, source = BatterySource.ADVERTISEMENT)
        else BatteryReading(source = BatterySource.ADVERTISEMENT, observedAt = now)
}

internal fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF
