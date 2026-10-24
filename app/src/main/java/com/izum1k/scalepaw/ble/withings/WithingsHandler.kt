package com.izum1k.scalepaw.ble.withings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gpo.yoin.symbols.YoinSymbols
import com.izum1k.scalepaw.BuildConfig
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.openscale.ScannedDeviceInfo
import com.izum1k.scalepaw.ble.openscale.data.ScaleMeasurement
import com.izum1k.scalepaw.ble.openscale.data.ScaleUser
import com.izum1k.scalepaw.ble.openscale.scales.DeviceCapability
import com.izum1k.scalepaw.ble.openscale.scales.DeviceSupport
import com.izum1k.scalepaw.ble.openscale.scales.LinkMode
import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.izum1k.scalepaw.ble.openscale.scales.TuningProfile
import com.izum1k.scalepaw.ble.openscale.vocab.Bpm
import com.izum1k.scalepaw.ble.openscale.vocab.Kcal
import com.izum1k.scalepaw.ble.openscale.vocab.Kg
import com.izum1k.scalepaw.ble.openscale.vocab.MeasurementType
import com.izum1k.scalepaw.ble.openscale.vocab.MeasurementTypeIcon
import com.izum1k.scalepaw.ble.openscale.vocab.Percent
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.ScaleTextField
import com.izum1k.scalepaw.ui.component.SwitchRow
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Withings scales over WPP (experimental). Read-only: authenticates with the account's `kl`, reads
 * the scale's stored weigh-ins and never deletes them, so the Withings app still receives them.
 */
class WithingsHandler : ScaleDeviceHandler() {

    companion object {
        const val SETTINGS_KEY_KL = "withings_kl"
        const val SETTINGS_KEY_TIME_SET = "withings_time_set"
        const val SETTINGS_KEY_LAST_SESSION = "withings_last_session"

        /** The scale only advertises after the weigh-in has finished, up to a minute after stepping on. */
        private const val FIND_TIMEOUT_MS = 120_000L

        /** Includes a first-time passkey entry, which the link allows 35 s for. */
        private const val SESSION_TIMEOUT_MS = 60_000L
        private const val DISCONNECT_GRACE_MS = 1_500L

        /** 2015-01-01: anything earlier comes from a clock that restarted at zero after a battery change. */
        private const val EARLIEST_PLAUSIBLE_SECONDS = 1_420_070_400L

        private const val MODEL_BODY_PLUS = 0x05
        private const val MODEL_BODY_SMART = 0x10

        private val NAMES = listOf(
            "Body Smart" to MODEL_BODY_SMART,
            "Body+" to MODEL_BODY_PLUS,
            "Body Comp" to null,
            "Body Scan" to null,
            "Body Cardio" to null,
        )

        private val MODEL_NAMES = mapOf(
            MODEL_BODY_PLUS to "Withings Body+",
            MODEL_BODY_SMART to "Withings Body Smart",
        )

        val METABOLIC_AGE = MeasurementType.deviceInt(
            "metabolic_age", R.string.measurement_type_metabolic_age,
            icon = MeasurementTypeIcon.IC_M_TIMER, color = 0xFF795548.toInt(),
        )

        private val CAPABILITIES = setOf(
            DeviceCapability.BODY_COMPOSITION,
            DeviceCapability.HISTORY_READ,
            DeviceCapability.TIME_SYNC,
        )

        private fun nameMatch(name: String): Pair<String, Int?>? =
            NAMES.firstOrNull { (prefix, _) -> name.startsWith(prefix, ignoreCase = true) }

        fun displayName(modelId: Int?, name: String): String =
            modelId?.let { MODEL_NAMES[it] ?: "Withings (model 0x%02x)".format(Locale.ROOT, it) }
                ?: nameMatch(name)?.let { "Withings ${it.first}" }
                ?: "Withings"
    }

    private var service: UUID? = null
    private var characteristic: UUID? = null
    private var session: WppSession? = null
    private val reassembler = Reassembler()
    private var timeoutJob: Job? = null
    private val trace = mutableListOf<String>()
    private var modelId: Int? = null

    override fun supportFor(device: ScannedDeviceInfo): DeviceSupport? {
        val uuid = WithingsAdvert.find(device.serviceUuids)
        val byName = nameMatch(device.name)
        if (uuid == null && byName == null) return null
        val model = uuid?.modelId ?: byName?.second
        return DeviceSupport(
            displayName = displayName(model, device.name),
            capabilities = CAPABILITIES,
            implemented = CAPABILITIES,
            tuningProfile = TuningProfile.Balanced,
            linkMode = LinkMode.CONNECT_GATT,
            experimental = true,
            findTimeoutMs = FIND_TIMEOUT_MS,
        )
    }

