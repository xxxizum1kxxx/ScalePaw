package com.izum1k.scalepaw.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.izum1k.scalepaw.data.repository.ScaleRepository
import com.izum1k.scalepaw.data.repository.Session
import com.izum1k.scalepaw.data.settings.AppSettings
import com.izum1k.scalepaw.data.settings.AppearanceSettings
import com.izum1k.scalepaw.data.settings.AssignmentSettings
import com.izum1k.scalepaw.data.settings.ChartSettings
import com.izum1k.scalepaw.data.settings.SettingsRepository
import com.izum1k.scalepaw.domain.FormulaSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Preference pages: read the session's settings, write through the repository. */
class SettingsViewModel(
    session: Session,
    private val settings: SettingsRepository,
    private val repository: ScaleRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> = session.state
        .map { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value.settings)

    fun appearance(change: (AppearanceSettings) -> AppearanceSettings) = launch { settings.setAppearance(change(state.value.appearance)) }

    fun chart(change: (ChartSettings) -> ChartSettings) = launch { settings.setChart(change(state.value.chart)) }

    fun assignment(change: (AssignmentSettings) -> AssignmentSettings) = launch { settings.setAssignment(change(state.value.assignment)) }

    fun haptics(enabled: Boolean) = launch { settings.setHaptics(enabled) }

    /** Formulas change stored values, so every user's history is recalculated. */
    fun formulas(change: (FormulaSettings) -> FormulaSettings) = launch {
        val old = state.value.formulas
        val new = change(old)
        settings.setFormulas(new)
        if (new.copy(carryOverValues = false) != old.copy(carryOverValues = false)) repository.recalculateAll()
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
