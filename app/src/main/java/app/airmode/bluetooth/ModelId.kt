package app.airmode.bluetooth

import app.airmode.domain.Mode

enum class DeviceVendor { APPLE, SONY }
enum class BatteryForm { EARBUDS, HEADPHONES }

data class ModelId(
    val number: String,
    val generation: String,
    val anc: Boolean,
    val vendor: DeviceVendor = DeviceVendor.APPLE,
    val batteryForm: BatteryForm = BatteryForm.EARBUDS,
    val supportedModes: Set<Mode> = if (anc) Mode.entries.toSet() else emptySet(),
) {
    companion object {
        fun fromNumber(number: String): ModelId? {
            val normalized = number.trim().uppercase()
            return when (normalized) {
                "A1523", "A1722" -> ModelId(normalized, "AirPods 1", false)
                "A2032", "A2031" -> ModelId(normalized, "AirPods 2", false)
                "A2565", "A2564" -> ModelId(normalized, "AirPods 3", false)
                "A3053", "A3050", "A3054" -> ModelId(normalized, "AirPods 4", false)
                "A3056", "A3055", "A3057" -> ModelId(normalized, "AirPods 4", true)
                "A3531", "A3532", "A3533", "A3439", "A3440", "A3441" -> ModelId(normalized, "AirPods 5", true)
                "A2084", "A2083" -> ModelId(normalized, "AirPods Pro 1", true,
                    supportedModes = setOf(Mode.OFF, Mode.ANC, Mode.TRANSPARENCY))
                "A2931", "A2699", "A2698", "A3047", "A3048", "A3049" -> ModelId(normalized, "AirPods Pro 2", true)
                "A3063", "A3064", "A3065" -> ModelId(normalized, "AirPods Pro 3", true)
                "A2096", "A3184" -> ModelId(normalized, "AirPods Max 1", true,
                    batteryForm = BatteryForm.HEADPHONES,
                    supportedModes = setOf(Mode.OFF, Mode.ANC, Mode.TRANSPARENCY))
                "A3454" -> ModelId(normalized, "AirPods Max 2", true, batteryForm = BatteryForm.HEADPHONES)
                else -> null
            }
        }

        /** Called only with a device-info reply and supported modes from the live Sony session. */
        fun sony(number: String, modes: Set<Mode>, form: BatteryForm): ModelId? {
            val name = number.trim()
            if (name.length !in 3..64 || name.any { it.isISOControl() } ||
                !(listOf("WH-", "WF-", "WI-", "MDR-", "LinkBuds").any { name.startsWith(it, true) } || name.equals("ULT WEAR", true))) return null
            val supported = modes - Mode.ADAPTIVE
            if (supported.isNotEmpty() && supported.size < 2) return null
            return ModelId(name, name, Mode.ANC in supported, DeviceVendor.SONY, form, supported)
        }
    }
}
