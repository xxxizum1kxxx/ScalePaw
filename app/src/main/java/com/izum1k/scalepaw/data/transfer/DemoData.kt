package com.izum1k.scalepaw.data.transfer

import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitConversion
import com.izum1k.scalepaw.domain.BodyFormulas
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Plausible sample history (a slow cut with weekly rhythm and noise) for trying the app. */
object DemoData {
    fun generate(
        types: List<MetricType>,
        days: Int = 120,
        today: LocalDate = LocalDate.now(),
        seed: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<Long, Map<Long, MetricValue>>> {
        val random = Random(seed)
        val byIdentity = types.associateBy { it.identity }
        fun put(map: MutableMap<Long, MetricValue>, identity: String, kgOrValue: Float, isMass: Boolean = false) {
            val type = byIdentity[identity] ?: return
            val v = if (isMass) UnitConversion.fromKg(kgOrValue, type.unit) else kgOrValue
            map[type.id] = MetricValue.of(BodyFormulas.round2(v))
        }
        fun putComposition(map: MutableMap<Long, MetricValue>, identity: String, percent: Float, weightKg: Float) {
            val type = byIdentity[identity] ?: return
            val v = if (type.unit.isWeight) UnitConversion.fromKg(weightKg * percent / 100f, type.unit) else percent
            map[type.id] = MetricValue.of(BodyFormulas.round2(v))
        }
        return (days downTo 0).mapNotNull { back ->
            if (random.nextFloat() < 0.15f) return@mapNotNull null
            val date = today.minusDays(back.toLong())
            val progress = (days - back) / days.toFloat()
            val weekly = sin(date.dayOfWeek.value / 7.0 * 2 * PI).toFloat() * 0.35f
            val weight = 82f - 5.5f * progress + weekly + (random.nextFloat() - 0.5f) * 0.6f
            val fat = 24f - 3.2f * progress + (random.nextFloat() - 0.5f) * 0.8f
            val water = 55f + 1.5f * progress + (random.nextFloat() - 0.5f) * 0.6f
            val muscle = 40f + 0.8f * progress + (random.nextFloat() - 0.5f) * 0.5f
            val values = mutableMapOf<Long, MetricValue>()
            put(values, Metrics.WEIGHT.identity, weight, isMass = true)
            putComposition(values, Metrics.BODY_FAT.identity, fat, weight)
            putComposition(values, Metrics.WATER.identity, water, weight)
            putComposition(values, Metrics.MUSCLE.identity, muscle, weight)
            put(values, Metrics.BONE.identity, 3.1f + (random.nextFloat() - 0.5f) * 0.1f, isMass = true)
            put(values, Metrics.VISCERAL_FAT.identity, 9f - 2f * progress)
            val minute = 7 * 60 + random.nextInt(-20, 40)
            date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli() to values
        }
    }
}
