package com.izum1k.scalepaw.ble

import com.izum1k.scalepaw.ble.openscale.scales.MiScaleS400Handler
import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.izum1k.scalepaw.ble.openscale.scales.XiaomiS800Handler

/** Drivers whose BLE bind key comes from the Xiaomi cloud, and the driver setting it lives under. */
object MiCloudKeys {
    private val HEX_KEY = Regex("^[0-9a-fA-F]{32}$")

    fun settingKey(handler: ScaleDeviceHandler): String? = when (handler) {
        is MiScaleS400Handler -> "s400_bind_key"
        is XiaomiS800Handler -> "s800_bind_key"
        else -> null
    }

    fun isValid(key: String?): Boolean = key != null && HEX_KEY.matches(key)
}
