package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitConversion
import com.izum1k.scalepaw.data.model.UnitType
import com.izum1k.scalepaw.data.model.User
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One Google Health data point: [body] is the request body without `name`, so it doubles as a change hash. */
data class HealthPoint(val id: String, val dataType: String, val body: String)

/** Maps a user's measurements to the Google Health data points that should exist for them. */
object HealthPoints {
    const val WEIGHT = "weight"
    const val BODY_FAT = "body-fat"
    const val HEIGHT = "height"

    private val ID = Regex("^[a-z0-9-]{4,63}$")
    private const val MAX_GRAMS = 1_000_000L
    private const val MAX_HEIGHT_MM = 3000L

    fun isValidId(id: String): Boolean = ID.matches(id)

    fun build(
        user: User,
        types: List<MetricType>,
        measurements: List<Measurement>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HealthPoint> {
        val weightType = types.firstOrNull { it.identity == Metrics.WEIGHT.identity }
        val fatType = types.firstOrNull { it.identity == Metrics.BODY_FAT.identity }
        val points = mutableListOf<HealthPoint>()
        for (m in measurements) {
            val weightKg = m.number(weightType)?.let { UnitConversion.toKg(it, weightType!!.unit) }?.takeIf { it > 0f }
            weightKg?.let { kg ->
                val grams = (kg * 1000.0).roundToLong()
                if (grams in 1..MAX_GRAMS) {
                    points += point("sp-${m.id}-w", WEIGHT, m.timestamp, zone, "weight") { put("weightGrams", grams) }
                }
            }
            val percent = m.number(fatType)?.let { fat ->
                if (fatType!!.unit == UnitType.PERCENT) {
                    fat
                } else {
                    weightKg?.let { UnitConversion.toKg(fat, fatType.unit) / it * 100f }
                }
            }
            if (percent != null && percent > 0f && percent <= 100f) {
                points += point("sp-${m.id}-f", BODY_FAT, m.timestamp, zone, "bodyFat") { put("percentage", (percent * 100.0).roundToLong() / 100.0) }
            }
        }
        val heightMm = (user.heightCm * 10.0).roundToLong()
        val since = measurements.minOfOrNull { it.timestamp }
        if (since != null && heightMm in 1..MAX_HEIGHT_MM) {
            points += point("sp-u${user.id}-h$heightMm", HEIGHT, since, zone, "height") { put("heightMillimeters", heightMm.toString()) }
        }
        return points
    }

    private fun point(
        id: String,
        dataType: String,
        timestamp: Long,
        zone: ZoneId,
        field: String,
        values: JsonObjectBuilder.() -> Unit,
    ): HealthPoint {
        val instant = Instant.ofEpochMilli(timestamp)
        val body: JsonObject = buildJsonObject {
            putJsonObject(field) {
                values()
                putJsonObject("sampleTime") {
                    put("physicalTime", instant.toString())
                    put("utcOffset", "${zone.rules.getOffset(instant).totalSeconds}s")
                }
            }
            putJsonObject("dataSource") { put("recordingMethod", "ACTIVELY_MEASURED") }
        }
        return HealthPoint(id, dataType, body.toString())
    }
}
