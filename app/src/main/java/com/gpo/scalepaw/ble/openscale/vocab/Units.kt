/*
 * openScale
 * Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.gpo.scalepaw.ble.openscale.vocab

import com.gpo.scalepaw.ble.openscale.util.ConverterUtils
import com.gpo.scalepaw.data.model.MetricIcon

typealias UnitType = com.gpo.scalepaw.data.model.UnitType

enum class GenderType {
    MALE,
    FEMALE;

    fun isMale(): Boolean = this == MALE
}

enum class ActivityLevel {
    SEDENTARY, MILD, MODERATE, HEAVY, EXTREME;

    fun toInt(): Int = ordinal

    companion object {
        @JvmStatic
        fun fromInt(unit: Int): ActivityLevel = entries.getOrElse(unit) { SEDENTARY }
    }
}

enum class WeightUnit {
    KG, LB, ST;

    override fun toString(): String = when (this) {
        LB -> "lb"
        ST -> "st"
        KG -> "kg"
    }

    fun toInt(): Int = when (this) {
        LB -> 1
        ST -> 2
        KG -> 0
    }
}

enum class MeasureUnit {
    CM, INCH;

    override fun toString(): String = when (this) {
        CM -> "cm"
        INCH -> "in"
    }

    fun toInt(): Int = ordinal
}

/** Icons handlers suggest for their device keys, mapped onto ScalePaw's icon set. */
enum class MeasurementTypeIcon(val metricIcon: MetricIcon) {
    IC_DEFAULT(MetricIcon.DEFAULT),
    IC_WEIGHT(MetricIcon.WEIGHT),
    IC_BODY_FAT(MetricIcon.FAT),
    IC_WATER(MetricIcon.WATER),
    IC_MUSCLE(MetricIcon.MUSCLE),
    IC_BONE(MetricIcon.BONE),
    IC_CHEST(MetricIcon.TAPE),
    IC_THIGH(MetricIcon.TAPE),
    IC_BICEPS(MetricIcon.TAPE),
    IC_M_TIMER(MetricIcon.DEFAULT),
    IC_M_WORKOUT(MetricIcon.MUSCLE),
    IC_M_WATER_DROP(MetricIcon.WATER),
    IC_M_SCATTER_PLOT(MetricIcon.DEFAULT),
    IC_M_BUBBLE_CHART(MetricIcon.DEFAULT),
    IC_M_HIVE(MetricIcon.DEFAULT),
}

/**
 * A raw scale value whose unit is part of its type. Every float-valued builtin
 * [MeasurementType.Key] binds one of these, so a handler cannot set a value without stating
 * the unit it is in.
 */
sealed interface UnitValue {
    val value: Float
}

/** A mass in kilograms. */
@JvmInline
value class Kg(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()

    companion object {
        /** Converts from the unit the scale natively reports in (kg/lb/st). */
        fun from(value: Float, unit: WeightUnit): Kg = Kg(ConverterUtils.toKilogram(value, unit))

        fun fromLb(lb: Float): Kg = from(lb, WeightUnit.LB)

        /** Chinese catty (市斤), used natively by some Asian scales. */
        fun fromJin(jin: Float): Kg = Kg(jin * 0.5f)
    }
}

/** A share of body weight in percent. */
@JvmInline
value class Percent(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()
}

/** A length in centimeters. */
@JvmInline
value class Cm(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()

    companion object {
        fun fromInch(inch: Float): Cm = Cm(inch * 2.54f)
    }
}

/** An energy in kilocalories. */
@JvmInline
value class Kcal(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()
}

/** An electrical resistance in ohms. */
@JvmInline
value class Ohm(override val value: Float) : UnitValue {
    override fun toString(): String = value.toString()
}

/** A heart rate in whole beats per minute. */
@JvmInline
value class Bpm(val value: Int) {
    override fun toString(): String = value.toString()
}
