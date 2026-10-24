package com.izum1k.scalepaw.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.openscale.scales.BleGattTuning
import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.izum1k.scalepaw.ble.openscale.util.BluetoothPeripheral
import com.izum1k.scalepaw.ble.openscale.util.LogManager
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A GATT connection. Driver I/O is queued and run one operation at a time, each awaiting its
 * completion callback (bounded by the tuning's timeout) and paced by the tuning's gaps, since
 * Android drops GATT requests issued while another is in flight.
 */
@SuppressLint("MissingPermission")
class GattLink(link: LinkContext, private val tuning: BleGattTuning) : ScaleLink(link) {

    private enum class Kind { NOTIFY, WRITE, READ, MTU }

    private class Pending(val kind: Kind, val uuid: UUID) {
        val done = CompletableDeferred<Unit>()
    }

    private val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var gatt: BluetoothGatt? = null
    private var pending: Pending? = null
    private var ready = false
    private var stopping = false
    private var attempts = 0

    init {
        scope.launch {
            for (op in ops) runCatching { op() }.onFailure { LogManager.e(tag, "GATT operation failed", it) }
        }
    }

    override fun start(address: String, device: BluetoothDevice?) {
        scope.launch {
            val target = device ?: adapter?.getRemoteDevice(address)
            if (target == null) {
                close(context.getString(R.string.ble_error_unavailable))
                return@launch
            }
            delay(tuning.connectAfterScanDelayMs.takeIf { device != null } ?: 0L)
            connect(target)
        }
    }

    private fun connect(device: BluetoothDevice) {
        emit(LinkState.Connecting)
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    override fun stop() {
        scope.launch {
            stopping = true
            val g = gatt ?: return@launch finish(null)
            g.disconnect()
            // No disconnect callback arrives when the link never came up.
            delay(2_000)
            g.close()
            finish(null)
        }
    }

    private fun finish(error: String?) {
        if (isClosed) return
        ops.close()
        gatt?.close()
        gatt = null
        close(error)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        delay(300)
                        if (!g.discoverServices()) g.disconnect()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> onDisconnected(g, status)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            scope.launch {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    g.disconnect()
                    return@launch
                }
                ready = true
                emit(LinkState.Connected(g.device.name))
                if (tuning.requestHighConnectionPriority) g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                if (tuning.requestMtuBytes > 23) ops.trySend { await(Kind.MTU, MTU) { g.requestMtu(tuning.requestMtuBytes) } }
                attach(transport)
                handler.handleConnected(link.user)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            deliver(ch.uuid, value.copyOf())
        }

        @Deprecated("Pre-Tiramisu callback")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) deliver(ch.uuid, ch.value?.copyOf() ?: return)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            onRead(ch.uuid, value.copyOf(), status)
        }

        @Deprecated("Pre-Tiramisu callback")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onRead(ch.uuid, ch.value?.copyOf() ?: ByteArray(0), status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            scope.launch {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    LogManager.w(tag, "Write to ${ch.uuid} failed with status $status")
                    if (status in AUTH_FAILURES) reportGattFailure(ch.uuid, status)
                }
                complete(Kind.WRITE, ch.uuid)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            scope.launch {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    LogManager.w(tag, "Enabling notifications on ${d.characteristic.uuid} failed with status $status")
                    reportGattFailure(d.characteristic.uuid, status)
                }
                complete(Kind.NOTIFY, d.characteristic.uuid)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            scope.launch { complete(Kind.MTU, MTU) }
        }
    }

    private suspend fun onDisconnected(g: BluetoothGatt, status: Int) {
        g.close()
        if (gatt === g) gatt = null
        if (!ready && !stopping && attempts < tuning.common.maxRetries) {
            attempts++
            emit(LinkState.Message(context.getString(R.string.ble_retrying, attempts, tuning.common.maxRetries)))
            delay(tuning.common.retryBackoffMs)
            connect(g.device)
            return
        }
        finish(if (ready || stopping) null else context.getString(R.string.ble_error_connect, status))
    }

    private fun deliver(uuid: UUID, value: ByteArray) {
        scope.launch { handler.handleNotification(uuid, value) }
    }

    private fun onRead(uuid: UUID, value: ByteArray, status: Int) {
        scope.launch {
            if (status == BluetoothGatt.GATT_SUCCESS) handler.handleNotification(uuid, value)
            complete(Kind.READ, uuid)
        }
    }

    private fun complete(kind: Kind, uuid: UUID) {
        val p = pending ?: return
        if (p.kind == kind && p.uuid == uuid) {
            pending = null
            p.done.complete(Unit)
        }
    }

    private suspend fun await(kind: Kind, uuid: UUID, start: () -> Boolean) {
        val p = Pending(kind, uuid)
        pending = p
        if (!start()) {
            LogManager.w(tag, "Could not start $kind on $uuid")
            pending = null
            return
        }
        val done = withTimeoutOrNull(tuning.operationTimeoutMs) { p.done.await() }
            ?: if (isBonding()) {
                // The scale demanded encryption and the system is pairing; the user may be typing a passkey.
                LogManager.i(tag, "Pairing in progress, waiting for $kind on $uuid")
                withTimeoutOrNull(PAIRING_TIMEOUT_MS) { p.done.await() }
            } else {
                null
            }
        if (done == null) LogManager.w(tag, "Timeout waiting for $kind on $uuid")
        if (pending === p) pending = null
    }

    private fun isBonding(): Boolean = gatt?.device?.bondState == BluetoothDevice.BOND_BONDING

    private fun reportGattFailure(uuid: UUID, status: Int) {
        link.callbacks.onError(R.string.ble_error_gatt_status, null, uuid.toString(), status)
    }

    private suspend fun gap(ms: Long) {
        if (ms > 0) delay(ms)
    }

    private fun characteristic(service: UUID, characteristic: UUID): BluetoothGattCharacteristic? =
        gatt?.getService(service)?.getCharacteristic(characteristic)

    private val transport = object : ScaleDeviceHandler.Transport {
        override fun setNotifyOn(service: UUID, characteristic: UUID) {
            ops.trySend {
                val g = gatt ?: return@trySend
                val ch = characteristic(service, characteristic) ?: return@trySend LogManager.w(tag, "No characteristic $characteristic")
                g.setCharacteristicNotification(ch, true)
                val cccd = ch.getDescriptor(CCCD) ?: return@trySend
                val indicateOnly = ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 &&
                    ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
                val value = if (indicateOnly) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                await(Kind.NOTIFY, characteristic) { writeDescriptor(g, cccd, value) }
                gap(tuning.notifySetupDelayMs)
            }
        }

        override fun write(service: UUID, characteristic: UUID, payload: ByteArray, withResponse: Boolean) {
            ops.trySend {
                val g = gatt ?: return@trySend
                val ch = characteristic(service, characteristic) ?: return@trySend LogManager.w(tag, "No characteristic $characteristic")
                val canNoResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                val canResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                val type = when {
                    withResponse && canResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    !withResponse && canNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    canResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    canNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else -> return@trySend LogManager.w(tag, "Characteristic $characteristic is not writable")
                }
                gap(if (withResponse) tuning.writeWithResponseDelayMs else tuning.writeWithoutResponseDelayMs)
                await(Kind.WRITE, characteristic) { writeCharacteristic(g, ch, payload, type) }
                gap(tuning.postWriteDelayMs)
            }
        }

        override fun read(service: UUID, characteristic: UUID) {
            ops.trySend {
                val g = gatt ?: return@trySend
                val ch = characteristic(service, characteristic) ?: return@trySend
                await(Kind.READ, characteristic) { g.readCharacteristic(ch) }
                gap(tuning.postReadDelayMs)
            }
        }

        override fun disconnect() = stop()

        override fun getPeripheral(): BluetoothPeripheral? = gatt?.let {
            BluetoothPeripheral(it.device.name.orEmpty(), it.device.address, it.services)
        }

        override fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean = characteristic(service, characteristic) != null
    }

    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            d.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(d)
        }

    private fun writeCharacteristic(g: BluetoothGatt, ch: BluetoothGattCharacteristic, payload: ByteArray, type: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, payload, type) == BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = type
            @Suppress("DEPRECATION")
            ch.value = payload
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }

    private companion object {
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        /** Pseudo key for the MTU exchange, which has no characteristic. */
        val MTU: UUID = UUID(0, 0)
        /** SMP gives up on an unanswered passkey after about 30 s. */
        const val PAIRING_TIMEOUT_MS = 35_000L
        /** Insufficient authentication, insufficient encryption, and the stack's generic auth failure. */
        val AUTH_FAILURES = setOf(0x05, 0x0F, 0x89)
    }
}
