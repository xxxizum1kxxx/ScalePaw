package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Confidence { HIGH, LOW, INSUFFICIENT }

enum class Volatility { STABLE, MODERATE, HIGH }

enum class CompositionPattern { FAT_GAIN, MUSCLE_GAIN, MUSCLE_AND_FAT_GAIN, WEIGHT_LOSS_MIXED, FAT_LOSS, RECOMPOSITION, STABLE }

data class SeriesAnalysis(
    val type: MetricType,
    val stats: SeriesStats,
    val volatility: Volatility,
    val shortTerm: Trend,
    val longTerm: Trend,
    val ratePerMonth: Float,
    val plateauDays: Int?,
    val confidence: Confidence,
)

data class CompositionInsight(
    val weight: Trend,
    val fat: Trend,
    val muscle: Trend,
    val pattern: CompositionPattern,
    val fatDelta: Float,
    val muscleDelta: Float,
    val basedOn: Int,
    val confidence: Confidence,
)

data class WeekdayInsight(
    val deviationByDay: Map<DayOfWeek, Float>,
    val heaviest: DayOfWeek?,
    val lightest: DayOfWeek?,
    val confidence: Confidence,
)

data class SeasonalInsight(val averageByMonth: Map<Month, Float>, val highest: Month?, val lowest: Month?, val years: Int)

data class Anomaly(val measurementId: Long, val time: Long, val value: Float, val expected: Float, val zScore: Float)

data class InsightReport(
    val analysis: SeriesAnalysis?,
    val composition: CompositionInsight?,
    val weekday: WeekdayInsight?,
    val seasonal: SeasonalInsight?,
    val anomalies: List<Anomaly>,
)

/** Trend, plateau, composition, weekday, seasonal and outlier analysis over one user's history. */
object Insights {
    private const val SHORT_TERM_DAYS = 14L
    private const val PLATEAU_CHANGE = 0.01f
    private const val VOLATILITY_STABLE = 0.01f
    private const val VOLATILITY_MODERATE = 0.025f
    private const val COMPOSITION_WINDOW_DAYS = 30L
    private const val COMPOSITION_MIN = 4
    private const val WEEKDAY_MIN = 2
    private const val WEEKDAY_HIGH = 5
    private const val ANOMALY_WINDOW = 7
    private const val ANOMALY_Z = 2.5f
    private const val ANOMALY_GAP_DAYS = 30L

    fun report(
        measurements: List<Measurement>,
        primary: MetricType,
        fat: MetricType?,
        muscle: MetricType?,
        weight: MetricType?,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): InsightReport {
        val sorted = measurements.sortedBy { it.timestamp }
        val points = Analytics.series(sorted, primary)
        return InsightReport(
            analysis = analyze(points, primary, zone),
            composition = composition(sorted, weight, fat, muscle, today, zone),
            weekday = weekday(points, zone),
            seasonal = seasonal(points, zone),
            anomalies = anomalies(sorted, primary, zone),
        )
    }

    private fun date(time: Long, zone: ZoneId) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun analyze(points: List<Point>, type: MetricType, zone: ZoneId = ZoneId.systemDefault()): SeriesAnalysis? {
        if (points.size < 2) return null
        val stats = Analytics.stats(points) ?: return null
        val values = points.map { it.value }
        val mean = stats.average

        val fit = Analytics.linearFit(points)
        val residuals = if (fit != null) points.map { it.value - (fit.first * it.time + fit.second).toFloat() } else values
        val relStd = if (abs(mean) > 1e-3f) Analytics.stdDev(residuals) / abs(mean) else 0f
        val volatility = when {
            relStd < VOLATILITY_STABLE -> Volatility.STABLE
            relStd < VOLATILITY_MODERATE -> Volatility.MODERATE
            else -> Volatility.HIGH
        }

        val quarter = (points.size / 4).coerceAtLeast(1)
        val longTerm = Analytics.classifyTrend(values.takeLast(quarter).average().toFloat() - values.take(quarter).average().toFloat(), mean)

        val lastDate = date(points.last().time, zone)
        val cutoff = lastDate.minusDays(SHORT_TERM_DAYS)
        val recent = points.filter { date(it.time, zone) >= cutoff }
        val preceding = points.filter { date(it.time, zone) < cutoff }.takeLast(recent.size.coerceAtLeast(1))
        val shortTerm = if (recent.isNotEmpty() && preceding.isNotEmpty()) {
            Analytics.classifyTrend(recent.map { it.value }.average().toFloat() - preceding.map { it.value }.average().toFloat(), mean)
        } else {
            longTerm
        }

        val months = ChronoUnit.DAYS.between(date(points.first().time, zone), lastDate) / 30.44f
        val rate = if (months > 0f) stats.change / months else 0f

        val plateau = run {
            val threshold = abs(mean) * PLATEAU_CHANGE
            if (points.size < 3 || threshold < 1e-6f) return@run null
            var start = points.size - 1
            var lo = points.last().value
            var hi = lo
            for (i in points.indices.reversed().drop(1)) {
                lo = minOf(lo, points[i].value)
                hi = maxOf(hi, points[i].value)
                if (hi - lo > threshold) break
                start = i
            }
            if (start == points.size - 1) null else ChronoUnit.DAYS.between(date(points[start].time, zone), lastDate).toInt().takeIf { it > 0 }
        }

        return SeriesAnalysis(
            type = type,
            stats = stats,
            volatility = volatility,
            shortTerm = shortTerm,
            longTerm = longTerm,
            ratePerMonth = rate,
            plateauDays = plateau,
            confidence = if (points.size >= 10) Confidence.HIGH else Confidence.LOW,
        )
    }

