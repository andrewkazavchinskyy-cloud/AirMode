package app.airmode.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import app.airmode.domain.Mode
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Classic L2CAP only. The narrow runtime bridge grants no Bluetooth privileges. */
class Session(
    private val device: BluetoothDevice,
    private val scope: CoroutineScope,
    private val onEvent: (ProtocolEvent) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val socket = AtomicReference<BluetoothSocket?>()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val ancConfirmed = AtomicBoolean(false)
    private val writes = Mutex()
    private var reader: Job? = null
    private var timeout: Job? = null

    fun start() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        // Closing the socket unblocks native connect/read even when thread interruption does not.
        reader = scope.launch {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { closeSocket() }
                scope.launch(Dispatchers.IO) {
                    try { receive() } catch (_: Exception) {
                        // A blocked API, absent PSM, malformed negotiation or revoked permission fails closed.
                    } finally {
                        finish()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }
        timeout = scope.launch {
            delay(10_000)
            if (!ready.get()) close()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun receive() {
        val opened = ClassicTransport.open(device)
        if (!socket.compareAndSet(null, opened) || closed.get()) {
            opened.close()
            return
        }
        if (opened.connectionType != BluetoothSocket.TYPE_L2CAP) throw IOException("Unexpected transport")
        opened.connect()
        val mtu = opened.maxReceivePacketSize
        if (mtu !in 1..65_535) throw IOException("Invalid receive MTU")
        // Android buffers each L2CAP SDU. Reading the negotiated MTU consumes its whole packet;
        // a small arbitrary buffer would fragment it and make variable-length metadata ambiguous.
        val buffer = ByteArray(mtu)
        send(AapProtocol.handshake())
        var acknowledged = false
        while (scope.isActive && !closed.get()) {
            val count = opened.inputStream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            val packet = buffer.copyOf(count)
            if (!acknowledged) {
                if (AapProtocol.handshakeAcknowledged(packet)) {
                    acknowledged = true
                    send(AapProtocol.notifications())
                }
                continue
            }
            val event = AapProtocol.parse(packet, SystemClock.elapsedRealtime()) ?: continue
            if (event is ProtocolEvent.Model) ancConfirmed.set(ModelId.fromNumber(event.number)?.anc == true)
            if (ready.compareAndSet(false, true)) onEvent(ProtocolEvent.Ready)
            onEvent(event)
        }
    }

    suspend fun writeMode(mode: Mode) {
        check(ready.get() && ancConfirmed.get() && !closed.get()) { "Supported ANC session required" }
        send(AapProtocol.listening(mode))
    }

    private suspend fun send(packet: ByteArray): Unit = suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation { closeSocket() }
        scope.launch(Dispatchers.IO) {
            try {
                writes.withLock {
                    val active = socket.get() ?: throw IOException("No control socket")
                    if (closed.get() || packet.size > active.maxTransmitPacketSize) throw IOException("Control socket unavailable")
                    active.outputStream.write(packet)
                    active.outputStream.flush()
                }
                if (continuation.isActive) continuation.resume(Unit)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    fun close() {
        closeSocket()
        reader?.cancel()
        finish()
    }

    private fun closeSocket() {
        closed.set(true)
        runCatching { socket.getAndSet(null)?.close() }
    }

    private fun finish() {
        closeSocket()
        timeout?.cancel()
        if (finished.compareAndSet(false, true)) onClosed()
    }

    private val finished = AtomicBoolean(false)
}
