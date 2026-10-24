package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.AggregationLevel
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.SmoothingAlgorithm
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.sqrt

data class Point(val time: Long, val value: Float)

enum class Trend { UP, DOWN, STABLE }

data class SeriesStats(
    val count: Int,
    val first: Point,
    val last: Point,
    val min: Point,
    val max: Point,
    val average: Float,
    val change: Float,
    val changePercent: Float,
)

/** One aggregated row: the mean of every numeric type over a calendar period. */
data class PeriodEntry(
    val start: Long,
    val endExclusive: Long,
    val count: Int,
    val averages: Map<Long, Float>,
)

object Analytics {

    fun series(measurements: List<Measurement>, type: MetricType): List<Point> =
        measurements.mapNotNull { m -> m.values[type.id]?.number?.let { Point(m.timestamp, it) } }
            .sortedBy { it.time }

    fun stats(points: List<Point>): SeriesStats? {
        if (points.isEmpty()) return null
        val sorted = points.sortedBy { it.time }
        val first = sorted.first()
        val last = sorted.last()
        val change = last.value - first.value
        return SeriesStats(
            count = sorted.size,
            first = first,
            last = last,
            min = sorted.minBy { it.value },
            max = sorted.maxBy { it.value },
            average = sorted.map { it.value }.average().toFloat(),
            change = change,
            changePercent = if (first.value != 0f) change / first.value * 100f else 0f,
        )
    }

    /** Difference to the previous (older) value of the same type, for list rows. */
    fun previousDifference(measurements: List<Measurement>, type: MetricType, at: Measurement): Float? {
        val current = at.values[type.id]?.number ?: return null
        val previous = measurements
            .filter { it.timestamp < at.timestamp }
            .maxByOrNull { it.timestamp }
            ?.values?.get(type.id)?.number ?: return null
        return current - previous
    }

    fun smooth(values: List<Float>, algorithm: SmoothingAlgorithm, window: Int, alpha: Float): List<Float> = when (algorithm) {
        SmoothingAlgorithm.NONE -> values
        SmoothingAlgorithm.SIMPLE_MOVING_AVERAGE -> movingAverage(values, window)
        SmoothingAlgorithm.EXPONENTIAL -> exponential(values, alpha)
    }

    /** Trailing simple moving average; the first points average what is available so far. */
    fun movingAverage(values: List<Float>, window: Int): List<Float> {
        if (window <= 1 || values.isEmpty()) return values
        var sum = 0.0
        return values.mapIndexed { i, v ->
            sum += v
            if (i >= window) sum -= values[i - window]
            (sum / minOf(i + 1, window)).toFloat()
        }
    }

    fun exponential(values: List<Float>, alpha: Float): List<Float> {
        if (values.isEmpty()) return values
        val a = alpha.coerceIn(0.01f, 1f)
        val out = ArrayList<Float>(values.size)
        out += values.first()
        for (i in 1 until values.size) out += a * values[i] + (1 - a) * out[i - 1]
        return out
    }

    /** Least squares line; returns slope per millisecond and intercept, or null for < 2 points. */
    fun linearFit(points: List<Point>): Pair<Double, Double>? {
        if (points.size < 2) return null
        val t0 = points.first().time.toDouble()
        val xs = points.map { it.time - t0 }
        val ys = points.map { it.value.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        if (den == 0.0) return null
        val slope = num / den
        return slope to (my - slope * mx - slope * t0)
    }

    fun periodStart(date: LocalDate, level: AggregationLevel): LocalDate = when (level) {
        AggregationLevel.NONE, AggregationLevel.DAY -> date
        AggregationLevel.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        AggregationLevel.MONTH -> date.withDayOfMonth(1)
        AggregationLevel.YEAR -> date.withDayOfYear(1)
    }

    private fun nextPeriod(start: LocalDate, level: AggregationLevel): LocalDate = when (level) {
        AggregationLevel.NONE, AggregationLevel.DAY -> start.plusDays(1)
        AggregationLevel.WEEK -> start.plusWeeks(1)
        AggregationLevel.MONTH -> start.plusMonths(1)
        AggregationLevel.YEAR -> start.plusYears(1)
    }

    /** Groups measurements by calendar period (newest first), averaging each numeric type. */
    fun aggregate(
        measurements: List<Measurement>,
        numericTypes: List<MetricType>,
        level: AggregationLevel,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PeriodEntry> {
        if (level == AggregationLevel.NONE) return emptyList()
        return measurements
            .groupBy { periodStart(Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(), level) }
            .map { (start, group) ->
                val averages = numericTypes.mapNotNull { type ->
                    val vs = group.mapNotNull { it.values[type.id]?.number }
                    if (vs.isEmpty()) null else type.id to vs.average().toFloat()
                }.toMap()
                PeriodEntry(
                    start = start.atStartOfDay(zone).toInstant().toEpochMilli(),
                    endExclusive = nextPeriod(start, level).atStartOfDay(zone).toInstant().toEpochMilli(),
                    count = group.size,
                    averages = averages,
                )
            }
            .sortedByDescending { it.start }
    }

    fun classifyTrend(delta: Float, mean: Float): Trend {
        val threshold = abs(mean) * 0.005f
        return when {
            delta > threshold -> Trend.UP
            delta < -threshold -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    fun stdDev(values: Collection<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size).toFloat()
    }
}
