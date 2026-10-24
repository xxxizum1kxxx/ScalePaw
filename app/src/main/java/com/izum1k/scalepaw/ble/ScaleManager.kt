package com.izum1k.scalepaw.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.openscale.BluetoothEvent.UserInteractionType
import com.izum1k.scalepaw.ble.openscale.ScaleFactory
import com.izum1k.scalepaw.ble.openscale.ScannedDeviceInfo
import com.izum1k.scalepaw.ble.openscale.data.ScaleMeasurement
import com.izum1k.scalepaw.ble.openscale.data.ScaleUser
import com.izum1k.scalepaw.ble.openscale.scales.DeviceSupport
import com.izum1k.scalepaw.ble.openscale.scales.LinkMode
import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.izum1k.scalepaw.ble.openscale.scales.TuningProfile
import com.izum1k.scalepaw.ble.openscale.scales.forBroadcast
import com.izum1k.scalepaw.ble.openscale.scales.forGatt
import com.izum1k.scalepaw.ble.openscale.scales.forSpp
import com.izum1k.scalepaw.ble.openscale.util.LogManager
import com.izum1k.scalepaw.ble.openscale.vocab.ActivityLevel as DriverActivity
import com.izum1k.scalepaw.ble.openscale.vocab.Bpm
import com.izum1k.scalepaw.ble.openscale.vocab.GenderType
import com.izum1k.scalepaw.ble.openscale.vocab.Kg
import com.izum1k.scalepaw.ble.openscale.vocab.MeasurementType
import com.izum1k.scalepaw.ble.openscale.vocab.Percent
import com.izum1k.scalepaw.ble.openscale.vocab.UnitValue
import com.izum1k.scalepaw.ble.openscale.vocab.WeightUnit
import com.izum1k.scalepaw.data.model.Gender
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricSpec
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitConversion
import com.izum1k.scalepaw.data.model.UnitType
import com.izum1k.scalepaw.data.model.User
import com.izum1k.scalepaw.data.model.UserGoal
import com.izum1k.scalepaw.data.repository.PendingReading
import com.izum1k.scalepaw.data.repository.Prepared
import com.izum1k.scalepaw.data.repository.ScaleReading
import com.izum1k.scalepaw.data.repository.ScaleRepository
import com.izum1k.scalepaw.data.settings.SavedScale
import com.izum1k.scalepaw.data.settings.SettingsRepository
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the scale connection of a weigh-in stands. */
sealed interface ScaleStatus {
    data object Idle : ScaleStatus
    /** Looking for the saved scale before connecting. */
    data object Scanning : ScaleStatus
    data object Connecting : ScaleStatus
    data class Connected(val name: String) : ScaleStatus
    /** Waiting for a broadcast-only scale to advertise a reading. */
    data class Listening(val name: String) : ScaleStatus
    data class Error(val message: String) : ScaleStatus

    val isActive: Boolean get() = this !is Idle && this !is Error
}

/** A prompt a driver raised; [choices] are (label, scale slot) for [UserInteractionType.CHOOSE_USER]. */
data class ScaleInteraction(
    val type: UserInteractionType,
    val choices: List<Pair<String, Int>> = emptyList(),
)

/** Whether the active scale can be heard right now, as the app bar shows it. */
enum class ScalePresence {
    /** No scale is active. */
    NONE,

    /** Bluetooth is off or its permissions are missing. */
    UNAVAILABLE,

    /** Not checked yet, or the scale cannot be detected without connecting (classic serial). */
    UNKNOWN,
    SEARCHING,
    FOUND,
    MISSING,
}

/** What a weigh-in has gathered so far; nothing is stored until [ScaleManager.saveAll]. */
data class WeighState(
    /** The driver asked the user to step on. */
    val stepOn: Boolean = false,
    /** A broadcast scale sent the first part of a reading. */
    val receiving: Boolean = false,
    /** The unsettled weight some drivers report while measuring. */
    val liveKg: Float? = null,
    /** The latest driver note, already localised. */
    val hint: String? = null,
    val pending: List<PendingReading> = emptyList(),
)

