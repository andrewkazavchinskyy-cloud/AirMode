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
    @Test fun noAcknowledgementRetriesOnceAndExpiresAt1500() = runTest {
        val sends = mutableListOf<Long>()
        assertTrue(sendModeRequest({ sends += testScheduler.currentTime }, { true }, { testScheduler.currentTime }))
        assertEquals(listOf(0L, 700L), sends)
        assertEquals(1_500L, testScheduler.currentTime)
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
        assertEquals(1_500L, testScheduler.currentTime)
    }

    @Test fun acknowledgementSkipsRetryAndIsNotReportedAsFailure() = runTest {
        val sends = mutableListOf<Long>()
        var pending = true
        launch { delay(100); pending = false }
        assertFalse(sendModeRequest({ sends += testScheduler.currentTime }, { pending }, { testScheduler.currentTime }))
        assertEquals(listOf(0L), sends)
        assertEquals(1_500L, testScheduler.currentTime)
    }

    @Test fun failedWriteDoesNotRetryOrClaimSuccess() = runTest {
        var sends = 0
        assertTrue(sendModeRequest({ sends++; throw IOException("closed") }, { true }, { testScheduler.currentTime }))
        assertEquals(1, sends)
        assertEquals(1_500L, testScheduler.currentTime)
    }

    @Test fun firstWriteTimeoutDoesNotRetryAndRemainsBounded() = runTest {
        var sends = 0
        assertTrue(sendModeRequest({ sends++; delay(501) }, { true }, { testScheduler.currentTime }))
        assertEquals(1, sends)
        assertEquals(1_500L, testScheduler.currentTime)
    }

    @Test fun retryTimeoutRemainsWithinTotalDeadline() = runTest {
        var sends = 0
        assertTrue(sendModeRequest({ if (++sends == 2) delay(401) }, { true }, { testScheduler.currentTime }))
        assertEquals(2, sends)
        assertEquals(1_500L, testScheduler.currentTime)
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
