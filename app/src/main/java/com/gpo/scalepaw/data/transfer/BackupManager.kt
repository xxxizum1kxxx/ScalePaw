package com.gpo.scalepaw.data.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.gpo.scalepaw.data.local.ScaleDatabase
import com.gpo.scalepaw.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Whole-database backups as a ZIP holding the SQLite file. Restoring replaces the database file
 * and needs a process restart, since Room keeps the old connection open.
 */
class BackupManager(
    private val context: Context,
    private val database: ScaleDatabase,
    private val settings: SettingsRepository,
) {
    private val dbFile: File get() = context.getDatabasePath(ScaleDatabase.NAME)

    suspend fun backup(out: OutputStream) = withContext(Dispatchers.IO) {
        checkpoint()
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** Validates and stages the archive; the caller restarts the app afterwards. */
    suspend fun restore(input: InputStream) = withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, "restore.db")
        var found = false
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name == DB_ENTRY) {
                    staged.outputStream().use { zip.copyTo(it) }
                    found = true
                }
            }
        }
        if (!found || !isSqlite(staged)) {
            staged.delete()
            throw IOException("Not a ScalePaw backup")
        }
        database.close()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        staged.copyTo(dbFile, overwrite = true)
        staged.delete()
    }

    /** Writes a dated backup into the chosen folder and prunes the oldest beyond the keep count. */
    suspend fun autoBackup(): Result<Unit> = runCatching {
        val s = settings.current().backup
        val folder = s.folderUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) } ?: error("No backup folder")
        val name = "$PREFIX${LocalDateTime.now().format(STAMP)}.zip"
        val file = folder.createFile("application/zip", name) ?: error("Cannot create $name")
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(file.uri)?.use { backup(it) } ?: error("Cannot open $name")
            folder.listFiles()
                .filter { it.name?.startsWith(PREFIX) == true && it.name?.endsWith(".zip") == true }
                .sortedByDescending { it.name }
                .drop(s.keepCount.coerceAtLeast(1))
                .forEach { it.delete() }
        }
    }.also { result ->
        val now = System.currentTimeMillis()
        val current = settings.current().backup
        settings.setBackup(
            current.copy(
                lastRun = if (result.isSuccess) now else current.lastRun,
                lastError = result.exceptionOrNull()?.message,
            ),
        )
    }

    private fun checkpoint() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
    }

    private fun isSqlite(file: File): Boolean {
        val magic = ByteArray(16)
        val read = file.inputStream().use { it.read(magic) }
        return read == 16 && String(magic, Charsets.US_ASCII).startsWith("SQLite format 3")
    }

    companion object {
        const val DB_ENTRY = "scalepaw.db"
        const val PREFIX = "scalepaw_backup_"
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        fun suggestedName(): String = "$PREFIX${LocalDateTime.now().format(STAMP)}.zip"
    }
}
