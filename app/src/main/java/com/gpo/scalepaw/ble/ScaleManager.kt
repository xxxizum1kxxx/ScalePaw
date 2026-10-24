package com.gpo.scalepaw.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.gpo.scalepaw.R
import com.gpo.scalepaw.ble.openscale.BluetoothEvent.UserInteractionType
import com.gpo.scalepaw.ble.openscale.ScaleFactory
import com.gpo.scalepaw.ble.openscale.ScannedDeviceInfo
import com.gpo.scalepaw.ble.openscale.data.ScaleMeasurement
import com.gpo.scalepaw.ble.openscale.data.ScaleUser
import com.gpo.scalepaw.ble.openscale.scales.DeviceSupport
import com.gpo.scalepaw.ble.openscale.scales.LinkMode
import com.gpo.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.gpo.scalepaw.ble.openscale.scales.TuningProfile
import com.gpo.scalepaw.ble.openscale.scales.forBroadcast
import com.gpo.scalepaw.ble.openscale.scales.forGatt
import com.gpo.scalepaw.ble.openscale.scales.forSpp
import com.gpo.scalepaw.ble.openscale.util.LogManager
import com.gpo.scalepaw.ble.openscale.vocab.ActivityLevel as DriverActivity
import com.gpo.scalepaw.ble.openscale.vocab.Bpm
import com.gpo.scalepaw.ble.openscale.vocab.GenderType
import com.gpo.scalepaw.ble.openscale.vocab.Kg
import com.gpo.scalepaw.ble.openscale.vocab.MeasurementType
import com.gpo.scalepaw.ble.openscale.vocab.Percent
import com.gpo.scalepaw.ble.openscale.vocab.UnitValue
import com.gpo.scalepaw.ble.openscale.vocab.WeightUnit
import com.gpo.scalepaw.data.model.Gender
import com.gpo.scalepaw.data.model.Measurement
import com.gpo.scalepaw.data.model.MetricSpec
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.MetricValue
import com.gpo.scalepaw.data.model.Metrics
import com.gpo.scalepaw.data.model.UnitConversion
import com.gpo.scalepaw.data.model.UnitType
import com.gpo.scalepaw.data.model.User
import com.gpo.scalepaw.data.model.UserGoal
import com.gpo.scalepaw.data.repository.SaveResult
import com.gpo.scalepaw.data.repository.ScaleReading
import com.gpo.scalepaw.data.repository.ScaleRepository
import com.gpo.scalepaw.data.settings.ScaleSettings
import com.gpo.scalepaw.data.settings.SettingsRepository
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the scale connection stands, as the app bar and the Bluetooth page show it. */
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

