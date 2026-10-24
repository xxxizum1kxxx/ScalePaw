package com.gpo.scalepaw.feature.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpo.scalepaw.AppContainer
import com.gpo.scalepaw.R
import com.gpo.scalepaw.data.settings.BackupSettings
import com.gpo.scalepaw.data.transfer.BackupScheduler
import com.gpo.scalepaw.data.transfer.DemoData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot result to show in the snackbar. */
data class DataMessage(val text: String)

class DataViewModel(private val app: Application, private val container: AppContainer) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _messages = Channel<DataMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    private val resolver get() = app.contentResolver
    private fun currentUser() = container.session.state.value.user?.id

    fun exportCsv(uri: Uri) = run {
        val userId = currentUser() ?: return@run
        val count = resolver.openOutputStream(uri)?.use { container.csv.export(userId, it) } ?: error("open")
        say(app.resources.getQuantityString(R.plurals.exported_count, count, count))
    }

    fun importCsv(uri: Uri, replace: Boolean) = run {
        val userId = currentUser() ?: return@run
        val report = resolver.openInputStream(uri)?.use { container.csv.import(userId, it, replace) } ?: error("open")
        val base = app.resources.getQuantityString(R.plurals.imported_count, report.imported, report.imported)
        val extra = if (report.skippedColumns.isEmpty()) "" else "\n" + app.getString(R.string.import_skipped_columns, report.skippedColumns.joinToString(", "))
        say(base + extra)
    }

    fun backup(uri: Uri) = run {
        resolver.openOutputStream(uri)?.use { container.backup.backup(it) } ?: error("open")
        say(app.getString(R.string.backup_done))
    }

    fun restore(uri: Uri) = run {
        resolver.openInputStream(uri)?.use { container.backup.restore(it) } ?: error("open")
        _restored.value = true
    }

    fun setAutoFolder(uri: Uri) = run {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        updateBackup { it.copy(folderUri = uri.toString(), enabled = true) }
    }

    fun updateBackup(change: (BackupSettings) -> BackupSettings) = viewModelScope.launch {
        val next = change(container.settings.current().backup)
        container.settings.setBackup(next)
        BackupScheduler.apply(app, next)
    }

    fun backupNow() = run {
        container.backup.autoBackup().getOrThrow()
        say(app.getString(R.string.backup_done))
    }

    fun addDemoData() = run {
        val userId = currentUser() ?: return@run
        val rows = DemoData.generate(container.repository.typesNow())
        container.repository.importMeasurements(userId, rows, replace = false)
        say(app.resources.getQuantityString(R.plurals.imported_count, rows.size, rows.size))
    }

    fun deleteAll() = run {
        val userId = currentUser() ?: return@run
        container.repository.deleteAllMeasurements(userId)
        say(app.getString(R.string.data_deleted))
    }

    private fun say(text: String) {
        _messages.trySend(DataMessage(text))
    }

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                say(app.getString(R.string.operation_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                _busy.value = false
            }
        }
    }
}
