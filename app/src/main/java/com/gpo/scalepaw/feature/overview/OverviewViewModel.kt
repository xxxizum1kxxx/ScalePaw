package com.gpo.scalepaw.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpo.scalepaw.data.model.Measurement
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.MetricValue
import com.gpo.scalepaw.data.model.Metrics
import com.gpo.scalepaw.data.model.User
import com.gpo.scalepaw.data.model.UserGoal
import com.gpo.scalepaw.data.repository.Session
import com.gpo.scalepaw.data.repository.SessionState
import com.gpo.scalepaw.domain.Analytics
import com.gpo.scalepaw.domain.Evaluation
import com.gpo.scalepaw.domain.EvaluationResult
import com.gpo.scalepaw.domain.Point
import com.gpo.scalepaw.domain.evaluate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

/** The newest value of [type], taken from [source], which may be older than the latest entry. */
data class Tile(val type: MetricType, val value: MetricValue, val source: Measurement, val delta: Float?, val evaluation: EvaluationResult?)

data class GoalProgress(val goal: UserGoal, val start: Float, val remaining: Float, val fraction: Float)

data class OverviewUiState(
    val loaded: Boolean = false,
    val user: User? = null,
    val latest: Measurement? = null,
    val headline: MetricType? = null,
    val headlineValue: Float? = null,
    val headlineDelta: Float? = null,
    val headlineEvaluation: EvaluationResult? = null,
    val goal: GoalProgress? = null,
    val recentSeries: List<Point> = emptyList(),
    val tiles: List<Tile> = emptyList(),
    val recent: List<Measurement> = emptyList(),
    val pinned: List<MetricType> = emptyList(),
    val all: List<Measurement> = emptyList(),
)

class OverviewViewModel(session: Session) : ViewModel() {
    val state: StateFlow<OverviewUiState> = session.state
        .map(::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    private fun build(s: SessionState): OverviewUiState {
        val user = s.user ?: return OverviewUiState(loaded = s.loaded)
        val latest = s.measurements.firstOrNull()
        val weight = s.type(Metrics.WEIGHT)?.takeIf { it.isVisible } ?: s.numericTypes.firstOrNull()
        val value = latest?.number(weight)
        val delta = if (latest != null && weight != null) Analytics.previousDifference(s.measurements, weight, latest) else null
        val since = (latest?.timestamp ?: 0L) - TimeUnit.DAYS.toMillis(RECENT_DAYS)
        val series = weight?.let { t -> Analytics.series(s.measurements.filter { it.timestamp >= since }, t) }.orEmpty()
        val tiles = s.numericTypes.filter { it.id != weight?.id }.mapNotNull { t ->
            val source = s.measurements.firstOrNull { m -> m.values[t.id]?.isEmpty == false } ?: return@mapNotNull null
            val v = source.values.getValue(t.id)
            Tile(
                type = t,
                value = v,
                source = source,
                delta = Analytics.previousDifference(s.measurements, t, source),
                evaluation = v.number?.let { Evaluation.evaluate(t, it, user, source.timestamp) },
            )
        }
        return OverviewUiState(
            loaded = true,
            user = user,
            latest = latest,
            headline = weight,
            headlineValue = value,
            headlineDelta = delta,
            headlineEvaluation = if (weight != null && value != null && latest != null) Evaluation.evaluate(weight, value, user, latest.timestamp) else null,
            goal = weight?.let { t -> s.goal(t.id)?.let { progress(it, s.measurements, t, value) } },
            recentSeries = series,
            tiles = tiles,
            recent = s.measurements.take(RECENT_ROWS),
            pinned = s.visibleTypes.filter { it.isPinned && it.id != weight?.id },
            all = s.measurements,
        )
    }

    private fun progress(goal: UserGoal, measurements: List<Measurement>, type: MetricType, current: Float?): GoalProgress? {
        current ?: return null
        val start = measurements.lastOrNull { it.timestamp >= goal.startDate }?.number(type)
            ?: measurements.lastOrNull()?.number(type)
            ?: return null
        val total = start - goal.goalValue
        val fraction = if (total == 0f) 1f else ((start - current) / total).coerceIn(0f, 1f)
        return GoalProgress(goal, start, current - goal.goalValue, fraction)
    }

    private companion object {
        const val RECENT_DAYS = 30L
        const val RECENT_ROWS = 5
    }
}
