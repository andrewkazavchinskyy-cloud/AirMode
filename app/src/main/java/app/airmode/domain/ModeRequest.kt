package app.airmode.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Returns whether the request still lacks confirmation at its deadline. */
internal suspend fun sendModeRequest(send: suspend () -> Unit, pending: () -> Boolean, now: () -> Long,
    awaitingConfirmation: () -> Unit = {}): Boolean {
    if (!pending()) return false
    val started = now()
    // The physical Pixel capture includes genuine reports at 1.65–1.81 s. Keep the
    // spinner bounded to 1.5 s, but do not mislabel those reports as "no reply".
    val spinnerDeadline = started + 1_500
    val deadline = started + 2_500
    suspend fun attempt(timeout: Long): Boolean = try {
        withTimeoutOrNull(timeout) { send(); true } == true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { false }

    val sent = attempt(500)
    if (!pending()) return false
    if (sent) {
        val retryAt = maxOf(started + 700, now() + 400)
        delay((retryAt - now()).coerceAtLeast(0))
        if (!pending()) return false
        if (now() < deadline) attempt(minOf(400, deadline - now()))
        if (!pending()) return false
    }
    delay((spinnerDeadline - now()).coerceAtLeast(0))
    if (!pending()) return false
    awaitingConfirmation()
    delay((deadline - now()).coerceAtLeast(0))
    return pending()
}
