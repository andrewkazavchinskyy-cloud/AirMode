package app.airmode.domain

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeRequestTest {
    @Test fun fastSecondRequestCanWaitForPhysicalRateLimitWithoutBeingLost() = runTest {
        delay(100) // Previous request was acknowledged quickly; its write was at t=0.
        val sends = mutableListOf<Long>()
        var pending = true
        launch { delay(350); pending = false }
        assertFalse(sendModeRequest({
            delay(300) // Native Session spaces actual mode writes at least 400 ms apart.
            sends += testScheduler.currentTime
        }, { pending }, { testScheduler.currentTime }))
        assertEquals(listOf(400L), sends)
        assertEquals(800L, testScheduler.currentTime)
    }

    @Test fun noAcknowledgementRetriesOnceAndExpiresAt2500() = runTest {
        val sends = mutableListOf<Long>()
        assertTrue(sendModeRequest({ sends += testScheduler.currentTime }, { true }, { testScheduler.currentTime }))
        assertEquals(listOf(0L, 700L), sends)
        assertEquals(2_500L, testScheduler.currentTime)
    }

    @Test fun slowFirstWriteStillLeaves400msBeforeRetry() = runTest {
        val sends = mutableListOf<Long>()
        var firstCompleted = 0L
        assertTrue(sendModeRequest({
            sends += testScheduler.currentTime
            if (sends.size == 1) { delay(450); firstCompleted = testScheduler.currentTime }
        }, { true }, { testScheduler.currentTime }))
        assertEquals(listOf(0L, 850L), sends)
        assertEquals(400L, sends.last() - firstCompleted)
        assertEquals(2_500L, testScheduler.currentTime)
    }

    @Test fun acknowledgementSkipsRetryAndIsNotReportedAsFailure() = runTest {
        val sends = mutableListOf<Long>()
        var pending = true
        launch { delay(100); pending = false }
        assertFalse(sendModeRequest({ sends += testScheduler.currentTime }, { pending }, { testScheduler.currentTime }))
        assertEquals(listOf(0L), sends)
        assertEquals(700L, testScheduler.currentTime)
    }

    @Test fun acknowledgementDuringWriteDoesNotCancelOrConfuseLaterSameModeRequest() = runTest {
        val sends = mutableListOf<Long>()
        var activeRequest = 1L
        var awaiting = true
        var firstCompleted = false
        var firstFinishedAt = -1L
        val first = launch {
            assertFalse(sendModeRequest({
                sends += testScheduler.currentTime
                delay(450)
                firstCompleted = true
            }, { activeRequest == 1L && awaiting }, { testScheduler.currentTime }))
            firstFinishedAt = testScheduler.currentTime
        }
        launch { delay(100); awaiting = false }
        delay(400)
        activeRequest = 2L
        awaiting = true
        assertTrue(sendModeRequest({ sends += testScheduler.currentTime },
            { activeRequest == 2L && awaiting }, { testScheduler.currentTime }))
        first.join()
        assertFalse(first.isCancelled)
        assertTrue(firstCompleted)
        assertEquals(450L, firstFinishedAt)
        assertEquals(listOf(0L, 400L, 1_100L), sends)
        assertEquals(2_900L, testScheduler.currentTime)
    }

    @Test fun failedWriteDoesNotRetryOrClaimSuccess() = runTest {
        var sends = 0
        assertTrue(sendModeRequest({ sends++; throw IOException("closed") }, { true }, { testScheduler.currentTime }))
        assertEquals(1, sends)
        assertEquals(2_500L, testScheduler.currentTime)
    }

    @Test fun firstWriteTimeoutDoesNotRetryAndRemainsBounded() = runTest {
        var sends = 0
        assertTrue(sendModeRequest({ sends++; delay(501) }, { true }, { testScheduler.currentTime }))
        assertEquals(1, sends)
        assertEquals(2_500L, testScheduler.currentTime)
    }

    @Test fun retryTimeoutRemainsWithinTotalDeadline() = runTest {
        var sends = 0
        assertTrue(sendModeRequest({ if (++sends == 2) delay(401) }, { true }, { testScheduler.currentTime }))
        assertEquals(2, sends)
        assertEquals(2_500L, testScheduler.currentTime)
    }

    @Test fun captured1807msResponseStopsSpinnerAt1500WithoutFalseFailure() = runTest {
        val sends = mutableListOf<Long>()
        var pending = true
        var stoppedSpinnerAt = -1L
        launch { delay(1_807); pending = false }
        assertFalse(sendModeRequest({ sends += testScheduler.currentTime }, { pending },
            { testScheduler.currentTime }, { stoppedSpinnerAt = testScheduler.currentTime }))
        assertEquals(1_500L, stoppedSpinnerAt)
        assertEquals(listOf(0L, 700L), sends)
        assertEquals(2_500L, testScheduler.currentTime)
    }

    @Test fun cancellationPropagates() = runTest {
        val job = launch {
            sendModeRequest({ throw CancellationException("request cancelled") }, { true }, { testScheduler.currentTime })
            fail("Cancellation must propagate")
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0L, testScheduler.currentTime)
    }
}
