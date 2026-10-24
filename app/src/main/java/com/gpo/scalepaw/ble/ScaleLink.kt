package com.gpo.scalepaw.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.gpo.scalepaw.ble.openscale.BluetoothEvent.UserInteractionType
import com.gpo.scalepaw.ble.openscale.data.ScaleUser
import com.gpo.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.gpo.scalepaw.ble.openscale.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** What a link reports to [ScaleManager]. */
sealed interface LinkState {
    data object Connecting : LinkState
    data class Connected(val name: String?) : LinkState
    data object Listening : LinkState
    data class Message(val text: String) : LinkState
    /** The session is over; [error] is null when it ended normally. */
    data class Closed(val error: String?) : LinkState
}

/** Everything a link needs from the app to drive one driver session. */
class LinkContext(
    val context: Context,
    val handler: ScaleDeviceHandler,
    val callbacks: ScaleDeviceHandler.Callbacks,
    val settings: ScaleDeviceHandler.DriverSettings,
    val data: ScaleDeviceHandler.DataProvider,
    val user: ScaleUser,
    val listener: (LinkState) -> Unit,
)

/**
 * One session between a driver and a scale over a concrete transport. All driver calls run on a
 * single serial dispatcher, so drivers never see concurrent callbacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ScaleLink(protected val link: LinkContext) {
    protected val tag: String = this::class.simpleName ?: "ScaleLink"
    protected val context: Context get() = link.context
    protected val handler: ScaleDeviceHandler get() = link.handler

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    protected val adapter: BluetoothAdapter? =
        (link.context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var attached = false
    private var closed = false

    /** Starts the session; [device] is the advertisement's device when a scan already found it. */
    abstract fun start(address: String, device: BluetoothDevice?)

    /** Ends the session; [LinkState.Closed] follows once the transport is released. */
    abstract fun stop()

    fun feedback(type: UserInteractionType, appUserId: Int, data: Any) {
        scope.launch {
            runCatching { handler.onUserInteractionFeedback(type, appUserId, data) }
                .onFailure { LogManager.e(tag, "Delivering user feedback failed", it) }
        }
    }

    protected fun attach(transport: ScaleDeviceHandler.Transport) {
        handler.attach(transport, link.callbacks, link.settings, link.data, scope)
        attached = true
    }

    protected fun emit(state: LinkState) = link.listener(state)

    /** Releases the driver and reports the end of the session; idempotent. */
    protected fun close(error: String?) {
        if (closed) return
        closed = true
        if (attached) {
            runCatching { handler.handleDisconnected() }
            runCatching { handler.detach() }
        }
        emit(LinkState.Closed(error))
        scope.cancel()
    }

    protected val isClosed: Boolean get() = closed
}
