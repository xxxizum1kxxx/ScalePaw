package com.gpo.scalepaw.feature.reminder

import com.gpo.scalepaw.data.settings.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderSchedulerTest {
    private val monday0800 = LocalDateTime.of(2025, 3, 3, 8, 0)

    @Test
    fun should_fire_later_today_when_time_not_yet_passed() {
        val next = ReminderScheduler.nextTrigger(ReminderSettings(enabled = true, minuteOfDay = 9 * 60), monday0800)
        assertEquals(LocalDateTime.of(2025, 3, 3, 9, 0), next)
    }

    @Test
    fun should_skip_to_next_enabled_day_when_today_passed() {
        val settings = ReminderSettings(enabled = true, minuteOfDay = 7 * 60, days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(LocalDateTime.of(2025, 3, 6, 7, 0), ReminderScheduler.nextTrigger(settings, monday0800))
    }

    @Test
    fun should_wait_a_week_when_only_today_enabled_and_passed() {
        val settings = ReminderSettings(enabled = true, minuteOfDay = 8 * 60, days = setOf(DayOfWeek.MONDAY))
        assertEquals(LocalDateTime.of(2025, 3, 10, 8, 0), ReminderScheduler.nextTrigger(settings, monday0800))
    }

    @Test
    fun should_not_schedule_when_disabled_or_no_days() {
        assertNull(ReminderScheduler.nextTrigger(ReminderSettings(enabled = false), monday0800))
        assertNull(ReminderScheduler.nextTrigger(ReminderSettings(enabled = true, days = emptySet()), monday0800))
    }
}
