package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.AggregationLevel
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.SmoothingAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnalyticsTest {
    private val weight = MetricType.seed(Metrics.WEIGHT, 0).copy(id = 1)
    private val utc = ZoneOffset.UTC

    private fun at(date: String, kg: Float, id: Long = 0) = Measurement(
        id = id,
        userId = 1,
        timestamp = LocalDate.parse(date).atStartOfDay(utc).toInstant().toEpochMilli() + 8 * 3_600_000,
        values = mapOf(weight.id to MetricValue.of(kg)),
    )

    @Test
    fun should_summarise_series_when_stats_requested() {
        val points = Analytics.series(listOf(at("2025-01-03", 79f), at("2025-01-01", 80f), at("2025-01-02", 82f)), weight)
        val stats = Analytics.stats(points)!!
        assertEquals(80f, stats.first.value, 0f)
        assertEquals(79f, stats.last.value, 0f)
        assertEquals(82f, stats.max.value, 0f)
        assertEquals(-1f, stats.change, 0f)
        assertEquals(-1.25f, stats.changePercent, 0.001f)
    }

    @Test
    fun should_average_available_points_when_window_not_yet_full() {
        assertEquals(listOf(1f, 1.5f, 2.5f, 3.5f), Analytics.movingAverage(listOf(1f, 2f, 3f, 4f), 2))
    }

    @Test
    fun should_blend_with_alpha_when_smoothing_exponentially() {
        assertEquals(listOf(10f, 15f, 17.5f), Analytics.smooth(listOf(10f, 20f, 20f), SmoothingAlgorithm.EXPONENTIAL, 3, 0.5f))
    }

    @Test
    fun should_return_input_when_smoothing_is_off() {
        val values = listOf(3f, 1f, 2f)
        assertEquals(values, Analytics.smooth(values, SmoothingAlgorithm.NONE, 5, 0.5f))
    }

    @Test
    fun should_fit_line_when_two_points_given() {
        val (slope, intercept) = Analytics.linearFit(listOf(Point(0, 1f), Point(1000, 3f)))!!
        assertEquals(0.002, slope, 1e-9)
        assertEquals(1.0, intercept, 1e-9)
        assertNull(Analytics.linearFit(listOf(Point(0, 1f))))
    }

    @Test
    fun should_group_by_iso_week_when_aggregating_weekly() {
        val periods = Analytics.aggregate(
            listOf(at("2025-03-03", 80f), at("2025-03-09", 82f), at("2025-03-10", 78f)),
            listOf(weight),
            AggregationLevel.WEEK,
            utc,
        )
        assertEquals(2, periods.size)
        assertEquals(1, periods[0].count)
        assertEquals(81f, periods[1].averages[weight.id]!!, 0f)
        assertEquals(LocalDate.parse("2025-03-03").atStartOfDay(utc).toInstant().toEpochMilli(), periods[1].start)
    }

    @Test
    fun should_return_nothing_when_aggregation_is_none() {
        assertEquals(emptyList<PeriodEntry>(), Analytics.aggregate(listOf(at("2025-03-03", 80f)), listOf(weight), AggregationLevel.NONE, utc))
    }

    @Test
    fun should_find_previous_value_when_older_entry_exists() {
        val all = listOf(at("2025-01-01", 80f), at("2025-01-05", 79.5f))
        assertEquals(-0.5f, Analytics.previousDifference(all, weight, all[1])!!, 0.0001f)
        assertNull(Analytics.previousDifference(all, weight, all[0]))
    }

    @Test
    fun should_classify_trend_relative_to_mean() {
        assertEquals(Trend.UP, Analytics.classifyTrend(1f, 100f))
        assertEquals(Trend.DOWN, Analytics.classifyTrend(-1f, 100f))
        assertEquals(Trend.STABLE, Analytics.classifyTrend(0.2f, 100f))
    }
}
