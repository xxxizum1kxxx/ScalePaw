package com.izum1k.scalepaw.feature.micloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.data.micloud.MiCloudClient
import com.izum1k.scalepaw.ui.component.ChoiceRow
import com.izum1k.scalepaw.ui.component.EmptyState
import com.izum1k.scalepaw.ui.component.Reveal
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleTextField
import com.izum1k.scalepaw.ui.component.ScaleTopBar
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.izum1k.scalepaw.ui.navigation.LocalNavigator
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes
import com.izum1k.scalepaw.ui.theme.YoinMotion

/** Signs in to the Xiaomi account and stores the BLE bind key of the scale at [address]. */
@Composable
fun MiCloudKeyScreen(address: String) {
    val vm = containerViewModel(key = "micloud:$address") { MiCloudKeyViewModel(it, address) }
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

    val webLogin = rememberLauncherForActivityResult(MiCloudLoginActivity.Contract()) { cookies ->
        if (cookies != null) vm.webSignedIn(cookies)
    }

    ScalePage(title = stringResource(R.string.micloud_title)) {
        item {
            Reveal(state.error != null) {
                val error = state.error
                if (error != null) ErrorBlock(stringResource(error, state.errorDetail.orEmpty()))
            }
        }
        item {
            AnimatedContent(
                targetState = state.step,
                contentKey = { it::class },
                transitionSpec = fadeThrough(),
                label = "micloudStep",
            ) { step ->
                when (step) {
                    MiCloudStep.Login -> LoginStep(state, vm, onWeb = { webLogin.launch(Unit) })
                    is MiCloudStep.Captcha -> CodeStep(
                        title = stringResource(R.string.micloud_captcha_title),
                        label = stringResource(R.string.micloud_captcha),
                        image = step.image,
                        busy = state.busy,
                        onCancel = vm::restart,
                        onSubmit = vm::submitCaptcha,
                    )
                    MiCloudStep.EmailCode -> CodeStep(
                        title = stringResource(R.string.micloud_email_title),
                        label = stringResource(R.string.micloud_email_code),
                        image = null,
                        busy = state.busy,
                        onCancel = vm::restart,
                        onSubmit = vm::submitEmailCode,
                    )
                    is MiCloudStep.Qr -> QrStep(step, vm::restart)
                    MiCloudStep.Searching -> Waiting(stringResource(R.string.micloud_searching))
                    is MiCloudStep.Devices -> DevicesStep(step.devices, vm::apply, vm::restart)
                }
            }
        }
    }
}

@Composable
private fun LoginStep(state: MiCloudUiState, vm: MiCloudKeyViewModel, onWeb: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section {
            SettingRow(
                title = stringResource(R.string.micloud_privacy_title),
                value = stringResource(R.string.micloud_privacy),
                icon = PiliSymbols.Lock,
            )
        }
        Section {
            ChoiceRow(
                title = stringResource(R.string.micloud_method),
                options = LoginMethod.entries,
                selected = state.method,
                label = { stringResource(it.labelRes) },
                onSelect = vm::setMethod,
                icon = if (state.method == LoginMethod.QR) PiliSymbols.Qr else YoinSymbols.Person,
            )
            ChoiceRow(
                title = stringResource(R.string.micloud_server),
                options = listOf<String?>(null) + MiCloudClient.SERVERS,
                selected = state.server,
                label = { serverLabel(it) },
                onSelect = vm::setServer,
                icon = YoinSymbols.Cloud,
            )
        }
        AnimatedContent(
            targetState = state.method,
            transitionSpec = fadeThrough(),
            label = "micloudMethod",
        ) { method ->
            when (method) {
                LoginMethod.WEB -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.micloud_web_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    PrimaryAction(text = stringResource(R.string.micloud_web_start), icon = YoinSymbols.Launch, busy = state.busy, enabled = true) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onWeb()
                    }
                }
                LoginMethod.PASSWORD -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScaleTextField(username, { username = it }, stringResource(R.string.micloud_username))
                    ScaleTextField(password, { password = it }, stringResource(R.string.micloud_password), password = true)
                    PrimaryAction(
                        text = stringResource(R.string.micloud_sign_in),
                        icon = YoinSymbols.Send,
                        busy = state.busy,
                        enabled = username.isNotBlank() && password.isNotEmpty(),
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.signIn(username, password)
                    }
                }
                LoginMethod.QR -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.micloud_qr_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    PrimaryAction(text = stringResource(R.string.micloud_qr_start), icon = PiliSymbols.Qr, busy = state.busy, enabled = true) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.startQr()
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeStep(title: String, label: String, image: ByteArray?, busy: Boolean, onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var code by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        if (image != null) RemoteImage(image, Modifier.fillMaxWidth().height(96.dp))
        ScaleTextField(code, { code = it }, label)
        PrimaryAction(text = stringResource(R.string.micloud_submit), icon = YoinSymbols.Check, busy = busy, enabled = code.isNotBlank()) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSubmit(code)
        }
        CancelAction(onCancel)
    }
}

