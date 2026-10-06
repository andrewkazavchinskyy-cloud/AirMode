package app.airmode.domain

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import app.airmode.BuildConfig
import app.airmode.bluetooth.*
import app.airmode.data.SettingsStore
import app.airmode.service.AirModeService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Application-scoped owner of the one selected device, session and confirmed state. */
@SuppressLint("MissingPermission")
class Repository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(context, scope)
    private val mutable = MutableStateFlow(DeviceState())
    val state = mutable.asStateFlow()
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val profiles = mutableMapOf<Int, BluetoothProfile>()
    private val connected = linkedMapOf<String, BluetoothDevice>()
    private val confirmed = mutableMapOf<String, ModelId>()
    private val rejected = mutableSetOf<String>()
    private val recent = mutableMapOf<String, Long>()
    private val sdpRequested = mutableSetOf<String>()
    private var selected: BluetoothDevice? = null
    private var metadataBattery = Battery()
    private var session: ControlSession? = null
    private var proximity: AppleProximity? = null
    private var protocolModelConfirmed = false
    private var transportReady = false
    private var generation = 0L
    private var pairEpoch = 0L
    private var command: Job? = null
    private var requestId = 0L
    private var lastScan = -15_000L
    private var scanJob: Job? = null
    private var scanCallback: ScanCallback? = null
    private var initialized = false
    private var connecting = false
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            scope.launch { profiles[profile] = proxy; discover() }
        }
        override fun onServiceDisconnected(profile: Int) {
            scope.launch { profiles.remove(profile); discover() }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            scope.launch {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { rejected.remove(it.address); sdpRequested.remove(it.address); connected[it.address] = it; recent[it.address] = SystemClock.elapsedRealtime() }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { connected.remove(it.address); sdpRequested.remove(it.address) }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (device?.bondState != BluetoothDevice.BOND_BONDED) device?.let { confirmed.remove(it.address) }
                    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> if (device != null && device.address == selected?.address) {
                        readMetadata(device)
                        // A live aggregate report belongs to a headset, never both earbuds.
                        val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                        if (state.value.model?.batteryForm == BatteryForm.HEADPHONES && level in 0..100)
                            mutable.value = state.value.copy(battery = state.value.battery.merge(Battery(
                                headset = BatteryReading(level, updatedAt = SystemClock.elapsedRealtime(), source = BatterySource.METADATA))))
                    }
                }
                refresh()
            }
        }
    }

    fun start() = refresh()
    fun retryControl(scan: Boolean = false) = refresh(retryProtocol = true, scan = scan)
    fun refresh(retryProtocol: Boolean = false, scan: Boolean = false) {
        scope.launch {
            if (!hasBluetoothPermission()) {
                disconnect(); mutable.value = mutable.value.copy(permissionGranted = false, problem = Problem.PERMISSION); return@launch
            }
            mutable.value = mutable.value.copy(permissionGranted = true, bluetoothEnabled = adapter?.isEnabled == true)
            if (adapter?.isEnabled != true) { disconnect(); return@launch }
            if (!initialized) {
                initialized = true
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED); addAction(BluetoothDevice.ACTION_UUID)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED); addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                    addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
                }
                // Bluetooth system broadcasts can originate in the privileged Bluetooth process.
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                else context.registerReceiver(receiver, filter)
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
            }
            discover(retryProtocol)
            selected?.let { readMetadata(it); if (scan) scanWindow() }
        }
    }
    private fun hasBluetoothPermission() = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun discover(retryProtocol: Boolean = false) {
        if (!hasBluetoothPermission() || adapter?.isEnabled != true) { disconnect(); return }
        val actual = profiles.values.flatMap { runCatching { it.connectedDevices }.getOrDefault(emptyList()) }
        if (profiles.isNotEmpty()) { connected.clear(); actual.forEach { connected[it.address] = it } }
        val candidates = connected.values.filter { it.bondState == BluetoothDevice.BOND_BONDED && candidate(it) }
        val preferred = candidates.maxWithOrNull(compareBy<BluetoothDevice> { confirmed[it.address] != null }
            .thenBy { recent[it.address] ?: 0L })
        if (preferred == null) {
            disconnect()
            if (connected.isNotEmpty()) mutable.value = mutable.value.copy(connection = ConnectionState.UnsupportedModel,
                name = connected.values.last().name)
            return
        }
        if (preferred.address == selected?.address) {
            val retry = state.value.problem == Problem.SERVICE_UNAVAILABLE ||
                (retryProtocol && state.value.connection == ConnectionState.ProtocolUnavailable) ||
                state.value.connection == ConnectionState.ConnectedNoSession
            if (retry && session == null && !connecting) {
                mutable.value = state.value.copy(connection = ConnectionState.ConnectedNoSession,
                    model = null, mode = null, problem = Problem.MODEL_PENDING)
                if (AirModeService.foregroundReady) openSession(preferred)
                else if (AirModeService.start(context)) return
                else mutable.value = state.value.copy(problem = Problem.SERVICE_UNAVAILABLE)
            }
            return
        }
        disconnect()
        selected = preferred
        recent[preferred.address] = SystemClock.elapsedRealtime()
        val model = confirmed[preferred.address] ?: ModelId.fromNumber(metadata(preferred, 1)?.decodeToString().orEmpty())
        if (model != null) confirmed[preferred.address] = model
        mutable.value = DeviceState(ConnectionState.ConnectedNoSession, preferred.name, model,
            problem = Problem.MODEL_PENDING, permissionGranted = true, bluetoothEnabled = true, connectionId = generation)
        ProtocolDiagnostics.note("connected pair selected")
        readMetadata(preferred)
        // Session must be owned by the connected-device FGS before opening transport.
        if (AirModeService.foregroundReady) openSession(preferred)
        else if (!AirModeService.start(context)) mutable.value = mutable.value.copy(problem = Problem.SERVICE_UNAVAILABLE)
        scanWindow()
    }
    private fun candidate(device: BluetoothDevice): Boolean {
        if (device.address in rejected) return false
        if (confirmed.containsKey(device.address)) return true
        val model = metadata(device, 1)?.decodeToString()
        if (ModelId.fromNumber(model.orEmpty()) != null) return true
        val name = device.name.orEmpty()
        // Names select candidates only; never authorize noise writes.
        if (name.contains("AirPods", ignoreCase = true)) return true
        if (SonySession.serviceUuid(device) != null) return true
        if (listOf("WH-", "WF-", "WI-", "MDR-", "LinkBuds").any { name.startsWith(it, true) } ||
            metadata(device, 0)?.decodeToString()?.contains("Sony", true) == true) {
            // Only SDP service discovery; a name never authorizes a vendor command.
            if (sdpRequested.add(device.address)) runCatching { device.fetchUuidsWithSdp() }
            return SonySession.serviceUuid(device) != null
        }
        // A bonded renamed audio accessory exposing Apple's accessory SDP UUID can be queried
        // for identity. The UUID is not proof of a supported model.
        val audio = device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        return audio && device.uuids.orEmpty().any { it.uuid.toString().equals("00000000-deca-fade-deca-deafdecacaff", true) }
    }
    private fun openSession(device: BluetoothDevice) {
        if (session != null || connecting) return
        connecting = true
        protocolModelConfirmed = false
        transportReady = false
        val token = ++generation
        fun identity(model: ModelId?, name: String?) {
            if (model == null) {
                confirmed.remove(device.address)
                rejected.add(device.address)
                mutable.value = mutable.value.copy(connection = ConnectionState.UnsupportedModel, model = null,
                    mode = null, pendingMode = null, controlBusy = false, battery = Battery(), problem = null)
                session?.close(); session = null; connecting = false
                proximity?.close(); proximity = null
                refresh()
            } else {
                protocolModelConfirmed = true
                confirmed[device.address] = model
                mutable.value = mutable.value.copy(model = model, name = name ?: state.value.name, problem = null)
                updateReady()
            }
        }
        val events: (ProtocolEvent) -> Unit = { event -> scope.launch {
            if (token != generation || device.address != selected?.address) {
                if (event is ProtocolEvent.ProximityKeys) event.keys.close()
                return@launch
            }
            when (event) {
                is ProtocolEvent.Model -> identity(ModelId.fromNumber(event.number), event.name)
                is ProtocolEvent.Identity -> identity(event.model, event.name)
                is ProtocolEvent.ProximityKeys -> {
                    if (!protocolModelConfirmed || state.value.model?.vendor != DeviceVendor.APPLE) event.keys.close()
                    else {
                        proximity?.close(); proximity = event.keys
                        if (BuildConfig.DEBUG) ProtocolDiagnostics.note("proximity keys received; identity=${event.keys.hasIrk} decryption=${event.keys.hasEncryption}")
                    }
                }
                is ProtocolEvent.Batteries -> mutable.value = mutable.value.copy(battery = state.value.battery.merge(event.battery))
                is ProtocolEvent.Listening -> {
                    mutable.value = mutable.value.copy(mode = event.mode)
                    if (state.value.problem == Problem.NO_REPLY && state.value.failedMode == event.mode)
                        clearProblem() // A late real report supersedes the earlier timeout.
                    val target = state.value.pendingMode
                    // An ACK can arrive before the suspended write resumes. Let that write
                    // finish; cancelling it would close the successful native socket.
                    if (target == event.mode) mutable.value = mutable.value.copy(connection = ConnectionState.SessionReady,
                        pendingMode = null, problem = null, failedMode = null)
                    updateReady()
                }
                ProtocolEvent.Ready -> { transportReady = true; updateReady() }
                is ProtocolEvent.ControlBusy -> mutable.value = mutable.value.copy(controlBusy = event.busy)
                else -> Unit
            }
        } }
        val closed: () -> Unit = { scope.launch {
            if (token == generation) {
                connecting = false; session = null; command?.cancel(); command = null
                proximity?.close(); proximity = null
                protocolModelConfirmed = false
                transportReady = false
                if (state.value.connection != ConnectionState.UnsupportedModel) mutable.value = mutable.value.copy(
                    connection = ConnectionState.ProtocolUnavailable, pendingMode = null, controlBusy = false, problem = Problem.PROTOCOL_UNAVAILABLE)
            }
        } }
        val current: ControlSession = if (SonySession.serviceUuid(device) != null)
            SonySession(device, scope, events, closed) else Session(device, scope, events, closed)
        session = current; current.start()
        scope.launch {
            // Sony negotiates identity, capabilities and battery requests on one ordered channel.
            delay(if (current is SonySession) 10_000 else 6000)
            if (token == generation && state.value.connection == ConnectionState.ConnectedNoSession) {
                current.close(); session = null; connecting = false
                mutable.value = mutable.value.copy(connection = ConnectionState.ProtocolUnavailable, problem = Problem.PROTOCOL_UNAVAILABLE)
            }
        }
    }
    private fun updateReady() {
        val s = state.value
        if (transportReady && protocolModelConfirmed && s.model != null &&
            (s.model.supportedModes.isEmpty() || s.mode != null) && s.connection == ConnectionState.ConnectedNoSession) {
            connecting = false; mutable.value = s.copy(connection = ConnectionState.SessionReady, problem = null)
        }
    }
    fun switchMode(mode: Mode) {
        scope.launch {
            val s = state.value
            if (!s.canSwitch || !protocolModelConfirmed || session == null || !hasBluetoothPermission() || mode !in s.model!!.supportedModes) return@launch
            if (s.mode == mode) { clearProblem(); return@launch }
            val token = generation
            val active = session ?: return@launch
            val request = ++requestId
            mutable.value = s.copy(connection = ConnectionState.Switching(mode), pendingMode = mode, problem = null, failedMode = null)
            command = scope.launch {
                val unanswered = sendModeRequest(
                    // Session waits for the physical 400 ms interval instead of silently
                    // dropping a fast second tap after the previous mode was acknowledged.
                    send = { active.writeMode(mode) },
                    pending = { token == generation && request == requestId &&
                        state.value.pendingMode == mode },
                    now = SystemClock::elapsedRealtime,
                    awaitingConfirmation = {
                        if (token == generation && request == requestId && state.value.pendingMode == mode)
                            mutable.value = state.value.copy(connection = ConnectionState.SessionReady)
                    },
                )
                if (unanswered) mutable.value = state.value.copy(connection = ConnectionState.SessionReady,
                    pendingMode = null, problem = Problem.NO_REPLY, failedMode = mode)
                if (unanswered && BuildConfig.DEBUG) ProtocolDiagnostics.note("mode request timed out target=${mode.code}; confirmed=${state.value.mode?.code}")
                if (request == requestId) command = null
            }
        }
    }
    fun serviceStopped() {
        generation++; command?.cancel(); command = null; session?.close(); session = null; connecting = false; protocolModelConfirmed = false
        transportReady = false
        proximity?.close(); proximity = null
        if (state.value.connected && state.value.connection != ConnectionState.UnsupportedModel) mutable.value = state.value.copy(
            connection = ConnectionState.ConnectedNoSession, pendingMode = null, controlBusy = false, problem = Problem.SERVICE_UNAVAILABLE)
    }
    fun clearProblem() { mutable.value = mutable.value.copy(problem = null, failedMode = null) }
    private fun disconnect() {
        pairEpoch++
        generation++; command?.cancel(); command = null; session?.close(); session = null; connecting = false; protocolModelConfirmed = false
        transportReady = false
        proximity?.close(); proximity = null
        selected = null; metadataBattery = Battery(); stopScan()
        mutable.value = DeviceState(permissionGranted = hasBluetoothPermission(), bluetoothEnabled = hasBluetoothPermission() && adapter?.isEnabled == true)
    }
    private fun metadata(device: BluetoothDevice, key: Int): ByteArray? = runCatching {
        BluetoothDevice::class.java.getMethod("getMetadata", Int::class.javaPrimitiveType).invoke(device, key) as? ByteArray
    }.getOrNull()
    private fun readMetadata(device: BluetoothDevice) {
        if (device.address != selected?.address || !hasBluetoothPermission()) return
        val now = SystemClock.elapsedRealtime()
        fun reading(key: Int, chargingKey: Int, old: BatteryReading): BatteryReading {
            val level = metadata(device, key)?.decodeToString()?.toIntOrNull()?.takeIf { it in 0..100 }
            if (level == null) return BatteryReading()
            val charging = metadata(device, chargingKey)?.decodeToString() == "true"
            return cachedMetadataReading(level, charging, old, now)
        }
        // Cached aggregate data has no known left/right attribution and is never duplicated.
        val aggregate = runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as? Int }.getOrNull()
        val headset = if (state.value.model?.batteryForm == BatteryForm.HEADPHONES && aggregate in 0..100)
            cachedMetadataReading(aggregate!!, false, metadataBattery.headset, now) else BatteryReading()
        val readings = Battery(reading(10,13, metadataBattery.left), reading(11,14, metadataBattery.right), reading(12,15, metadataBattery.case), headset)
        metadataBattery = metadataBattery.merge(readings)
        mutable.value = state.value.copy(battery = state.value.battery.merge(readings))
        ProtocolDiagnostics.metadata(readings)
    }
    private fun scanWindow() {
        val device = selected ?: return
        if (state.value.model?.vendor == DeviceVendor.SONY || SonySession.serviceUuid(device) != null) return
        if (!hasBluetoothPermission() || scanJob?.isActive == true || SystemClock.elapsedRealtime() - lastScan < 15000) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        lastScan = SystemClock.elapsedRealtime()
        // A control session can start/reconnect during this same four-second scan.
        // Only changing the selected pair invalidates its advertisement callbacks.
        val token = pairEpoch
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Random BLE addresses cannot safely be attributed by RSSI/name alone.
                if (token != pairEpoch || selected?.address != device.address) return
                val bytes = result.scanRecord?.getManufacturerSpecificData(0x004c) ?: return
                val direct = result.device.address == device.address
                val keys = proximity
                val battery = keys?.decode(result.device.address, bytes, SystemClock.elapsedRealtime())
                    ?: if (direct) AdvertParser.parse(bytes, SystemClock.elapsedRealtime()) else null
                val matched = direct || keys?.matchesAddress(result.device.address) == true || battery != null
                ProtocolDiagnostics.advertisement(matched, battery != null,
                    bytes.size == 19 && bytes.u(0) == 7 && bytes.u(1) == 0x11)
                if (battery == null) return
                if (BuildConfig.DEBUG) ProtocolDiagnostics.note("BLE battery accepted; case=${ProtocolDiagnostics.readingSummary(battery.case)}")
                scope.launch { if (token == pairEpoch && selected?.address == device.address)
                    mutable.value = state.value.copy(battery = state.value.battery.merge(battery)) }
            }
            override fun onScanFailed(errorCode: Int) {
                if (BuildConfig.DEBUG) ProtocolDiagnostics.note("BLE scan failed code=$errorCode")
                scope.launch { stopScan() }
            }
        }
        scanCallback = callback
        try {
            scanner.startScan(listOf(ScanFilter.Builder().setManufacturerData(0x004c, byteArrayOf()).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            ProtocolDiagnostics.note("BLE scan window started")
            scanJob = scope.launch { delay(4000); stopScan() }
        } catch (_: SecurityException) { stopScan() }
        catch (_: IllegalStateException) { stopScan() }
    }
    private fun stopScan() {
        scanCallback?.let { if (hasBluetoothPermission()) runCatching { adapter?.bluetoothLeScanner?.stopScan(it) } }
        scanCallback = null; scanJob?.cancel(); scanJob = null
    }
    companion object {
        // The singleton stores applicationContext only and intentionally lives for the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }
    }
}
