package app.airmode.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import app.airmode.BuildConfig
import app.airmode.domain.Mode
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Experimental native RFCOMM session. Transport ACKs never confirm a listening mode. */
class SonySession(
    private val device: BluetoothDevice,
    parentScope: CoroutineScope,
    private val onEvent: (ProtocolEvent) -> Unit,
    private val onClosed: () -> Unit,
) : ControlSession {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val socket = AtomicReference<BluetoothSocket?>()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private val liveModel = AtomicReference<ModelId?>()
    private val settings = AtomicReference<ByteArray?>()
    private val reply = AtomicReference<Reply?>()
    private val acknowledgement = AtomicReference<Acknowledgement?>()
    private val requests = Mutex()
    private val writes = Mutex()
    private val modeWrites = SonyModeWriteQueue(scope, ::close)
    private var sequence = 0 // Serialized by requests; toggles only after a matching ACK.
    private var lastModeWrite = -400L
    @Volatile private var generation: SonyProtocol.Generation? = null
    @Volatile private var features: Set<Int> = emptySet()
    @Volatile private var subtype: Int? = null
    private data class Reply(val opcode: Int, val subtype: Int, val result: CompletableDeferred<ByteArray>)
    private data class Acknowledgement(val sequence: Int, val result: CompletableDeferred<Unit>)

    override fun start() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        scope.launch {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { closeSocket() }
                scope.launch(Dispatchers.IO) {
                    try { receive() } catch (_: Exception) { /* Unknown negotiation fails closed. */ }
                    finally { finish(); if (continuation.isActive) continuation.resume(Unit) }
                }
            }
        }
        scope.launch { delay(10_000); if (liveModel.get() == null) close() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun receive() {
        val uuid = serviceUuid(device) ?: throw IOException("Sony service not advertised")
        if (device.bondState != BluetoothDevice.BOND_BONDED) throw IOException("Bond required")
        val opened = device.createRfcommSocketToServiceRecord(uuid)
        if (!socket.compareAndSet(null, opened) || closed.get()) { opened.close(); return }
        opened.connect()
        ProtocolDiagnostics.note("Sony RFCOMM connected")
        scope.launch {
            try { bootstrap(uuid) } catch (_: Exception) { close() }
        }
        val decoder = SonyProtocol.Decoder()
        val buffer = ByteArray(2048)
        var previous: SonyProtocol.Frame? = null
        while (scope.isActive && !closed.get()) {
            val count = opened.inputStream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            for (frame in decoder.feed(buffer.copyOf(count))) {
                if (frame.type == SonyProtocol.ACK) {
                    acknowledgement.get()?.takeIf { it.sequence == frame.sequence }?.result?.complete(Unit)
                    continue
                }
                writeRaw(SonyProtocol.encode(SonyProtocol.Frame(SonyProtocol.ACK, 1 - frame.sequence, byteArrayOf())))
                if (previous?.sequence == frame.sequence) {
                    if (previous?.type != frame.type || previous?.payload?.contentEquals(frame.payload) != true)
                        throw IOException("Conflicting Sony sequence")
                    continue // Retransmissions are ACKed but never applied twice.
                }
                previous = frame
                if (frame.type != SonyProtocol.DATA) continue
                val payload = frame.payload
                val waiting = reply.get()
                if (payload.size >= 2 && waiting != null && payload.u(0) == waiting.opcode && payload.u(1) == waiting.subtype)
                    waiting.result.complete(payload)
                val gen = generation ?: continue
                val model = liveModel.get() ?: continue
                SonyProtocol.battery(payload, gen, model.batteryForm, SystemClock.elapsedRealtime(), features)
                    ?.let { onEvent(ProtocolEvent.Batteries(it)) }
                val noise = subtype ?: continue
                SonyProtocol.listening(payload, gen, noise)?.let { mode ->
                    settings.set(payload.copyOf())
                    onEvent(ProtocolEvent.Listening(mode))
                }
            }
        }
    }

    private suspend fun bootstrap(uuid: UUID) = requests.withLock {
        val version = queryLocked(SonyProtocol.bytes(0, 0), 1, 0) ?: throw IOException("No Sony version reply")
        val gen = SonyProtocol.generation(version) ?: throw IOException("Unknown Sony protocol")
        if ((gen == SonyProtocol.Generation.V1 && uuid != V1_UUID) ||
            (gen == SonyProtocol.Generation.V2 && uuid != V2_UUID)) throw IOException("Sony service/version mismatch")
        generation = gen
        val name = queryLocked(SonyProtocol.bytes(4, 1), 5, 1)?.let(SonyProtocol::model)
            ?: throw IOException("No verified Sony model")
        features = queryLocked(SonyProtocol.bytes(6, 0), 7, 0)?.let { SonyProtocol.features(it, gen) }
            ?: throw IOException("No verified Sony capabilities")
        subtype = SonyProtocol.noiseSubtype(gen, name, features)
        val current = subtype?.let { queryLocked(SonyProtocol.bytes(0x66, it), 0x67, it) }
        val modes = SonyProtocol.supportedModes(gen, name, features, current)
        val form = if (name.startsWith("WF-", true) || name.startsWith("LinkBuds", true))
            BatteryForm.EARBUDS else BatteryForm.HEADPHONES
        val model = ModelId.sony(name, modes, form) ?: throw IOException("Unsupported Sony identity")
        if (closed.get()) return@withLock
        settings.set(current?.copyOf())
        liveModel.set(model)
        onEvent(ProtocolEvent.Identity(model))
        if (current != null && subtype != null) SonyProtocol.listening(current, gen, subtype!!)
            ?.let { onEvent(ProtocolEvent.Listening(it)) }
        if (BuildConfig.DEBUG) ProtocolDiagnostics.note("Sony live identity/capabilities verified; generation=${gen.name}; modes=${modes.size}")
        for (query in SonyProtocol.batteryQueries(gen, form, features)) {
            if (closed.get()) break
            queryLocked(query, if (gen == SonyProtocol.Generation.V1) 0x11 else 0x23, query.u(1))
        }
        // Controls become available only once bootstrap no longer occupies the request mutex.
        if (!closed.get()) onEvent(ProtocolEvent.Ready)
    }

    override suspend fun writeMode(mode: Mode) {
        check(!closed.get() && mode in liveModel.get()?.supportedModes.orEmpty()) { "Live Sony mode required" }
        modeWrites.send(mode) { delivered ->
            onEvent(ProtocolEvent.ControlBusy(true))
            try {
                requests.withLock {
                    val model = liveModel.get() ?: throw IOException("Sony identity expired")
                    val gen = generation ?: throw IOException("Sony generation unknown")
                    val current = settings.get() ?: throw IOException("Sony settings unknown")
                    if (mode !in model.supportedModes || closed.get()) throw IOException("Unsupported Sony mode")
                    val command = SonyProtocol.writeMode(gen, model.number, features, current, mode)
                    delay((lastModeWrite + 400 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    if (closed.get() || liveModel.get() != model) throw IOException("Sony identity expired")
                    lastModeWrite = SystemClock.elapsedRealtime()
                    sendLocked(command, delivered)
                    // Confirm from device settings, never from the transport ACK or command write.
                    val noise = subtype ?: throw IOException("Sony noise subtype unknown")
                    queryLocked(SonyProtocol.bytes(0x66, noise), 0x67, noise)
                }
            } catch (error: Exception) {
                if (error !is CancellationException) close()
                throw error
            } finally { onEvent(ProtocolEvent.ControlBusy(false)) }
        }
    }

    private suspend fun queryLocked(payload: ByteArray, opcode: Int, subtype: Int): ByteArray? {
        val waiting = Reply(opcode, subtype, CompletableDeferred())
        check(reply.compareAndSet(null, waiting))
        return try {
            sendLocked(payload)
            withTimeoutOrNull(1_500) { waiting.result.await() }
        } finally { reply.compareAndSet(waiting, null) }
    }

    private suspend fun sendLocked(payload: ByteArray, written: () -> Unit = {}) {
        if (closed.get()) throw IOException("Sony session closed")
        val waiting = Acknowledgement(1 - sequence, CompletableDeferred())
        check(acknowledgement.compareAndSet(null, waiting))
        try {
            writeRaw(SonyProtocol.encode(SonyProtocol.Frame(SonyProtocol.DATA, sequence, payload)))
            written()
            if (withTimeoutOrNull(1_500) { waiting.result.await(); true } != true) throw IOException("Sony transport ACK missing")
            sequence = 1 - sequence
        } finally { acknowledgement.compareAndSet(waiting, null) }
    }

    private suspend fun writeRaw(bytes: ByteArray): Unit = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { closeSocket() }
        scope.launch(Dispatchers.IO) {
            try {
                writes.withLock {
                    val active = socket.get() ?: throw IOException("Sony socket closed")
                    if (closed.get()) throw IOException("Sony session closed")
                    active.outputStream.write(bytes)
                    active.outputStream.flush()
                }
                if (continuation.isActive) continuation.resume(Unit)
            } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
        }
    }

    override fun close() { finish() }
    private fun closeSocket() {
        closed.set(true)
        liveModel.set(null)
        settings.getAndSet(null)?.fill(0)
        reply.getAndSet(null)?.result?.cancel()
        acknowledgement.getAndSet(null)?.result?.cancel()
        runCatching { socket.getAndSet(null)?.close() }
    }
    private fun finish() {
        closeSocket()
        scope.cancel()
        if (finished.compareAndSet(false, true)) onClosed()
    }

    companion object {
        private val V1_UUID = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba")
        private val V2_UUID = UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2")
        @SuppressLint("MissingPermission")
        fun serviceUuid(device: BluetoothDevice): UUID? = runCatching {
            val advertised = device.uuids.orEmpty().map { it.uuid }
            listOf(V2_UUID, V1_UUID).firstOrNull { it in advertised }
        }.getOrNull()
        private fun ByteArray.u(index: Int) = this[index].toInt() and 255
    }
}

/** Coalesce a repository retry while the original SET/ACK/readback still owns the channel. */
internal class SonyModeWriteQueue(private val scope: CoroutineScope, private val onCancelled: () -> Unit) {
    private class Request(val mode: Mode, parent: Job?) {
        val delivered = CompletableDeferred<Unit>(parent)
        val finished = CompletableDeferred<Unit>(parent)
    }
    private val active = AtomicReference<Request?>()

    suspend fun send(mode: Mode, operation: suspend (() -> Unit) -> Unit) {
        var awaitingPhysicalDelivery = false
        try {
            while (true) {
                val previous = active.get()
                if (previous != null) {
                    if (previous.mode == mode) {
                        awaitingPhysicalDelivery = true
                        previous.delivered.await()
                        return
                    }
                    // This caller owns no operation yet; cancelling it must leave the active one intact.
                    previous.finished.await()
                    continue
                }
                val request = Request(mode, scope.coroutineContext[Job])
                if (!active.compareAndSet(null, request)) {
                    request.delivered.cancel(); request.finished.cancel()
                    continue
                }
                awaitingPhysicalDelivery = true
                scope.launch {
                    try { operation { request.delivered.complete(Unit) } }
                    catch (error: Exception) { request.delivered.completeExceptionally(error) }
                    finally {
                        active.compareAndSet(request, null)
                        request.finished.complete(Unit)
                    }
                }
                request.delivered.await()
                return
            }
        } catch (cancelled: CancellationException) {
            if (awaitingPhysicalDelivery) onCancelled()
            throw cancelled
        }
    }
}
