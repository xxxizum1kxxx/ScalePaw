/*
 * openScale
 * Copyright (C) 2026 openScale contributors
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
 *
 * Modified for ScalePaw: routing goes through ScaleFactory.resolve(device, debug) instead of a
 * settings-backed communicator factory.
 */
package com.gpo.scalepaw.ble.openscale

import com.google.common.truth.Truth.assertThat
import com.gpo.scalepaw.ble.openscale.scales.DebugGattHandler
import com.gpo.scalepaw.ble.openscale.scales.LinkMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Developer mode is a setting of its own, never encoded in the device name: the registry decides
 * while the mode is off, the diagnostic handler wins while it is on — for known and unknown scales
 * alike. A broadcast-only scale makes the override observable through its link mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScaleFactoryDeveloperModeTest {

    /** Broadcast-only scale (see `EufyC20Handler`), used to tell the two routes apart. */
    private val BROADCAST_SCALE = "EUFY C20"

    /** Name-matched GATT scale. */
    private val GATT_SCALE = "SANITAS SBF70"

    private fun linkFor(name: String, debug: Boolean): LinkMode? =
        ScaleFactory.resolve(ScaleCatalog.device(name), debug)?.second?.linkMode

    @Test
    fun `developer mode off keeps the registry in charge`() {
        assertThat(linkFor(GATT_SCALE, debug = false)).isEqualTo(LinkMode.CONNECT_GATT)
        assertThat(linkFor(BROADCAST_SCALE, debug = false)).isEqualTo(LinkMode.BROADCAST_ONLY)
        assertThat(ScaleFactory.resolve(ScaleCatalog.device(GATT_SCALE))?.first)
            .isNotInstanceOf(DebugGattHandler::class.java)
    }

    @Test
    fun `developer mode routes every scale to the diagnostic handler`() {
        assertThat(linkFor(BROADCAST_SCALE, debug = true)).isEqualTo(LinkMode.CONNECT_GATT)
        assertThat(ScaleFactory.resolve(ScaleCatalog.device(GATT_SCALE), debug = true)?.first)
            .isInstanceOf(DebugGattHandler::class.java)
    }

    @Test
    fun `developer mode leaves the reported driver of the saved scale untouched`() {
        val device = ScaleCatalog.device(GATT_SCALE)
        assertThat(ScaleFactory.supportFor(device)?.displayName)
            .isEqualTo("Sanitas SBF70 / SilverCrest SBF75 / Crane")
    }

    @Test
    fun `developer mode also reaches a scale no handler supports`() {
        val unknown = "NoSuchScale-XYZ"
        assertThat(ScaleFactory.resolve(ScaleCatalog.device(unknown))).isNull()
        assertThat(ScaleFactory.resolve(ScaleCatalog.device(unknown), debug = true)?.first)
            .isInstanceOf(DebugGattHandler::class.java)
    }

    @Test
    fun `the diagnostic handler never claims a device by name`() {
        val debug = DebugGattHandler()

        assertThat(debug.supportFor(ScaleCatalog.device("Debug"))).isNull()
        assertThat(debug.supportFor(ScaleCatalog.device("debug"))).isNull()
        assertThat(ScaleFactory.createHandlers().map { it.javaClass.simpleName })
            .doesNotContain(DebugGattHandler::class.java.simpleName)
    }
}
