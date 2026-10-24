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

import androidx.annotation.StringRes
import com.gpo.scalepaw.data.model.InputType
import com.gpo.scalepaw.data.model.MetricSpec
import com.gpo.scalepaw.data.model.Metrics

/**
 * The slice of openScale's measurement vocabulary the scale handlers speak. Builtin keys are
 * backed by ScalePaw's [Metrics] table (same identities), device keys become `ble.*` metric
 * types on first use.
 */
object MeasurementType {

    /**
     * One quantity a handler can report. [T] is bound to the delivery unit by the factories, so
     * `m[WEIGHT] = Kg(72.5f)` compiles while `m[WEIGHT] = Percent(20f)` does not.
     */
    class Key<T : Any> internal constructor(
        val identity: String,
        @param:StringRes val nameResId: Int,
        val inputType: InputType,
        val wireUnit: UnitType,
        val defaultIcon: MeasurementTypeIcon,
        val defaultColor: Int,
        val defaultEnabled: Boolean,
        val defaultPinned: Boolean,
        val defaultOnRightYAxis: Boolean,
        val isInternal: Boolean,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Key<*> && other.identity == identity)

        override fun hashCode(): Int = identity.hashCode()

        override fun toString(): String = "Key($identity)"

        /** The metric type a fresh row for this key starts out as. */
        fun toSpec(): MetricSpec = Metrics.specOf(identity) ?: MetricSpec(
            identity = identity,
            nameRes = nameResId,
            inputType = inputType,
            wireUnit = wireUnit,
            units = if (inputType == InputType.FLOAT) wireUnit.convertible() else listOf(wireUnit),
            defaultColor = defaultColor,
            icon = defaultIcon.metricIcon,
            enabledByDefault = defaultEnabled,
            pinnedByDefault = defaultPinned,
            rightAxisByDefault = defaultOnRightYAxis,
            isInternal = isInternal,
        )

