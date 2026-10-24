package com.izum1k.scalepaw.feature.bluetooth

import com.izum1k.scalepaw.ui.component.PillShape
import com.izum1k.scalepaw.ui.theme.YoinMotion
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.animateColorAsState
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
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
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ble.DiscoveredScale
import com.izum1k.scalepaw.ble.ScaleStatus
import com.izum1k.scalepaw.ble.openscale.scales.LinkMode
import com.izum1k.scalepaw.ble.openscale.scales.TuningProfile
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.ui.component.ChoiceRow
import com.izum1k.scalepaw.ui.component.ConfirmDialog
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.SwitchRow
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.gpo.yoin.symbols.YoinSymbols

/** Pair a scale, connect to it, tune the link and reach the driver's own settings. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun BluetoothScreen() {
    val context = LocalContext.current
    val scales = context.container.scales
    val haptics = LocalHapticFeedback.current
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val status by scales.status.collectAsStateWithLifecycle()
    val devices by scales.scanner.devices.collectAsStateWithLifecycle()
    val scanning by scales.scanner.scanning.collectAsStateWithLifecycle()
    val saved = state.settings.scale
    var permitted by remember { mutableStateOf(scales.hasPermissions()) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = scales.hasPermissions()
        if (permitted) scales.startDiscovery()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (scales.isBluetoothOn()) scales.startDiscovery()
    }
    fun search() {
        when {
            !permitted -> permissionLauncher.launch(scales.requiredPermissions)
            !scales.isBluetoothOn() -> enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> scales.startDiscovery()
        }
    }
    DisposableEffect(Unit) { onDispose { scales.stopDiscovery() } }

    val support = remember(saved.snapshot, saved.address) { scales.supportFor(saved) }
    val configHandler = remember(saved.snapshot, saved.address) { scales.configurationHandler(saved) }

    ScalePage(title = stringResource(R.string.bluetooth)) {
        item { StatusCard(saved.name, support?.displayName, status, hasScale = saved.address != null) }

        if (saved.address != null) {
            item {
                Section(title = stringResource(R.string.ble_saved_scale)) {
                    SettingRow(
                        title = saved.name ?: saved.address.orEmpty(),
                        value = listOfNotNull(support?.displayName ?: stringResource(R.string.ble_unsupported), saved.address).joinToString("\n"),
                        icon = ScaleSymbols.Scale,
                    )
                    if (support != null && support.implemented.isNotEmpty()) {
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            support.implemented.forEach { cap ->
                                Text(
                                    stringResource(cap.labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
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
                        onSelect = scales::setTuning,
                        icon = YoinSymbols.Settings,
                    )
                    SettingRow(
                        title = stringResource(R.string.ble_forget),
                        icon = YoinSymbols.Delete,
                        onClick = { confirmForget = true },
                    )
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
                        FilledTonalButton(onClick = ::search) {
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
                selected = device.address == saved.address,
                enabled = device.isSupported || saved.developerMode,
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
                    checked = saved.developerMode,
                    onCheckedChange = scales::setDeveloperMode,
                    icon = YoinSymbols.Info,
                )
            }
        }
    }

    if (confirmForget) {
        ConfirmDialog(
            title = stringResource(R.string.ble_forget),
            text = stringResource(R.string.ble_forget_text),
            confirm = stringResource(R.string.action_remove),
            onDismiss = { confirmForget = false },
        ) {
            confirmForget = false
            scales.forget()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(name: String?, driver: String?, status: ScaleStatus, hasScale: Boolean) {
    val scales = LocalContext.current.container.scales
    val busy = status is ScaleStatus.Scanning || status is ScaleStatus.Connecting
    val (title, body) = statusText(status, hasScale)
    val error = status is ScaleStatus.Error
    val container by animateColorAsState(
        if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        YoinMotion.defaultEffectsSpec(),
        label = "status",
    )
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    CompositionLocalProvider(LocalContentColor provides content) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(YoinContainerShapes.Card)
                .background(container)
                .padding(20.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) LoadingIndicator(Modifier.size(40.dp)) else Icon(ScaleSymbols.Bluetooth, contentDescription = null)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    listOfNotNull(name ?: driver, body).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f))
                    }
                }
            }
            if (hasScale) {
                Spacer(Modifier.height(16.dp))
                if (status.isActive) {
                    FilledTonalButton(onClick = scales::disconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ble_disconnect)) }
                } else {
                    Button(onClick = scales::connectSaved, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ble_connect)) }
                }
            }
        }
    }
}

@Composable
private fun statusText(status: ScaleStatus, hasScale: Boolean): Pair<String, String?> = when (status) {
    ScaleStatus.Idle -> stringResource(if (hasScale) R.string.ble_status_idle else R.string.ble_status_none) to null
    ScaleStatus.Scanning -> stringResource(R.string.ble_status_scanning) to null
    ScaleStatus.Connecting -> stringResource(R.string.ble_status_connecting) to null
    is ScaleStatus.Connected -> stringResource(R.string.ble_status_connected) to stringResource(R.string.ble_step_on)
    is ScaleStatus.Listening -> stringResource(R.string.ble_status_listening) to stringResource(R.string.ble_step_on)
    is ScaleStatus.Error -> stringResource(R.string.ble_status_error) to status.message
}

@Composable
private fun DeviceRow(device: DiscoveredScale, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
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
                device.support?.displayName ?: stringResource(R.string.ble_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = if (device.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (device.info.rssi != 0) {
            Text("${device.info.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(YoinSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
