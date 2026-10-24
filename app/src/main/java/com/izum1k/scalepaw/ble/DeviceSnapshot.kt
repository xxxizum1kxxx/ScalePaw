package com.izum1k.scalepaw.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.util.Base64
import android.util.SparseArray
import com.izum1k.scalepaw.ble.openscale.ScannedDeviceInfo
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Converts advertisements to the shape drivers inspect, and persists them for offline re-identification. */
object DeviceSnapshot {

    @SuppressLint("MissingPermission")
    fun from(result: ScanResult): ScannedDeviceInfo {
        val record = result.scanRecord
        val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull()
        return ScannedDeviceInfo(
            name = cleanName(name),
            address = result.device.address,
            rssi = result.rssi,
            serviceUuids = record?.serviceUuids?.map { it.uuid }.orEmpty(),
            manufacturerData = record?.manufacturerSpecificData,
            serviceData = record?.serviceData?.mapKeys { it.key.uuid }.orEmpty(),
        )
    }

    /** Some scales pad their advertised name with NULs or other control characters. */
    fun cleanName(raw: String?): String = raw.orEmpty().substringBefore('\u0000').filterNot { it.isISOControl() }.trim()

    fun encode(info: ScannedDeviceInfo): String = JSONObject().apply {
        put("name", info.name)
        put("address", info.address)
        put("uuids", JSONArray(info.serviceUuids.map { it.toString() }))
        val manufacturer = JSONObject()
        info.manufacturerData?.let { data ->
            for (i in 0 until data.size()) manufacturer.put(data.keyAt(i).toString(), data.valueAt(i).b64())
        }
        put("manufacturer", manufacturer)
        put("service", JSONObject().apply { info.serviceData.forEach { (k, v) -> put(k.toString(), v.b64()) } })
    }.toString()

    fun decode(raw: String?): ScannedDeviceInfo? = runCatching {
        val o = JSONObject(raw ?: return null)
        val uuids = o.optJSONArray("uuids")?.let { a -> (0 until a.length()).map { UUID.fromString(a.getString(it)) } }.orEmpty()
        val manufacturer = SparseArray<ByteArray>()
        o.optJSONObject("manufacturer")?.let { m -> m.keys().forEach { k -> manufacturer.put(k.toInt(), m.getString(k).unb64()) } }
        val service = buildMap {
            o.optJSONObject("service")?.let { m -> m.keys().forEach { k -> put(UUID.fromString(k), m.getString(k).unb64()) } }
        }
        ScannedDeviceInfo(
            name = o.optString("name"),
            address = o.getString("address"),
            rssi = 0,
            serviceUuids = uuids,
            manufacturerData = manufacturer,
            serviceData = service,
        )
    }.getOrNull()

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.unb64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
