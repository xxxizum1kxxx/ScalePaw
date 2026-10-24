package com.izum1k.scalepaw.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.SystemClock
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.openscale.scales.BleBroadcastTuning
import com.izum1k.scalepaw.ble.openscale.scales.BroadcastAction
import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A scale that only advertises its readings. Scans for the one address, drops repeated and
 * too-frequent packets, and hands the rest to the driver until it reports a final reading.
 */
@SuppressLint("MissingPermission")
class BroadcastLink(link: LinkContext, private val tuning: BleBroadcastTuning) : ScaleLink(link) {

    private val seen = LinkedHashMap<Int, Long>()
    private var lastForward = 0L
    private var attempt = 0
    private var scanning = false
    private var timeout: Job? = null
    private var address = ""

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch { onResult(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            scope.launch { finish(context.getString(R.string.ble_error_scan, errorCode)) }
        }
    }

    override fun start(address: String, device: BluetoothDevice?) {
        this.address = address
        scope.launch {
            attach(noTransport)
            emit(LinkState.Listening)
            startAttempt()
        }
    }

    override fun stop() {
        scope.launch { finish(null) }
    }

    private fun startAttempt() {
        val scanner = adapter?.bluetoothLeScanner ?: return finish(context.getString(R.string.ble_error_unavailable))
        scanning = true
        lastForward = 0
        seen.clear()
        val filter = ScanFilter.Builder().setDeviceAddress(address).build()
        val settings = ScanSettings.Builder().setScanMode(tuning.scanMode).build()
        runCatching { scanner.startScan(listOf(filter), settings, callback) }
            .onFailure { return finish(it.message ?: context.getString(R.string.ble_error_unavailable)) }
        timeout?.cancel()
        timeout = scope.launch {
            delay(tuning.maxScanMs)
            if (!scanning) return@launch
            stopScan()
            attempt++
            if (attempt <= tuning.common.maxRetries) {
                delay(tuning.common.retryBackoffMs)
                startAttempt()
            } else {
                finish(context.getString(R.string.ble_error_no_signal))
            }
        }
    }

    private fun onResult(result: ScanResult) {
        // Results already queued when the scan stopped must not reach the released driver.
        if (!scanning) return
        tuning.minRssiDbm?.let { if (result.rssi < it) return }
        val now = SystemClock.elapsedRealtime()
        val hash = contentHash(result.scanRecord?.bytes, result.rssi)
        val last = seen[hash]
        if (last != null && now - last <= tuning.packetDedupWindowMs) return
        seen[hash] = now
        seen.entries.removeAll { now - it.value > tuning.packetDedupWindowMs }
        if (now - lastForward < tuning.stabilizeWindowMs) return

        when (handler.onAdvertisement(result, link.user)) {
            BroadcastAction.IGNORED -> Unit
            BroadcastAction.CONSUMED_KEEP_SCANNING -> lastForward = now
            BroadcastAction.CONSUMED_STOP -> finish(null)
        }
    }

    private fun stopScan() {
        timeout?.cancel()
        timeout = null
        if (scanning) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanning = false
    }

    private fun finish(error: String?) {
        stopScan()
        close(error)
    }

    /** Identical payloads with level-only RSSI jitter collapse to one key. */
    private fun contentHash(bytes: ByteArray?, rssi: Int): Int {
        if (bytes == null || bytes.isEmpty()) return rssi
        var h = 1125899907
        for (b in bytes) h = (h * 131) xor (b.toInt() and 0xFF)
        return (h shl 1) xor (rssi / 3)
    }

    private val noTransport = object : ScaleDeviceHandler.Transport {
        override fun setNotifyOn(service: UUID, characteristic: UUID) = Unit
        override fun write(service: UUID, characteristic: UUID, payload: ByteArray, withResponse: Boolean) = Unit
        override fun read(service: UUID, characteristic: UUID) = Unit
        override fun disconnect() = stop()
        override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean = false
    }
}
