package com.izum1k.scalepaw.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleAssignmentTest {

    private val users = listOf(1L, 2L, 3L)
    private val last = mapOf(1L to 80f, 2L to 55f)

    private fun choose(
        forced: Long? = null,
        slot: Long? = null,
        weight: Float? = 56f,
        smart: Boolean = true,
        tolerance: Int = 5,
        ignore: Boolean = false,
        current: Long = 1L,
    ) = ScaleAssignment.choose(users, current, forced, slot, weight, last, smart, tolerance, ignore)

    @Test
    fun should_use_forced_user_when_one_is_chosen() {
        assertEquals(Assignment.To(3L), choose(forced = 3L, slot = 1L))
    }

    @Test
    fun should_prefer_scale_slot_when_it_matches_a_user() {
        assertEquals(Assignment.To(1L), choose(slot = 1L))
    }

    @Test
    fun should_ignore_unknown_slot_when_falling_back() {
        assertEquals(Assignment.To(2L), choose(slot = 99L))
    }

    @Test
    fun should_pick_closest_weight_when_smart_assignment_is_on() {
        assertEquals(Assignment.To(2L), choose())
    }

    @Test
    fun should_keep_current_user_when_smart_assignment_is_off() {
        assertEquals(Assignment.To(1L), choose(smart = false))
    }

    @Test
    fun should_keep_current_user_when_outside_tolerance() {
        assertEquals(Assignment.To(1L), choose(weight = 68f))
    }

    @Test
    fun should_ignore_reading_when_outside_tolerance_and_ignoring_is_on() {
        assertEquals(Assignment.Ignored, choose(weight = 68f, ignore = true))
    }

    @Test
    fun should_fall_back_to_first_user_when_current_is_gone() {
        assertEquals(Assignment.To(1L), choose(smart = false, current = 42L))
    }
}
