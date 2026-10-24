package com.izum1k.scalepaw.feature.bluetooth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.yoin.symbols.YoinSymbols
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.DiscoveredScale
import com.izum1k.scalepaw.ble.openscale.scales.DeviceSupport
import com.izum1k.scalepaw.ble.openscale.scales.LinkMode
import com.izum1k.scalepaw.ble.openscale.scales.TuningProfile
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.settings.SavedScale
import com.izum1k.scalepaw.ui.component.ChoiceRow
import com.izum1k.scalepaw.ui.component.ConfirmDialog
import com.izum1k.scalepaw.ui.component.PillShape
import com.izum1k.scalepaw.ui.component.Reveal
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.SwitchRow
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.navigation.Route
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion

/** Pair scales, pick the active one, tune its link and reach the driver's own settings. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BluetoothScreen() {
    val context = LocalContext.current
    val scales = context.container.scales
    val haptics = LocalHapticFeedback.current
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val devices by scales.scanner.devices.collectAsStateWithLifecycle()
    val scanning by scales.scanner.scanning.collectAsStateWithLifecycle()
    val settings = state.settings.scale
    val active = settings.active
    val savedAddresses = remember(settings.devices) { settings.devices.map { it.address }.toSet() }
    var forgetting by rememberSaveable { mutableStateOf<String?>(null) }

    val search = rememberBluetoothAccess(onDone = scales::startDiscovery)
    DisposableEffect(Unit) { onDispose { scales.stopDiscovery() } }

    val configHandler = remember(active) { active?.let(scales::configurationHandler) }

    ScalePage(title = stringResource(R.string.bluetooth)) {
        item { ActiveCard(active, active?.let(scales::supportFor)) }

        if (settings.devices.isNotEmpty()) {
            item {
                Section(title = stringResource(R.string.ble_saved_scales)) {
                    settings.devices.forEach { saved ->
                        SavedScaleBlock(
                            saved = saved,
                            support = remember(saved) { scales.supportFor(saved) },
                            active = saved.address == settings.activeAddress,
                            onActivate = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                scales.activate(saved.address)
                            },
                            onTuning = { scales.setTuning(saved.address, it) },
                            onForget = { forgetting = saved.address },
                        )
                    }
                }
            }
            if (configHandler != null) {
                item {
                    Section(title = stringResource(R.string.ble_driver_settings)) {
                        Box(Modifier.padding(16.dp)) { configHandler.DeviceConfigurationUi() }
                    }
                }
            }
        }

        item {
            Section(title = stringResource(R.string.ble_nearby)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (scanning) R.string.ble_searching else R.string.ble_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (scanning) {
                        LoadingIndicator(Modifier.size(40.dp))
                    } else {
                        FilledTonalButton(onClick = search) {
                            Icon(YoinSymbols.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ble_search))
                        }
                    }
                }
            }
        }
        items(devices, key = { it.address }) { device ->
            DeviceRow(
                device = device,
                saved = device.address in savedAddresses,
                active = device.address == settings.activeAddress,
                enabled = device.isSupported || settings.developerMode,
                modifier = Modifier.animateItem(),
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scales.pair(device)
            }
        }

        item {
            Section(title = stringResource(R.string.ble_advanced)) {
                SwitchRow(
                    title = stringResource(R.string.ble_developer),
                    value = stringResource(R.string.ble_developer_summary),
                    checked = settings.developerMode,
                    onCheckedChange = scales::setDeveloperMode,
                    icon = YoinSymbols.Info,
                )
            }
        }
    }

    forgetting?.let { address ->
        ConfirmDialog(
            title = stringResource(R.string.ble_forget),
            text = stringResource(R.string.ble_forget_text),
            confirm = stringResource(R.string.action_remove),
            onDismiss = { forgetting = null },
        ) {
            forgetting = null
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scales.forget(address)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveCard(active: SavedScale?, support: DeviceSupport?) {
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        if (active != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        YoinMotion.defaultEffectsSpec(),
        label = "activeScale",
    )
    val content = if (active != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides content) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(YoinContainerShapes.Card)
                .background(container)
                .padding(20.dp)
                .animateContentSize(YoinMotion.defaultSpatialSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip((if (active != null) MaterialShapes.Cookie9Sided else MaterialShapes.Circle).toShape())
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ScaleSymbols.Scale, contentDescription = null)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        active?.name ?: stringResource(R.string.ble_status_none),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (active != null) {
                            support?.displayName ?: stringResource(R.string.ble_unsupported)
                        } else {
                            stringResource(R.string.ble_search_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.75f),
                    )
                }
            }
            if (active != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        nav.push(Route.Weigh)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(YoinSymbols.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.weigh_start))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedScaleBlock(
    saved: SavedScale,
    support: DeviceSupport?,
    active: Boolean,
    onActivate: () -> Unit,
    onTuning: (TuningProfile?) -> Unit,
    onForget: () -> Unit,
) {
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        YoinMotion.defaultEffectsSpec(),
        label = "savedScale",
    )
    Column(Modifier.background(container)) {
        SettingRow(
            title = saved.name ?: saved.address,
            value = listOfNotNull(support?.displayName ?: stringResource(R.string.ble_unsupported), saved.address).joinToString("\n"),
            icon = ScaleSymbols.Scale,
            onClick = onActivate,
        ) {
            RadioButton(selected = active, onClick = onActivate)
        }
        Reveal(active) {
            Column {
                if (support != null && support.implemented.isNotEmpty()) {
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        support.implemented.forEach { cap ->
                            Text(
                                stringResource(cap.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                val profiles = listOf<TuningProfile?>(null) + TuningProfile.entries
                ChoiceRow(
                    title = stringResource(R.string.ble_tuning),
                    options = profiles,
                    selected = saved.tuning?.let { runCatching { TuningProfile.valueOf(it) }.getOrNull() },
                    label = { p ->
                        p?.let { stringResource(it.labelRes) }
                            ?: stringResource(R.string.ble_tuning_default, support?.tuningProfile?.let { stringResource(it.labelRes) }.orEmpty())
                    },
                    onSelect = onTuning,
                    icon = YoinSymbols.Settings,
                )
                SettingRow(
                    title = stringResource(R.string.ble_forget),
                    icon = YoinSymbols.Delete,
                    onClick = onForget,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredScale,
    saved: Boolean,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Card)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (device.support?.linkMode == LinkMode.BROADCAST_ONLY) ScaleSymbols.Scale else ScaleSymbols.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name.ifBlank { device.address },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                listOfNotNull(
                    device.support?.displayName ?: stringResource(R.string.ble_unsupported),
                    stringResource(R.string.ble_saved).takeIf { saved },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (device.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (device.info.rssi != 0) {
            Text("${device.info.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (active) {
            Spacer(Modifier.width(8.dp))
            Icon(YoinSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
