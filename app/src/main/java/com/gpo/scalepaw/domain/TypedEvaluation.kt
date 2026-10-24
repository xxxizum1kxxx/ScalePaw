package com.gpo.scalepaw.domain

import com.gpo.scalepaw.data.model.MetricType
import com.gpo.scalepaw.data.model.UnitConversion
import com.gpo.scalepaw.data.model.UnitType
import com.gpo.scalepaw.data.model.User

/**
 * Evaluates a value stored in the type's display unit: converts to the canonical unit the tables
 * use and converts the band back. Composition metrics stored as mass have no range.
 */
fun Evaluation.evaluate(type: MetricType, value: Float, user: User, at: Long): EvaluationResult? {
    val spec = type.spec ?: return null
    if (spec.wireUnit == UnitType.PERCENT && type.unit != UnitType.PERCENT) return null
    val canonical = when {
        type.unit.isWeight -> UnitType.KG
        type.unit.isLength -> UnitType.CM
        else -> type.unit
    }
    val result = evaluate(type.identity, UnitConversion.convert(value, type.unit, canonical), user.ageOn(at), user.gender, user.heightCm)
        ?: return null
    return result.copy(
        value = value,
        low = UnitConversion.convert(result.low, canonical, type.unit),
        high = UnitConversion.convert(result.high, canonical, type.unit),
    )
}
