package com.izum1k.scalepaw.feature.withings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.izum1k.scalepaw.AppContainer
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.WithingsKeys
import com.izum1k.scalepaw.ble.withings.WithingsHandler
import com.izum1k.scalepaw.data.withings.WithingsAccountClient
import com.izum1k.scalepaw.data.withings.WithingsFailure
import com.izum1k.scalepaw.data.withings.WithingsResult
import com.izum1k.scalepaw.data.withings.WithingsScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WithingsKeyRow(val scale: WithingsScale, val matches: Boolean) {
    val label: String get() = WithingsHandler.displayName(scale.model, "")
    val usable: Boolean get() = WithingsKeys.isValid(scale.kl)
}

data class WithingsKeyUiState(
    /** Null until a sign-in has listed the account's scales. */
    val rows: List<WithingsKeyRow>? = null,
    val busy: Boolean = false,
    @param:StringRes val error: Int? = null,
    val errorDetail: String? = null,
)

class WithingsKeyViewModel(private val container: AppContainer, private val address: String) : ViewModel() {
    private val client = WithingsAccountClient()
    private val scaleMac = WithingsAccountClient.normalizeMac(address)

    private val _state = MutableStateFlow(WithingsKeyUiState())
    val state: StateFlow<WithingsKeyUiState> = _state.asStateFlow()

    private val _applied = Channel<Unit>(Channel.CONFLATED)
    val applied = _applied.receiveAsFlow()

    fun signIn(email: String, password: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                when (val result = client.fetchScales(email.trim(), password)) {
                    is WithingsResult.Success -> _state.update { s ->
                        val rows = result.scales
                            .map { WithingsKeyRow(it, matches = scaleMac != null && WithingsAccountClient.normalizeMac(it.mac) == scaleMac) }
                            .sortedWith(compareByDescending<WithingsKeyRow> { it.matches }.thenByDescending { it.usable })
                        s.copy(rows = rows, busy = false)
                    }
                    is WithingsResult.Failed -> fail(result.reason.message, result.status?.toString())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(R.string.withings_error_network, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun restart() = _state.update { WithingsKeyUiState() }

    fun apply(row: WithingsKeyRow) {
        viewModelScope.launch {
            if (container.scales.applyWithingsKey(address, row.scale.kl)) _applied.trySend(Unit) else fail(R.string.withings_error_apply)
        }
    }

    private fun fail(@StringRes message: Int, detail: String? = null) =
        _state.update { it.copy(busy = false, error = message, errorDetail = detail) }

    private val WithingsFailure.message: Int
        get() = when (this) {
            WithingsFailure.INVALID_CREDENTIALS -> R.string.withings_error_credentials
            WithingsFailure.NO_SESSION -> R.string.withings_error_session
            WithingsFailure.UNEXPECTED -> R.string.withings_error_unexpected
        }
}
