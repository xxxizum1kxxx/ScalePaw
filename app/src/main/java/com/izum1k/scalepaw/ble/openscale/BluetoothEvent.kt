package com.izum1k.scalepaw.ble.openscale

import android.util.SparseArray
import java.util.UUID

object BluetoothEvent {
    /**
     * Prompts a driver can raise. CHOOSE_USER carries `Pair<Array<CharSequence>, IntArray>`
     * (labels, scale slot indices) and expects the chosen index back; ENTER_CONSENT expects the
     * numeric code shown on the scale.
     */
    enum class UserInteractionType { CHOOSE_USER, ENTER_CONSENT }
}

/** One advertisement as drivers see it when deciding whether they support a device. */
data class ScannedDeviceInfo(
    var name: String,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<UUID>,
    val manufacturerData: SparseArray<ByteArray>?,
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    var isSupported: Boolean = false,
    var determinedHandlerDisplayName: String? = null,
)
