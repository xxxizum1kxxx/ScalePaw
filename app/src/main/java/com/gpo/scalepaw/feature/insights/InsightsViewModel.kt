package com.gpo.scalepaw.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.Metrics
import com.gpo.scalepaw.data.repository.Session
import com.gpo.scalepaw.domain.InsightReport
import com.gpo.scalepaw.domain.Insights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class InsightsUiState(
    val loaded: Boolean = false,
    val types: List<MetricType> = emptyList(),
    val primary: MetricType? = null,
    val report: InsightReport? = null,
    val fat: MetricType? = null,
    val muscle: MetricType? = null,
)

class InsightsViewModel(session: Session) : ViewModel() {
    private val primaryId = MutableStateFlow<Long?>(null)

    val state: StateFlow<InsightsUiState> = combine(session.state, primaryId) { s, chosen ->
        if (!s.loaded) return@combine InsightsUiState()
        val types = s.numericTypes
        val weight = s.type(Metrics.WEIGHT)
        val primary = types.firstOrNull { it.id == chosen } ?: weight?.takeIf { it.isVisible } ?: types.firstOrNull()
            ?: return@combine InsightsUiState(loaded = true)
        val fat = s.type(Metrics.BODY_FAT)?.takeIf { it.isEnabled }
        val muscle = s.type(Metrics.MUSCLE)?.takeIf { it.isEnabled }
        InsightsUiState(
            loaded = true,
            types = types,
            primary = primary,
            report = Insights.report(s.measurements, primary, fat, muscle, weight),
            fat = fat,
            muscle = muscle,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun select(typeId: Long) {
        primaryId.value = typeId
    }
}
