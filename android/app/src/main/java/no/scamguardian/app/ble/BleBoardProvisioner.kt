package no.scamguardian.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import no.scamguardian.app.board.BoardJson

/**
 * Android BLE for the board's setup service. Untested on hardware in this repo (no board or phone
 * was available); written against the platform APIs for minSdk 26 to 36.
 *
 * Android allows one GATT operation at a time, so every read/write goes through [BleSession.op],
 * which waits for its callback before the next starts.
 */
class BleBoardProvisioner(private val context: Context) : BoardProvisioner {

    private val manager get() = context.getSystemService(BluetoothManager::class.java)

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothOn(): Boolean = manager?.adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<FoundBoard>> = callbackFlow {
        if (!hasPermissions()) throw ProvisionException.MissingPermission()
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) throw ProvisionException.BluetoothOff()
        val scanner = adapter.bluetoothLeScanner ?: throw ProvisionException.BluetoothOff()
        val found = linkedMapOf<String, FoundBoard>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = add(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::add)
            override fun onScanFailed(errorCode: Int) {
                close(ProvisionException.Gatt("scan", errorCode))
            }

            private fun add(r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: "Guardian"
                found[r.device.address] = FoundBoard(r.device.address, name, r.rssi)
                trySend(found.values.sortedByDescending { it.rssi })
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(GuardianGatt.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(board: FoundBoard): ProvisioningSession {
        if (!hasPermissions()) throw ProvisionException.MissingPermission()
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) throw ProvisionException.BluetoothOff()
        val session = BleSession(context, adapter.getRemoteDevice(board.address))
        try {
            session.open()
        } catch (e: Throwable) {
            session.close()
            throw e
        }
        return session
    }

    companion object {
        fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

@SuppressLint("MissingPermission")
private class BleSession(
    private val context: Context,
    private val device: BluetoothDevice,
) : ProvisioningSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val statusFlow = MutableSharedFlow<SetupStatus>(replay = 1, extraBufferCapacity = 32)
    override val status: Flow<SetupStatus> = statusFlow.asSharedFlow()

    private class OpResult(val status: Int, val value: ByteArray?)

    private val opLock = Mutex()
    @Volatile private var pending: CompletableDeferred<OpResult>? = null
    @Volatile private var connected = CompletableDeferred<Unit>()
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var closed = false
    private lateinit var commandChar: BluetoothGattCharacteristic
    private lateinit var statusChar: BluetoothGattCharacteristic
    private lateinit var networksChar: BluetoothGattCharacteristic

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected.completeExceptionally(ProvisionException.ConnectFailed("status $status"))
                pending?.completeExceptionally(ProvisionException.Disconnected())
                if (!closed) statusFlow.tryEmit(SetupStatus(state = SetupStatus.DISCONNECTED))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            pending?.complete(OpResult(status, null))
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pending?.complete(OpResult(status, null))
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            pending?.complete(OpResult(status, value))
        }

        @Deprecated("Used before API 33")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            pending?.complete(OpResult(status, c.value))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pending?.complete(OpResult(status, null))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pending?.complete(OpResult(status, null))
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == GuardianGatt.STATUS) onStatusBytes(value)
        }

        @Deprecated("Used before API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (c.uuid == GuardianGatt.STATUS) onStatusBytes(c.value ?: return)
        }
    }

    suspend fun open() {
        // The board forgets every pairing when it enters setup mode (each session has
        // a new passkey). An old bond on the phone would make Android encrypt with keys
        // the board no longer has, and drop the link before service discovery.
        clearStaleBond()
        connectGatt()
        // MTU 247 so a whole status JSON fits in one notification. Not fatal if refused.
        runCatching { op("mtu", 5_000) { it.requestMtu(GuardianGatt.MTU) } }
        val discovered = try {
            op("discover", 15_000) { it.discoverServices() }
        } catch (e: ProvisionException) {
            // Dropped right after connecting while still bonded: the old-pairing case.
            if (device.bondState == BluetoothDevice.BOND_BONDED) throw ProvisionException.StaleBond()
            throw e
        }
        if (discovered.status != BluetoothGatt.GATT_SUCCESS) throw ProvisionException.Gatt("discover", discovered.status)
        val service = gatt?.getService(GuardianGatt.SERVICE) ?: throw ProvisionException.NotAGuardian()
        commandChar = service.getCharacteristic(GuardianGatt.COMMAND) ?: throw ProvisionException.NotAGuardian()
        statusChar = service.getCharacteristic(GuardianGatt.STATUS) ?: throw ProvisionException.NotAGuardian()
        networksChar = service.getCharacteristic(GuardianGatt.NETWORKS) ?: throw ProvisionException.NotAGuardian()

        // Every characteristic needs an encrypted, MITM-protected link: bond first. Android shows
        // its pairing dialog, where the user types the 6-digit passkey from the board's screen.
        ensureBonded()

        gatt?.setCharacteristicNotification(statusChar, true)
        val cccd = statusChar.getDescriptor(GuardianGatt.CCCD) ?: throw ProvisionException.NotAGuardian()
        withAuthRetry("subscribe") { writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
        statusFlow.emit(readStatus())
    }

    private suspend fun connectGatt() {
        repeat(2) { attempt ->
            connected = CompletableDeferred()
            gatt?.close()
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            try {
                withTimeout(15_000) { connected.await() }
                return
            } catch (e: TimeoutCancellationException) {
                if (attempt == 1) throw ProvisionException.ConnectFailed("timeout")
            } catch (e: ProvisionException) {
                if (attempt == 1) throw e
                delay(600) // status 133 on the first try is common; one retry helps
            }
        }
    }

    /** Removes this phone's bond with the board, if any. removeBond() is not public API,
     *  so it is called by reflection; if that is not allowed, the user is asked to do it. */
    private suspend fun clearStaleBond() {
        if (device.bondState != BluetoothDevice.BOND_BONDED) return
        val gone = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR) == BluetoothDevice.BOND_NONE) {
                    gone.complete(Unit)
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            val started = runCatching {
                device.javaClass.getMethod("removeBond").invoke(device) as? Boolean ?: false
            }.getOrDefault(false)
            if (!started) throw ProvisionException.StaleBond()
            withTimeoutOrNull(5_000) { gone.await() }
            if (device.bondState == BluetoothDevice.BOND_BONDED) throw ProvisionException.StaleBond()
            delay(300)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private suspend fun ensureBonded() {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        val result = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (d?.address != device.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> result.complete(true)
                    BluetoothDevice.BOND_NONE -> result.complete(false)
                }
            }
        }
        // ACTION_BOND_STATE_CHANGED is a protected system broadcast.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) return
            if (device.bondState != BluetoothDevice.BOND_BONDING && !device.createBond()) {
                throw ProvisionException.PairingFailed()
            }
            val ok = withTimeoutOrNull(120_000) { result.await() } ?: false
            if (!ok) throw ProvisionException.PairingFailed()
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** Runs one GATT operation and waits for its callback. */
    private suspend fun op(name: String, timeoutMs: Long, start: (BluetoothGatt) -> Boolean): OpResult = opLock.withLock {
        val g = gatt ?: throw ProvisionException.Disconnected()
        val d = CompletableDeferred<OpResult>()
        pending = d
        try {
            if (!start(g)) throw ProvisionException.Gatt(name, -1)
            withTimeout(timeoutMs) { d.await() }
        } catch (e: TimeoutCancellationException) {
            throw ProvisionException.Timeout(name)
        } finally {
            pending = null
        }
    }

    /** Retries an operation that failed because the link was not encrypted yet (bonding still finishing). */
    private suspend fun withAuthRetry(name: String, block: suspend () -> OpResult): OpResult {
        var last: OpResult? = null
        repeat(4) {
            val r = block()
            if (r.status == BluetoothGatt.GATT_SUCCESS) return r
            last = r
            if (r.status !in AUTH_ERRORS) throw ProvisionException.Gatt(name, r.status)
            delay(1_500)
        }
        if (last?.status in AUTH_ERRORS) throw ProvisionException.PairingFailed()
        throw ProvisionException.Gatt(name, last?.status ?: -1)
    }

    private suspend fun read(c: BluetoothGattCharacteristic): ByteArray =
        withAuthRetry("read") { op("read", 10_000) { it.readCharacteristic(c) } }.value ?: ByteArray(0)

    private suspend fun readStatus(): SetupStatus = parseStatus(read(statusChar))
        ?: throw ProvisionException.Gatt("status", -2)

    private suspend fun writeDescriptor(d: BluetoothGattDescriptor, value: ByteArray): OpResult =
        op("descriptor", 10_000) { g ->
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                d.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(d)
            }
        }

    override suspend fun send(command: SetupCommand) {
        val bytes = command.toJson().toString().toByteArray(Charsets.UTF_8)
        // Write with response; Android does a long (prepared) write when the value is over MTU - 3.
        withAuthRetry("write") {
            op("write", 15_000) { g ->
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(commandChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    commandChar.value = bytes
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(commandChar)
                }
            }
        }
    }

    override suspend fun readNetworks(): List<WifiNetwork> {
        val text = read(networksChar).toString(Charsets.UTF_8)
        return runCatching { BoardJson.decodeFromString(ListSerializer(WifiNetwork.serializer()), text) }
            .getOrDefault(emptyList())
            .filter { it.ssid.isNotEmpty() }
    }

    private fun onStatusBytes(value: ByteArray) {
        val parsed = parseStatus(value)
        if (parsed != null) {
            statusFlow.tryEmit(parsed)
        } else {
            // A notification is cut to MTU - 3 bytes if the MTU request was refused; the
            // characteristic read is a long read and returns the whole value.
            scope.launch { runCatching { statusFlow.emit(readStatus()) } }
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    companion object {
        /** INSUFFICIENT_AUTHENTICATION, INSUFFICIENT_AUTHORIZATION, INSUFFICIENT_ENCRYPTION, GATT_AUTH_FAIL. */
        private val AUTH_ERRORS = setOf(5, 8, 15, 137)

        fun parseStatus(bytes: ByteArray): SetupStatus? = runCatching {
            BoardJson.decodeFromString(SetupStatus.serializer(), bytes.toString(Charsets.UTF_8))
        }.getOrNull()
    }
}
