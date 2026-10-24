package com.izum1k.scalepaw.ble.withings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WppCodecTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun should_encode_header_and_tlvs_big_endian_when_encoding_message() {
        val msg = WppMessage(0x0101, listOf(Tlv(0x0112, bytes(0xAB, 0xCD))))
        assertArrayEquals(bytes(0x01, 0x01, 0x01, 0x00, 0x06, 0x01, 0x12, 0x00, 0x02, 0xAB, 0xCD), msg.encode())
    }

    @Test
    fun should_restore_message_when_round_tripping() {
        val msg = WppMessage(WppCmd.STORED_MEASURE, listOf(Tlv(WppObj.STORED_MEASURE_ACTION, bytes(1, 0)), Tlv(WppObj.NULL, ByteArray(0))))
        assertEquals(msg, WppCodec.decode(msg.encode()))
    }

    @Test
    fun should_flag_device_request_when_bit_0x4000_set() {
        val msg = WppMessage(WppCmd.SYNC_OK or WppCodec.DEVICE_REQUEST)
        assertTrue(msg.fromDevice)
        assertEquals(WppCmd.SYNC_OK, msg.baseCommand)
    }

    @Test(expected = WppException::class)
    fun should_reject_when_tlv_length_overruns_payload() {
        WppCodec.decode(bytes(0x01, 0x01, 0x01, 0x00, 0x05, 0x01, 0x12, 0x00, 0x09, 0xAB))
    }

    @Test
    fun should_join_fragments_when_message_spans_notifications() {
        val a = WppMessage(WppCmd.CONNECT_REASON, listOf(Tlv(WppObj.CONNECT_REASON, bytes(0, 2)))).encode()
        val b = WppMessage(WppCmd.SYNC_OK).encode()
        val stream = a + b
        val r = Reassembler()
        assertTrue(r.feed(stream.copyOfRange(0, 3)).isEmpty())
        val first = r.feed(stream.copyOfRange(3, a.size + 2))
        assertEquals(listOf(WppCodec.decode(a)), first)
        val second = r.feed(stream.copyOfRange(a.size + 2, stream.size))
        assertEquals(listOf(WppCodec.decode(b)), second)
        assertEquals(0, r.pending)
    }

    @Test
    fun should_read_length_prefixed_fields_when_parsing_challenge() {
        val value = WppCodec.packStr("aa:bb:cc:dd:ee:ff") + WppCodec.packStr(ByteArray(16) { it.toByte() })
        val challenge = ProbeChallenge.parse(value)
        assertEquals("aa:bb:cc:dd:ee:ff", challenge.mac)
        assertArrayEquals(ByteArray(16) { it.toByte() }, challenge.challenge)
    }

    @Test
    fun should_hide_values_when_describing_message() {
        val msg = WppMessage(WppCmd.PROBE_CHALLENGE, listOf(Tlv(WppObj.PROBE_CHALLENGE, WppCodec.packStr("aa:bb"))))
        assertEquals("PROBE_CHALLENGE(296): 290[6]", msg.describe())
    }
}