    override fun onConnected(user: ScaleUser) {
        trace.clear()
        reassembler.reset()
        note("ScalePaw ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.SDK_INT} · ${Date()}")

        // Without a key the session still runs: a setup-mode session pairs and needs none.
        val kl = settingsGetString(SETTINGS_KEY_KL).orEmpty()
        note("kl ${if (WppAuth.isValidKey(kl)) "set" else "missing"}")

        val found = findWppCharacteristic()
        if (found == null) {
            note("WPP characteristic not found")
            saveTrace()
            userError(R.string.withings_error_no_characteristic)
            requestDisconnect()
            return
        }
        service = found.first
        characteristic = found.second
        modelId = WithingsAdvert.parse(found.first)?.modelId
        note("model ${modelId?.let { "0x%02x".format(Locale.ROOT, it) } ?: "?"}")

        val timeSet = settingsGetInt(SETTINGS_KEY_TIME_SET, 1) == 1
        note("time set ${if (timeSet) "on" else "off"}")
        val s = WppSession(kl, if (timeSet) ::currentTime else null, osVersion = Build.VERSION.SDK_INT)
        session = s

        setNotifyOn(found.first, found.second)
        send(s.start())
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(SESSION_TIMEOUT_MS)
            note("timeout at stage ${s.stage}")
            saveTrace()
            userError(R.string.withings_error_timeout)
            requestDisconnect()
        }
    }

    override fun onNotification(characteristic: UUID, data: ByteArray, user: ScaleUser) {
        if (characteristic != this.characteristic) return
        val s = session ?: return
        val messages = try {
            reassembler.feed(data)
        } catch (e: WppException) {
            note("<- unreadable: ${e.message}")
            return
        }
        for (msg in messages) {
            note("<- ${msg.describe()}")
            val reaction = s.handle(msg)
            reaction.send.forEach(::send)
            reaction.outcome?.let { finish(it) }
        }
    }

    override fun onDisconnected() {
        timeoutJob?.cancel()
        if (session?.stage != WppSession.Stage.DONE && trace.isNotEmpty()) {
            note("disconnected at stage ${session?.stage}")
            saveTrace()
        }
    }

    private fun send(msg: WppMessage) {
        val svc = service ?: return
        val chr = characteristic ?: return
        note("-> ${msg.describe()}")
        writeTo(svc, chr, msg.encode(), withResponse = true)
    }

    private fun finish(outcome: WppOutcome) {
        timeoutJob?.cancel()
        when (outcome) {
            is WppOutcome.Synced -> onSynced(outcome.result)
            is WppOutcome.Failed -> onFailed(outcome.failure)
        }
        session?.skippedRecords?.forEach { note("skipped record: $it") }
        saveTrace()
        scope.launch {
            delay(DISCONNECT_GRACE_MS)
            requestDisconnect()
        }
    }

    private fun onSynced(result: WppSyncResult) {
        note(
            "synced · firmware ${result.firmware ?: "?"} · reason ${result.connectReason ?: "?"} · " +
                "drift ${result.clockDriftSeconds ?: "?"} s · stored ${result.storedCount} · weigh-ins ${result.groups.size}",
        )
        result.groups.forEach { g ->
            note("weigh-in · users ${g.userIds.size} · algorithm ${g.algorithm ?: "?"} · types ${g.values.keys.sorted()} · extra ${g.unknownTlvs.sorted()}")
        }

        val (plausible, unset) = result.groups.partition { it.timeSeconds >= EARLIEST_PLAUSIBLE_SECONDS }
        if (unset.isNotEmpty()) {
            note("${unset.size} weigh-in(s) with an unset clock dropped")
            userWarn(R.string.withings_clock_unset)
        }
        val since = usersForDevice().mapNotNull { lastMeasurementFor(it.id)?.dateTime?.time }.maxOrNull() ?: 0L
        val fresh = plausible.filter { it.timeSeconds * 1000 > since && WppMeasureType.WEIGHT in it.values }
        note("new weigh-ins ${fresh.size}")
        if (fresh.isEmpty()) {
            userInfo(R.string.withings_no_new)
            return
        }
        fresh.forEach { publish(toMeasurement(it)) }
    }

    private fun onFailed(failure: WppFailure) {
        note("failed · $failure")
        when (failure) {
            is WppFailure.SetupMode -> userInfo(R.string.withings_setup_mode)
            WppFailure.MissingKey -> userWarn(R.string.withings_missing_key)
            WppFailure.ScaleUnverified -> userError(R.string.withings_error_unverified)
            is WppFailure.CommandError -> when {
                failure.error == WppError.AUTH_FAILED || failure.command == WppCmd.PROBE_CHALLENGE -> userError(R.string.withings_error_key)
                failure.error == WppError.NOT_AUTHENTICATED -> userError(R.string.withings_error_not_authenticated)
                else -> userError(R.string.withings_error_command, failure.command, failure.error)
            }
            is WppFailure.Malformed -> userError(R.string.withings_error_malformed, failure.detail)
        }
    }

    private fun toMeasurement(g: WppGroup): ScaleMeasurement = ScaleMeasurement().apply {
        dateTime = Date(g.timeSeconds * 1000)
        val v = g.values
        val weight = v[WppMeasureType.WEIGHT]?.toFloat() ?: return@apply
        this[MeasurementType.WEIGHT] = Kg(weight)
        fun share(kg: Double?): Percent? = kg?.let { Percent((it / weight * 100).toFloat()) }
        (v[WppMeasureType.FAT_RATIO]?.let { Percent(it.toFloat()) } ?: share(v[WppMeasureType.FAT_MASS]))
            ?.let { this[MeasurementType.BODY_FAT] = it }
        share(v[WppMeasureType.MUSCLE_MASS])?.let { this[MeasurementType.MUSCLE] = it }
        share(v[WppMeasureType.HYDRATION])?.let { this[MeasurementType.WATER] = it }
        v[WppMeasureType.BONE_MASS]?.let { this[MeasurementType.BONE] = Kg(it.toFloat()) }
        v[WppMeasureType.FAT_FREE_MASS]?.let { this[MeasurementType.LBM] = Kg(it.toFloat()) }
        v[WppMeasureType.VISCERAL_FAT]?.let { this[MeasurementType.VISCERAL_FAT] = it.toFloat() }
        v[WppMeasureType.BMR]?.let { this[MeasurementType.BMR] = Kcal(it.toFloat()) }
        v[WppMeasureType.HEART_RATE]?.let { this[MeasurementType.HEART_RATE] = Bpm(it.roundToInt()) }
        v[WppMeasureType.METABOLIC_AGE]?.roundToInt()?.takeIf { it in 10..99 }?.let { this[METABOLIC_AGE] = it }
    }

    /** The WPP characteristic `00000024-5749-5448-…` under its service, whatever the model byte. */
    private fun findWppCharacteristic(): Pair<UUID, UUID>? {
        val services = getPeripheral()?.services.orEmpty()
        for (svc in services) {
            val chr = svc.characteristics.firstOrNull { c ->
                WithingsAdvert.parse(c.uuid)?.service == WithingsAdvert.CHARACTERISTIC
            } ?: continue
            return svc.uuid to chr.uuid
        }
        return null
    }

    private fun currentTime(): WppTime {
        val now = Instant.now()
        val rules = ZoneId.systemDefault().rules
        val offset = rules.getOffset(now).totalSeconds
        val next = rules.nextTransition(now)
        return WppTime(
            utcSeconds = now.epochSecond,
            gmtOffsetSeconds = offset,
            dstChangeSeconds = next?.instant?.epochSecond ?: 0,
            nextGmtOffsetSeconds = next?.offsetAfter?.totalSeconds ?: offset,
        )
    }

    private fun note(line: String) {
        trace += line
        logI(line)
    }

    private fun saveTrace() {
        settingsPutString(SETTINGS_KEY_LAST_SESSION, trace.takeLast(MAX_TRACE_LINES).joinToString("\n"))
    }

    @Composable
    override fun DeviceConfigurationUi() {
        val context = LocalContext.current
        val haptics = LocalHapticFeedback.current
        val persisted = settingsGetString(SETTINGS_KEY_KL).orEmpty()
        var key by remember(persisted) { mutableStateOf(persisted) }
        var timeSet by remember { mutableStateOf(settingsGetInt(SETTINGS_KEY_TIME_SET, 1) == 1) }
        val diagnostics = remember { settingsGetString(SETTINGS_KEY_LAST_SESSION).orEmpty() }
        var copied by remember { mutableStateOf(false) }
        val valid = WppAuth.isValidKey(key)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(YoinContainerShapes.Card)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(YoinSymbols.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.withings_experimental_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Text(
                stringResource(R.string.withings_kl_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScaleTextField(
                value = key,
                onValueChange = { input ->
                    key = input.trim().take(WppAuth.KEY_LENGTH)
                    if (WppAuth.isValidKey(key) && key != persisted) settingsPutString(SETTINGS_KEY_KL, key)
                },
                label = stringResource(R.string.withings_kl_label),
                isError = key.isNotEmpty() && !valid,
                password = true,
            )
            Text(
                when {
                    key.isEmpty() -> "0/${WppAuth.KEY_LENGTH}"
                    !valid -> stringResource(R.string.withings_kl_error) + " · ${key.length}/${WppAuth.KEY_LENGTH}"
                    else -> stringResource(R.string.saved)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (key.isNotEmpty() && !valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SwitchRow(
                title = stringResource(R.string.withings_time_set),
                value = stringResource(R.string.withings_time_set_summary),
                checked = timeSet,
                onCheckedChange = {
                    timeSet = it
                    settingsPutInt(SETTINGS_KEY_TIME_SET, if (it) 1 else 0)
                },
                icon = ScaleSymbols.Clock,
            )

            Text(stringResource(R.string.withings_diagnostics), style = MaterialTheme.typography.titleSmall)
            Text(
                diagnostics.ifEmpty { stringResource(R.string.withings_diagnostics_empty) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (diagnostics.isNotEmpty()) {
                FilledTonalButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Withings", diagnostics))
                    copied = true
                }) {
                    Icon(if (copied) YoinSymbols.Check else YoinSymbols.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (copied) R.string.withings_diagnostics_copied else R.string.withings_diagnostics_copy))
                }
            }
        }
    }
}

private const val MAX_TRACE_LINES = 200