        val isBuiltin: Boolean get() = identity.startsWith(Metrics.BUILTIN)
    }

    private const val DEVICE_DEFAULT_COLOR = 0xFFFFA726.toInt()

    private fun <T : Any> builtin(spec: MetricSpec): Key<T> = Key(
        spec.identity, spec.nameRes, spec.inputType, spec.wireUnit, MeasurementTypeIcon.IC_DEFAULT,
        spec.defaultColor, spec.enabledByDefault, spec.pinnedByDefault, spec.rightAxisByDefault, spec.isInternal,
    )

    val WEIGHT: Key<Kg> = builtin(Metrics.WEIGHT)
    val BMI: Key<Float> = builtin(Metrics.BMI)
    val BODY_FAT: Key<Percent> = builtin(Metrics.BODY_FAT)
    val WATER: Key<Percent> = builtin(Metrics.WATER)
    val MUSCLE: Key<Percent> = builtin(Metrics.MUSCLE)
    val LBM: Key<Kg> = builtin(Metrics.LBM)
    val BONE: Key<Kg> = builtin(Metrics.BONE)
    val WAIST: Key<Cm> = builtin(Metrics.WAIST)
    val HIPS: Key<Cm> = builtin(Metrics.HIPS)
    val VISCERAL_FAT: Key<Float> = builtin(Metrics.VISCERAL_FAT)
    val CHEST: Key<Cm> = builtin(Metrics.CHEST)
    val THIGH: Key<Cm> = builtin(Metrics.THIGH)
    val BICEPS: Key<Cm> = builtin(Metrics.BICEPS)
    val NECK: Key<Cm> = builtin(Metrics.NECK)
    val BMR: Key<Kcal> = builtin(Metrics.BMR)
    val TDEE: Key<Kcal> = builtin(Metrics.TDEE)
    val HEART_RATE: Key<Bpm> = builtin(Metrics.HEART_RATE)
    val IMPEDANCE: Key<Ohm> = builtin(Metrics.IMPEDANCE)
    val IMPEDANCE_LOW: Key<Ohm> = builtin(Metrics.IMPEDANCE_LOW)
    val PROTEIN: Key<Percent> = builtin(Metrics.PROTEIN)
    val CALORIES: Key<Kcal> = builtin(Metrics.CALORIES)
    val COMMENT: Key<String> = builtin(Metrics.COMMENT)

    /**
     * A quantity a scale handler contributes, e.g. a segmental body-fat percentage. Prefer a
     * semantic path a second scale reporting the same quantity can reuse
     * (`"segmental.fat.left_arm"`), so history survives switching scales.
     */
    fun devicePercent(
        path: String,
        @StringRes nameResId: Int,
        icon: MeasurementTypeIcon = MeasurementTypeIcon.IC_DEFAULT,
        color: Int = DEVICE_DEFAULT_COLOR,
        enabled: Boolean = true,
        pinned: Boolean = false,
        rightAxis: Boolean = false,
        isInternal: Boolean = false,
    ): Key<Percent> = device(path, nameResId, InputType.FLOAT, UnitType.PERCENT, icon, color, enabled, pinned, rightAxis, isInternal)

    /** See [devicePercent]; raw values arrive as [Kg]. */
    fun deviceKg(
        path: String,
        @StringRes nameResId: Int,
        icon: MeasurementTypeIcon = MeasurementTypeIcon.IC_DEFAULT,
        color: Int = DEVICE_DEFAULT_COLOR,
        enabled: Boolean = true,
        pinned: Boolean = false,
        rightAxis: Boolean = false,
        isInternal: Boolean = false,
    ): Key<Kg> = device(path, nameResId, InputType.FLOAT, UnitType.KG, icon, color, enabled, pinned, rightAxis, isInternal)

    /** See [devicePercent]; a dimensionless decimal (score, index, ratio). */
    fun deviceFloat(
        path: String,
        @StringRes nameResId: Int,
        icon: MeasurementTypeIcon = MeasurementTypeIcon.IC_DEFAULT,
        color: Int = DEVICE_DEFAULT_COLOR,
        enabled: Boolean = true,
        pinned: Boolean = false,
        rightAxis: Boolean = false,
        isInternal: Boolean = false,
    ): Key<Float> = device(path, nameResId, InputType.FLOAT, UnitType.NONE, icon, color, enabled, pinned, rightAxis, isInternal)

    /** A whole number a scale handler contributes, e.g. a count or a score. */
    fun deviceInt(
        path: String,
        @StringRes nameResId: Int,
        wireUnit: UnitType = UnitType.NONE,
        icon: MeasurementTypeIcon = MeasurementTypeIcon.IC_DEFAULT,
        color: Int = DEVICE_DEFAULT_COLOR,
        enabled: Boolean = true,
        pinned: Boolean = false,
        isInternal: Boolean = false,
    ): Key<Int> = device(path, nameResId, InputType.INT, wireUnit, icon, color, enabled, pinned, false, isInternal)

    /** Free text a scale handler contributes, e.g. a body-type classification code. */
    fun deviceText(
        path: String,
        @StringRes nameResId: Int,
        icon: MeasurementTypeIcon = MeasurementTypeIcon.IC_DEFAULT,
        color: Int = DEVICE_DEFAULT_COLOR,
        enabled: Boolean = true,
        pinned: Boolean = false,
        isInternal: Boolean = false,
    ): Key<String> = device(path, nameResId, InputType.TEXT, UnitType.NONE, icon, color, enabled, pinned, false, isInternal)

    private fun <T : Any> device(
        path: String, @StringRes nameResId: Int, inputType: InputType, wireUnit: UnitType,
        icon: MeasurementTypeIcon, color: Int, enabled: Boolean, pinned: Boolean,
        rightAxis: Boolean, isInternal: Boolean,
    ): Key<T> = Key(Metrics.DEVICE + path, nameResId, inputType, wireUnit, icon, color, enabled, pinned, rightAxis, isInternal)
}
