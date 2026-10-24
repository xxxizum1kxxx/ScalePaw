package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.ActivityLevel
import com.izum1k.scalepaw.data.model.AmputationPart
import com.izum1k.scalepaw.data.model.BodyFatFormula
import com.izum1k.scalepaw.data.model.BodyWaterFormula
import com.izum1k.scalepaw.data.model.Gender
import com.izum1k.scalepaw.data.model.LbmFormula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyFormulasTest {

    @Test
    fun should_compute_bmi_when_weight_and_height_given() {
        assertEquals(24.69f, BodyFormulas.bmi(80f, 180f)!!, 0.01f)
    }

    @Test
    fun should_return_null_when_height_missing() {
        assertNull(BodyFormulas.bmi(80f, 0f))
        assertNull(BodyFormulas.bmi(null, 180f))
    }

    @Test
    fun should_apply_mifflin_st_jeor_when_computing_bmr() {
        assertEquals(1780f, BodyFormulas.bmr(80f, 180f, 30, Gender.MALE)!!, 0.01f)
        assertEquals(1614f, BodyFormulas.bmr(80f, 180f, 30, Gender.FEMALE)!!, 0.01f)
    }

    @Test
    fun should_scale_bmr_by_activity_when_computing_tdee() {
        assertEquals(2759f, BodyFormulas.tdee(1780f, ActivityLevel.MODERATE)!!, 0.01f)
        assertNull(BodyFormulas.tdee(null, ActivityLevel.MODERATE))
    }

    @Test
    fun should_use_jackson_pollock_when_three_calipers_given() {
        assertEquals(9.06f, BodyFormulas.caliperFat(1f, 1f, 1f, 30, Gender.MALE)!!, 0.05f)
        assertNull(BodyFormulas.caliperFat(1f, null, 1f, 30, Gender.MALE))
    }

    @Test
    fun should_estimate_body_fat_when_formula_is_deurenberg_1991() {
        assertEquals(20.33f, BodyFormulas.bodyFat(BodyFatFormula.DEURENBERG_1991, 80f, 180f, 30, Gender.MALE)!!, 0.05f)
    }

    @Test
    fun should_use_tape_measurements_when_formula_is_us_navy() {
        val male = BodyFormulas.bodyFat(BodyFatFormula.US_NAVY, 80f, 180f, 30, Gender.MALE, waistCm = 90f, neckCm = 40f)
        assertEquals(18.46f, male!!, 0.1f)
        assertNull(BodyFormulas.bodyFat(BodyFatFormula.US_NAVY, 60f, 165f, 30, Gender.FEMALE, waistCm = 70f, neckCm = 32f))
    }

    @Test
    fun should_clamp_body_fat_when_formula_overshoots() {
        assertEquals(70f, BodyFormulas.bodyFat(BodyFatFormula.EDDY_1976, 250f, 150f, 30, Gender.FEMALE)!!, 0f)
    }

    @Test
    fun should_return_null_when_formula_is_off() {
        assertNull(BodyFormulas.bodyFat(BodyFatFormula.OFF, 80f, 180f, 30, Gender.MALE))
        assertNull(BodyFormulas.bodyWaterLiters(BodyWaterFormula.OFF, 80f, 180f, Gender.MALE))
        assertNull(BodyFormulas.lbmKg(LbmFormula.OFF, 80f, 180f, Gender.MALE, 20f))
    }

    @Test
    fun should_estimate_body_water_when_formula_is_lee_song_kim() {
        assertEquals(44.70f, BodyFormulas.bodyWaterLiters(BodyWaterFormula.LEE_SONG_KIM_2001, 80f, 180f, Gender.MALE)!!, 0.05f)
    }

    @Test
    fun should_subtract_fat_when_lbm_formula_is_weight_minus_fat() {
        assertEquals(64f, BodyFormulas.lbmKg(LbmFormula.WEIGHT_MINUS_BODY_FAT, 80f, 180f, Gender.MALE, 20f)!!, 0.01f)
        assertNull(BodyFormulas.lbmKg(LbmFormula.WEIGHT_MINUS_BODY_FAT, 80f, 180f, Gender.MALE, null))
    }

    @Test
    fun should_restore_full_weight_when_limbs_are_missing() {
        assertEquals(93.37f, BodyFormulas.amputationCorrected(76f, listOf(AmputationPart.FULL_LEG)), 0.01f)
        assertEquals(76f, BodyFormulas.amputationCorrected(76f, emptyList()), 0f)
    }

    @Test
    fun should_round_to_nearest_when_rounding_to_two_decimals() {
        assertEquals(24.69f, BodyFormulas.round2(24.6913f), 0f)
        assertEquals(0.9f, BodyFormulas.round2(0.9f), 0f)
        assertEquals(21.48f, BodyFormulas.round2(21.4796f), 0f)
    }
}
