package com.izum1k.scalepaw.feature.googlehealth

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.googlehealth.GoogleHealthError
import com.izum1k.scalepaw.ui.component.ChoiceRow
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.Reveal
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.ScaleTextField
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.izum1k.scalepaw.ui.theme.YoinContainerShapes

/** One-way push of the linked user's weight, body fat and height to Google Health with the user's own OAuth client. */
@Composable
fun GoogleHealthScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val vm = containerViewModel { GoogleHealthViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val actionError by vm.error.collectAsStateWithLifecycle()
    val session by context.container.session.state.collectAsStateWithLifecycle()
    var clientId by rememberSaveable(state.clientId) { mutableStateOf(state.clientId) }
    var clientSecret by remember { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.openUrl.collect { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) {
                vm.cancel()
            }
        }
    }
    LaunchedEffect(actionError) {
        if (actionError != null) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    val syncError = state.lastError?.let { stored ->
        val name = stored.substringBefore(':')
        val error = GoogleHealthError.entries.firstOrNull { it.name == name } ?: GoogleHealthError.FAILED
        errorText(error, stored.substringAfter(':', "").trim().ifEmpty { null })
    }
    val actionErrorText = actionError?.let { e -> errorText(e.error, e.message?.takeIf { it != e.error.name }) }

    ScalePage(title = stringResource(R.string.google_health)) {
        if (busy != null) {
            item { LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        }
        item {
            Section {
                SettingRow(
                    title = stringResource(R.string.google_health_disclosure_title),
                    value = stringResource(R.string.google_health_disclosure),
                    icon = PiliSymbols.Lock,
                )
            }
        }
        item {
            Reveal(actionErrorText != null) {
                actionErrorText?.let { ErrorBlock(it) }
            }
        }
        item {
            Section(title = stringResource(R.string.google_health_section_credentials)) {
                SettingRow(
                    title = stringResource(R.string.google_health_get_credentials),
                    value = stringResource(R.string.google_health_get_credentials_summary),
                    icon = YoinSymbols.Launch,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, CONSOLE_URL.toUri())) }
                    },
                )
                Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScaleTextField(clientId, { clientId = it }, stringResource(R.string.google_health_client_id), modifier = Modifier.padding(horizontal = 8.dp))
                    ScaleTextField(
                        clientSecret,
                        { clientSecret = it },
                        stringResource(R.string.google_health_client_secret),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        placeholder = if (state.hasSecret) stringResource(R.string.google_health_secret_saved) else null,
                        password = true,
                    )
                }
                SettingRow(
                    title = stringResource(R.string.google_health_save_credentials),
                    icon = YoinSymbols.Check,
                    enabled = clientId.isNotBlank() && (clientSecret.isNotBlank() || state.hasSecret) &&
                        (clientId.trim() != state.clientId || clientSecret.isNotBlank()),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.saveCredentials(clientId, clientSecret)
                        clientSecret = ""
                    },
                )
            }
            Text(
                stringResource(R.string.google_health_credentials_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        item {
            Section(title = stringResource(R.string.google_health_section_account)) {
                if (!state.connected || state.needsReauth) {
                    if (busy == GoogleHealthBusy.CONNECTING) {
                        SettingRow(
                            title = stringResource(R.string.google_health_connecting),
                            value = stringResource(R.string.google_health_connecting_summary),
                            icon = YoinSymbols.Launch,
                        )
                        SettingRow(
                            title = stringResource(R.string.action_cancel),
                            icon = YoinSymbols.Close,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                vm.cancel()
                            },
                        )
                    } else {
                        SettingRow(
                            title = stringResource(if (state.needsReauth) R.string.google_health_reconnect else R.string.google_health_connect),
                            value = stringResource(if (state.hasCredentials) R.string.google_health_connect_summary else R.string.google_health_need_credentials),
                            icon = PiliSymbols.Link,
                            enabled = state.hasCredentials && busy == null,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.connect()
                            },
                        )
                    }
                }
                if (state.connected) {
                    SettingRow(
                        title = stringResource(R.string.google_health_connected),
                        value = state.account ?: stringResource(R.string.google_health_account_unknown),
                        icon = YoinSymbols.Cloud,
                    )
                    val users = session.users
                    val linked = users.firstOrNull { it.id == state.linkedUserId }
                    if (users.isNotEmpty() && linked != null) {
                        ChoiceRow(
                            title = stringResource(R.string.google_health_linked_user),
                            options = users,
                            selected = linked,
                            label = { it.name },
                            onSelect = { user ->
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                vm.setLinkedUser(user.id)
                            },
                            icon = YoinSymbols.Person,
                        )
                    }
                    SettingRow(
                        title = stringResource(R.string.google_health_sync_now),
                        value = when {
                            syncError != null -> stringResource(R.string.google_health_last_error, syncError)
                            state.lastSync > 0 -> stringResource(R.string.google_health_last_sync, Fmt.date(state.lastSync) + " " + Fmt.time(context, state.lastSync))
                            else -> stringResource(R.string.google_health_never_synced)
                        },
                        icon = YoinSymbols.Refresh,
                        enabled = busy == null && !state.needsReauth,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            vm.syncNow()
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.google_health_resync),
                        value = stringResource(R.string.google_health_resync_summary),
                        icon = ScaleSymbols.Upload,
                        enabled = busy == null && !state.needsReauth,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            vm.resyncAll()
                        },
                    )
                    SettingRow(
                        title = stringResource(R.string.google_health_disconnect),
                        icon = PiliSymbols.Logout,
                        enabled = busy == null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            confirmDisconnect = true
                        },
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.google_health_limits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmDisconnect) {
        val dismiss = { confirmDisconnect = false }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.google_health_disconnect)) },
            text = { Text(stringResource(R.string.google_health_disconnect_body)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.disconnect(deleteRemote = false)
                        dismiss()
                    }) { Text(stringResource(R.string.google_health_disconnect_keep)) }
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.disconnect(deleteRemote = true)
                        dismiss()
                    }) { Text(stringResource(R.string.google_health_disconnect_delete), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private const val CONSOLE_URL = "https://console.cloud.google.com/apis/library/health.googleapis.com"

@Composable
private fun errorText(error: GoogleHealthError, detail: String?): String = LocalContext.current.googleHealthErrorText(error, detail)

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
