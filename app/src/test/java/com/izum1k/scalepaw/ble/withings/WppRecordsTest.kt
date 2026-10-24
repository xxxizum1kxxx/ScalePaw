package com.izum1k.scalepaw.ble.withings

import com.izum1k.scalepaw.ble.withings.WppFixtures.algorithm
import com.izum1k.scalepaw.ble.withings.WppFixtures.data
import com.izum1k.scalepaw.ble.withings.WppFixtures.meta
import com.izum1k.scalepaw.ble.withings.WppFixtures.nullTlv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WppRecordsTest {
    @Test
    fun should_scale_value_when_exponent_is_negative() {
        val (type, value) = WppRecords.parseData(data(WppMeasureType.WEIGHT, 72345, -3).value)
        assertEquals(WppMeasureType.WEIGHT, type)
        assertEquals(72.345, value, 1e-9)
    }

    @Test
    fun should_keep_only_valid_user_ids_when_parsing_meta() {
        val group = WppRecords.parseMeta(meta(1_800_000_000, listOf(42, 99, 7), valid = 1).value)
        assertEquals(1_800_000_000L, group.timeSeconds)
        assertEquals(listOf(42L), group.userIds)
    }

    @Test
    fun should_attach_values_to_preceding_meta_when_parsing_groups() {
        val msg = WppMessage(
            WppCmd.STORED_MEASURE,
            listOf(
                meta(100, listOf(1)), algorithm(3), data(WppMeasureType.WEIGHT, 700, -1), data(WppMeasureType.FAT_MASS, 1400, -2), Tlv(2451, byteArrayOf(1)),
                meta(200, listOf(1)), data(WppMeasureType.WEIGHT, 710, -1),
                nullTlv(),
            ),
        )
        val groups = WppRecords.parseGroups(listOf(msg))
        assertEquals(2, groups.size)
        assertEquals(mapOf(WppMeasureType.WEIGHT to 70.0, WppMeasureType.FAT_MASS to 14.0), groups[0].values)
        assertEquals(3, groups[0].algorithm)
        assertEquals(setOf(2451), groups[0].unknownTlvs)
        assertEquals(mapOf(WppMeasureType.WEIGHT to 71.0), groups[1].values)
    }

    @Test
    fun should_drop_values_when_no_meta_precedes_them() {
        val msg = WppMessage(WppCmd.STORED_MEASURE, listOf(data(WppMeasureType.WEIGHT, 700, -1), Tlv(WppObj.STORED_MEASURE_META, byteArrayOf(1)), data(WppMeasureType.WEIGHT, 1, 0)))
        val skipped = mutableListOf<String>()
        assertTrue(WppRecords.parseGroups(listOf(msg)) { skipped += it }.isEmpty())
        assertEquals(1, skipped.size)
    }

    @Test
    fun should_merge_groups_when_they_share_a_timestamp() {
        val groups = listOf(
            WppGroup(200, listOf(5), 0, values = mapOf(WppMeasureType.WEIGHT to 71.0)),
            WppGroup(100, emptyList(), 0, values = mapOf(WppMeasureType.HEART_RATE to 60.0)),
            WppGroup(100, listOf(9), 0, algorithm = 3, values = mapOf(WppMeasureType.WEIGHT to 70.0)),
        )
        val merged = WppRecords.mergeByTime(groups)
        assertEquals(listOf(100L, 200L), merged.map { it.timeSeconds })
        assertEquals(mapOf(WppMeasureType.HEART_RATE to 60.0, WppMeasureType.WEIGHT to 70.0), merged[0].values)
        assertEquals(listOf(9L), merged[0].userIds)
        assertEquals(3, merged[0].algorithm)
    }
}
