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
package com.gpo.scalepaw.ble.openscale.scales

import android.bluetooth.le.ScanSettings
import androidx.annotation.StringRes
import com.gpo.scalepaw.R

// -------------------------------------------------------------------------------------------------
// Shared tuning for link pacing & retry, per transport.
// -------------------------------------------------------------------------------------------------

// Common knobs every link can use
data class CommonTuning(
    val reconnectCooldownMs: Long = 2000,
    val retryBackoffMs: Long = 1500,
    val maxRetries: Int = 3
)

// GATT-specific
data class BleGattTuning(
    val common: CommonTuning = CommonTuning(),
    val notifySetupDelayMs: Long = 120,
    val writeWithResponseDelayMs: Long = 80,
    val writeWithoutResponseDelayMs: Long = 35,
    val postWriteDelayMs: Long = 20,
    val postReadDelayMs: Long = 20,
    val connectAfterScanDelayMs: Long = 650,
    val requestHighConnectionPriority: Boolean = false,
    val requestMtuBytes: Int = 185,
    val operationTimeoutMs: Long = 1000
)

// Broadcast scanner tuning
data class BleBroadcastTuning(
    val common: CommonTuning = CommonTuning(),
    val scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,
    val maxScanMs: Long = 20_000,
    val restartBackoffMs: Long = 1500,
    val packetDedupWindowMs: Long = 750,
    val stabilizeWindowMs: Long = 1200,
    val minRssiDbm: Int? = null // e.g. -90
)

// Classic SPP tuning
data class BtSppTuning(
    val common: CommonTuning = CommonTuning(),
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 3000,
    val writeChunkBytes: Int = 256,
    val interChunkDelayMs: Long = 10,
    val soKeepAlive: Boolean = true
)

enum class TuningProfile(@param:StringRes val labelRes: Int) {
    Conservative(R.string.tuning_conservative),
    Balanced(R.string.tuning_balanced),
    Aggressive(R.string.tuning_aggressive),
}
fun TuningProfile.forGatt(): BleGattTuning = when (this) {
    TuningProfile.Balanced -> BleGattTuning(
        common = CommonTuning(2200, 1500, 3),
        notifySetupDelayMs = 120,
        writeWithResponseDelayMs = 80,
        writeWithoutResponseDelayMs = 35,
        postWriteDelayMs = 20,
        postReadDelayMs = 20,
        connectAfterScanDelayMs = 650,
        requestHighConnectionPriority = false,
        requestMtuBytes = 185,
        operationTimeoutMs = 1000
    )
    TuningProfile.Conservative -> BleGattTuning(
        common = CommonTuning(2500, 1800, 3),
        notifySetupDelayMs = 160,
        writeWithResponseDelayMs = 100,
        writeWithoutResponseDelayMs = 50,
        postWriteDelayMs = 30,
        postReadDelayMs = 30,
        connectAfterScanDelayMs = 800,
        requestHighConnectionPriority = false,
        requestMtuBytes = 0,
        operationTimeoutMs = 2000
    )
    TuningProfile.Aggressive -> BleGattTuning(
        common = CommonTuning(1200, 1200, 2),
        notifySetupDelayMs = 80,
        writeWithResponseDelayMs = 60,
        writeWithoutResponseDelayMs = 25,
        postWriteDelayMs = 15,
        postReadDelayMs = 15,
        connectAfterScanDelayMs = 400,
        requestHighConnectionPriority = true,
        requestMtuBytes = 247,
        operationTimeoutMs = 500
    )
}

fun TuningProfile.forBroadcast(): BleBroadcastTuning = when (this) {
    TuningProfile.Balanced -> BleBroadcastTuning(common = CommonTuning(2200,1500,3))
    TuningProfile.Conservative -> BleBroadcastTuning(
        common = CommonTuning(2500,1800,3),
        scanMode = ScanSettings.SCAN_MODE_BALANCED,
        maxScanMs = 30_000
    )
    TuningProfile.Aggressive -> BleBroadcastTuning(
        common = CommonTuning(1200,1200,2),
        scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
        maxScanMs = 15_000,
        stabilizeWindowMs = 900
    )
}

fun TuningProfile.forSpp(): BtSppTuning = when (this) {
    TuningProfile.Balanced -> BtSppTuning()
    TuningProfile.Conservative -> BtSppTuning(connectTimeoutMs = 12_000, interChunkDelayMs = 15)
    TuningProfile.Aggressive -> BtSppTuning(connectTimeoutMs = 8_000, interChunkDelayMs = 5)
}
