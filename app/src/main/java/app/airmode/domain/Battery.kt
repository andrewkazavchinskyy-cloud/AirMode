package app.airmode.domain

enum class BatterySource { UNKNOWN, METADATA, ADVERTISEMENT, PROTOCOL }

internal fun cachedMetadataReading(percent: Int, charging: Boolean, previous: BatteryReading, now: Long): BatteryReading {
    require(percent in 0..100)
    // Android exposes no timestamp for this cache, including its first read after process death.
    return if (previous.percent == percent && previous.charging == charging) previous
        else BatteryReading(percent, charging, now, available = false, source = BatterySource.METADATA)
}

data class BatteryReading(
    val percent: Int? = null,
    val charging: Boolean = false,
    val updatedAt: Long = 0L,
    val available: Boolean = percent != null,
    val source: BatterySource = BatterySource.UNKNOWN,
    val observedAt: Long = updatedAt,
) {
    init { require(percent == null || percent in 0..100) }
    fun stale(now: Long) = percent != null && now - updatedAt > 120_000
}
data class Battery(val left: BatteryReading = BatteryReading(), val right: BatteryReading = BatteryReading(),
    val case: BatteryReading = BatteryReading(), val headset: BatteryReading = BatteryReading()) {
    val known get() = listOf(left, right, case, headset).any { it.percent != null }
    fun merge(new: Battery) = Battery(fresh(left, new.left), fresh(right, new.right), fresh(case, new.case), fresh(headset, new.headset))
    private fun fresh(old: BatteryReading, new: BatteryReading): BatteryReading {
        if (new.observedAt == 0L || new.observedAt < old.observedAt) return old
        // Android metadata has no observation time. A cache read cannot override a live
        // control-channel reading, even if getMetadata was called more recently.
        if (old.source == BatterySource.PROTOCOL && old.available && !old.stale(new.observedAt) &&
            new.source != BatterySource.PROTOCOL) return old
        if (new.source == BatterySource.METADATA && !new.available && old.available && !old.stale(new.observedAt)) return old
        // A bud's "case disconnected" describes that link, not the independent case
        // advertiser. Keep a fresh reading from the other source until it becomes stale.
        if (!new.available && old.available && new.source != old.source && !old.stale(new.observedAt)) return old
        if (!new.available && new.percent == null && old.percent != null) return new.copy(
            percent = old.percent, updatedAt = old.updatedAt, charging = false)
        return new
    }
}
