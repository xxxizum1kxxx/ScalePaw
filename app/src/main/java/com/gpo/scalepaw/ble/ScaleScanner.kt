package com.gpo.scalepaw.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.gpo.scalepaw.ble.openscale.ScaleFactory
import com.gpo.scalepaw.ble.openscale.ScannedDeviceInfo
import com.gpo.scalepaw.ble.openscale.scales.DeviceSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A device seen while looking for scales, with the driver that would handle it. */
data class DiscoveredScale(
    val info: ScannedDeviceInfo,
    val support: DeviceSupport?,
    val device: BluetoothDevice?,
) {
    val address: String get() = info.address
    val name: String get() = info.name
    val isSupported: Boolean get() = support != null
}

/** Finds nearby scales (discovery) and single known ones (before connecting). */
@SuppressLint("MissingPermission")
class ScaleScanner(context: Context, private val scope: CoroutineScope) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _devices = MutableStateFlow<List<DiscoveredScale>>(emptyList())
    val devices: StateFlow<List<DiscoveredScale>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var timeout: Job? = null

    private val discovery = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = add(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::add)

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    fun start(durationMs: Long = 20_000) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        stop()
        _devices.value = bondedClassicScales()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(null, settings, discovery) }.onFailure { return }
        _scanning.value = true
        timeout = scope.launch {
            delay(durationMs)
            stop()
        }
    }

    fun stop() {
        timeout?.cancel()
        timeout = null
        if (_scanning.value) runCatching { adapter?.bluetoothLeScanner?.stopScan(discovery) }
        _scanning.value = false
    }

    private fun add(result: ScanResult) {
        val info = DeviceSnapshot.from(result)
        if (info.name.isBlank() && info.serviceUuids.isEmpty() && info.serviceData.isEmpty()) return
        val known = _devices.value.firstOrNull { it.address == info.address && it.name == info.name }
        val support = if (known != null) known.support else ScaleFactory.supportFor(info)
        val entry = DiscoveredScale(info, support, result.device)
        _devices.update { list ->
            val rest = list.filterNot { it.address == entry.address }
            (rest + entry).sortedWith(compareByDescending<DiscoveredScale> { it.isSupported }.thenByDescending { it.info.rssi })
        }
    }

    /** Paired serial scales never advertise over BLE; offer the ones a driver recognises by name. */
    private fun bondedClassicScales(): List<DiscoveredScale> = runCatching {
        adapter?.bondedDevices.orEmpty()
            .filter { it.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
            .mapNotNull { d ->
                val info = ScannedDeviceInfo(d.name.orEmpty(), d.address, 0, emptyList(), null)
                val support = ScaleFactory.supportFor(info) ?: return@mapNotNull null
                DiscoveredScale(info, support, d)
            }
    }.getOrDefault(emptyList())

    /** Waits for [address] to advertise; null when it stays silent for [timeoutMs]. */
    suspend fun find(address: String, timeoutMs: Long): ScanResult? {
        val scanner = adapter?.bluetoothLeScanner ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        runCatching { scanner.stopScan(this) }
                        if (cont.isActive) cont.resumeWith(Result.success(result))
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                }
                val filter = ScanFilter.Builder().setDeviceAddress(address).build()
                val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                runCatching { scanner.startScan(listOf(filter), settings, callback) }
                    .onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
                cont.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
            }
        }
    }
}
