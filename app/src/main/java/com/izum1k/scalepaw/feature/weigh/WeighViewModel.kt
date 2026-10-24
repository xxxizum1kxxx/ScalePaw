package com.izum1k.scalepaw.feature.weigh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.izum1k.scalepaw.ble.ScaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds one weigh-in session for as long as the page is on the back stack. */
class WeighViewModel(private val scales: ScaleManager) : ViewModel() {
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var started = false

    /** Starts the session, or restarts it after access was granted again. */
    fun start() {
        if (started) scales.retry() else scales.startSession()
        started = true
    }

    fun retry() = scales.retry()

    fun reassign(index: Int, userId: Long) = scales.reassign(index, userId)

    fun save() {
        viewModelScope.launch {
            if (scales.saveAll() > 0) _saved.value = true
        }
    }

    override fun onCleared() {
        scales.endSession()
    }
}
