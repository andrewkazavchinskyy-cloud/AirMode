package app.airmode.bluetooth

import app.airmode.domain.Mode

/** Each vendor validates its live identity and wire capabilities before a mode write. */
interface ControlSession {
    fun start()
    suspend fun writeMode(mode: Mode)
    fun close()
}
