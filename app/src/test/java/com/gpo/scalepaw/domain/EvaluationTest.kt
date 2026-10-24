package com.gpo.scalepaw.domain

import com.gpo.scalepaw.data.model.EvaluationState
import com.gpo.scalepaw.data.model.Gender
import com.gpo.scalepaw.data.model.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationTest {

    private fun eval(spec: String, value: Float, age: Int = 30, gender: Gender = Gender.MALE) =
        Evaluation.evaluate(spec, value, age, gender, 180f)

    @Test
    fun should_rate_bmi_against_adult_range() {
        assertEquals(EvaluationState.NORMAL, eval(Metrics.BMI.identity, 24.7f)!!.state)
        assertEquals(EvaluationState.HIGH, eval(Metrics.BMI.identity, 26f)!!.state)
        assertEquals(EvaluationState.LOW, eval(Metrics.BMI.identity, 17f)!!.state)
    }

    @Test
    fun should_use_child_table_when_user_is_young() {
        val result = eval(Metrics.BMI.identity, 16f, age = 8, gender = Gender.FEMALE)!!
        assertEquals(14.1f, result.low, 0f)
        assertEquals(18.4f, result.high, 0f)
    }

    @Test
    fun should_pick_age_band_when_rating_body_fat() {
        val result = eval(Metrics.BODY_FAT.identity, 25f, age = 45)!!
        assertEquals(15f, result.low, 0f)
        assertEquals(EvaluationState.HIGH, result.state)
    }

    @Test
    fun should_return_null_when_age_has_no_band() {
        assertNull(eval(Metrics.MUSCLE.identity, 40f, age = 12))
    }

    @Test
    fun should_return_null_when_metric_has_no_reference() {
        assertNull(eval(Metrics.HEART_RATE.identity, 60f))
    }

    @Test
    fun should_derive_target_weight_from_bmi_band() {
        val result = Evaluation.targetWeight(70f, 180f, Gender.MALE)!!
        assertEquals(64.8f, result.low, 0.01f)
        assertEquals(81f, result.high, 0.01f)
        assertEquals(EvaluationState.NORMAL, result.state)
    }
}
