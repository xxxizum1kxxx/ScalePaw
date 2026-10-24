package com.gpo.scalepaw.feature.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpo.scalepaw.data.model.InputType
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.MetricValue
import com.gpo.scalepaw.data.model.UnitType
import com.gpo.scalepaw.data.repository.ScaleRepository
import com.gpo.scalepaw.data.repository.Session
import com.gpo.scalepaw.ui.component.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class EditField(val type: MetricType, val text: String, val hint: String?, val error: Boolean = false)

data class MeasurementEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val fields: List<EditField> = emptyList(),
    val derived: List<MetricType> = emptyList(),
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val canSave: Boolean get() = !saving && fields.none { it.error } && fields.any { it.text.isNotBlank() }
}

class MeasurementEditViewModel(
    private val id: Long,
    private val session: Session,
    private val repository: ScaleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MeasurementEditUiState())
    val state: StateFlow<MeasurementEditUiState> = _state.asStateFlow()
    private var userId = 0L

    init {
        viewModelScope.launch {
            val s = session.state.first { it.loaded }
            val existing = if (id != 0L) repository.measurement(id) else null
            userId = existing?.userId ?: s.user?.id ?: 0L
            val latest = s.measurements.firstOrNull()
            val editable = s.visibleTypes.filter { !it.isDerived }
            _state.value = MeasurementEditUiState(
                loaded = true,
                isNew = existing == null,
                timestamp = existing?.timestamp ?: System.currentTimeMillis(),
                fields = editable.map { t ->
                    EditField(
                        type = t,
                        text = existing?.values?.get(t.id)?.let { format(it, t) }.orEmpty(),
                        hint = latest?.values?.get(t.id)?.let { format(it, t) },
                    )
                },
                derived = s.visibleTypes.filter { it.isDerived },
            )
        }
    }

    fun setText(typeId: Long, text: String) = _state.update { s ->
        s.copy(fields = s.fields.map { f -> if (f.type.id == typeId) f.copy(text = text, error = parse(text, f.type) == null && text.isNotBlank()) else f })
    }

    fun setTimestamp(timestamp: Long) = _state.update { it.copy(timestamp = timestamp) }

    fun save() {
        val s = _state.value
        if (!s.canSave || userId == 0L) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val values = s.fields.mapNotNull { f -> parse(f.text, f.type)?.let { f.type.id to it } }.toMap()
            repository.saveMeasurement(userId, s.timestamp, values, id.takeIf { it != 0L })
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() {
        if (id == 0L) return
        viewModelScope.launch {
            repository.deleteMeasurement(id)
            _state.update { it.copy(done = true) }
        }
    }

    companion object {
        fun format(value: MetricValue, type: MetricType): String = when (type.inputType) {
            InputType.TEXT -> value.text.orEmpty()
            InputType.INT -> value.int?.toString() ?: value.number?.toInt()?.toString().orEmpty()
            InputType.FLOAT -> value.number?.let { if (type.unit == UnitType.ST) Fmt.value(it, type) else plain(it) }.orEmpty()
        }

        private fun plain(v: Float): String = String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')

        /** Accepts either decimal separator; stone also accepts `st:lb`. Null for invalid input. */
        fun parse(text: String, type: MetricType): MetricValue? {
            val t = text.trim()
            if (t.isEmpty()) return null
            return when (type.inputType) {
                InputType.TEXT -> MetricValue.of(t)
                InputType.INT -> t.toIntOrNull()?.let { MetricValue.of(it) }
                InputType.FLOAT -> {
                    if (type.unit == UnitType.ST && ':' in t) {
                        val (st, lb) = t.split(':', limit = 2)
                        val s = st.toIntOrNull() ?: return null
                        val l = lb.toFloatOrNull() ?: return null
                        if (l < 0f || l >= 14f) return null
                        MetricValue.of(s + l / 14f)
                    } else {
                        t.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.let { MetricValue.of(it) }
                    }
                }
            }
        }
    }
}
