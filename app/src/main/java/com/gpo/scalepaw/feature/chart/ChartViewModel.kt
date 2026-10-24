package com.gpo.scalepaw.feature.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpo.scalepaw.data.model.AggregationLevel
import com.gpo.scalepaw.data.model.Measurement
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.Metrics
import com.gpo.scalepaw.data.model.SmoothingAlgorithm
import com.gpo.scalepaw.data.model.TimeRange
import com.gpo.scalepaw.data.repository.Session
import com.gpo.scalepaw.data.repository.SessionState
import com.gpo.scalepaw.data.settings.ChartSettings
import com.gpo.scalepaw.data.settings.SettingsRepository
import com.gpo.scalepaw.domain.Analytics
import com.gpo.scalepaw.domain.Point
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class SeriesData(
    val type: MetricType,
    val points: List<Point>,
    val smoothed: List<Float>?,
    val trend: Pair<Double, Double>?,
    val goal: Float?,
)

data class ChartUiState(
    val loaded: Boolean = false,
    val types: List<MetricType> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val settings: ChartSettings = ChartSettings(),
    val series: List<SeriesData> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
)

class ChartViewModel(session: Session, private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<ChartUiState> = session.state
        .map(::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartUiState())

    private fun build(s: SessionState): ChartUiState {
        val chart = s.settings.chart
        val types = s.numericTypes
        val selected = chart.selectedTypeIds.filter { id -> types.any { it.id == id } }.toSet()
            .ifEmpty { setOfNotNull(s.type(Metrics.WEIGHT)?.id ?: types.firstOrNull()?.id) }
        val inRange = filterRange(s.measurements, chart.range)
        val series = types.filter { it.id in selected }.map { type ->
            val points = if (chart.aggregation == AggregationLevel.NONE) {
                Analytics.series(inRange, type)
            } else {
                Analytics.aggregate(inRange, listOf(type), chart.aggregation)
                    .mapNotNull { e -> e.averages[type.id]?.let { Point(e.start, it) } }
                    .sortedBy { it.time }
            }
            SeriesData(
                type = type,
                points = points,
                smoothed = Analytics.smooth(points.map { it.value }, chart.smoothing, chart.smoothingWindow, chart.smoothingAlpha)
                    .takeIf { chart.smoothing != SmoothingAlgorithm.NONE },
                trend = if (chart.showTrendLine) Analytics.linearFit(points) else null,
                goal = s.goal(type.id)?.goalValue,
            )
        }
        return ChartUiState(true, types, selected, chart, series, inRange)
    }

    fun toggle(typeId: Long) = update { c ->
        val current = state.value.selected
        val next = if (typeId in current) current - typeId else current + typeId
        c.copy(selectedTypeIds = next.ifEmpty { current })
    }

    fun setRange(range: TimeRange) = update { it.copy(range = range) }

    fun setAggregation(level: AggregationLevel) = update { it.copy(aggregation = level) }

    private fun update(change: (ChartSettings) -> ChartSettings) {
        viewModelScope.launch { settings.setChart(change(state.value.settings)) }
    }

    companion object {
        fun filterRange(measurements: List<Measurement>, range: TimeRange): List<Measurement> {
            val days = range.days ?: return measurements
            val newest = measurements.maxOfOrNull { it.timestamp } ?: return measurements
            val since = newest - TimeUnit.DAYS.toMillis(days)
            return measurements.filter { it.timestamp >= since }
        }
    }
}
