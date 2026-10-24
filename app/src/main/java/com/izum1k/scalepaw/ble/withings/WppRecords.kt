package com.izum1k.scalepaw.ble.withings

import java.math.BigDecimal

/** One stored weigh-in: its UTC time, the Withings users it was attributed to and its values by type. */
data class WppGroup(
    val timeSeconds: Long,
    val userIds: List<Long>,
    val attrib: Int,
    val algorithm: Int? = null,
    val values: Map<Int, Double> = emptyMap(),
    val unknownTlvs: Set<Int> = emptySet(),
)

/** Measure type codes shared with the public Withings API. */
object WppMeasureType {
    const val WEIGHT = 1
    const val FAT_FREE_MASS = 5
    const val FAT_RATIO = 6
    const val FAT_MASS = 8
    const val HEART_RATE = 11
    const val MUSCLE_MASS = 76
    const val HYDRATION = 77
    const val BONE_MASS = 88
    const val VISCERAL_FAT = 170
    const val BMR = 226
    const val METABOLIC_AGE = 227
}

object WppRecords {
    /**
     * `STORED_MEASURE_META`: uid u32, valid u8, ids (u8 count + u32 each), attrib u8, time u32.
     * Only the first `valid` ids mean anything; the rest of the array is junk.
     */
    fun parseMeta(value: ByteArray): WppGroup {
        val r = WppReader(value)
        r.u32() // uid, not stable across reads
        val valid = r.u8()
        val ids = List(r.u8()) { r.u32() }
        val attrib = r.u8()
        val time = r.u32()
        if (r.remaining != 0) throw WppException("STORED_MEASURE_META has ${r.remaining} trailing bytes")
        return WppGroup(time, ids.take(valid).filter { it != 0L }, attrib)
    }

    /** `STORED_MEASURE_DATA`: value i32, type u16, exponent i16; real value = value × 10^exponent. */
    fun parseData(value: ByteArray): Pair<Int, Double> {
        val r = WppReader(value)
        val raw = r.i32()
        val type = r.u16()
        val exponent = r.i16()
        return type to BigDecimal.valueOf(raw.toLong()).scaleByPowerOfTen(exponent).toDouble()
    }

    /**
     * Records in order: each META starts a group and the values after it belong to it. A record that
     * does not parse is skipped; values after a bad or missing META are dropped with it rather than
     * attached to the previous group, which may be someone else's.
     */
    fun parseGroups(messages: List<WppMessage>, onSkip: (String) -> Unit = {}): List<WppGroup> {
        val groups = mutableListOf<WppGroup>()
        var current: WppGroup? = null
        for (tlv in messages.flatMap { it.tlvs }) {
            try {
                when (tlv.type) {
                    WppObj.STORED_MEASURE_META -> {
                        current?.let(groups::add)
                        current = null
                        current = parseMeta(tlv.value)
                    }
                    WppObj.STORED_MEASURE_DATA -> current?.let { g ->
                        val (type, v) = parseData(tlv.value)
                        current = g.copy(values = g.values + (type to v))
                    }
                    WppObj.STORED_MEASURE_META_EXTEND -> current?.let { g ->
                        current = g.copy(algorithm = tlv.value.firstOrNull()?.toInt()?.and(0xFF))
                    }
                    WppObj.NULL -> Unit
                    else -> current?.let { g -> current = g.copy(unknownTlvs = g.unknownTlvs + tlv.type) }
                }
            } catch (e: WppException) {
                onSkip("TLV ${tlv.type} (${tlv.value.size} bytes): ${e.message}")
            }
        }
        current?.let(groups::add)
        return groups
    }

    /**
     * One group per timestamp, oldest first. A weigh-in arrives as several groups sharing a time
     * (body composition, heart rate), and user ids are not stable, so time is the only key.
     */
    fun mergeByTime(groups: List<WppGroup>): List<WppGroup> =
        groups.groupBy { it.timeSeconds }.toSortedMap().values.map { same ->
            same.reduce { acc, g ->
                acc.copy(
                    userIds = acc.userIds.ifEmpty { g.userIds },
                    algorithm = maxOf(acc.algorithm ?: -1, g.algorithm ?: -1).takeIf { it >= 0 },
                    values = acc.values + g.values,
                    unknownTlvs = acc.unknownTlvs + g.unknownTlvs,
                )
            }
        }
}
