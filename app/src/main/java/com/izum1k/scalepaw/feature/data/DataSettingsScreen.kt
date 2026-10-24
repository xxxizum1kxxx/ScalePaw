package com.izum1k.scalepaw.feature.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.izum1k.scalepaw.MainActivity
import com.izum1k.scalepaw.R
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.model.BackupInterval
import com.izum1k.scalepaw.data.transfer.BackupManager
import com.izum1k.scalepaw.ui.component.ChoiceRow
import com.izum1k.scalepaw.ui.component.ConfirmDialog
import com.izum1k.scalepaw.ui.component.Fmt
import com.izum1k.scalepaw.ui.component.ScalePage
import com.izum1k.scalepaw.ui.component.ScaleSymbols
import com.izum1k.scalepaw.ui.component.Section
import com.izum1k.scalepaw.ui.component.SettingRow
import com.izum1k.scalepaw.ui.component.SwitchRow
import com.izum1k.scalepaw.ui.component.containerViewModel
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import kotlin.system.exitProcess

@Composable
fun DataSettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm = containerViewModel { DataViewModel(app, it) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val restored by vm.restored.collectAsStateWithLifecycle()
    val state by context.container.session.state.collectAsStateWithLifecycle()
    val backup = state.settings.backup
    val snackbar = remember { SnackbarHostState() }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val userName = state.user?.name.orEmpty()

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it.text) } }

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::backup) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setAutoFolder) }

    ScalePage(title = stringResource(R.string.settings_data), snackbar = snackbar) {
        if (busy) {
            item { LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        }
        item {
            Section(title = stringResource(R.string.section_csv, userName)) {
                SettingRow(
                    title = stringResource(R.string.csv_export),
                    value = stringResource(R.string.csv_export_summary),
                    icon = YoinSymbols.Share,
                    onClick = { exportCsv.launch("scalepaw_${userName.ifBlank { "user" }}.csv") },
                )
                SettingRow(
                    title = stringResource(R.string.csv_import),
                    value = stringResource(R.string.csv_import_summary),
                    icon = YoinSymbols.Download,
                    onClick = { importCsv.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
                )
            }
        }
        item {
            Section(title = stringResource(R.string.section_backup)) {
                SettingRow(
                    title = stringResource(R.string.backup_create),
                    value = stringResource(R.string.backup_create_summary),
                    icon = ScaleSymbols.Upload,
                    onClick = { createBackup.launch(BackupManager.suggestedName()) },
                )
                SettingRow(
                    title = stringResource(R.string.backup_restore),
                    value = stringResource(R.string.backup_restore_summary),
                    icon = PiliSymbols.History,
                    onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
            }
        }
        item {
            Section(title = stringResource(R.string.section_auto_backup)) {
                SwitchRow(
                    title = stringResource(R.string.auto_backup),
                    value = when {
                        backup.lastError != null -> stringResource(R.string.auto_backup_error, backup.lastError)
                        backup.lastRun > 0 -> stringResource(R.string.auto_backup_last, Fmt.date(backup.lastRun))
                        else -> stringResource(R.string.auto_backup_summary)
                    },
                    checked = backup.enabled && backup.folderUri != null,
                    onCheckedChange = { on -> if (on && backup.folderUri == null) pickFolder.launch(null) else vm.updateBackup { it.copy(enabled = on) } },
                )
                SettingRow(
                    title = stringResource(R.string.auto_backup_folder),
                    value = backup.folderUri?.let { Uri.parse(it).lastPathSegment } ?: stringResource(R.string.auto_backup_no_folder),
                    icon = YoinSymbols.Folder,
                    onClick = { pickFolder.launch(null) },
                )
                ChoiceRow(
                    title = stringResource(R.string.auto_backup_interval),
                    options = BackupInterval.entries,
                    selected = backup.interval,
                    label = { stringResource(it.label) },
                    onSelect = { i -> vm.updateBackup { it.copy(interval = i) } },
                    enabled = backup.enabled,
                )
                ChoiceRow(
                    title = stringResource(R.string.auto_backup_keep),
                    options = listOf(1, 3, 5, 10, 20),
                    selected = backup.keepCount,
                    label = { it.toString() },
                    onSelect = { k -> vm.updateBackup { it.copy(keepCount = k) } },
                    enabled = backup.enabled,
                )
                SettingRow(
                    title = stringResource(R.string.auto_backup_now),
                    enabled = backup.folderUri != null,
                    onClick = vm::backupNow,
                )
            }
        }
        item {
            Section {
                SettingRow(
                    title = stringResource(R.string.demo_data),
                    value = stringResource(R.string.demo_data_summary),
                    icon = YoinSymbols.Sparkle,
                    onClick = vm::addDemoData,
                )
                SettingRow(
                    title = stringResource(R.string.data_delete_all),
                    value = stringResource(R.string.data_delete_all_summary, userName),
                    icon = YoinSymbols.Delete,
                    onClick = { confirmDelete = true },
                )
            }
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text(stringResource(R.string.csv_import)) },
            text = { Text(stringResource(R.string.csv_import_mode, userName)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.importCsv(uri, replace = false)
                    importUri = null
                }) { Text(stringResource(R.string.csv_import_merge)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.importCsv(uri, replace = true)
                    importUri = null
                }) { Text(stringResource(R.string.csv_import_replace)) }
            },
        )
    }
    restoreUri?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_restore),
            text = stringResource(R.string.backup_restore_confirm),
            confirm = stringResource(R.string.backup_restore_action),
            onDismiss = { restoreUri = null },
            onConfirm = { vm.restore(uri) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.data_delete_all),
            text = stringResource(R.string.data_delete_all_confirm, userName),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = vm::deleteAll,
        )
    }
    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_restored)) },
            text = { Text(stringResource(R.string.backup_restored_body)) },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                    exitProcess(0)
                }) { Text(stringResource(R.string.action_restart)) }
            },
        )
    }
}
