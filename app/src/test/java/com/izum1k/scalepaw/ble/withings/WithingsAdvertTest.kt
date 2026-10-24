package com.izum1k.scalepaw.ble.withings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WithingsAdvertTest {
    @Test
    fun should_read_model_and_mac_when_uuid_carries_identity() {
        val parsed = WithingsAdvert.parse(UUID.fromString("00000020-5749-5448-0010-0024e4000001"))!!
        assertEquals(0x20, parsed.service)
        assertEquals(0x10, parsed.modelId)
        assertEquals("00:24:e4:00:00:01", parsed.mac)
    }

    @Test
    fun should_report_setup_mode_when_tail_is_zero() {
        val parsed = WithingsAdvert.parse(UUID.fromString("00000020-5749-5448-0005-000000000000"))!!
        assertTrue(parsed.isSetupMode)
        assertNull(parsed.mac)
    }

    @Test
    fun should_ignore_uuid_when_not_withings() {
        assertNull(WithingsAdvert.parse(UUID.fromString("0000181b-0000-1000-8000-00805f9b34fb")))
    }

    @Test
    fun should_build_wpp_uuids_when_given_model() {
        assertEquals(UUID.fromString("00000020-5749-5448-0010-000000000000"), WithingsAdvert.serviceUuid(0x10))
        assertEquals(UUID.fromString("00000024-5749-5448-0005-000000000000"), WithingsAdvert.characteristicUuid(0x05))
    }
}
