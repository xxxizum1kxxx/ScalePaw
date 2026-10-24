package com.izum1k.scalepaw.ble.withings

import java.nio.ByteBuffer

/**
 * The WPP commands this read-only client sends or answers. Commands that change the scale
 * (association keys, setup, users, units, Wi-Fi, firmware, deleting measurements) are left out
 * on purpose so they cannot be sent by mistake.
 */
object WppCmd {
    const val ERROR = 256
    const val PROBE = 257
    const val STORED_MEASURE = 271
    const val DISCONNECT = 272
    const val CONNECT_REASON = 273
    const val SYNC_OK = 277
    const val PROBE_CHALLENGE = 296
    const val TIME_SET = 1281

    private val names = mapOf(
        ERROR to "ERROR", PROBE to "PROBE", STORED_MEASURE to "STORED_MEASURE", DISCONNECT to "DISCONNECT",
        CONNECT_REASON to "CONNECT_REASON", SYNC_OK to "SYNC_OK", PROBE_CHALLENGE to "PROBE_CHALLENGE", TIME_SET to "TIME_SET",
    )

    fun name(command: Int): String = names[command] ?: command.toString()
}

/** TLV object types. They share a number space with commands but are a separate table. */
object WppObj {
    const val NULL = 256
    const val PROBE_REPLY = 257
    const val CMD_ERROR = 272
    const val STORED_MEASURE_ACTION = 276
    const val STORED_MEASURE_STATUS = 277
    const val STORED_MEASURE_META = 278
    const val STORED_MEASURE_DATA = 279
    const val CONNECT_REASON = 280
    const val PROBE_CHALLENGE = 290
    const val PROBE_CHALLENGE_RESPONSE = 291
    const val APP_PROBE = 298
    const val STORED_MEASURE_META_EXTEND = 299
    const val FACTORY_STATE = 300
    const val TIME_SET = 1281
    const val TIME_SET_REPLY = 1282
    const val APP_PROBE_OS_VERSION = 2344
}

object WppError {
    const val BUSY = -2
    const val UNSUPPORTED = -3
    const val REJECTED = -4
    const val NOT_AUTHENTICATED = -5
    const val AUTH_FAILED = -6
    const val INVALID_ARGUMENTS = -8
    const val ARGUMENTS_NOT_SET = -9
}

object ConnectReason {
    const val BUTTON = 1
    const val WEIGH_IN = 2
}

/** `STORED_MEASURE_ACTION.cmd`: only the two reads. */
object MeasureAction {
    const val GET_STATE = 0
    const val GET_ALL = 1
}

/** The scale's clock settings for `TIME_SET`, in seconds. */
data class WppTime(val utcSeconds: Long, val gmtOffsetSeconds: Int, val dstChangeSeconds: Long = 0, val nextGmtOffsetSeconds: Int = gmtOffsetSeconds)

object WppRequests {
    /** AppProbe identifies the client as the Android phone app (os 1, app 1). */
    private const val OS_ANDROID = 1
    private const val APP_WITHINGS = 1
    private const val APP_VERSION = 202609240L

    fun probe(osVersion: Int): WppMessage = WppMessage(
        WppCmd.PROBE,
        listOf(
            Tlv(WppObj.APP_PROBE, ByteBuffer.allocate(6).put(OS_ANDROID.toByte()).put(APP_WITHINGS.toByte()).putInt(APP_VERSION.toInt()).array()),
            Tlv(WppObj.APP_PROBE_OS_VERSION, ByteBuffer.allocate(2).putShort(osVersion.toShort()).array()),
        ),
    )

    fun timeSet(time: WppTime): WppMessage = WppMessage(
        WppCmd.TIME_SET,
        listOf(
            Tlv(
                WppObj.TIME_SET,
                ByteBuffer.allocate(16)
                    .putInt(time.utcSeconds.toInt())
                    .putInt(time.gmtOffsetSeconds)
                    .putInt(time.dstChangeSeconds.toInt())
                    .putInt(time.nextGmtOffsetSeconds)
                    .array(),
            ),
        ),
    )

    fun connectReason(): WppMessage = WppMessage(WppCmd.CONNECT_REASON)

    fun storedMeasure(action: Int): WppMessage =
        WppMessage(WppCmd.STORED_MEASURE, listOf(Tlv(WppObj.STORED_MEASURE_ACTION, byteArrayOf(action.toByte(), 0))))

    fun syncOk(): WppMessage = WppMessage(WppCmd.SYNC_OK)

    fun disconnect(): WppMessage = WppMessage(WppCmd.DISCONNECT)

    /** The echo the scale expects when it sends SYNC_OK as a device request. */
    fun syncOkEcho(): WppMessage = WppMessage(WppCmd.SYNC_OK or WppCodec.DEVICE_REQUEST)

    /** What the phone app answers to device requests it does not handle. */
    fun unsupported(command: Int): WppMessage = WppMessage(
        WppCmd.ERROR or WppCodec.DEVICE_REQUEST,
        listOf(Tlv(WppObj.CMD_ERROR, ByteBuffer.allocate(6).putShort(command.toShort()).putInt(WppError.UNSUPPORTED).array())),
    )
}

data class ProbeReply(
    val name: String,
    val hardVersion: Long,
    val bootloaderVersion: Long,
    val firmwareVersion: Long,
) {
    companion object {
        fun parse(value: ByteArray): ProbeReply {
            val r = WppReader(value)
            r.u16() // vid
            r.u16() // pid
            val name = r.str()
            r.str() // mac
            r.str() // secret
            val hard = r.u32()
            r.str() // manufacturing id
            return ProbeReply(name, hard, r.u32(), r.u32())
        }
    }
}

class ProbeChallenge(val mac: String, val challenge: ByteArray) {
    companion object {
        fun parse(value: ByteArray): ProbeChallenge = WppReader(value).let { ProbeChallenge(it.str(), it.bytes()) }
    }
}

data class CmdError(val command: Int, val error: Int) {
    companion object {
        fun parse(value: ByteArray): CmdError = WppReader(value).let { CmdError(it.u16(), it.i32()) }
    }
}

data class StoredMeasureStatus(val count: Int, val oldestSeconds: Long, val wifiConfigured: Int) {
    companion object {
        fun parse(value: ByteArray): StoredMeasureStatus = WppReader(value).let { StoredMeasureStatus(it.i16(), it.i32().toLong(), it.i8()) }
    }
}

/** A one-line summary for diagnostics that never includes TLV values, which can hold identifiers. */
fun WppMessage.describe(): String {
    val origin = if (fromDevice) "device " else ""
    val objects = tlvs.joinToString(", ") { "${it.type}[${it.value.size}]" }.ifEmpty { "-" }
    return "$origin${WppCmd.name(baseCommand)}($baseCommand): $objects"
}
