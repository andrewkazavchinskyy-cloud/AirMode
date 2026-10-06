package app.airmode.bluetooth

data class ModelId(val number: String, val generation: String, val anc: Boolean) {
    companion object {
        fun fromNumber(number: String): ModelId? {
            val normalized = number.trim().uppercase()
            return when (normalized) {
                "A3053", "A3050", "A3054" -> ModelId(normalized, "AirPods 4", false)
                "A3056", "A3055", "A3057" -> ModelId(normalized, "AirPods 4", true)
                "A3531", "A3532", "A3533", "A3439", "A3440", "A3441" -> ModelId(normalized, "AirPods 5", true)
                else -> null
            }
        }
    }
}
