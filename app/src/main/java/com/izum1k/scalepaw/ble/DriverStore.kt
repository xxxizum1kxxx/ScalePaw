package com.izum1k.scalepaw.ble

import android.content.Context
import androidx.core.content.edit
import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler

/**
 * Per-scale, per-driver key/value storage (scale user slots, consent codes, pairing secrets).
 * Synchronous, because drivers read it from protocol callbacks, so it lives in SharedPreferences
 * rather than DataStore.
 */
class DriverStore(context: Context) {
    private val prefs = context.getSharedPreferences("scale_drivers", Context.MODE_PRIVATE)

    fun forHandler(address: String, handler: ScaleDeviceHandler): ScaleDeviceHandler.DriverSettings {
        val prefix = "${scalePrefix(address)}${handler::class.simpleName}/"
        return object : ScaleDeviceHandler.DriverSettings {
            override fun getInt(key: String, default: Int): Int = prefs.getInt(prefix + key, default)
            override fun putInt(key: String, value: Int) = prefs.edit { putInt(prefix + key, value) }
            override fun getString(key: String, default: String?): String? = prefs.getString(prefix + key, default)
            override fun putString(key: String, value: String) = prefs.edit { putString(prefix + key, value) }
            override fun remove(key: String) = prefs.edit { remove(prefix + key) }
        }
    }

    /** Forgets everything drivers stored for the scale at [address]. */
    fun clear(address: String) {
        val prefix = scalePrefix(address)
        prefs.edit { prefs.all.keys.filter { it.startsWith(prefix) }.forEach(::remove) }
    }

    /** Moves keys written before storage was per scale under [address]. */
    fun adoptLegacy(address: String) {
        val legacy = prefs.all.filterKeys { !it.startsWith(SCALE_PREFIX) }
        if (legacy.isEmpty()) return
        val prefix = scalePrefix(address)
        prefs.edit {
            for ((key, value) in legacy) {
                remove(key)
                when (value) {
                    is Int -> putInt(prefix + key, value)
                    is String -> putString(prefix + key, value)
                    is Boolean -> putBoolean(prefix + key, value)
                    is Long -> putLong(prefix + key, value)
                    is Float -> putFloat(prefix + key, value)
                }
            }
        }
    }

    private fun scalePrefix(address: String) = "$SCALE_PREFIX$address/"

    private companion object {
        const val SCALE_PREFIX = "scale:"
    }
}
