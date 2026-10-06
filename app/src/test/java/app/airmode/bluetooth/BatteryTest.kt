package app.airmode.bluetooth

import app.airmode.domain.Battery
import app.airmode.domain.BatteryReading
import app.airmode.domain.BatterySource
import app.airmode.domain.cachedMetadataReading
import org.junit.Assert.*
import org.junit.Test

class BatteryTest {
    @Test fun initialMetadataCacheIsVisibleOnlyAsLastKnown() {
        val cache = Battery(case = cachedMetadataReading(100, true, BatteryReading(), 1000))
        val state = Battery().merge(cache)
        assertTrue(state.known)
        assertEquals(100, state.case.percent)
        assertFalse(state.case.available)
        val protocol = Battery(case = BatteryReading(50, false, 2000, source = BatterySource.PROTOCOL))
        assertEquals(protocol.case, state.merge(protocol).case)
        val repeated = cachedMetadataReading(100, true, cache.case, 3000)
        assertEquals(cache.case, repeated)
        assertEquals(protocol.case, protocol.merge(Battery(case = repeated)).case)
        val changedCache = cachedMetadataReading(55, false, cache.case, 4000)
        assertEquals(55, state.merge(Battery(case = changedCache)).case.percent)
        assertFalse(state.merge(Battery(case = changedCache)).case.available)
    }

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

    @Test fun unavailableCaseWithdrawsLiveValueWithoutRefreshingHistory() {
        val original = Battery(case = BatteryReading(100, false, 1000, source = BatterySource.PROTOCOL))
        val withdrawal = Battery(case = BatteryReading(available = false,
            source = BatterySource.PROTOCOL, observedAt = 2000))
        val merged = original.merge(withdrawal)
        assertEquals(100, merged.case.percent)
        assertFalse(merged.case.available)
        assertFalse(merged.case.charging)
        assertEquals(1000L, merged.case.updatedAt)
        assertEquals(2000L, merged.case.observedAt)
        assertEquals(BatterySource.PROTOCOL, merged.case.source)
        assertEquals(merged, merged.merge(Battery()))
        val live = merged.merge(Battery(case = BatteryReading(100, true, 3000, source = BatterySource.PROTOCOL)))
        assertTrue(live.case.available)
        assertTrue(live.case.charging)
        assertEquals(3000L, live.case.updatedAt)
    }

    @Test fun presentProtocolWinsMetadataThenWithdrawalAllowsFreshFallback() {
        val live = Battery(left = BatteryReading(48, false, 1000, source = BatterySource.PROTOCOL))
        val metadata = Battery(left = BatteryReading(90, false, 2000, source = BatterySource.METADATA))
        assertEquals(live, live.merge(metadata))
        val unavailable = live.merge(Battery(left = BatteryReading(available = false,
            source = BatterySource.PROTOCOL, observedAt = 3000)))
        val fallback = Battery(left = BatteryReading(50, false, 4000, source = BatterySource.ADVERTISEMENT))
        assertEquals(fallback.left, unavailable.merge(fallback).left)
    }

    @Test fun oldMetadataObservationCannotAcquireTimestampFromMergedState() {
        val current = Battery(right = BatteryReading(61, false, 3000, source = BatterySource.PROTOCOL))
        val oldMetadata = Battery(right = BatteryReading(40, false, 1000, source = BatterySource.METADATA))
        assertEquals(current, current.merge(oldMetadata))
        // A repeated independent cache value still has its original observation time.
        val unavailable = current.merge(Battery(right = BatteryReading(available = false,
            source = BatterySource.PROTOCOL, observedAt = 4000)))
        assertEquals(unavailable, unavailable.merge(oldMetadata))
        assertEquals(3000L, unavailable.right.updatedAt)
        assertEquals(4000L, unavailable.right.observedAt)
    }

    @Test fun expiredProtocolReadingAllowsNewerObservedSource() {
        val protocol = Battery(case = BatteryReading(100, false, 1000, source = BatterySource.PROTOCOL))
        val advertisement = Battery(case = BatteryReading(90, false, 121001, source = BatterySource.ADVERTISEMENT))
        assertEquals(advertisement.case, protocol.merge(advertisement).case)
    }

    @Test fun budLinkUnavailabilityCannotHideAnIndependentFreshCaseAdvert() {
        val case = Battery(case = BatteryReading(51, true, 1000, source = BatterySource.ADVERTISEMENT))
        val noCaseOnBudLink = Battery(case = BatteryReading(available = false,
            source = BatterySource.PROTOCOL, observedAt = 2000))
        assertEquals(case.case, case.merge(noCaseOnBudLink).case)
        val liveLink = Battery(case = BatteryReading(50, false, 3000, source = BatterySource.PROTOCOL))
        assertEquals(liveLink.case, case.merge(liveLink).case)
        val expired = case.merge(noCaseOnBudLink.copy(case = noCaseOnBudLink.case.copy(observedAt = 121001)))
        assertFalse(expired.case.available)
        assertEquals(1000L, expired.case.updatedAt)
    }

    @Test fun sameMillisecondCacheReadDoesNotWithdrawFreshSystemBatteryReport() {
        val live = Battery(headset = BatteryReading(75, updatedAt = 1000, source = BatterySource.METADATA))
        val cached = Battery(headset = cachedMetadataReading(75, false, BatteryReading(), 1000))
        assertEquals(live.headset, live.merge(cached).headset)
        val changedCache = Battery(headset = cachedMetadataReading(74, false, cached.headset, 2000))
        assertEquals(live.headset, live.merge(changedCache).headset)
    }
}
