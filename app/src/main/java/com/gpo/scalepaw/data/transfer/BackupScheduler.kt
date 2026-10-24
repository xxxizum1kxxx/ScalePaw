package com.gpo.scalepaw.data.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.settings.BackupSettings
import java.util.concurrent.TimeUnit

/** Periodic automatic backups through WorkManager, rescheduled whenever the settings change. */
object BackupScheduler {
    private const val WORK = "auto_backup"

    fun apply(context: Context, settings: BackupSettings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.enabled || settings.folderUri == null) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(settings.interval.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (applicationContext.container.backup.autoBackup().isSuccess) Result.success() else Result.retry()
}
