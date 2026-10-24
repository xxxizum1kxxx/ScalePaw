package com.gpo.scalepaw.data.model

import androidx.annotation.StringRes
import com.gpo.scalepaw.R

enum class MetricIcon {
    WEIGHT, BMI, FAT, WATER, MUSCLE, LBM, BONE, TAPE, RATIO, VISCERAL, CALIPER, ENERGY, HEART, IMPEDANCE, PROTEIN, COMMENT, DEFAULT,
}

/**
 * One quantity the app knows how to track, identified by a namespaced identity:
 * `builtin.*` for the vocabulary below, `ble.*` for values a scale driver adds, `user.*` for
 * types the user creates. The identities match openScale's, so CSV headers stay compatible.
 */
data class MetricSpec(
    val identity: String,
    @param:StringRes val nameRes: Int,
    val inputType: InputType,
    /** The unit values arrive in from scales and formulas. */
    val wireUnit: UnitType,
    val units: List<UnitType>,
    val defaultColor: Int,
    val icon: MetricIcon,
    val isDerived: Boolean = false,
    val enabledByDefault: Boolean = true,
    val pinnedByDefault: Boolean = false,
    val rightAxisByDefault: Boolean = false,
    val isInternal: Boolean = false,
) {
    val defaultUnit: UnitType get() = units.first()

    /** CSV column header: the identity without its namespace, uppercased (`BODY_FAT`). */
    val columnKey: String get() = columnKeyOf(identity)

    companion object {
        fun columnKeyOf(identity: String): String = identity.substringAfter('.').replace('.', '_').uppercase()
    }
}

object Metrics {
    const val BUILTIN = "builtin."
    const val DEVICE = "ble."
    const val USER = "user."

    private val weightUnits = listOf(UnitType.KG, UnitType.LB, UnitType.ST)
    private val compositionUnits = listOf(UnitType.PERCENT, UnitType.KG, UnitType.LB, UnitType.ST)
    private val lengthUnits = listOf(UnitType.CM, UnitType.INCH)
    private val none = listOf(UnitType.NONE)

    private val ordered = mutableListOf<MetricSpec>()

    private fun float(
        id: String,
        @StringRes name: Int,
        units: List<UnitType>,
        color: Long,
        icon: MetricIcon,
        derived: Boolean = false,
        enabled: Boolean = true,
        pinned: Boolean = false,
        rightAxis: Boolean = false,
        internal: Boolean = false,
    ) = MetricSpec(
        identity = BUILTIN + id,
        nameRes = name,
        inputType = InputType.FLOAT,
        wireUnit = units.first(),
        units = units,
        defaultColor = color.toInt(),
        icon = icon,
        isDerived = derived,
        enabledByDefault = enabled,
        pinnedByDefault = pinned,
        rightAxisByDefault = rightAxis,
        isInternal = internal,
    ).also { ordered += it }