/** Owns the saved scales and the weigh-in session: discovery, pairing, the driver link and its readings. */
class ScaleManager(
    private val context: Context,
    private val repository: ScaleRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    val scanner = ScaleScanner(context, scope)
    private val drivers = DriverStore(context)
    private val _driverRevision = MutableStateFlow(0)

    /** Bumps when driver settings change outside the driver's own UI, so that UI can re-read them. */
    val driverRevision: StateFlow<Int> = _driverRevision.asStateFlow()

    private val _status = MutableStateFlow<ScaleStatus>(ScaleStatus.Idle)
    val status: StateFlow<ScaleStatus> = _status.asStateFlow()

    private val _interaction = MutableStateFlow<ScaleInteraction?>(null)
    val interaction: StateFlow<ScaleInteraction?> = _interaction.asStateFlow()

    private val _weigh = MutableStateFlow(WeighState())
    val weigh: StateFlow<WeighState> = _weigh.asStateFlow()

    private val _presence = MutableStateFlow(ScalePresence.NONE)
    val presence: StateFlow<ScalePresence> = _presence.asStateFlow()
    private val recheck = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var link: ScaleLink? = null
    private var connectJob: Job? = null
    @Volatile private var generation = 0

    @Volatile private var snapshot = Snapshot()

    private class Snapshot(
        val current: ScaleUser? = null,
        val users: List<ScaleUser> = emptyList(),
        val last: Map<Int, ScaleMeasurement> = emptyMap(),
    )

    init {
        scope.launch { settings.current().scale.activeAddress?.let(drivers::adoptLegacy) }
    }

    // ── Preconditions ──────────────────────────────────────────────────────

    val requiredPermissions: Array<String> = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothOn(): Boolean =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true

    // ── Discovery and pairing ──────────────────────────────────────────────

    fun startDiscovery() {
        if (hasPermissions() && isBluetoothOn()) scanner.start()
    }

    fun stopDiscovery() = scanner.stop()

    /** Saves [scale] (or refreshes it when already saved) and makes it the active one. */
    fun pair(scale: DiscoveredScale) {
        scanner.stop()
        scope.launch {
            val known = settings.current().scale.devices.any { it.address == scale.address }
            if (!known) drivers.clear(scale.address)
            val saved = SavedScale(
                address = scale.address,
                name = scale.name.ifBlank { scale.support?.displayName ?: scale.address },
                driverId = scale.support?.displayName,
                snapshot = DeviceSnapshot.encode(scale.info),
            )
            settings.updateScale { s ->
                val devices = if (known) {
                    s.devices.map { if (it.address == saved.address) saved.copy(tuning = it.tuning) else it }
                } else {
                    s.devices + saved
                }
                s.copy(devices = devices, activeAddress = saved.address)
            }
        }
    }

    fun activate(address: String) {
        scope.launch { settings.updateScale { it.copy(activeAddress = address) } }
    }

    /** Forgets the scale at [address] and everything its driver stored; another one takes over if it was active. */
    fun forget(address: String) {
        scope.launch {
            if (settings.current().scale.activeAddress == address) disconnectNow()
            settings.updateScale { s ->
                val devices = s.devices.filterNot { it.address == address }
                val active = s.activeAddress.takeIf { it != address } ?: devices.firstOrNull()?.address
                s.copy(devices = devices, activeAddress = active)
            }
            drivers.clear(address)
        }
    }

    fun setTuning(address: String, profile: TuningProfile?) {
        scope.launch {
            settings.updateScale { s ->
                s.copy(devices = s.devices.map { if (it.address == address) it.copy(tuning = profile?.name) else it })
            }
        }
    }

    fun setDeveloperMode(enabled: Boolean) {
        scope.launch { settings.updateScale { it.copy(developerMode = enabled) } }
    }

    /** The saved scale's advertisement as drivers see it. */
    fun savedDevice(s: SavedScale): ScannedDeviceInfo =
        DeviceSnapshot.decode(s.snapshot) ?: ScannedDeviceInfo(s.name.orEmpty(), s.address, 0, emptyList(), null)

    fun supportFor(s: SavedScale): DeviceSupport? = ScaleFactory.supportFor(savedDevice(s))

    /** A driver instance for showing its configuration UI; its settings are live, its link is not. */
    fun configurationHandler(s: SavedScale): ScaleDeviceHandler? =
        ScaleFactory.resolve(savedDevice(s))?.first?.also { it.attachSettings(drivers.forHandler(s.address, it)) }

    /** Stores a Xiaomi cloud bind key for the saved scale at [address]; false when its driver takes none. */
    suspend fun applyBindKey(address: String, key: String): Boolean {
        val saved = settings.current().scale.devices.firstOrNull { it.address == address } ?: return false
        val handler = ScaleFactory.resolve(savedDevice(saved))?.first ?: return false
        val setting = MiCloudKeys.settingKey(handler) ?: return false
        if (!MiCloudKeys.isValid(key)) return false
        drivers.forHandler(address, handler).putString(setting, key.lowercase())
        _driverRevision.update { it + 1 }
        return true
    }

    /** Stores a Withings account `kl` for the saved scale at [address]; false when its driver takes none. */
    suspend fun applyWithingsKey(address: String, key: String): Boolean {
        val saved = settings.current().scale.devices.firstOrNull { it.address == address } ?: return false
        val handler = ScaleFactory.resolve(savedDevice(saved))?.first ?: return false
        val setting = WithingsKeys.settingKey(handler) ?: return false
        if (!WithingsKeys.isValid(key)) return false
        drivers.forHandler(address, handler).putString(setting, key)
        _driverRevision.update { it + 1 }
        return true
    }

    // ── Presence ───────────────────────────────────────────────────────────

    /**
     * Checks every [PRESENCE_INTERVAL_MS] whether the active scale advertises, until cancelled; run it
     * only while the app is in front. [refreshPresence] checks at once.
     */
    suspend fun watchPresence() {
        settings.settings.map { it.scale.active }.distinctUntilChanged().collectLatest { active ->
            if (active == null) {
                _presence.value = ScalePresence.NONE
                return@collectLatest
            }
            if (active.address != presenceAddress) _presence.value = ScalePresence.UNKNOWN
            presenceAddress = active.address
            var asked = false
            while (true) {
                checkPresence(active, showSearch = asked || _presence.value !in settled)
                asked = withTimeoutOrNull(PRESENCE_INTERVAL_MS) { merge(recheck, adapterChanges()).first() } != null
            }
        }
    }

    private fun adapterChanges() = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private var presenceAddress: String? = null
    private val settled = setOf(ScalePresence.FOUND, ScalePresence.MISSING)

    /** Searches for the active scale now; while a quiet background search runs, just shows it. */
    fun refreshPresence() {
        if (presenceSearching) _presence.value = ScalePresence.SEARCHING else recheck.tryEmit(Unit)
    }

    @Volatile private var presenceSearching = false

    private suspend fun checkPresence(active: SavedScale, showSearch: Boolean) {
        if (!hasPermissions() || !isBluetoothOn()) {
            _presence.value = ScalePresence.UNAVAILABLE
            return
        }
        if (supportFor(active)?.linkMode == LinkMode.CLASSIC_SPP) {
            _presence.value = ScalePresence.UNKNOWN
            return
        }
        if (_status.value.isActive) return
        if (showSearch) _presence.value = ScalePresence.SEARCHING
        presenceSearching = true
        try {
            // Discovery and a targeted scan would compete for the radio; let a running discovery answer.
            val found = if (scanner.scanning.value) {
                withTimeoutOrNull(PRESENCE_SCAN_MS) { scanner.devices.first { list -> list.any { it.address == active.address } } } != null
            } else {
                scanner.find(active.address, PRESENCE_SCAN_MS) != null
            }
            _presence.value = if (found) ScalePresence.FOUND else ScalePresence.MISSING
        } finally {
            presenceSearching = false
        }
    }

    // ── Weigh-in session ───────────────────────────────────────────────────

    /** Connects to the active scale and starts collecting readings; a running session continues. */
    fun startSession() {
        scope.launch {
            if (!_status.value.isActive) _weigh.value = WeighState()
            connectNow()
        }
    }

    /** Disconnects and drops whatever was not saved. */
    fun endSession() {
        scope.launch {
            disconnectNow()
            _weigh.value = WeighState()
        }
    }

    /** Discards the readings so far and waits for the next one, reconnecting if the scale left. */
    fun retry() {
        scope.launch {
            _weigh.value = WeighState()
            if (_status.value is ScaleStatus.Error) _status.value = ScaleStatus.Idle
            connectNow()
        }
    }

    /** Recomputes the pending reading at [index] for [userId]. */
    fun reassign(index: Int, userId: Long) {
        scope.launch {
            val current = _weigh.value.pending.getOrNull(index) ?: return@launch
            val prepared = repository.prepareScaleReading(current.reading, forcedUserId = userId)
            if (prepared !is Prepared.Ready) return@launch
            _weigh.update { w ->
                if (w.pending.getOrNull(index) !== current) return@update w
                w.copy(pending = w.pending.toMutableList().also { it[index] = prepared.pending })
            }
        }
    }

    /** Stores every pending reading; returns how many were saved. */
    suspend fun saveAll(): Int {
        val pending = _weigh.getAndUpdate { it.copy(pending = emptyList(), liveKg = null) }.pending
        pending.forEach { repository.commitScaleReading(it) }
        snapshot = buildSnapshot()
        return pending.size
    }

    private fun disconnectNow() {
        connectJob?.cancel()
        connectJob = null
        // Detach the old link's reports first, so a new session can start while it still closes.
        generation++
        link?.stop()
        link = null
        _status.value = ScaleStatus.Idle
        _interaction.value = null
    }

    private fun fail(@StringRes message: Int) {
        _status.value = ScaleStatus.Error(context.getString(message))
    }

    private suspend fun connectNow() {
        if (_status.value.isActive) return
        val s = settings.current().scale
        val saved = s.active ?: return fail(R.string.ble_error_no_scale)
        val info = savedDevice(saved)
        if (!hasPermissions()) return fail(R.string.ble_error_permission)
        if (!isBluetoothOn()) return fail(R.string.ble_error_off)
        val (handler, support) = ScaleFactory.resolve(info, s.developerMode) ?: return fail(R.string.ble_error_unsupported)
        snapshot = buildSnapshot()
        val user = snapshot.current ?: return fail(R.string.ble_error_no_user)
        val tuning = saved.tuning?.let { runCatching { TuningProfile.valueOf(it) }.getOrNull() } ?: support.tuningProfile
        val name = saved.name ?: support.displayName
        val session = ++generation

        val linkContext = LinkContext(
            context = context,
            handler = handler,
            callbacks = callbacks(session),
            settings = drivers.forHandler(saved.address, handler),
            data = dataProvider,
            user = user,
            // A stopped session may still report after a new one started; only the latest counts.
            listener = { state -> if (session == generation) onLinkState(state, name) },
        )
        connectJob = scope.launch {
            var device: BluetoothDevice? = null
            if (support.linkMode == LinkMode.CONNECT_GATT) {
                _status.value = ScaleStatus.Scanning
                if (support.findTimeoutMs != null) _weigh.update { it.copy(hint = context.getString(R.string.weigh_wait_for_advert)) }
                device = scanner.find(info.address, support.findTimeoutMs ?: FIND_TIMEOUT_MS)?.device
            }
            val created = when (support.linkMode) {
                LinkMode.CONNECT_GATT -> GattLink(linkContext, tuning.forGatt())
                LinkMode.BROADCAST_ONLY -> BroadcastLink(linkContext, tuning.forBroadcast())
                LinkMode.CLASSIC_SPP -> SppLink(linkContext, tuning.forSpp())
            }
            link = created
            created.start(info.address, device)
            if (support.linkMode == LinkMode.BROADCAST_ONLY) watchHolder(info.address, session)
        }
    }

    /** A broadcast scale stops advertising while another app (Mi Home) holds a connection to it. */
    private suspend fun watchHolder(address: String, session: Int) {
        val busy = context.getString(R.string.weigh_scale_busy)
        while (session == generation && _status.value.isActive) {
            val held = heldElsewhere(address)
            _weigh.update {
                when {
                    held && it.pending.isEmpty() -> it.copy(hint = busy)
                    !held && it.hint == busy -> it.copy(hint = null)
                    else -> it
                }
            }
            delay(HOLDER_CHECK_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun heldElsewhere(address: String): Boolean = hasPermissions() && runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
            .any { it.address.equals(address, ignoreCase = true) }
    }.getOrDefault(false)

    private fun onLinkState(state: LinkState, name: String) {
        when (state) {
            LinkState.Connecting -> _status.value = ScaleStatus.Connecting
            is LinkState.Connected -> {
                _status.value = ScaleStatus.Connected(state.name ?: name)
                _presence.value = ScalePresence.FOUND
            }
            LinkState.Listening -> _status.value = ScaleStatus.Listening(name)
            LinkState.Receiving -> {
                _weigh.update { it.copy(receiving = true) }
                _presence.value = ScalePresence.FOUND
            }
            is LinkState.Message -> _weigh.update { it.copy(hint = state.text) }
            is LinkState.Closed -> {
                link = null
                _interaction.value = null
                val busy = context.getString(R.string.weigh_scale_busy)
                val error = state.error?.let { if (_weigh.value.hint == busy) busy else it }
                _status.value = error?.let(ScaleStatus::Error) ?: ScaleStatus.Idle
            }
        }
    }

    // ── Driver prompts ─────────────────────────────────────────────────────

    fun answer(value: Int) {
        val prompt = _interaction.value ?: return
        _interaction.value = null
        val userId = snapshot.current?.id ?: return
        link?.feedback(prompt.type, userId, value)
    }

    fun dismissInteraction() {
        _interaction.value = null
    }

    // ── Driver wiring ──────────────────────────────────────────────────────

    /** Driver callbacks for one session; reports from an ended session are dropped. */
    private fun callbacks(session: Int) = object : ScaleDeviceHandler.Callbacks {
        private val live get() = session == generation

        override fun onPublish(measurement: ScaleMeasurement) {
            if (live) scope.launch { collect(measurement) }
        }

        override fun onInfo(resId: Int, vararg args: Any) {
            if (!live) return
            when (resId) {
                R.string.bluetooth_scale_info_measuring_weight ->
                    _weigh.update { it.copy(stepOn = true, liveKg = (args.firstOrNull() as? Number)?.toFloat() ?: it.liveKg) }
                R.string.bt_info_step_on_scale, R.string.bt_info_waiting_for_measurement ->
                    _weigh.update { it.copy(stepOn = true, hint = context.getString(resId, *args)) }
                else -> _weigh.update { it.copy(hint = context.getString(resId, *args)) }
            }
        }

        override fun onWarn(resId: Int, vararg args: Any) {
            if (live) _weigh.update { it.copy(hint = context.getString(resId, *args)) }
        }

        override fun onError(resId: Int, t: Throwable?, vararg args: Any) {
            val message = context.getString(resId, *args)
            LogManager.e("ScaleManager", message, t)
            if (live) _weigh.update { it.copy(hint = message) }
        }

        override fun onUserInteractionRequired(interactionType: UserInteractionType, data: Any?) {
            if (!live) return
            val choices = (data as? Pair<*, *>)?.let { (labels, slots) ->
                val l = (labels as? Array<*>)?.map { it.toString() }.orEmpty()
                val s = (slots as? IntArray)?.toList().orEmpty()
                l.zip(s)
            }.orEmpty()
            _interaction.value = ScaleInteraction(interactionType, choices)
        }

        override fun resolveString(resId: Int, vararg args: Any): String = context.getString(resId, *args)
    }

    private val dataProvider = object : ScaleDeviceHandler.DataProvider {
        override fun currentUser(): ScaleUser = snapshot.current ?: error("No user selected")
        override fun usersForDevice(): List<ScaleUser> = snapshot.users
        override fun lastMeasurementFor(userId: Int): ScaleMeasurement? = snapshot.last[userId]
    }

    private suspend fun collect(measurement: ScaleMeasurement) {
        val reading = toReading(measurement) { context.getString(it) } ?: return
        when (val result = repository.prepareScaleReading(reading)) {
            is Prepared.Ready -> _weigh.update { it.copy(liveKg = null, pending = it.pending + result.pending) }
            Prepared.Ignored -> _weigh.update { it.copy(liveKg = null, hint = context.getString(R.string.ble_ignored)) }
            Prepared.NoUser -> _weigh.update { it.copy(hint = context.getString(R.string.ble_error_no_user)) }
        }
    }

    private suspend fun buildSnapshot(): Snapshot {
        val users = repository.usersNow()
        if (users.isEmpty()) return Snapshot()
        val types = repository.typesNow()
        val goals = repository.allGoals()
        val currentId = settings.current().currentUserId
        val history = users.associate { it.id to repository.measurementsNow(it.id).sortedBy(Measurement::timestamp) }
        val mapped = users.map { mapUser(it, history[it.id].orEmpty(), types, goals) }
        return Snapshot(
            current = mapped.firstOrNull { it.id.toLong() == currentId } ?: mapped.first(),
            users = mapped,
            last = users.mapNotNull { u -> history[u.id]?.lastOrNull()?.let { u.id.toInt() to mapMeasurement(it, types) } }.toMap(),
        )
    }

    companion object {
        private const val FIND_TIMEOUT_MS = 20_000L
        private const val PRESENCE_INTERVAL_MS = 120_000L
        private const val PRESENCE_SCAN_MS = 10_000L
        private const val HOLDER_CHECK_MS = 5_000L
        private const val NO_USER_SLOT = 0xFF

        /** Driver output → a reading in each metric's wire unit; null when it carries nothing to store. */
        fun toReading(m: ScaleMeasurement, name: (Int) -> String = { "" }): ScaleReading? {
            val values = LinkedHashMap<String, MetricValue>()
            val extras = mutableListOf<MetricSpec>()
            val names = HashMap<String, String>()
            for ((key, raw) in m.values) {
                val value = when (raw) {
                    is UnitValue -> MetricValue.of(raw.value)
                    is Float -> MetricValue.of(raw)
                    is Bpm -> MetricValue.of(raw.value)
                    is Int -> MetricValue.of(raw)
                    is String -> MetricValue.of(raw)
                    else -> null
                } ?: continue
                values[key.identity] = value
                if (!key.isBuiltin) {
                    extras += key.toSpec()
                    names[key.identity] = name(key.nameResId)
                }
            }
            if (values.isEmpty()) return null
            return ScaleReading(
                timestamp = m.dateTime?.time ?: System.currentTimeMillis(),
                values = values,
                userId = m.userId.takeIf { it > 0 && it != NO_USER_SLOT }?.toLong(),
                extraSpecs = extras,
                extraNames = names.filterValues { it.isNotBlank() },
            )
        }

        fun mapUser(u: User, history: List<Measurement>, types: List<MetricType>, goals: List<UserGoal>): ScaleUser {
            val weight = types.firstOrNull { it.identity == Metrics.WEIGHT.identity }
            val firstKg = weight?.let { t -> history.firstNotNullOfOrNull { it.number(t) }?.let { UnitConversion.toKg(it, t.unit) } }
            val goalKg = weight?.let { t -> goals.firstOrNull { it.userId == u.id && it.typeId == t.id }?.let { UnitConversion.toKg(it.goalValue, t.unit) } }
            return ScaleUser(
                id = u.id.toInt(),
                userName = u.name,
                birthday = Date.from(u.birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                bodyHeight = u.heightCm,
                gender = if (u.gender == Gender.MALE) GenderType.MALE else GenderType.FEMALE,
                initialWeight = firstKg ?: 0f,
                goalWeight = goalKg ?: 0f,
                scaleUnit = when (weight?.unit) {
                    UnitType.LB -> WeightUnit.LB
                    UnitType.ST -> WeightUnit.ST
                    else -> WeightUnit.KG
                },
                activityLevel = DriverActivity.valueOf(u.activityLevel.name),
            )
        }

        /** The stored measurement in the units drivers expect (kg, percent of body weight). */
        fun mapMeasurement(m: Measurement, types: List<MetricType>): ScaleMeasurement {
            val byIdentity = types.associateBy { it.identity }
            fun number(spec: MetricSpec): Pair<Float, UnitType>? = byIdentity[spec.identity]?.let { t -> m.number(t)?.let { it to t.unit } }
            val out = ScaleMeasurement(userId = m.userId.toInt(), dateTime = Date(m.timestamp))
            val weightKg = number(Metrics.WEIGHT)?.let { (v, unit) -> UnitConversion.toKg(v, unit) }
            weightKg?.let { out[MeasurementType.WEIGHT] = Kg(it) }
            fun percent(spec: MetricSpec): Percent? = number(spec)?.let { (v, unit) ->
                when {
                    unit == UnitType.PERCENT -> Percent(v)
                    unit.isWeight && weightKg != null && weightKg > 0f -> Percent(UnitConversion.toKg(v, unit) / weightKg * 100f)
                    else -> null
                }
            }
            percent(Metrics.BODY_FAT)?.let { out[MeasurementType.BODY_FAT] = it }
            percent(Metrics.WATER)?.let { out[MeasurementType.WATER] = it }
            percent(Metrics.MUSCLE)?.let { out[MeasurementType.MUSCLE] = it }
            number(Metrics.VISCERAL_FAT)?.let { (v, _) -> out[MeasurementType.VISCERAL_FAT] = v }
            number(Metrics.LBM)?.let { (v, unit) -> out[MeasurementType.LBM] = Kg(UnitConversion.toKg(v, unit)) }
            number(Metrics.BONE)?.let { (v, unit) -> out[MeasurementType.BONE] = Kg(UnitConversion.toKg(v, unit)) }
            return out
        }
    }
}