@Composable
private fun QrStep(step: MiCloudStep.Qr, onCancel: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.micloud_qr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(
            Modifier
                .clip(YoinContainerShapes.Card)
                .background(Color.White)
                .padding(16.dp),
        ) {
            RemoteImage(step.image, Modifier.size(240.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                copyToClipboard(context, step.loginUrl)
                copied = true
            }) {
                Icon(if (copied) YoinSymbols.Check else YoinSymbols.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (copied) R.string.micloud_link_copied else R.string.micloud_copy_link))
            }
            FilledTonalButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, step.loginUrl)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) {
                Icon(YoinSymbols.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.micloud_share_link))
            }
        }
        Waiting(stringResource(R.string.micloud_qr_waiting))
        CancelAction(onCancel)
    }
}

@Composable
private fun DevicesStep(rows: List<MiCloudKeyRow>, onApply: (MiCloudKeyRow) -> Unit, onRestart: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.isEmpty()) {
            EmptyState(icon = YoinSymbols.Devices, title = stringResource(R.string.micloud_no_devices))
        } else {
            Section(title = stringResource(R.string.micloud_devices)) {
                rows.forEach { row ->
                    val container by animateColorAsState(
                        if (row.matches) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        YoinMotion.defaultEffectsSpec(),
                        label = "micloudRow",
                    )
                    SettingRow(
                        title = row.device.name,
                        value = listOfNotNull(
                            stringResource(R.string.micloud_match).takeIf { row.matches },
                            row.device.model,
                            row.device.mac,
                            stringResource(R.string.micloud_no_key).takeIf { !row.usable },
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
            Text(
                stringResource(R.string.micloud_devices_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        TextButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onRestart()
        }) {
            Icon(YoinSymbols.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.micloud_restart))
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
private fun Waiting(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrimaryAction(text: String, icon: ImageVector, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = busy,
            transitionSpec = fadeThrough(),
            label = "micloudAction",
        ) { loading ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text)
                }
            }
        }
    }
}

@Composable
private fun CancelAction(onCancel: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    TextButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onCancel()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.action_cancel)) }
}

@Composable
private fun RemoteImage(bytes: ByteArray, modifier: Modifier) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return
    Box(modifier.widthIn(max = 480.dp), contentAlignment = Alignment.Center) {
        Image(bitmap, contentDescription = null, filterQuality = FilterQuality.None)
    }
}

@Composable
private fun serverLabel(server: String?): String = when (server) {
    null -> stringResource(R.string.micloud_server_all)
    "cn" -> stringResource(R.string.micloud_server_cn)
    "de" -> stringResource(R.string.micloud_server_de)
    "us" -> stringResource(R.string.micloud_server_us)
    "ru" -> stringResource(R.string.micloud_server_ru)
    "tw" -> stringResource(R.string.micloud_server_tw)
    "sg" -> stringResource(R.string.micloud_server_sg)
    "in" -> stringResource(R.string.micloud_server_in)
    else -> server
}

private val LoginMethod.labelRes: Int
    get() = when (this) {
        LoginMethod.WEB -> R.string.micloud_method_web
        LoginMethod.PASSWORD -> R.string.micloud_method_password
        LoginMethod.QR -> R.string.micloud_method_qr
    }

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Xiaomi", text))
}

@Composable
private fun <S> fadeThrough(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
    val enter = YoinMotion.defaultEffectsSpec<Float>()
    val exit = YoinMotion.fastEffectsSpec<Float>()
    return { fadeIn(enter) togetherWith fadeOut(exit) }
}
