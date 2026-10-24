package com.gpo.scalepaw.feature.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gpo.scalepaw.MainActivity
import com.gpo.scalepaw.R
import com.gpo.scalepaw.container
import com.gpo.scalepaw.data.settings.ReminderSettings
import com.gpo.scalepaw.data.transfer.BackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A daily weigh-in reminder on chosen weekdays: one inexact alarm, re-armed after it fires. */
object ReminderScheduler {
    private const val CHANNEL = "reminder"
    private const val NOTIFICATION_ID = 1

    fun apply(context: Context, settings: ReminderSettings) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        alarms.cancel(pending)
        val next = nextTrigger(settings, LocalDateTime.now()) ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    /** The next moment at [ReminderSettings.minuteOfDay] on an enabled weekday, strictly after [now]. */
    fun nextTrigger(settings: ReminderSettings, now: LocalDateTime): LocalDateTime? {
        if (!settings.enabled || settings.days.isEmpty()) return null
        val time = LocalTime.of(settings.minuteOfDay / 60, settings.minuteOfDay % 60)
        return (0..7).asSequence()
            .map { now.toLocalDate().plusDays(it.toLong()).atTime(time) }
            .first { it.isAfter(now) && it.dayOfWeek in settings.days }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notify(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Fires the reminder and re-arms it; also re-arms after reboot, time or timezone changes. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = app.container.settings.current()
                if (intent.action == null && settings.reminder.enabled) ReminderScheduler.notify(app)
                ReminderScheduler.apply(app, settings.reminder)
                BackupScheduler.apply(app, settings.backup)
            } finally {
                result.finish()
            }
        }
    }
}
