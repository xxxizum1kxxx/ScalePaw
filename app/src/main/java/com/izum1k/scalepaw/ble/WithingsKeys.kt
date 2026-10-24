package com.izum1k.scalepaw.ble

import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.izum1k.scalepaw.ble.withings.WithingsHandler
import com.izum1k.scalepaw.ble.withings.WppAuth

/** Drivers whose `kl` secret comes from the Withings account, and the driver setting it lives under. */
object WithingsKeys {
    fun settingKey(handler: ScaleDeviceHandler): String? = when (handler) {
        is WithingsHandler -> WithingsHandler.SETTINGS_KEY_KL
        else -> null
    }

    fun isValid(key: String?): Boolean = WppAuth.isValidKey(key)
}
