package com.gpo.scalepaw.ble

import android.content.Context
import androidx.core.content.edit
import com.gpo.scalepaw.ble.openscale.scales.ScaleDeviceHandler

/**
 * Per-driver key/value storage (scale user slots, consent codes, pairing secrets). Synchronous,
 * because drivers read it from protocol callbacks, so it lives in SharedPreferences rather than
 * DataStore.
 */
class DriverStore(context: Context) {
    private val prefs = context.getSharedPreferences("scale_drivers", Context.MODE_PRIVATE)

    fun forHandler(handler: ScaleDeviceHandler): ScaleDeviceHandler.DriverSettings {
        val prefix = "${handler::class.simpleName}/"
        return object : ScaleDeviceHandler.DriverSettings {
            override fun getInt(key: String, default: Int): Int = prefs.getInt(prefix + key, default)
            override fun putInt(key: String, value: Int) = prefs.edit { putInt(prefix + key, value) }
            override fun getString(key: String, default: String?): String? = prefs.getString(prefix + key, default)
            override fun putString(key: String, value: String) = prefs.edit { putString(prefix + key, value) }
            override fun remove(key: String) = prefs.edit { remove(prefix + key) }
        }
    }

    /** Forgets everything drivers stored, e.g. when the saved scale is removed. */
    fun clear() = prefs.edit { clear() }
}
