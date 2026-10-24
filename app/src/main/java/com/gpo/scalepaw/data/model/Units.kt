package com.gpo.scalepaw.data.model

import kotlin.math.floor
import kotlin.math.roundToInt

enum class UnitType(val symbol: String) {
    KG("kg"),
    LB("lb"),
    ST("st"),
    PERCENT("%"),
    CM("cm"),
    INCH("in"),
    KCAL("kcal"),
    BPM("bpm"),
    OHM("Ω"),
    NONE(""),
    ;

    val isWeight: Boolean get() = this == KG || this == LB || this == ST
    val isLength: Boolean get() = this == CM || this == INCH

    fun convertible(): List<UnitType> = when {
        isWeight -> listOf(KG, LB, ST)
        isLength -> listOf(CM, INCH)
        else -> listOf(this)
    }
}

object UnitConversion {
    private const val LB_PER_KG = 2.20462f
    private const val ST_PER_KG = 0.157473f
    private const val CM_PER_INCH = 2.54f

    fun toKg(value: Float, from: UnitType): Float = when (from) {
        UnitType.LB -> value / LB_PER_KG
        UnitType.ST -> value / ST_PER_KG
        else -> value
    }

    fun fromKg(kg: Float, to: UnitType): Float = when (to) {
        UnitType.LB -> kg * LB_PER_KG
        UnitType.ST -> kg * ST_PER_KG
        else -> kg
    }

    fun toCm(value: Float, from: UnitType): Float = if (from == UnitType.INCH) value * CM_PER_INCH else value

    fun fromCm(cm: Float, to: UnitType): Float = if (to == UnitType.INCH) cm / CM_PER_INCH else cm

    /** Converts between two units of the same family; values of other units pass through. */
    fun convert(value: Float, from: UnitType, to: UnitType): Float = when {
        from == to -> value
        from.isWeight && to.isWeight -> fromKg(toKg(value, from), to)
        from.isLength && to.isLength -> fromCm(toCm(value, from), to)
        else -> value
    }

    /** Decimal stone to whole stone and pounds (14 lb per st), carrying 14 lb into the next stone. */
    fun stToStLb(stDecimal: Float): Pair<Int, Int> {
        val totalLb = stDecimal * 14f
        var st = floor(totalLb / 14f).toInt()
        var lb = (totalLb - st * 14f).roundToInt()
        if (lb == 14) {
            st += 1
            lb = 0
        }
        return st to lb
    }
}
