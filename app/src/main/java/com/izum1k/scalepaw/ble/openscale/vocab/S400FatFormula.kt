package com.izum1k.scalepaw.ble.openscale.vocab

import com.izum1k.scalepaw.ble.openscale.libs.MiScaleLib

/**
 * Body-fat equation for the S400. [DEURENBERG] is the DXA-validated pipeline in
 * `S400BodyComposition`; [MI_COMPATIBLE] is the reverse-engineered Zepp Life formula,
 * which reads lower and sits closer to the scale's own display and the Mi app.
 */
enum class S400FatFormula { DEURENBERG, MI_COMPATIBLE }

data class S400FatOverride(val bodyFatPct: Float, val waterPct: Float, val lbmKg: Float)

/** Mi-compatible body fat, water and fat-free mass from the 50 kHz impedance. */
fun miCompatibleFat(sexMale: Boolean, age: Int, heightCm: Float, weightKg: Float, impedance50k: Float): S400FatOverride {
    val lib = MiScaleLib(if (sexMale) 1 else 0, age, heightCm)
    val fat = lib.getBodyFat(weightKg, impedance50k).coerceIn(5f, 75f)
    return S400FatOverride(
        bodyFatPct = fat,
        waterPct = lib.getWater(weightKg, impedance50k),
        lbmKg = weightKg * (1f - fat / 100f),
    )
}
