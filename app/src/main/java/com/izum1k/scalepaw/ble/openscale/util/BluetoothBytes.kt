package com.izum1k.scalepaw.ble.openscale.util

import java.io.ByteArrayOutputStream

/** Sequential little-endian reader over a GATT payload. */
class BluetoothBytesParser(private val value: ByteArray) {
    var offset: Int = 0

    fun getUInt8(): UInt = (value[offset++].toInt() and 0xFF).toUInt()

    fun getUInt16(): UInt {
        val v = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
        offset += 2
        return v.toUInt()
    }

    /** The remaining bytes as text, cut at the first NUL. */
    fun getString(): String {
        val rest = value.copyOfRange(offset.coerceAtMost(value.size), value.size)
        offset = value.size
        val end = rest.indexOf(0).let { if (it < 0) rest.size else it }
        return String(rest, 0, end, Charsets.UTF_8)
    }
}

/** Sequential little-endian writer for GATT payloads. */
class BluetoothBytesBuilder {
    private val out = ByteArrayOutputStream()

    fun addUInt8(v: Int): BluetoothBytesBuilder = apply { out.write(v and 0xFF) }

    fun addUInt16(v: Int): BluetoothBytesBuilder = apply {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    fun build(): ByteArray = out.toByteArray()
}
