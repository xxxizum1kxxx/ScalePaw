package com.izum1k.scalepaw.ble.withings

import java.io.ByteArrayOutputStream

/** Malformed WPP data. */
class WppException(message: String) : Exception(message)

/** One `type u16 | length u16 | value` element of a WPP payload. */
class Tlv(val type: Int, val value: ByteArray) {
    fun encode(): ByteArray {
        require(value.size <= 0xFFFF) { "TLV value too long" }
        return byteArrayOf(
            (type shr 8).toByte(), type.toByte(),
            (value.size shr 8).toByte(), value.size.toByte(),
        ) + value
    }

    override fun equals(other: Any?): Boolean = other is Tlv && other.type == type && other.value.contentEquals(value)

    override fun hashCode(): Int = 31 * type + value.contentHashCode()

    override fun toString(): String = "Tlv($type, ${value.size} bytes)"
}

/**
 * A WPP message: `0x01 | command u16 | length u16 | TLV*`, all big-endian. Bit [WppCodec.DEVICE_REQUEST]
 * in the command marks a request the device initiated.
 */
data class WppMessage(val command: Int, val tlvs: List<Tlv> = emptyList()) {
    val baseCommand: Int get() = command and WppCodec.DEVICE_REQUEST.inv()
    val fromDevice: Boolean get() = command and WppCodec.DEVICE_REQUEST != 0

    fun find(type: Int): List<Tlv> = tlvs.filter { it.type == type }

    fun first(type: Int): Tlv? = tlvs.firstOrNull { it.type == type }

    fun encode(): ByteArray {
        val payload = ByteArrayOutputStream().apply { tlvs.forEach { write(it.encode()) } }.toByteArray()
        require(payload.size <= 0xFFFF) { "WPP payload too long" }
        return byteArrayOf(
            WppCodec.VERSION.toByte(),
            (command shr 8).toByte(), command.toByte(),
            (payload.size shr 8).toByte(), payload.size.toByte(),
        ) + payload
    }
}

object WppCodec {
    const val VERSION = 0x01
    const val HEADER_SIZE = 5
    const val DEVICE_REQUEST = 0x4000

    fun decode(data: ByteArray): WppMessage {
        if (data.size < HEADER_SIZE) throw WppException("shorter than a header")
        val version = data[0].toInt() and 0xFF
        if (version != VERSION) throw WppException("unexpected protocol version 0x%02x".format(version))
        val command = u16(data, 1)
        val length = u16(data, 3)
        if (data.size != HEADER_SIZE + length) throw WppException("length field says $length, got ${data.size - HEADER_SIZE}")
        return WppMessage(command, decodeTlvs(data.copyOfRange(HEADER_SIZE, data.size)))
    }

    fun decodeTlvs(payload: ByteArray): List<Tlv> {
        val out = mutableListOf<Tlv>()
        var pos = 0
        while (pos < payload.size) {
            if (pos + 4 > payload.size) throw WppException("truncated TLV header at offset $pos")
            val type = u16(payload, pos)
            val length = u16(payload, pos + 2)
            pos += 4
            if (pos + length > payload.size) throw WppException("TLV $type claims $length bytes, ${payload.size - pos} left")
            out += Tlv(type, payload.copyOfRange(pos, pos + length))
            pos += length
        }
        return out
    }

    /** A string or byte array inside a TLV value: one length byte, then the bytes. */
    fun packStr(value: ByteArray): ByteArray {
        if (value.size > 0xFF) throw WppException("string longer than 255 bytes")
        return byteArrayOf(value.size.toByte()) + value
    }

    fun packStr(value: String): ByteArray = packStr(value.toByteArray(Charsets.UTF_8))

    internal fun u16(data: ByteArray, at: Int): Int = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
}

/** Joins notification fragments into whole messages using the header's length field. */
class Reassembler {
    private val buffer = ByteArrayOutputStream()

    val pending: Int get() = buffer.size()

    fun feed(fragment: ByteArray): List<WppMessage> {
        buffer.write(fragment)
        var data = buffer.toByteArray()
        val out = mutableListOf<WppMessage>()
        while (data.size >= WppCodec.HEADER_SIZE) {
            if ((data[0].toInt() and 0xFF) != WppCodec.VERSION) {
                reset()
                throw WppException("stream out of sync (first byte 0x%02x)".format(data[0].toInt() and 0xFF))
            }
            val total = WppCodec.HEADER_SIZE + WppCodec.u16(data, 3)
            if (data.size < total) break
            out += WppCodec.decode(data.copyOfRange(0, total))
            data = data.copyOfRange(total, data.size)
        }
        buffer.reset()
        buffer.write(data)
        return out
    }

    fun reset() = buffer.reset()
}

/** Sequential reader for the fields inside a TLV value (big-endian). */
class WppReader(private val data: ByteArray) {
    private var pos = 0

    val remaining: Int get() = data.size - pos

    private fun take(n: Int): ByteArray {
        if (pos + n > data.size) throw WppException("need $n bytes at offset $pos, have ${data.size - pos}")
        return data.copyOfRange(pos, pos + n).also { pos += n }
    }

    fun u8(): Int = take(1)[0].toInt() and 0xFF

    fun i8(): Int = take(1)[0].toInt()

    fun u16(): Int = take(2).let { WppCodec.u16(it, 0) }

    fun i16(): Int = u16().toShort().toInt()

    fun u32(): Long = take(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }

    fun i32(): Int = u32().toInt()

    fun bytes(): ByteArray = take(u8())

    fun str(): String = String(bytes(), Charsets.UTF_8)

    fun rest(): ByteArray = take(remaining)
}
