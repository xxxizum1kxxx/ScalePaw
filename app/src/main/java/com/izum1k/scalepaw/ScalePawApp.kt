package com.izum1k.scalepaw

import android.app.Application
import android.content.Context
import com.izum1k.scalepaw.data.transfer.BackupScheduler
import com.izum1k.scalepaw.feature.reminder.ReminderScheduler
import com.izum1k.scalepaw.feature.widget.WeightWidget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
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
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ScalePawApp).container
