package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.Gender
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricSpec
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitType
import com.izum1k.scalepaw.data.model.User
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthPointsTest {
    private val types = Metrics.builtins.mapIndexed { i, spec -> MetricType.seed(spec, i).copy(id = i + 1L, isEnabled = true) }
    private val user = User(id = 3, name = "A", birthDate = LocalDate.of(1990, 1, 1), gender = Gender.MALE, heightCm = 180f)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val timestamp = LocalDate.of(2025, 6, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun withUnit(spec: MetricSpec, unit: UnitType) = types.map { if (it.identity == spec.identity) it.copy(unit = unit) else it }

    private fun measurement(values: Map<MetricSpec, Float>, list: List<MetricType> = types, id: Long = 42) = Measurement(
        id = id,
        userId = user.id,
        timestamp = timestamp,
        values = values.mapKeys { (spec, _) -> list.first { it.identity == spec.identity }.id }.mapValues { MetricValue.of(it.value) },
    )

    private fun build(vararg m: Measurement, list: List<MetricType> = types) = HealthPoints.build(user, list, m.toList(), zone)

    private fun List<HealthPoint>.field(dataType: String, obj: String, key: String) =
        firstOrNull { it.dataType == dataType }?.let { Json.parseToJsonElement(it.body).jsonObject[obj]!!.jsonObject[key]!!.jsonPrimitive }

    @Test
    fun should_convert_to_grams_when_weight_unit_is_pounds() {
        val list = withUnit(Metrics.WEIGHT, UnitType.LB)
        val points = build(measurement(mapOf(Metrics.WEIGHT to 176.37f), list), list = list)
        assertEquals(80000.0, points.field(HealthPoints.WEIGHT, "weight", "weightGrams")!!.long.toDouble(), 5.0)
    }

    @Test
    fun should_convert_to_grams_when_weight_unit_is_stone() {
        val list = withUnit(Metrics.WEIGHT, UnitType.ST)
        val points = build(measurement(mapOf(Metrics.WEIGHT to 12.6f), list), list = list)
        assertEquals(80014.0, points.field(HealthPoints.WEIGHT, "weight", "weightGrams")!!.long.toDouble(), 10.0)
    }

    @Test
    fun should_compute_percentage_when_body_fat_unit_is_kg() {
        val list = withUnit(Metrics.BODY_FAT, UnitType.KG)
        val points = build(measurement(mapOf(Metrics.WEIGHT to 80f, Metrics.BODY_FAT to 16f), list), list = list)
        assertEquals(20.0, points.field(HealthPoints.BODY_FAT, "bodyFat", "percentage")!!.double, 0.001)
    }

    @Test
    fun should_skip_body_fat_when_unit_is_kg_and_weight_missing() {
        val list = withUnit(Metrics.BODY_FAT, UnitType.KG)
        val points = build(measurement(mapOf(Metrics.BODY_FAT to 16f), list), list = list)
        assertNull(points.firstOrNull { it.dataType == HealthPoints.BODY_FAT })
    }

    @Test
    fun should_skip_points_when_values_missing() {
        val points = build(measurement(mapOf(Metrics.WATER to 55f)))
        assertEquals(listOf(HealthPoints.HEIGHT), points.map { it.dataType })
    }

    @Test
    fun should_emit_valid_ids_when_building_points() {
        val points = build(measurement(mapOf(Metrics.WEIGHT to 80f, Metrics.BODY_FAT to 20f), id = 123456789))
        assertEquals(3, points.size)
        assertTrue(points.all { HealthPoints.isValidId(it.id) })
        assertEquals("sp-u3-h1800", points.first { it.dataType == HealthPoints.HEIGHT }.id)
    }

    @Test
    fun should_carry_local_offset_when_building_sample_time() {
        val point = build(measurement(mapOf(Metrics.WEIGHT to 80f))).first { it.dataType == HealthPoints.WEIGHT }
        val sample = Json.parseToJsonElement(point.body).jsonObject["weight"]!!.jsonObject["sampleTime"]!!.jsonObject
        assertEquals("28800s", sample["utcOffset"]!!.jsonPrimitive.content)
        assertEquals("2025-05-31T16:00:00Z", sample["physicalTime"]!!.jsonPrimitive.content)
    }

    @Test
    fun should_emit_nothing_when_no_measurements() {
        assertTrue(HealthPoints.build(user, types, emptyList(), zone).isEmpty())
    }
}
