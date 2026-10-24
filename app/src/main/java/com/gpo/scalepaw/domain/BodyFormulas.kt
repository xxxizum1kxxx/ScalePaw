package com.gpo.scalepaw.domain

import com.gpo.scalepaw.data.model.ActivityLevel
import com.gpo.scalepaw.data.model.AmputationPart
import com.gpo.scalepaw.data.model.BodyFatFormula
import com.gpo.scalepaw.data.model.BodyWaterFormula
import com.gpo.scalepaw.data.model.Gender
import com.gpo.scalepaw.data.model.LbmFormula
import kotlin.math.log10
import kotlin.math.round

/**
 * Body metrics derived from other values. Inputs are in kg / cm / years; every function
 * returns null when an input is missing or out of range rather than a nonsense number.
 * Formulas and constants follow openScale's implementations.
 */
object BodyFormulas {

    fun bmi(weightKg: Float?, heightCm: Float?): Float? {
        if (weightKg == null || weightKg <= 0f || heightCm == null || heightCm <= 0f) return null
        val m = heightCm / 100f
        return weightKg / (m * m)
    }

    fun whr(waistCm: Float?, hipsCm: Float?): Float? =
        if (waistCm != null && waistCm > 0f && hipsCm != null && hipsCm > 0f) waistCm / hipsCm else null

    fun whtr(waistCm: Float?, heightCm: Float?): Float? =
        if (waistCm != null && waistCm > 0f && heightCm != null && heightCm > 0f) waistCm / heightCm else null

    /** Mifflin-St Jeor. */
    fun bmr(weightKg: Float?, heightCm: Float?, age: Int, gender: Gender): Float? {
        if (weightKg == null || weightKg <= 0f || heightCm == null || heightCm <= 0f || age !in 1..120) return null
        val base = 10f * weightKg + 6.25f * heightCm - 5f * age
        return if (gender.isMale) base + 5f else base - 161f
    }

    fun tdee(bmr: Float?, activity: ActivityLevel): Float? = bmr?.takeIf { it > 0f }?.let { it * activity.factor }

    /** Jackson-Pollock three-site skinfold, Siri equation. Calipers in cm. */
    fun caliperFat(c1Cm: Float?, c2Cm: Float?, c3Cm: Float?, age: Int, gender: Gender): Float? {
        if (c1Cm == null || c2Cm == null || c3Cm == null || c1Cm <= 0f || c2Cm <= 0f || c3Cm <= 0f || age <= 0) return null
        val sumMm = (c1Cm + c2Cm + c3Cm) * 10f
        val density = if (gender.isMale) {
            1.10938f - 0.0008267f * sumMm + 0.0000016f * sumMm * sumMm - 0.0002574f * age
        } else {
            1.0994921f - 0.0009929f * sumMm + 0.0000023f * sumMm * sumMm - 0.0001392f * age
        }
        if (density <= 0f) return null
        return (4.95f / density - 4.5f) * 100f
    }

    /** Body fat in percent, clamped to 1..70. US Navy needs waist and neck (and hips for women) in cm. */
    fun bodyFat(
        formula: BodyFatFormula,
        weightKg: Float,
        heightCm: Float,
        age: Int,
        gender: Gender,
        waistCm: Float? = null,
        neckCm: Float? = null,
        hipsCm: Float? = null,
    ): Float? {
        val bmi = bmi(weightKg, heightCm) ?: return null
        val male = gender.isMale
        val value: Double? = when (formula) {
            BodyFatFormula.OFF -> null
            BodyFatFormula.DEURENBERG_1991 -> 1.2 * bmi + 0.23 * age - if (male) 16.2 else 5.4
            BodyFatFormula.DEURENBERG_1992 -> if (age >= 16) {
                1.2 * bmi + 0.23 * age - 10.8 * (if (male) 1 else 0) - 5.4
            } else {
                1.294 * bmi + 0.20 * age - 11.4 * (if (male) 1 else 0) - 8.0
            }
            BodyFatFormula.EDDY_1976 -> if (male) 1.281 * bmi - 10.13 else 1.48 * bmi - 7.0
            BodyFatFormula.GALLAGHER_2000_NON_ASIAN -> 64.5 - 848.0 / bmi + 0.079 * age
            BodyFatFormula.GALLAGHER_2000_ASIAN -> if (male) {
                51.9 - 740.0 / bmi + 0.029 * age
            } else {
                64.8 - 752.0 / bmi + 0.016 * age
            }
            BodyFatFormula.US_NAVY -> usNavy(heightCm, waistCm, neckCm, hipsCm, male)?.toDouble()
        }
        return value?.toFloat()?.coerceIn(1f, 70f)
    }

