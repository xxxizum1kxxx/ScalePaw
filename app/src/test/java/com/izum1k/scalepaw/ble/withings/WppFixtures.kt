package com.izum1k.scalepaw.ble.withings

import java.nio.ByteBuffer

/** Synthetic scale-side frames; no real MAC, key or reading. */
object WppFixtures {
    const val KL = "0123456789abcdef0123456789ABCDEF"
    const val MAC = "00:24:e4:00:00:01"

    fun meta(time: Long, ids: List<Long>, valid: Int = ids.size, uid: Long = 0xDEADBEEF): Tlv {
        val buf = ByteBuffer.allocate(4 + 1 + 1 + ids.size * 4 + 1 + 4)
        buf.putInt(uid.toInt()).put(valid.toByte()).put(ids.size.toByte())
        ids.forEach { buf.putInt(it.toInt()) }
        buf.put(0).putInt(time.toInt())
        return Tlv(WppObj.STORED_MEASURE_META, buf.array())
    }

    fun data(type: Int, value: Int, exponent: Int): Tlv =
        Tlv(WppObj.STORED_MEASURE_DATA, ByteBuffer.allocate(8).putInt(value).putShort(type.toShort()).putShort(exponent.toShort()).array())

    fun algorithm(value: Int): Tlv = Tlv(WppObj.STORED_MEASURE_META_EXTEND, byteArrayOf(value.toByte()))

    fun nullTlv(): Tlv = Tlv(WppObj.NULL, ByteArray(0))

    fun challenge(challenge: ByteArray): WppMessage =
        WppMessage(WppCmd.PROBE_CHALLENGE, listOf(Tlv(WppObj.PROBE_CHALLENGE, WppCodec.packStr(MAC) + WppCodec.packStr(challenge))))

    fun probeReply(firmware: Long = 1311): Tlv {
        val buf = ByteBuffer.allocate(256)
        buf.putShort(1).putShort(2)
        buf.put(WppCodec.packStr("Body Smart")).put(WppCodec.packStr(MAC)).put(WppCodec.packStr(""))
        buf.putInt(0xFFFFFF).put(WppCodec.packStr("00000000")).putInt(2).putInt(firmware.toInt()).putInt(0xFFFFFF)
        return Tlv(WppObj.PROBE_REPLY, buf.array().copyOf(buf.position()))
    }

    /** The scale's answer to our challenge, signed with [key]. */
    fun scaleAnswer(own: ByteArray, key: String = KL): WppMessage = WppMessage(
        WppCmd.PROBE,
        listOf(Tlv(WppObj.PROBE_CHALLENGE_RESPONSE, WppCodec.packStr(WppAuth.answer(own, MAC, key))), probeReply()),
    )

    fun status(count: Int): WppMessage =
        WppMessage(WppCmd.STORED_MEASURE, listOf(Tlv(WppObj.STORED_MEASURE_STATUS, ByteBuffer.allocate(7).putShort(count.toShort()).putInt(0).put(1).array())))

    fun error(command: Int, error: Int): WppMessage =
        WppMessage(WppCmd.ERROR, listOf(Tlv(WppObj.CMD_ERROR, ByteBuffer.allocate(6).putShort(command.toShort()).putInt(error).array())))
}
