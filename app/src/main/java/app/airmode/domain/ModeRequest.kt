package app.airmode.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Returns whether the request still lacks confirmation at its deadline. */
internal suspend fun sendModeRequest(send: suspend () -> Unit, pending: () -> Boolean, now: () -> Long): Boolean {
    if (!pending()) return false
    val started = now()
    val deadline = started + 1_500
    suspend fun attempt(timeout: Long): Boolean = try {
        withTimeoutOrNull(timeout) { send(); true } == true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { false }

    if (attempt(500)) {
        val retryAt = maxOf(started + 700, now() + 400)
        delay((retryAt - now()).coerceAtLeast(0))
        if (pending() && now() < deadline) attempt(minOf(400, deadline - now()))
    }
    delay((deadline - now()).coerceAtLeast(0))
    return pending()
}