    private fun usNavy(heightCm: Float, waistCm: Float?, neckCm: Float?, hipsCm: Float?, male: Boolean): Float? {
        val heightIn = heightCm / 2.54f
        val waistIn = waistCm?.div(2.54f) ?: return null
        val neckIn = neckCm?.div(2.54f) ?: return null
        if (waistIn <= 0f || neckIn <= 0f || heightIn <= 0f) return null
        return if (male) {
            val diff = waistIn - neckIn
            if (diff > 0f) 86.010f * log10(diff) - 70.041f * log10(heightIn) + 36.76f else null
        } else {
            val hipsIn = hipsCm?.div(2.54f)?.takeIf { it > 0f } ?: return null
            val diff = waistIn + hipsIn - neckIn
            if (diff > 0f) 163.205f * log10(diff) - 97.684f * log10(heightIn) - 78.387f else null
        }
    }

    /** Total body water in litres (≈ kg). */
    fun bodyWaterLiters(formula: BodyWaterFormula, weightKg: Float, heightCm: Float, gender: Gender): Float? {
        val male = gender.isMale
        val liters = when (formula) {
            BodyWaterFormula.OFF -> null
            BodyWaterFormula.BEHNKE_1963 -> 0.72 * ((if (male) 0.204 else 0.18) * (heightCm * heightCm)) / 100.0
            BodyWaterFormula.DELWAIDE_CRENIER_1973 -> 0.72 * (-1.976 + 0.907 * weightKg)
            BodyWaterFormula.HUME_WEYERS_1971 -> if (male) {
                0.194786 * heightCm + 0.296785 * weightKg - 14.012934
            } else {
                0.34454 * heightCm + 0.183809 * weightKg - 35.270121
            }
            BodyWaterFormula.LEE_SONG_KIM_2001 -> if (male) {
                -28.3497 + 0.243057 * heightCm + 0.366248 * weightKg
            } else {
                -26.6224 + 0.262513 * heightCm + 0.232948 * weightKg
            }
        }
        return liters?.coerceAtLeast(0.0)?.toFloat()
    }

    fun lbmKg(formula: LbmFormula, weightKg: Float, heightCm: Float, gender: Gender, bodyFatPercent: Float?): Float? {
        val male = gender.isMale
        val kg = when (formula) {
            LbmFormula.OFF -> null
            LbmFormula.WEIGHT_MINUS_BODY_FAT -> bodyFatPercent?.let { weightKg * (1f - it / 100f).toDouble() }
            LbmFormula.BOER_1984 -> if (male) 0.4071 * weightKg + 0.267 * heightCm - 19.2 else 0.252 * weightKg + 0.473 * heightCm - 48.3
            LbmFormula.HUME_1966 -> if (male) {
                0.32810 * weightKg + 0.33929 * heightCm - 29.5336
            } else {
                0.29569 * weightKg + 0.41813 * heightCm - 43.2933
            }
        }
        return kg?.coerceAtLeast(0.0)?.toFloat()
    }

    /** The weight the scale would show with every limb present. */
    fun amputationCorrected(measuredKg: Float, parts: Collection<AmputationPart>): Float {
        val correction = parts.sumOf { it.correctionPercent.toDouble() }.toFloat().coerceIn(0f, 100f)
        val remaining = 100f - correction
        return if (correction > 0f && remaining > 0f) measuredKg * 100f / remaining else measuredKg
    }

    fun round2(value: Float): Float = round(value * 100f) / 100f
}
