package com.izum1k.scalepaw.data.model

import androidx.annotation.StringRes
import com.izum1k.scalepaw.R

enum class Gender(@param:StringRes val label: Int) {
    MALE(R.string.gender_male),
    FEMALE(R.string.gender_female),
    ;

    val isMale: Boolean get() = this == MALE
}

enum class ActivityLevel(@param:StringRes val label: Int, val factor: Float) {
    SEDENTARY(R.string.activity_sedentary, 1.2f),
    MILD(R.string.activity_mild, 1.375f),
    MODERATE(R.string.activity_moderate, 1.55f),
    HEAVY(R.string.activity_heavy, 1.725f),
    EXTREME(R.string.activity_extreme, 1.9f),
}

enum class Limb(@param:StringRes val label: Int) {
    LEFT_ARM(R.string.limb_left_arm),
    RIGHT_ARM(R.string.limb_right_arm),
    LEFT_LEG(R.string.limb_left_leg),
    RIGHT_LEG(R.string.limb_right_leg),
}

/** Share of total body weight each missing part accounts for, in percent. */
enum class AmputationPart(@param:StringRes val label: Int, val correctionPercent: Float, val arm: Boolean) {
    HAND(R.string.amputation_hand, 0.8f, arm = true),
    FOREARM(R.string.amputation_forearm, 3.0f, arm = true),
    FULL_ARM(R.string.amputation_full_arm, 11.5f, arm = true),
    FOOT(R.string.amputation_foot, 1.8f, arm = false),
    LOWER_LEG(R.string.amputation_lower_leg, 7.1f, arm = false),
    FULL_LEG(R.string.amputation_full_leg, 18.6f, arm = false),
}

enum class InputType { FLOAT, INT, TEXT }

enum class EvaluationState { LOW, NORMAL, HIGH, UNDEFINED }

enum class BodyFatFormula(@param:StringRes val label: Int) {
    OFF(R.string.formula_off),
    DEURENBERG_1991(R.string.formula_bf_deurenberg_1991),
    DEURENBERG_1992(R.string.formula_bf_deurenberg_1992),
    EDDY_1976(R.string.formula_bf_eddy_1976),
    GALLAGHER_2000_NON_ASIAN(R.string.formula_bf_gallagher_non_asian),
    GALLAGHER_2000_ASIAN(R.string.formula_bf_gallagher_asian),
    US_NAVY(R.string.formula_bf_us_navy),
}

enum class BodyWaterFormula(@param:StringRes val label: Int) {
    OFF(R.string.formula_off),
    BEHNKE_1963(R.string.formula_bw_behnke_1963),
    DELWAIDE_CRENIER_1973(R.string.formula_bw_delwaide_crenier_1973),
    HUME_WEYERS_1971(R.string.formula_bw_hume_weyers_1971),
    LEE_SONG_KIM_2001(R.string.formula_bw_lee_song_kim_2001),
}

enum class LbmFormula(@param:StringRes val label: Int) {
    OFF(R.string.formula_off),
    BOER_1984(R.string.formula_lbm_boer_1984),
    HUME_1966(R.string.formula_lbm_hume_1966),
    WEIGHT_MINUS_BODY_FAT(R.string.formula_lbm_weight_minus_fat),
}

enum class TimeRange(@param:StringRes val label: Int, val days: Long?) {
    WEEK(R.string.range_week, 7),
    MONTH(R.string.range_month, 30),
    QUARTER(R.string.range_quarter, 90),
    YEAR(R.string.range_year, 365),
    ALL(R.string.range_all, null),
}

enum class AggregationLevel(@param:StringRes val label: Int) {
    NONE(R.string.aggregation_none),
    DAY(R.string.aggregation_day),
    WEEK(R.string.aggregation_week),
    MONTH(R.string.aggregation_month),
    YEAR(R.string.aggregation_year),
}

enum class SmoothingAlgorithm(@param:StringRes val label: Int) {
    NONE(R.string.smoothing_none),
    SIMPLE_MOVING_AVERAGE(R.string.smoothing_sma),
    EXPONENTIAL(R.string.smoothing_ses),
}

enum class BackupInterval(@param:StringRes val label: Int, val days: Long) {
    DAILY(R.string.interval_daily, 1),
    WEEKLY(R.string.interval_weekly, 7),
    MONTHLY(R.string.interval_monthly, 30),
}

enum class ThemeMode(@param:StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

/** With dynamic colour on: wallpaper seeds through SchemeExpressive, or the system's own scheme. */
enum class ColorStyle(@param:StringRes val label: Int) {
    EXPRESSIVE(R.string.color_style_expressive),
    SYSTEM(R.string.color_style_system),
}
