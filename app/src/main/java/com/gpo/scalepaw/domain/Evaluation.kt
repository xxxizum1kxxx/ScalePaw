package com.gpo.scalepaw.domain

import com.gpo.scalepaw.data.model.EvaluationState
import com.gpo.scalepaw.data.model.Gender
import com.gpo.scalepaw.data.model.Metrics

data class EvaluationResult(val value: Float, val low: Float, val high: Float, val state: EvaluationState)

/**
 * Reference ranges by sex and age (openScale's tables: body fat, water, muscle, BMI incl.
 * children, WHtR, WHR, visceral fat, LBM, waist), and a BMI-based target weight band.
 */
object Evaluation {
    private data class Band(val ageMin: Int, val ageMax: Int, val low: Float, val high: Float)

    private class Table(private val bands: List<Band>) {
        fun evaluate(value: Float, age: Int): EvaluationResult {
            val band = bands.firstOrNull { age in it.ageMin..it.ageMax }
                ?: return EvaluationResult(value, -1f, -1f, EvaluationState.UNDEFINED)
            return EvaluationResult(value, band.low, band.high, stateOf(value, band.low, band.high))
        }
    }

    private fun stateOf(value: Float, low: Float, high: Float) = when {
        value < low -> EvaluationState.LOW
        value > high -> EvaluationState.HIGH
        else -> EvaluationState.NORMAL
    }

    private fun table(vararg bands: Band) = Table(bands.toList())

    private val fatMale = table(
        Band(10, 14, 11f, 16f), Band(15, 19, 12f, 17f), Band(20, 29, 13f, 18f), Band(30, 39, 14f, 19f),
        Band(40, 49, 15f, 20f), Band(50, 59, 16f, 21f), Band(60, 69, 17f, 22f), Band(70, 1000, 18f, 23f),
    )
    private val fatFemale = table(
        Band(10, 14, 16f, 21f), Band(15, 19, 17f, 22f), Band(20, 29, 18f, 23f), Band(30, 39, 19f, 24f),
        Band(40, 49, 20f, 25f), Band(50, 59, 21f, 26f), Band(60, 69, 22f, 27f), Band(70, 1000, 23f, 28f),
    )
    private val waterMale = table(Band(10, 1000, 50f, 65f))
    private val waterFemale = table(Band(10, 1000, 45f, 60f))
    private val muscleMale = table(
        Band(18, 29, 37.9f, 46.7f), Band(30, 39, 34.1f, 44.1f), Band(40, 49, 33.1f, 41.1f),
        Band(50, 59, 31.7f, 38.5f), Band(60, 69, 29.9f, 37.7f), Band(70, 1000, 28.7f, 43.3f),
    )
    private val muscleFemale = table(
        Band(18, 29, 28.4f, 39.8f), Band(30, 39, 25.0f, 36.2f), Band(40, 49, 24.2f, 34.2f),
        Band(50, 59, 24.7f, 33.5f), Band(60, 69, 22.7f, 31.9f), Band(70, 1000, 25.5f, 34.9f),
    )
    private val bmiMale = table(
        Band(5, 5, 14.4f, 16.8f), Band(6, 6, 14.5f, 17.1f), Band(7, 7, 14.6f, 17.5f), Band(8, 8, 14.8f, 17.9f),
        Band(9, 9, 15.1f, 18.5f), Band(10, 10, 15.4f, 19.2f), Band(11, 11, 15.8f, 20f), Band(12, 12, 16.3f, 20.9f),
        Band(13, 13, 16.9f, 21.8f), Band(14, 14, 17.6f, 22.7f), Band(15, 15, 18.3f, 23.6f), Band(16, 16, 18.9f, 24.3f),
        Band(17, 17, 19.5f, 25f), Band(18, 18, 20f, 25.5f), Band(19, 64, 18.5f, 25f), Band(65, 120, 18.5f, 25f),
    )
    private val bmiFemale = table(
        Band(5, 5, 13.8f, 17.1f), Band(6, 6, 13.8f, 17.3f), Band(7, 7, 13.9f, 17.8f), Band(8, 8, 14.1f, 18.4f),
        Band(9, 9, 14.4f, 19.1f), Band(10, 10, 14.8f, 19.9f), Band(11, 11, 15.3f, 20.8f), Band(12, 12, 15.9f, 21.8f),
        Band(13, 13, 16.5f, 22.8f), Band(14, 14, 17.2f, 23.6f), Band(15, 15, 17.7f, 24.2f), Band(16, 16, 18.1f, 24.6f),
        Band(17, 17, 18.3f, 24.9f), Band(18, 64, 18.5f, 25f), Band(65, 120, 18.5f, 25f),
    )
    private val whtr = table(
        Band(15, 40, 0.4f, 0.5f), Band(41, 42, 0.4f, 0.51f), Band(43, 44, 0.4f, 0.53f), Band(45, 46, 0.4f, 0.55f),
        Band(47, 48, 0.4f, 0.57f), Band(49, 50, 0.4f, 0.59f), Band(51, 90, 0.4f, 0.6f),
    )
    private val whrMale = table(Band(18, 90, 0.8f, 0.9f))
    private val whrFemale = table(Band(18, 90, 0.7f, 0.8f))
    private val visceralFat = table(Band(18, 90, 1f, 12f))
    private val lbmMale = table(
        Band(18, 24, 52.90f, 62.70f), Band(25, 34, 53.10f, 64.80f), Band(35, 44, 53.83f, 65.60f), Band(45, 54, 53.60f, 65.20f),
        Band(55, 64, 51.63f, 61.10f), Band(65, 74, 48.48f, 58.20f), Band(75, 88, 43.35f, 60.23f),
    )
    private val lbmFemale = table(
        Band(18, 24, 34.30f, 41.90f), Band(25, 34, 35.20f, 43.70f), Band(35, 44, 35.60f, 47.10f), Band(45, 54, 36.10f, 44.90f),
        Band(55, 64, 35.15f, 43.95f), Band(65, 74, 34.10f, 42.05f), Band(75, 88, 33.80f, 40.40f),
    )
    private val waistMale = table(Band(18, 90, 0f, 94f))
    private val waistFemale = table(Band(18, 90, 0f, 80f))

