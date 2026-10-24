package com.gpo.scalepaw.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import com.gpo.scalepaw.R
import com.gpo.scalepaw.ble.openscale.scales.BtSppTuning
import com.gpo.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.gpo.scalepaw.ble.openscale.util.LogManager
import java.io.IOException
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A Bluetooth Classic (RFCOMM serial) scale. The byte stream is delivered to the driver as
 * notifications on [ScaleDeviceHandler.CLASSIC_DATA_UUID]; writes are chunked and paced.
 */
@SuppressLint("MissingPermission")
class SppLink(link: LinkContext, private val tuning: BtSppTuning) : ScaleLink(link) {

    private var socket: BluetoothSocket? = null
    private var reader: Job? = null

    override fun start(address: String, device: BluetoothDevice?) {
        scope.launch {
            val target = device ?: runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
                ?: return@launch close(context.getString(R.string.ble_error_unavailable))
            emit(LinkState.Connecting)
            var attempt = 0
            while (isActive) {
                val connected = withContext(Dispatchers.IO) { tryConnect(target) }
                if (connected != null) {
                    socket = connected
                    break
                }
                attempt++
                if (attempt > tuning.common.maxRetries) return@launch close(context.getString(R.string.ble_error_connect, -1))
                emit(LinkState.Message(context.getString(R.string.ble_retrying, attempt, tuning.common.maxRetries)))
                delay(tuning.common.retryBackoffMs)
            }
            delay(maxOf(50L, tuning.interChunkDelayMs * 3))
            emit(LinkState.Connected(target.name))
            attach(transport)
            handler.handleConnected(link.user)
            reader = launch { readLoop() }
        }
    }

    private suspend fun tryConnect(device: BluetoothDevice): BluetoothSocket? = coroutineScope {
        runCatching { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() }
        val s = runCatching { device.createRfcommSocketToServiceRecord(ScaleDeviceHandler.CLASSIC_DATA_UUID) }.getOrNull()
            ?: return@coroutineScope null
        // connect() blocks and ignores cancellation; closing the socket is what aborts it.
        val guard = launch {
            delay(tuning.connectTimeoutMs)
            runCatching { s.close() }
        }
        val ok = runCatching { s.connect() }.isSuccess
        guard.cancel()
        if (ok) s else null.also { runCatching { s.close() } }
    }

    private suspend fun readLoop() {
        val input = socket?.inputStream ?: return
        val buf = ByteArray(1024)
        var lastRx = SystemClock.elapsedRealtime()
        try {
            while (scope.isActive) {
                val available = withContext(Dispatchers.IO) { input.available() }
                if (available > 0) {
                    val n = withContext(Dispatchers.IO) { input.read(buf, 0, min(buf.size, available)) }
                    if (n <= 0) break
                    lastRx = SystemClock.elapsedRealtime()
                    handler.handleNotification(ScaleDeviceHandler.CLASSIC_DATA_UUID, buf.copyOf(n))
                } else {
                    delay(50)
                    if (tuning.readTimeoutMs > 0 && SystemClock.elapsedRealtime() - lastRx >= tuning.readTimeoutMs) break
                }
            }
        } catch (e: IOException) {
            LogManager.w(tag, "Serial stream closed: ${e.message}")
        }
        finish()
    }

    override fun stop() {
        scope.launch { finish() }
    }

    private fun finish() {
        reader?.cancel()
        runCatching { socket?.close() }
        socket = null
        close(null)
    }

    private val transport = object : ScaleDeviceHandler.Transport {
        override fun setNotifyOn(service: UUID, characteristic: UUID) = Unit

        override fun write(service: UUID, characteristic: UUID, payload: ByteArray, withResponse: Boolean) {
            scope.launch {
                val out = socket?.outputStream ?: return@launch
                val chunk = maxOf(1, tuning.writeChunkBytes)
                runCatching {
                    var i = 0
                    while (i < payload.size) {
                        val end = min(i + chunk, payload.size)
                        withContext(Dispatchers.IO) {
                            out.write(payload, i, end - i)
                            out.flush()
                        }
                        i = end
                        if (i < payload.size) delay(tuning.interChunkDelayMs)
                    }
                }.onFailure { LogManager.e(tag, "Serial write failed", it) }
            }
        }

        override fun read(service: UUID, characteristic: UUID) = Unit
        override fun disconnect() = stop()
        override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean = false
    }
}
