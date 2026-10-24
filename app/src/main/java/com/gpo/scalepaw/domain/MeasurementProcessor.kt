package com.gpo.scalepaw.domain

import com.gpo.scalepaw.data.model.BodyFatFormula
import com.gpo.scalepaw.data.model.BodyWaterFormula
import com.gpo.scalepaw.data.model.InputType
import com.gpo.scalepaw.data.model.LbmFormula
import com.gpo.scalepaw.data.model.Measurement
import com.gpo.scalepaw.data.model.MetricSpec
import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.MetricValue
import com.gpo.scalepaw.data.model.Metrics
import com.gpo.scalepaw.data.model.UnitConversion
import com.gpo.scalepaw.data.model.UnitType
import com.gpo.scalepaw.data.model.User

data class FormulaSettings(
    val bodyFat: BodyFatFormula = BodyFatFormula.OFF,
    val bodyWater: BodyWaterFormula = BodyWaterFormula.OFF,
    val lbm: LbmFormula = LbmFormula.OFF,
    /** Carry values the new entry lacks over from the previous one (manual entry only). */
    val carryOverValues: Boolean = false,
)

/**
 * Turns the values a user typed or a scale sent into the values that are stored:
 * amputation correction, value carry-over, body composition formulas, then derived metrics
 * (BMI, WHR, WHtR, BMR, TDEE, caliper body fat). Pure: no I/O, so every rule is unit tested.
 */
class MeasurementProcessor(private val types: List<MetricType>) {

    private val byIdentity = types.associateBy { it.identity }

    private fun type(spec: MetricSpec) = byIdentity[spec.identity]

    fun process(
        user: User,
        timestamp: Long,
        input: Map<Long, MetricValue>,
        settings: FormulaSettings,
        previous: Measurement? = null,
        applyAmputation: Boolean = true,
    ): Map<Long, MetricValue> {
        val values = input.filterValues { !it.isEmpty }.toMutableMap()

        if (applyAmputation && user.amputations.isNotEmpty()) {
            type(Metrics.WEIGHT)?.let { weight ->
                values[weight.id]?.float?.let { shown ->
                    val kg = UnitConversion.toKg(shown, weight.unit)
                    val corrected = BodyFormulas.amputationCorrected(kg, user.amputations.values)
                    values[weight.id] = MetricValue.of(BodyFormulas.round2(UnitConversion.fromKg(corrected, weight.unit)))
                }
            }
        }

        if (settings.carryOverValues && previous != null) {
            for (t in types) {
                if (t.id in values || t.isDerived || t.isInternal || !t.isEnabled || t.inputType == InputType.TEXT) continue
                val old = previous.values[t.id] ?: continue
                if ((old.number ?: 0f) != 0f) values[t.id] = old
            }
        }

        val age = user.ageOn(timestamp)
        val heightCm = user.heightCm
        fun kgOf(spec: MetricSpec): Float? = type(spec)?.let { t -> values[t.id]?.float?.let { UnitConversion.toKg(it, t.unit) } }
        fun cmOf(spec: MetricSpec): Float? = type(spec)?.let { t -> values[t.id]?.float?.let { UnitConversion.toCm(it, t.unit) } }
        fun put(spec: MetricSpec, value: Float?) {
            val t = type(spec) ?: return
            if (value == null || value.isNaN() || value.isInfinite()) values.remove(t.id) else values[t.id] = MetricValue.of(BodyFormulas.round2(value))
        }

        /** Stores a composition value given in kg, in the type's unit (percent of [weightKg] or a mass). */
        fun putComposition(spec: MetricSpec, kg: Float, weightKg: Float) {
            val t = type(spec) ?: return
            put(spec, if (t.unit == UnitType.PERCENT) (kg / weightKg * 100f).coerceIn(0f, 100f) else UnitConversion.fromKg(kg, t.unit))
        }

        val weightKg = kgOf(Metrics.WEIGHT)
        if (weightKg != null && weightKg > 0f) {
            if (settings.bodyFat != BodyFatFormula.OFF) {
                BodyFormulas.bodyFat(
                    settings.bodyFat, weightKg, heightCm, age, user.gender,
                    waistCm = cmOf(Metrics.WAIST), neckCm = cmOf(Metrics.NECK), hipsCm = cmOf(Metrics.HIPS),
                )?.let { pct -> putComposition(Metrics.BODY_FAT, weightKg * pct / 100f, weightKg) }
            }
            if (settings.bodyWater != BodyWaterFormula.OFF) {
                BodyFormulas.bodyWaterLiters(settings.bodyWater, weightKg, heightCm, user.gender)
                    ?.let { putComposition(Metrics.WATER, it, weightKg) }
            }
            if (settings.lbm != LbmFormula.OFF) {
                val fatPct = type(Metrics.BODY_FAT)?.let { t ->
                    values[t.id]?.float?.let { if (t.unit == UnitType.PERCENT) it else UnitConversion.toKg(it, t.unit) / weightKg * 100f }
                }
                BodyFormulas.lbmKg(settings.lbm, weightKg, heightCm, user.gender, fatPct)?.let { kg ->
                    type(Metrics.LBM)?.let { put(Metrics.LBM, UnitConversion.fromKg(kg, it.unit)) }
                }
            }
        }

        put(Metrics.BMI, BodyFormulas.bmi(weightKg, heightCm))
        put(Metrics.WHR, BodyFormulas.whr(cmOf(Metrics.WAIST), cmOf(Metrics.HIPS)))
        put(Metrics.WHTR, BodyFormulas.whtr(cmOf(Metrics.WAIST), heightCm))
        // A scale may report BMR itself; only compute it when it is missing.
        val bmrType = type(Metrics.BMR)
        val bmr = bmrType?.let { values[it.id]?.float } ?: BodyFormulas.bmr(weightKg, heightCm, age, user.gender)?.also { put(Metrics.BMR, it) }
        put(Metrics.TDEE, BodyFormulas.tdee(bmr, user.activityLevel))
        put(Metrics.CALIPER, BodyFormulas.caliperFat(cmOf(Metrics.CALIPER_1), cmOf(Metrics.CALIPER_2), cmOf(Metrics.CALIPER_3), age, user.gender))

        return values
    }
}