    fun classify(weight: Trend, fat: Trend, muscle: Trend): CompositionPattern = when {
        fat == Trend.DOWN && muscle == Trend.UP -> CompositionPattern.RECOMPOSITION
        fat == Trend.DOWN && muscle != Trend.DOWN -> CompositionPattern.FAT_LOSS
        fat == Trend.DOWN -> CompositionPattern.WEIGHT_LOSS_MIXED
        muscle == Trend.UP && fat == Trend.UP -> CompositionPattern.MUSCLE_AND_FAT_GAIN
        muscle == Trend.UP -> CompositionPattern.MUSCLE_GAIN
        fat == Trend.UP -> CompositionPattern.FAT_GAIN
        muscle == Trend.DOWN -> CompositionPattern.WEIGHT_LOSS_MIXED
        weight == Trend.DOWN && muscle == Trend.DOWN -> CompositionPattern.WEIGHT_LOSS_MIXED
        else -> CompositionPattern.STABLE
    }

    private fun List<Float>.trend(): Trend {
        val q = (size / 4).coerceAtLeast(1)
        return Analytics.classifyTrend(takeLast(q).average().toFloat() - take(q).average().toFloat(), average().toFloat())
    }

    fun composition(
        sorted: List<Measurement>,
        weight: MetricType?,
        fat: MetricType?,
        muscle: MetricType?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CompositionInsight? {
        if (weight == null || fat == null || muscle == null) return null
        val start = today.minusDays(COMPOSITION_WINDOW_DAYS)
        val rows = sorted.filter { date(it.timestamp, zone) >= start }.mapNotNull { m ->
            val w = m.number(weight) ?: return@mapNotNull null
            val f = m.number(fat) ?: return@mapNotNull null
            val mu = m.number(muscle) ?: return@mapNotNull null
            Triple(w, f, mu)
        }
        if (rows.isEmpty()) return null
        if (rows.size < COMPOSITION_MIN) {
            return CompositionInsight(Trend.STABLE, Trend.STABLE, Trend.STABLE, CompositionPattern.STABLE, 0f, 0f, rows.size, Confidence.INSUFFICIENT)
        }
        val w = rows.map { it.first }.trend()
        val f = rows.map { it.second }.trend()
        val m = rows.map { it.third }.trend()
        return CompositionInsight(
            weight = w,
            fat = f,
            muscle = m,
            pattern = classify(w, f, m),
            fatDelta = rows.last().second - rows.first().second,
            muscleDelta = rows.last().third - rows.first().third,
            basedOn = rows.size,
            confidence = if (rows.size >= COMPOSITION_MIN * 2) Confidence.HIGH else Confidence.LOW,
        )
    }

    fun weekday(points: List<Point>, zone: ZoneId = ZoneId.systemDefault()): WeekdayInsight? {
        val byDay = points.groupBy({ date(it.time, zone).dayOfWeek }, { it.value })
        val counts = DayOfWeek.entries.associateWith { byDay[it]?.size ?: 0 }
        if (counts.values.any { it < WEEKDAY_MIN }) return null
        val mean = points.map { it.value }.average().toFloat()
        val deviation = byDay.mapValues { (_, vs) -> vs.average().toFloat() - mean }
        return WeekdayInsight(
            deviationByDay = deviation,
            heaviest = deviation.maxByOrNull { it.value }?.key,
            lightest = deviation.minByOrNull { it.value }?.key,
            confidence = if (counts.values.all { it >= WEEKDAY_HIGH }) Confidence.HIGH else Confidence.LOW,
        )
    }

    fun seasonal(points: List<Point>, zone: ZoneId = ZoneId.systemDefault()): SeasonalInsight? {
        if (points.isEmpty()) return null
        val years = points.map { date(it.time, zone).year }.toSet().size
        if (years < 2) return null
        val byMonth = points.groupBy({ date(it.time, zone).month }, { it.value }).mapValues { it.value.average().toFloat() }
        return SeasonalInsight(byMonth, byMonth.maxByOrNull { it.value }?.key, byMonth.minByOrNull { it.value }?.key, years)
    }

    fun anomalies(sorted: List<Measurement>, type: MetricType, zone: ZoneId = ZoneId.systemDefault()): List<Anomaly> {
        val out = mutableListOf<Anomaly>()
        val window = ArrayDeque<Float>()
        var prev: LocalDate? = null
        for (m in sorted) {
            val value = m.number(type) ?: continue
            val d = date(m.timestamp, zone)
            if (prev != null && ChronoUnit.DAYS.between(prev, d) > ANOMALY_GAP_DAYS) window.clear()
            prev = d
            if (window.size >= ANOMALY_WINDOW) {
                val mean = window.average().toFloat()
                val sd = Analytics.stdDev(window)
                if (sd > 0f) {
                    val z = abs((value - mean) / sd)
                    if (z >= ANOMALY_Z) out += Anomaly(m.id, m.timestamp, value, mean, z)
                }
                window.removeFirst()
            }
            window.addLast(value)
        }
        return out.sortedByDescending { it.time }
    }
}