/** Owns the scale connection: discovery, pairing, the driver session and storing what it reports. */
class ScaleManager(
    private val context: Context,
    private val repository: ScaleRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    val scanner = ScaleScanner(context, scope)
    private val drivers = DriverStore(context)

    private val _status = MutableStateFlow<ScaleStatus>(ScaleStatus.Idle)
    val status: StateFlow<ScaleStatus> = _status.asStateFlow()

    private val _interaction = MutableStateFlow<ScaleInteraction?>(null)
    val interaction: StateFlow<ScaleInteraction?> = _interaction.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short, user-facing notes: saved readings, driver hints, warnings. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var link: ScaleLink? = null
    private var connectJob: Job? = null
    @Volatile private var generation = 0

    @Volatile private var snapshot = Snapshot()

    private class Snapshot(
        val current: ScaleUser? = null,
        val users: List<ScaleUser> = emptyList(),
        val last: Map<Int, ScaleMeasurement> = emptyMap(),
    )

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

    /** Makes [scale] the saved scale and connects to it. */
    fun pair(scale: DiscoveredScale) {
        scanner.stop()
        scope.launch {
            val current = settings.current().scale
            if (current.address != scale.address) drivers.clear()
            settings.setScale(
                current.copy(
                    address = scale.address,
                    name = scale.name.ifBlank { scale.support?.displayName ?: scale.address },
                    driverId = scale.support?.displayName,
                    snapshot = DeviceSnapshot.encode(scale.info),
                    tuning = null,
                ),
            )
            disconnectNow()
            withTimeoutOrNull(STOP_TIMEOUT_MS) { status.first { !it.isActive } }
            _status.value = ScaleStatus.Idle
            connectSavedNow(scale.device)
        }
    }

    /** Forgets the saved scale and everything its driver stored. */
    fun forget() {
        scope.launch {
            disconnectNow()
            settings.setScale(ScaleSettings(developerMode = settings.current().scale.developerMode))
            drivers.clear()
        }
    }

    fun setTuning(profile: TuningProfile?) {
        scope.launch { settings.setScale(settings.current().scale.copy(tuning = profile?.name)) }
    }

    fun setDeveloperMode(enabled: Boolean) {
        scope.launch { settings.setScale(settings.current().scale.copy(developerMode = enabled)) }
    }

    /** The saved scale's advertisement as drivers see it. */
    fun savedDevice(s: ScaleSettings): ScannedDeviceInfo? {
        val address = s.address ?: return null
        return DeviceSnapshot.decode(s.snapshot) ?: ScannedDeviceInfo(s.name.orEmpty(), address, 0, emptyList(), null)
    }

    fun supportFor(s: ScaleSettings): DeviceSupport? = savedDevice(s)?.let(ScaleFactory::supportFor)

    /** A driver instance for showing its configuration UI; its settings are live, its link is not. */
    fun configurationHandler(s: ScaleSettings): ScaleDeviceHandler? =
        savedDevice(s)?.let { ScaleFactory.resolve(it)?.first }?.also { it.attachSettings(drivers.forHandler(it)) }

    // ── Connection ─────────────────────────────────────────────────────────

    fun connectSaved() {
        scope.launch { connectSavedNow(null) }
    }

    fun disconnect() {
        scope.launch { disconnectNow() }
    }

    private fun disconnectNow() {
        connectJob?.cancel()
        connectJob = null
        val active = link
        if (active != null) active.stop() else if (_status.value.isActive) _status.value = ScaleStatus.Idle
        _interaction.value = null
    }

    private fun fail(@StringRes message: Int) {
        _status.value = ScaleStatus.Error(context.getString(message))
    }

    private suspend fun connectSavedNow(found: BluetoothDevice?) {
        if (_status.value.isActive) return
        val s = settings.current().scale
        val info = savedDevice(s) ?: return
        if (!hasPermissions()) return fail(R.string.ble_error_permission)
        if (!isBluetoothOn()) return fail(R.string.ble_error_off)
        val (handler, support) = ScaleFactory.resolve(info, s.developerMode) ?: return fail(R.string.ble_error_unsupported)
        snapshot = buildSnapshot()
        val user = snapshot.current ?: return fail(R.string.ble_error_no_user)
        val tuning = s.tuning?.let { runCatching { TuningProfile.valueOf(it) }.getOrNull() } ?: support.tuningProfile
        val name = s.name ?: support.displayName
        val session = ++generation

        val linkContext = LinkContext(
            context = context,
            handler = handler,
            callbacks = callbacks,
            settings = drivers.forHandler(handler),
            data = dataProvider,
            user = user,
            // A stopped session may still report after a new one started; only the latest counts.
            listener = { state -> if (session == generation) onLinkState(state, name) },
        )
        connectJob = scope.launch {
            var device = found
            if (support.linkMode == LinkMode.CONNECT_GATT && device == null) {
                _status.value = ScaleStatus.Scanning
                device = scanner.find(info.address, FIND_TIMEOUT_MS)?.device
            }
            val created = when (support.linkMode) {
                LinkMode.CONNECT_GATT -> GattLink(linkContext, tuning.forGatt())
                LinkMode.BROADCAST_ONLY -> BroadcastLink(linkContext, tuning.forBroadcast())
                LinkMode.CLASSIC_SPP -> SppLink(linkContext, tuning.forSpp())
            }
            link = created
            created.start(info.address, device)
        }
    }

    private fun onLinkState(state: LinkState, name: String) {
        when (state) {
            LinkState.Connecting -> _status.value = ScaleStatus.Connecting
            is LinkState.Connected -> _status.value = ScaleStatus.Connected(state.name ?: name)
            LinkState.Listening -> _status.value = ScaleStatus.Listening(name)
            is LinkState.Message -> _messages.tryEmit(state.text)
            is LinkState.Closed -> {
                link = null
                _interaction.value = null
                _status.value = state.error?.let(ScaleStatus::Error) ?: ScaleStatus.Idle
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

    private val callbacks = object : ScaleDeviceHandler.Callbacks {
        override fun onPublish(measurement: ScaleMeasurement) {
            scope.launch { save(measurement) }
        }

        override fun onInfo(resId: Int, vararg args: Any) {
            _messages.tryEmit(context.getString(resId, *args))
        }

        override fun onWarn(resId: Int, vararg args: Any) {
            _messages.tryEmit(context.getString(resId, *args))
        }

        override fun onError(resId: Int, t: Throwable?, vararg args: Any) {
            val message = context.getString(resId, *args)
            LogManager.e("ScaleManager", message, t)
            _messages.tryEmit(message)
        }

        override fun onUserInteractionRequired(interactionType: UserInteractionType, data: Any?) {
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

    private suspend fun save(measurement: ScaleMeasurement) {
        val reading = toReading(measurement) { context.getString(it) } ?: return
        val message = when (val result = repository.saveScaleReading(reading)) {
            is SaveResult.Saved -> {
                snapshot = buildSnapshot()
                val name = repository.usersNow().firstOrNull { it.id == result.userId }?.name.orEmpty()
                context.getString(R.string.ble_saved, name)
            }
            SaveResult.Ignored -> context.getString(R.string.ble_ignored)
            SaveResult.NoUser -> context.getString(R.string.ble_error_no_user)
        }
        _messages.tryEmit(message)
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
        private const val STOP_TIMEOUT_MS = 3_000L
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
