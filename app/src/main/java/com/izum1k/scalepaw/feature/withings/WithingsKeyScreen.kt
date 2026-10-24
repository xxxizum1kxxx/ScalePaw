package com.izum1k.scalepaw.feature.withings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.Reveal
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleTextField
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion

/** Signs in to the Withings account and stores the `kl` secret of the scale at [address]. */
@Composable
fun WithingsKeyScreen(address: String) {
    val vm = containerViewModel(key = "withings:$address") { WithingsKeyViewModel(it, address) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(vm) {
        vm.applied.collect {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            nav.pop()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    ScalePage(title = stringResource(R.string.withings_key_title)) {
        item {
            Reveal(state.error != null) {
                val error = state.error
                if (error != null) ErrorBlock(stringResource(error, state.errorDetail.orEmpty()))
            }
        }
        item {
            val enter = YoinMotion.defaultEffectsSpec<Float>()
            val exit = YoinMotion.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = state.rows,
                contentKey = { it == null },
                transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
                label = "withingsStep",
            ) { rows ->
                if (rows == null) LoginStep(state.busy, vm::signIn) else DevicesStep(rows, vm::apply, vm::restart)
            }
        }
    }
}

@Composable
private fun LoginStep(busy: Boolean, onSignIn: (String, String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section {
            SettingRow(
                title = stringResource(R.string.withings_privacy_title),
                value = stringResource(R.string.withings_privacy),
                icon = PiliSymbols.Lock,
            )
            SettingRow(
                title = stringResource(R.string.withings_account_title),
                value = stringResource(R.string.withings_account_hint),
                icon = YoinSymbols.Info,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScaleTextField(email, { email = it }, stringResource(R.string.withings_email))
            ScaleTextField(password, { password = it }, stringResource(R.string.withings_password), password = true)
            PrimaryAction(
                text = stringResource(R.string.withings_sign_in),
                busy = busy,
                enabled = email.isNotBlank() && password.isNotEmpty(),
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSignIn(email, password)
            }
        }
    }
}

@Composable
private fun DevicesStep(rows: List<WithingsKeyRow>, onApply: (WithingsKeyRow) -> Unit, onRestart: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.isEmpty()) {
            EmptyState(icon = YoinSymbols.Devices, title = stringResource(R.string.withings_no_devices))
        } else {
            Section(title = stringResource(R.string.withings_devices)) {
                rows.forEach { row ->
                    val container by animateColorAsState(
                        if (row.matches) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        YoinMotion.defaultEffectsSpec(),
                        label = "withingsRow",
                    )
                    SettingRow(
                        title = row.label,
                        value = listOfNotNull(
                            stringResource(R.string.withings_match).takeIf { row.matches },
                            row.scale.mac,
                            stringResource(R.string.withings_no_key).takeIf { !row.usable },
                        ).joinToString("\n"),
                        icon = if (row.matches) YoinSymbols.Check else YoinSymbols.DeviceOther,
                        enabled = row.usable,
                        modifier = Modifier.background(container),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onApply(row)
                        },
                    )
                }
            }
        }
        TextButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onRestart()
        }) {
            Icon(YoinSymbols.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.withings_restart))
        }
    }
}

@Composable
private fun ErrorBlock(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(YoinContainerShapes.Card)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(YoinSymbols.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrimaryAction(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) {
            LoadingIndicator(Modifier.size(24.dp))
        } else {
            Icon(YoinSymbols.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text)
        }
    }
}
