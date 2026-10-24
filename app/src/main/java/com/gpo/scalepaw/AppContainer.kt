package com.gpo.scalepaw

import android.content.Context
import com.gpo.scalepaw.ble.ScaleManager
import com.gpo.scalepaw.data.local.ScaleDatabase
import com.gpo.scalepaw.data.repository.ScaleRepository
import com.gpo.scalepaw.data.repository.Session
import com.gpo.scalepaw.data.settings.SettingsRepository
import com.gpo.scalepaw.data.transfer.BackupManager
import com.gpo.scalepaw.data.transfer.CsvTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Manual dependency injection: one instance of each long-lived object, built on first use. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ScaleDatabase by lazy { ScaleDatabase.build(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val repository: ScaleRepository by lazy { ScaleRepository(database, settings) }
    val session: Session by lazy { Session(repository, settings, appScope) }
    val csv: CsvTransfer by lazy { CsvTransfer(repository) }
    val backup: BackupManager by lazy { BackupManager(app, database, settings) }
    val scales: ScaleManager by lazy { ScaleManager(app, repository, settings, appScope) }
}
