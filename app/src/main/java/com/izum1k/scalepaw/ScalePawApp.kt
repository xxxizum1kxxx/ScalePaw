package com.izum1k.scalepaw

import android.app.Application
import android.content.Context
import com.izum1k.scalepaw.data.googlehealth.GoogleHealthScheduler
import com.izum1k.scalepaw.data.transfer.BackupScheduler
import com.izum1k.scalepaw.feature.reminder.ReminderScheduler
import com.izum1k.scalepaw.feature.widget.WeightWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ScalePawApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.repository.ensureSeeded()
            val s = container.settings.current()
            ReminderScheduler.apply(this@ScalePawApp, s.reminder)
            BackupScheduler.apply(this@ScalePawApp, s.backup)
        }
        container.appScope.launch {
            container.session.state
                .filter { it.loaded }
                .map { Triple(it.user?.id, it.measurements.firstOrNull(), it.measurements.getOrNull(1)?.id) }
                .distinctUntilChanged()
                .drop(1)
                .collect { WeightWidget.refresh(this@ScalePawApp) }
        }
        watchGoogleHealth()
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun watchGoogleHealth() = container.appScope.launch {
        val db = container.database
        container.googleHealth.store.state
            .map { if (it.connected) it.linkedUserId else null }
            .distinctUntilChanged()
            .flatMapLatest { userId ->
                if (userId == null) {
                    emptyFlow()
                } else {
                    combine(
                        db.measurements().observeForUser(userId),
                        db.users().observeAll().map { users -> users.firstOrNull { it.id == userId }?.heightCm },
                    ) { measurements, height -> measurements to height }
                }
            }
            .debounce(5_000)
            .collect { GoogleHealthScheduler.enqueue(this@ScalePawApp) }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ScalePawApp).container
