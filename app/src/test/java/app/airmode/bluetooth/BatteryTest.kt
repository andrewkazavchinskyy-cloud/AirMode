package app.airmode.bluetooth

import app.airmode.domain.Battery
import app.airmode.domain.BatteryReading
import org.junit.Assert.*
import org.junit.Test

class BatteryTest {
    @Test fun mergeUsesPerComponentFreshnessAndKeepsUnknownFallback() {
        val previous = Battery(BatteryReading(80, false, 1000), BatteryReading(70, true, 2000))
        val update = Battery(BatteryReading(60, true, 3000), BatteryReading(99, false, 1000), BatteryReading(50, false, 3000))
        val merged = previous.merge(update)
        assertEquals(update.left, merged.left)
        assertEquals(previous.right, merged.right)
        assertEquals(update.case, merged.case)
        assertEquals(merged, merged.merge(Battery()))
    }

    @Test fun staleBoundaryUsesMonotonicTimeAndUnknownIsNeverStale() {
        val reading = BatteryReading(80, false, 1000)
        assertFalse(reading.stale(121000))
        assertTrue(reading.stale(121001))
        assertFalse(BatteryReading().stale(999999))
        assertFalse(reading.stale(999))
    }
}
