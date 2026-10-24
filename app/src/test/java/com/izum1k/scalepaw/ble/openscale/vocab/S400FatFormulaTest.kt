package com.izum1k.scalepaw.ble.openscale.vocab

import com.google.common.truth.Truth.assertThat
import com.izum1k.scalepaw.ble.openscale.libs.S400BodyComposition
import com.izum1k.scalepaw.ble.openscale.libs.S400Inputs
import org.junit.Test

class S400FatFormulaTest {

    @Test
    fun should_read_below_deurenberg_when_mi_compatible() {
        val deurenberg = S400BodyComposition.compute(S400Inputs(56, true, 170f, 67.9f, rHighRaw = 457.8f, rLowRaw = 509.1f))
        val mi = miCompatibleFat(sexMale = true, age = 56, heightCm = 170f, weightKg = 67.9f, impedance50k = 509.1f)

        assertThat(mi.bodyFatPct).isWithin(0.1f).of(22.2f)
        assertThat(mi.bodyFatPct).isLessThan(deurenberg.bfPct!!)
    }

    @Test
    fun should_keep_fat_and_lbm_summing_to_weight_when_mi_compatible() {
        val mi = miCompatibleFat(sexMale = false, age = 27, heightCm = 164f, weightKg = 63.1f, impedance50k = 513.8f)

        assertThat(mi.lbmKg + 63.1f * mi.bodyFatPct / 100f).isWithin(0.01f).of(63.1f)
    }
}
