package com.izum1k.scalepaw.domain

import com.izum1k.scalepaw.data.model.AmputationPart
import com.izum1k.scalepaw.data.model.BodyFatFormula
import com.izum1k.scalepaw.data.model.Gender
import com.izum1k.scalepaw.data.model.LbmFormula
import com.izum1k.scalepaw.data.model.Limb
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricSpec
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitType
import com.izum1k.scalepaw.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MeasurementProcessorTest {
    private val types = Metrics.builtins.mapIndexed { i, spec -> MetricType.seed(spec, i).copy(id = i + 1L, isEnabled = true) }
    private val user = User(id = 1, name = "A", birthDate = LocalDate.of(1990, 1, 1), gender = Gender.MALE, heightCm = 180f)
    private val timestamp = LocalDate.of(2025, 6, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun id(spec: MetricSpec, list: List<MetricType> = types) = list.first { it.identity == spec.identity }.id

    private fun process(
        input: Map<MetricSpec, Float>,
        settings: FormulaSettings = FormulaSettings(),
        who: User = user,
        list: List<MetricType> = types,
        previous: Measurement? = null,
    ): Map<Long, MetricValue> = MeasurementProcessor(list).process(
        who,
        timestamp,
        input.mapKeys { id(it.key, list) }.mapValues { MetricValue.of(it.value) },
        settings,
        previous,
    )

    private fun Map<Long, MetricValue>.of(spec: MetricSpec, list: List<MetricType> = types) = this[id(spec, list)]?.number

    @Test
    fun should_derive_bmi_bmr_and_tdee_when_only_weight_given() {
        val out = process(mapOf(Metrics.WEIGHT to 80f))
        assertEquals(24.69f, out.of(Metrics.BMI)!!, 0.01f)
        assertEquals(1755f, out.of(Metrics.BMR)!!, 0.01f)
        assertEquals(2106f, out.of(Metrics.TDEE)!!, 0.01f)
    }

    @Test
    fun should_keep_scale_bmr_when_scale_reports_it() {
        val out = process(mapOf(Metrics.WEIGHT to 80f, Metrics.BMR to 1900f))
        assertEquals(1900f, out.of(Metrics.BMR)!!, 0f)
        assertEquals(2280f, out.of(Metrics.TDEE)!!, 0.01f)
    }

    @Test
    fun should_convert_to_kg_when_weight_unit_is_pounds() {
        val list = types.map { if (it.identity == Metrics.WEIGHT.identity) it.copy(unit = UnitType.LB) else it }
        val out = process(mapOf(Metrics.WEIGHT to 176.37f), list = list)
        assertEquals(24.69f, out.of(Metrics.BMI, list)!!, 0.01f)
    }

    @Test
    fun should_fill_composition_when_formulas_enabled() {
        val out = process(
            mapOf(Metrics.WEIGHT to 80f),
            FormulaSettings(bodyFat = BodyFatFormula.DEURENBERG_1991, lbm = LbmFormula.WEIGHT_MINUS_BODY_FAT),
        )
        assertEquals(21.48f, out.of(Metrics.BODY_FAT)!!, 0.001f)
        assertEquals(62.82f, out.of(Metrics.LBM)!!, 0.001f)
    }

    @Test
    fun should_store_kg_when_body_fat_unit_is_a_mass() {
        val list = types.map { if (it.identity == Metrics.BODY_FAT.identity) it.copy(unit = UnitType.KG) else it }
        val out = process(mapOf(Metrics.WEIGHT to 80f), FormulaSettings(bodyFat = BodyFatFormula.DEURENBERG_1991), list = list)
        assertEquals(17.18f, out.of(Metrics.BODY_FAT, list)!!, 0.001f)
    }

    @Test
    fun should_correct_weight_when_user_has_amputation() {
        val amputee = user.copy(amputations = mapOf(Limb.LEFT_LEG to AmputationPart.FULL_LEG))
        val out = process(mapOf(Metrics.WEIGHT to 76f), who = amputee)
        assertEquals(93.37f, out.of(Metrics.WEIGHT)!!, 0.001f)
    }

    @Test
    fun should_carry_over_values_when_enabled_and_previous_exists() {
        val previous = Measurement(userId = 1, timestamp = timestamp - 86_400_000, values = mapOf(id(Metrics.WAIST) to MetricValue.of(90f)))
        val out = process(mapOf(Metrics.WEIGHT to 80f), FormulaSettings(carryOverValues = true), previous = previous)
        assertEquals(90f, out.of(Metrics.WAIST)!!, 0f)
        assertEquals(0.5f, out.of(Metrics.WHTR)!!, 0f)
    }

    @Test
    fun should_not_carry_over_when_disabled() {
        val previous = Measurement(userId = 1, timestamp = timestamp - 86_400_000, values = mapOf(id(Metrics.WAIST) to MetricValue.of(90f)))
        val out = process(mapOf(Metrics.WEIGHT to 80f), previous = previous)
        assertFalse(id(Metrics.WAIST) in out)
    }

    @Test
    fun should_drop_derived_values_when_inputs_missing() {
        val out = process(mapOf(Metrics.WAIST to 90f, Metrics.HIPS to 100f))
        assertFalse(id(Metrics.BMI) in out)
        assertEquals(0.9f, out.of(Metrics.WHR)!!, 0f)
    }
}