    val WEIGHT = float("weight", R.string.metric_weight, weightUnits, 0xFF7E57C2, MetricIcon.WEIGHT, pinned = true, rightAxis = true)
    val BMI = float("bmi", R.string.metric_bmi, none, 0xFFFFCA28, MetricIcon.BMI, derived = true, pinned = true)
    val BODY_FAT = float("body_fat", R.string.metric_body_fat, compositionUnits, 0xFFEF5350, MetricIcon.FAT, pinned = true)
    val WATER = float("water", R.string.metric_water, compositionUnits, 0xFF29B6F6, MetricIcon.WATER, pinned = true)
    val MUSCLE = float("muscle", R.string.metric_muscle, compositionUnits, 0xFF66BB6A, MetricIcon.MUSCLE, pinned = true)
    val LBM = float("lbm", R.string.metric_lbm, weightUnits, 0xFF4DBAC0, MetricIcon.LBM)
    val BONE = float("bone", R.string.metric_bone, listOf(UnitType.KG, UnitType.LB), 0xFFBDBDBD, MetricIcon.BONE)
    val WAIST = float("waist", R.string.metric_waist, lengthUnits, 0xFF78909C, MetricIcon.TAPE)
    val WHR = float("whr", R.string.metric_whr, none, 0xFFFFA726, MetricIcon.RATIO, derived = true)
    val WHTR = float("whtr", R.string.metric_whtr, none, 0xFFFF7043, MetricIcon.RATIO, derived = true)
    val HIPS = float("hips", R.string.metric_hips, lengthUnits, 0xFF5C6BC0, MetricIcon.TAPE)
    val VISCERAL_FAT = float("visceral_fat", R.string.metric_visceral_fat, none, 0xFFD84315, MetricIcon.VISCERAL)
    val CHEST = float("chest", R.string.metric_chest, lengthUnits, 0xFF8E24AA, MetricIcon.TAPE)
    val THIGH = float("thigh", R.string.metric_thigh, lengthUnits, 0xFFA1887F, MetricIcon.TAPE)
    val BICEPS = float("biceps", R.string.metric_biceps, lengthUnits, 0xFFEC407A, MetricIcon.TAPE)
    val NECK = float("neck", R.string.metric_neck, lengthUnits, 0xFFB0BEC5, MetricIcon.TAPE)
    val CALIPER_1 = float("caliper_1", R.string.metric_caliper_1, lengthUnits, 0xFFFFF59D, MetricIcon.CALIPER)
    val CALIPER_2 = float("caliper_2", R.string.metric_caliper_2, lengthUnits, 0xFFFFE082, MetricIcon.CALIPER)
    val CALIPER_3 = float("caliper_3", R.string.metric_caliper_3, lengthUnits, 0xFFFFCC80, MetricIcon.CALIPER)
    val CALIPER = float("caliper", R.string.metric_fat_caliper, listOf(UnitType.PERCENT), 0xFFFB8C00, MetricIcon.CALIPER, derived = true)
    val BMR = float("bmr", R.string.metric_bmr, listOf(UnitType.KCAL), 0xFFAB47BC, MetricIcon.ENERGY, derived = true)
    val TDEE = float("tdee", R.string.metric_tdee, listOf(UnitType.KCAL), 0xFF26A69A, MetricIcon.ENERGY, derived = true)
    val HEART_RATE = MetricSpec(
        identity = BUILTIN + "heart_rate",
        nameRes = R.string.metric_heart_rate,
        inputType = InputType.INT,
        wireUnit = UnitType.BPM,
        units = listOf(UnitType.BPM),
        defaultColor = 0xFFE91E63.toInt(),
        icon = MetricIcon.HEART,
    ).also { ordered += it }
    val IMPEDANCE = float("impedance", R.string.metric_impedance, listOf(UnitType.OHM), 0xFF607D8B, MetricIcon.IMPEDANCE, enabled = false, internal = true)
    val IMPEDANCE_LOW = float("impedance_low", R.string.metric_impedance_low, listOf(UnitType.OHM), 0xFF455A64, MetricIcon.IMPEDANCE, enabled = false, internal = true)
    val PROTEIN = float("protein", R.string.metric_protein, compositionUnits, 0xFF9CCC65, MetricIcon.PROTEIN)
    val CALORIES = float("calories", R.string.metric_calories, listOf(UnitType.KCAL), 0xFF4CAF50, MetricIcon.ENERGY)
    val COMMENT = MetricSpec(
        identity = BUILTIN + "comment",
        nameRes = R.string.metric_comment,
        inputType = InputType.TEXT,
        wireUnit = UnitType.NONE,
        units = none,
        defaultColor = 0xFFE0E0E0.toInt(),
        icon = MetricIcon.COMMENT,
        pinnedByDefault = true,
    ).also { ordered += it }

    /** Declaration order is the default display order. */
    val builtins: List<MetricSpec> = ordered.toList()

    private val byIdentity = builtins.associateBy { it.identity }

    fun specOf(identity: String): MetricSpec? = byIdentity[identity]

    /** A value a scale driver reports that has no builtin home (`ble.<path>`). */
    fun device(path: String, @StringRes name: Int, unit: UnitType = UnitType.NONE, inputType: InputType = InputType.FLOAT) = MetricSpec(
        identity = DEVICE + path,
        nameRes = name,
        inputType = inputType,
        wireUnit = unit,
        units = unit.convertible(),
        defaultColor = 0xFFFFA726.toInt(),
        icon = MetricIcon.DEFAULT,
    )

    fun userIdentityFor(name: String, taken: Set<String>): String {
        val slug = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "custom" }
        val reserved = builtins.map { it.columnKey }.toSet() + taken.map(MetricSpec::columnKeyOf)
        var candidate = slug
        var n = 1
        while (MetricSpec.columnKeyOf(USER + candidate) in reserved) {
            n++
            candidate = "${slug}_$n"
        }
        return USER + candidate
    }
}
