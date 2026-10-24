package com.izum1k.scalepaw.ble.withings

import java.util.Locale
import java.util.UUID

/**
 * A Withings 128-bit UUID `000000SS-5749-5448-MMMM-<tail>`: "5749-5448" is ASCII "WITH", SS the
 * service, MMMM the model id. In advertisements the tail holds the scale's MAC after a weigh-in and
 * is all zeros in setup mode.
 */
data class WithingsUuid(val service: Int, val modelId: Int, val tail: String) {
    val isSetupMode: Boolean get() = tail.all { it == '0' }

    /** The MAC in the tail as `aa:bb:cc:dd:ee:ff`, or null in setup mode. */
    val mac: String? get() = tail.takeUnless { isSetupMode || it.length != 12 }?.chunked(2)?.joinToString(":")
}

object WithingsAdvert {
    private const val MARKER = "-5749-5448-"
    const val SERVICE = 0x20
    const val CHARACTERISTIC = 0x24

    fun isWithings(uuid: UUID): Boolean = MARKER in uuid.toString().lowercase(Locale.ROOT)

    fun parse(uuid: UUID): WithingsUuid? {
        val text = uuid.toString().lowercase(Locale.ROOT)
        if (MARKER !in text) return null
        val parts = text.split('-')
        if (parts.size != 5) return null
        return WithingsUuid(parts[0].toInt(16), parts[3].toInt(16), parts[4])
    }

    fun find(uuids: List<UUID>): WithingsUuid? = uuids.firstNotNullOfOrNull(::parse)

    fun serviceUuid(modelId: Int): UUID = wpp(SERVICE, modelId)

    fun characteristicUuid(modelId: Int): UUID = wpp(CHARACTERISTIC, modelId)

    private fun wpp(id: Int, modelId: Int): UUID =
        UUID.fromString("%08x-5749-5448-%04x-000000000000".format(id, modelId and 0xFFFF))
}
