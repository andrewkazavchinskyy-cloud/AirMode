package app.airmode.domain

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
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
    private val recent = mutableMapOf<String, Long>()
    private var selected: BluetoothDevice? = null
    private var session: Session? = null
    private var generation = 0L
    private var command: Job? = null
    private var lastSend = -400L
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
                    BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { connected[it.address] = it; recent[it.address] = SystemClock.elapsedRealtime() }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { connected.remove(it.address) }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (device?.bondState != BluetoothDevice.BOND_BONDED) device?.let { confirmed.remove(it.address) }
                    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> if (device != null && device.address == selected?.address) readMetadata(device)
                }
                refresh()
            }
        }
    }

    fun start() = refresh()
    fun refresh() {
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
            discover()
            selected?.let { readMetadata(it); scanWindow() }
        }
    }
    private fun hasBluetoothPermission() = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun discover() {
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
            if (state.value.problem == Problem.SERVICE_UNAVAILABLE && session == null && !connecting && AirModeService.start(context)) {
                mutable.value = state.value.copy(problem = Problem.MODEL_PENDING)
                openSession(preferred)
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
        readMetadata(preferred)
        // Session must be owned by the connected-device FGS before opening transport.
        if (AirModeService.start(context)) openSession(preferred) else mutable.value = mutable.value.copy(problem = Problem.SERVICE_UNAVAILABLE)
        scanWindow()
    }
    private fun candidate(device: BluetoothDevice): Boolean {
        if (confirmed.containsKey(device.address)) return true
        val model = metadata(device, 1)?.decodeToString()
        if (ModelId.fromNumber(model.orEmpty()) != null) return true
        val name = device.name.orEmpty()
        // Names select candidates only; never authorize noise writes.
        if (name.contains("AirPods", ignoreCase = true)) return true
        // A bonded renamed audio accessory exposing Apple's accessory SDP UUID can be queried
        // for identity. The UUID is not proof of a supported model.
        val audio = device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        return audio && device.uuids.orEmpty().any { it.uuid.toString().equals("00000000-deca-fade-deca-deafdecacaff", true) }
    }
    private fun openSession(device: BluetoothDevice) {
        if (session != null || connecting) return
        connecting = true
        val token = ++generation
        val current = Session(device, scope, { event -> scope.launch {
            if (token != generation || device.address != selected?.address) return@launch
            when (event) {
                is ProtocolEvent.Model -> {
                    val model = ModelId.fromNumber(event.number)
                    if (model == null) {
                        confirmed.remove(device.address)
                        mutable.value = mutable.value.copy(connection = ConnectionState.UnsupportedModel, model = null, mode = null, battery = Battery(), problem = null)
                        session?.close(); session = null; connecting = false
                    } else {
                        confirmed[device.address] = model
                        mutable.value = mutable.value.copy(model = model, name = event.name ?: state.value.name, problem = null)
                        updateReady()
                    }
                }
                is ProtocolEvent.Batteries -> mutable.value = mutable.value.copy(battery = state.value.battery.merge(event.battery))
                is ProtocolEvent.Listening -> {
                    mutable.value = mutable.value.copy(mode = event.mode)
                    val target = (state.value.connection as? ConnectionState.Switching)?.target
                    if (target == event.mode) { command?.cancel(); command = null; mutable.value = mutable.value.copy(connection = ConnectionState.SessionReady, problem = null) }
                    updateReady()
                }
                else -> Unit
            }
        } }, { scope.launch {
            if (token == generation) {
                connecting = false; session = null; command?.cancel(); command = null
                if (state.value.connection != ConnectionState.UnsupportedModel) mutable.value = mutable.value.copy(
                    connection = ConnectionState.ProtocolUnavailable, problem = Problem.PROTOCOL_UNAVAILABLE)
            }
        } })
        session = current; current.start()
        scope.launch {
            delay(6000)
            if (token == generation && state.value.connection == ConnectionState.ConnectedNoSession) {
                current.close(); session = null; connecting = false
                mutable.value = mutable.value.copy(connection = ConnectionState.ProtocolUnavailable, problem = Problem.PROTOCOL_UNAVAILABLE)
            }
        }
    }
    private fun updateReady() {
        val s = state.value
        if (s.model != null && (!s.model.anc || s.mode != null) && s.connection == ConnectionState.ConnectedNoSession) {
            connecting = false; mutable.value = s.copy(connection = ConnectionState.SessionReady, problem = null)
        }
    }
    fun switchMode(mode: Mode) {
        scope.launch {
            val s = state.value
            if (!s.canSwitch || session == null || !hasBluetoothPermission()) return@launch
            val now = SystemClock.elapsedRealtime()
            if (now - lastSend < 400) return@launch
            val token = generation
            val active = session ?: return@launch
            mutable.value = s.copy(connection = ConnectionState.Switching(mode), problem = null)
            command = scope.launch {
                val unanswered = sendModeRequest(
                    send = { active.writeMode(mode); lastSend = SystemClock.elapsedRealtime() },
                    pending = { token == generation && (state.value.connection as? ConnectionState.Switching)?.target == mode },
                    now = SystemClock::elapsedRealtime,
                )
                if (unanswered) mutable.value = state.value.copy(connection = ConnectionState.SessionReady, problem = Problem.NO_REPLY)
            }
        }
    }
    fun serviceStopped() {
        generation++; command?.cancel(); command = null; session?.close(); session = null; connecting = false
        if (state.value.connected && state.value.connection != ConnectionState.UnsupportedModel) mutable.value = state.value.copy(
            connection = ConnectionState.ConnectedNoSession, problem = Problem.SERVICE_UNAVAILABLE)
    }
    fun clearProblem() { mutable.value = mutable.value.copy(problem = null) }
    private fun disconnect() {
        generation++; command?.cancel(); command = null; session?.close(); session = null; connecting = false
        selected = null; stopScan()
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
            // getMetadata returns a cache with no observation timestamp. Reopening the UI
            // must not make unchanged cached case data appear freshly observed.
            return if (old.percent == level && old.charging == charging) old else BatteryReading(level, charging, now)
        }
        // A single aggregate level has no known left/right attribution and is never duplicated.
        runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) }
        mutable.value = state.value.copy(battery = state.value.battery.merge(Battery(reading(10,13, state.value.battery.left), reading(11,14, state.value.battery.right), reading(12,15, state.value.battery.case))))
    }
    private fun scanWindow() {
        val device = selected ?: return
        if (!hasBluetoothPermission() || scanJob?.isActive == true || SystemClock.elapsedRealtime() - lastScan < 15000) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        lastScan = SystemClock.elapsedRealtime()
        val token = generation
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Random BLE addresses cannot safely be attributed by RSSI/name alone.
                if (result.device.address != device.address || token != generation) return
                val bytes = result.scanRecord?.getManufacturerSpecificData(0x004c) ?: return
                val battery = AdvertParser.parse(bytes, SystemClock.elapsedRealtime()) ?: return
                scope.launch { if (token == generation) mutable.value = state.value.copy(battery = state.value.battery.merge(battery)) }
            }
            override fun onScanFailed(errorCode: Int) { scope.launch { stopScan() } }
        }
        scanCallback = callback
        try {
            scanner.startScan(listOf(ScanFilter.Builder().setManufacturerData(0x004c, byteArrayOf()).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
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
