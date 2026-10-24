package com.izum1k.scalepaw.ble.openscale.util

import android.bluetooth.BluetoothGattService

/** What a driver may inspect about the connected device: identity and the discovered GATT table. */
class BluetoothPeripheral(
    val name: String,
    val address: String,
    val services: List<BluetoothGattService>,
)
