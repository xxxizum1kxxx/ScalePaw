package com.izum1k.scalepaw.feature.googlehealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.izum1k.scalepaw.AppContainer
import com.izum1k.scalepaw.data.googlehealth.GoogleHealthError
import com.izum1k.scalepaw.data.googlehealth.GoogleHealthException
import com.izum1k.scalepaw.data.googlehealth.GoogleHealthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GoogleHealthBusy { CONNECTING, SYNCING, DISCONNECTING }

class GoogleHealthViewModel(private val container: AppContainer) : ViewModel() {
    private val health = container.googleHealth
    val state: StateFlow<GoogleHealthState> = health.store.state

    private val action = MutableStateFlow<GoogleHealthBusy?>(null)
    val busy: StateFlow<GoogleHealthBusy?> = combine(action, health.syncing) { a, syncing -> a ?: GoogleHealthBusy.SYNCING.takeIf { syncing } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Failures of connect and disconnect; sync failures are kept in [state] instead. */
    private val _error = MutableStateFlow<GoogleHealthException?>(null)
    val error: StateFlow<GoogleHealthException?> = _error.asStateFlow()

    private val _openUrl = Channel<String>(Channel.BUFFERED)
    val openUrl = _openUrl.receiveAsFlow()

    private var job: Job? = null

    fun saveCredentials(clientId: String, clientSecret: String) {
        health.store.setCredentials(clientId, clientSecret)
        _error.value = null
    }

    fun connect() = run(GoogleHealthBusy.CONNECTING) {
        val userId = state.value.linkedUserId
            ?: container.session.state.value.user?.id
            ?: throw GoogleHealthException(GoogleHealthError.FAILED, "no user")
        health.connect(userId) { url -> _openUrl.trySend(url) }
    }

    fun cancel() {
        job?.cancel()
        action.value = null
    }

    fun disconnect(deleteRemote: Boolean) = run(GoogleHealthBusy.DISCONNECTING) { health.disconnect(deleteRemote) }

    fun syncNow() {
        health.requestSync()
    }

    fun resyncAll() {
        health.requestSync(everything = true)
    }

    fun setLinkedUser(id: Long) = health.setLinkedUser(id)

    private fun run(kind: GoogleHealthBusy, block: suspend () -> Unit) {
        if (busy.value != null) return
        action.value = kind
        _error.value = null
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoogleHealthException) {
                _error.value = e
            } catch (e: Exception) {
                _error.value = GoogleHealthException(GoogleHealthError.FAILED, e.message ?: e.javaClass.simpleName)
            } finally {
                action.value = null
            }
        }
    }
}
