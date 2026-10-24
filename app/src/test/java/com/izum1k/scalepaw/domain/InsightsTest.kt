package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class InsightsTest {
    private val utc = ZoneOffset.UTC
    private val weight = MetricType.seed(Metrics.WEIGHT, 0).copy(id = 1)
    private val start = LocalDate.of(2025, 1, 6)

    private fun time(day: LocalDate) = day.atStartOfDay(utc).toInstant().toEpochMilli() + 7 * 3_600_000

    private fun daily(values: List<Float>) = values.mapIndexed { i, v ->
        Measurement(id = i + 1L, userId = 1, timestamp = time(start.plusDays(i.toLong())), values = mapOf(weight.id to MetricValue.of(v)))
    }

    @Test
    fun should_report_downward_trend_and_rate_when_weight_falls() {
        val points = Analytics.series(daily(List(60) { 90f - it * 0.1f }), weight)
        val analysis = Insights.analyze(points, weight, utc)!!
        assertEquals(Trend.DOWN, analysis.longTerm)
        assertEquals(Trend.DOWN, analysis.shortTerm)
        assertEquals(-3.05f, analysis.ratePerMonth, 0.05f)
        assertEquals(Volatility.STABLE, analysis.volatility)
        assertEquals(Confidence.HIGH, analysis.confidence)
    }

    @Test
    fun should_detect_plateau_when_recent_values_flat() {
        val values = List(20) { 90f } + List(15) { 80.2f + (it % 2) * 0.1f }
        val analysis = Insights.analyze(Analytics.series(daily(values), weight), weight, utc)!!
        assertEquals(14, analysis.plateauDays)
    }

    @Test
    fun should_return_null_when_fewer_than_two_points() {
        assertNull(Insights.analyze(Analytics.series(daily(listOf(80f)), weight), weight, utc))
    }

    @Test
    fun should_classify_recomposition_when_fat_falls_and_muscle_rises() {
        assertEquals(CompositionPattern.RECOMPOSITION, Insights.classify(Trend.STABLE, Trend.DOWN, Trend.UP))
        assertEquals(CompositionPattern.FAT_LOSS, Insights.classify(Trend.DOWN, Trend.DOWN, Trend.STABLE))
        assertEquals(CompositionPattern.MUSCLE_AND_FAT_GAIN, Insights.classify(Trend.UP, Trend.UP, Trend.UP))
        assertEquals(CompositionPattern.STABLE, Insights.classify(Trend.STABLE, Trend.STABLE, Trend.STABLE))
    }

    @Test
    fun should_flag_outlier_when_far_from_recent_window() {
        val values = List(14) { 80f + (it % 3) * 0.2f }.toMutableList().apply { this[10] = 86f }
        val anomalies = Insights.anomalies(daily(values), weight, utc)
        assertEquals(1, anomalies.size)
        assertEquals(11L, anomalies.single().measurementId)
        assertTrue(anomalies.single().zScore >= 2.5f)
    }

    @Test
    fun should_find_heaviest_weekday_when_every_day_has_two_entries() {
        val values = List(14) { i -> if (start.plusDays(i.toLong()).dayOfWeek == DayOfWeek.MONDAY) 81f else 80f }
        val insight = Insights.weekday(Analytics.series(daily(values), weight), utc)!!
        assertEquals(DayOfWeek.MONDAY, insight.heaviest)
        assertEquals(Confidence.LOW, insight.confidence)
    }

    @Test
    fun should_skip_weekday_insight_when_a_day_lacks_data() {
        assertNull(Insights.weekday(Analytics.series(daily(List(10) { 80f }), weight), utc))
    }

    @Test
    fun should_require_two_years_when_computing_seasons() {
        assertNull(Insights.seasonal(Analytics.series(daily(List(30) { 80f }), weight), utc))
    }
}
