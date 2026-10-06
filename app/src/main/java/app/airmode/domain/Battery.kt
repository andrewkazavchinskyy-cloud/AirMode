package app.airmode.domain

data class BatteryReading(val percent: Int? = null, val charging: Boolean = false, val updatedAt: Long = 0L) {
    init { require(percent == null || percent in 0..100) }
    fun stale(now: Long) = percent != null && now - updatedAt > 120_000
}
data class Battery(val left: BatteryReading = BatteryReading(), val right: BatteryReading = BatteryReading(), val case: BatteryReading = BatteryReading()) {
    val known get() = listOf(left, right, case).any { it.percent != null }
    fun merge(new: Battery) = Battery(fresh(left, new.left), fresh(right, new.right), fresh(case, new.case))
    private fun fresh(old: BatteryReading, new: BatteryReading) = if (new.updatedAt >= old.updatedAt && new.updatedAt > 0) new else old
}