    /**
     * Evaluates [value] given in the metric's canonical unit (kg, cm, % or none).
     * Returns null for metrics without a reference range.
     */
    fun evaluate(identity: String, value: Float, age: Int, gender: Gender, heightCm: Float): EvaluationResult? {
        val male = gender.isMale
        return when (identity) {
            Metrics.WEIGHT.identity -> targetWeight(value, heightCm, gender)
            Metrics.BMI.identity -> (if (male) bmiMale else bmiFemale).evaluate(value, age)
            Metrics.BODY_FAT.identity, Metrics.CALIPER.identity -> (if (male) fatMale else fatFemale).evaluate(value, age)
            Metrics.WATER.identity -> (if (male) waterMale else waterFemale).evaluate(value, age)
            Metrics.MUSCLE.identity -> (if (male) muscleMale else muscleFemale).evaluate(value, age)
            Metrics.WHTR.identity -> whtr.evaluate(value, age)
            Metrics.WHR.identity -> (if (male) whrMale else whrFemale).evaluate(value, age)
            Metrics.VISCERAL_FAT.identity -> visceralFat.evaluate(value, age)
            Metrics.LBM.identity -> (if (male) lbmMale else lbmFemale).evaluate(value, age)
            Metrics.WAIST.identity -> (if (male) waistMale else waistFemale).evaluate(value, age)
            else -> null
        }?.takeIf { it.state != EvaluationState.UNDEFINED }
    }

    /** Weight band for a BMI of 20–25 (men) or 19–24 (women). */
    fun targetWeight(weightKg: Float, heightCm: Float, gender: Gender): EvaluationResult? {
        if (heightCm <= 0f) return null
        val h2 = (heightCm / 100f) * (heightCm / 100f)
        val (lo, hi) = if (gender.isMale) 20f to 25f else 19f to 24f
        return EvaluationResult(weightKg, h2 * lo, h2 * hi, stateOf(weightKg, h2 * lo, h2 * hi))
    }
}
