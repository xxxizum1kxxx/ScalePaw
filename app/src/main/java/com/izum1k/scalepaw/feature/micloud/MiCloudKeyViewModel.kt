package com.izum1k.scalepaw.feature.micloud

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.izum1k.scalepaw.AppContainer
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.MiCloudKeys
import com.izum1k.scalepaw.data.micloud.MiCloudClient
import com.izum1k.scalepaw.data.micloud.MiCloudCrypto
import com.izum1k.scalepaw.data.micloud.MiCloudDevice
import com.izum1k.scalepaw.data.micloud.MiCloudFailure
import com.izum1k.scalepaw.data.micloud.MiCloudLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginMethod { WEB, PASSWORD, QR }

sealed interface MiCloudStep {
    data object Login : MiCloudStep
    class Captcha(val image: ByteArray) : MiCloudStep
    data object EmailCode : MiCloudStep
    class Qr(val image: ByteArray, val loginUrl: String) : MiCloudStep
    data object Searching : MiCloudStep
    data class Devices(val devices: List<MiCloudKeyRow>) : MiCloudStep
}

data class MiCloudKeyRow(val device: MiCloudDevice, val matches: Boolean) {
    val usable: Boolean get() = MiCloudKeys.isValid(device.beaconKey)
}

data class MiCloudUiState(
    val step: MiCloudStep = MiCloudStep.Login,
    val method: LoginMethod = LoginMethod.WEB,
    /** Null searches every server. */
    val server: String? = null,
    val busy: Boolean = false,
    @param:StringRes val error: Int? = null,
    val errorDetail: String? = null,
)

class MiCloudKeyViewModel(private val container: AppContainer, private val address: String) : ViewModel() {
    private val client = MiCloudClient()
    private val scaleMac = MiCloudCrypto.normalizeMac(address)
    private var job: Job? = null

    private val _state = MutableStateFlow(MiCloudUiState())
    val state: StateFlow<MiCloudUiState> = _state.asStateFlow()

    private val _applied = Channel<Unit>(Channel.CONFLATED)
    val applied = _applied.receiveAsFlow()

    fun setMethod(method: LoginMethod) = _state.update { it.copy(method = method, error = null) }

    fun setServer(server: String?) = _state.update { it.copy(server = server) }

    fun webSignedIn(accountCookies: Map<String, String>) = run {
        _state.update { it.copy(step = MiCloudStep.Searching) }
        handle(client.webLogin(accountCookies))
    }

    fun signIn(username: String, password: String) = run { handle(client.passwordLogin(username.trim(), password)) }

    fun submitCaptcha(code: String) = run { handle(client.submitCaptcha(code.trim())) }

    fun submitEmailCode(code: String) = run { handle(client.submitEmailCode(code.trim())) }

    fun startQr() = run {
        val qr = client.startQrLogin()
        if (qr == null) {
            fail(R.string.micloud_error_unexpected)
            return@run
        }
        _state.update { it.copy(step = MiCloudStep.Qr(qr.image, qr.loginUrl), busy = false) }
        handle(client.awaitQrLogin())
    }

    fun restart() {
        job?.cancel()
        _state.update { it.copy(step = MiCloudStep.Login, busy = false, error = null, errorDetail = null) }
    }

    fun apply(row: MiCloudKeyRow) {
        val key = row.device.beaconKey ?: return
        viewModelScope.launch {
            if (container.scales.applyBindKey(address, key)) _applied.trySend(Unit) else fail(R.string.micloud_error_apply)
        }
    }

    private suspend fun handle(result: MiCloudLogin) {
        when (result) {
            MiCloudLogin.Success -> loadDevices()
            is MiCloudLogin.NeedCaptcha -> _state.update { it.copy(step = MiCloudStep.Captcha(result.image), busy = false) }
            MiCloudLogin.NeedEmailCode -> _state.update { it.copy(step = MiCloudStep.EmailCode, busy = false) }
            is MiCloudLogin.Failed -> fail(result.reason.message, keepStep = result.reason.retryInPlace)
        }
    }

    private suspend fun loadDevices() {
        _state.update { it.copy(step = MiCloudStep.Searching, busy = true) }
        val servers = _state.value.server?.let(::listOf) ?: MiCloudClient.SERVERS
        val rows = client.devices(servers)
            .filter { "blt" in it.did }
            .map { MiCloudKeyRow(it, matches = scaleMac != null && MiCloudCrypto.normalizeMac(it.mac) == scaleMac) }
            .sortedWith(compareByDescending<MiCloudKeyRow> { it.matches }.thenByDescending { it.usable }.thenBy { it.device.name })
        _state.update { it.copy(step = MiCloudStep.Devices(rows), busy = false) }
    }

    private fun fail(@StringRes message: Int, detail: String? = null, keepStep: Boolean = false) = _state.update {
        it.copy(
            step = if (keepStep || it.step is MiCloudStep.Devices) it.step else MiCloudStep.Login,
            busy = false,
            error = message,
            errorDetail = detail,
        )
    }

    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, errorDetail = null) }
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(R.string.micloud_error_network, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private val MiCloudFailure.message: Int
        get() = when (this) {
            MiCloudFailure.INVALID_USER -> R.string.micloud_error_user
            MiCloudFailure.INVALID_CREDENTIALS -> R.string.micloud_error_credentials
            MiCloudFailure.INVALID_CAPTCHA -> R.string.micloud_error_captcha
            MiCloudFailure.INVALID_CODE -> R.string.micloud_error_code
            MiCloudFailure.NO_TOKEN -> R.string.micloud_error_token
            MiCloudFailure.QR_EXPIRED -> R.string.micloud_error_qr_expired
            MiCloudFailure.UNEXPECTED -> R.string.micloud_error_unexpected
        }

    private val MiCloudFailure.retryInPlace: Boolean
        get() = this == MiCloudFailure.INVALID_CODE
}
