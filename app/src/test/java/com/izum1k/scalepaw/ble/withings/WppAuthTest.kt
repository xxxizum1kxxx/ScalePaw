package com.izum1k.scalepaw.ble.withings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WppAuthTest {
    private val kl = "0123456789abcdef0123456789ABCDEF"
    private val mac = "00:24:e4:00:00:01"

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun should_hash_challenge_mac_and_key_in_order_when_answering() {
        val answer = WppAuth.answer("a".toByteArray(), "b", "c")
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hex(answer))
    }

    @Test
    fun should_accept_answer_when_scale_knows_key() {
        val own = ByteArray(16) { 7 }
        assertTrue(WppAuth.verify(WppAuth.answer(own, mac, kl), own, mac, kl))
    }

    @Test
    fun should_reject_answer_when_scale_uses_other_key() {
        val own = ByteArray(16) { 7 }
        assertFalse(WppAuth.verify(WppAuth.answer(own, mac, kl.reversed()), own, mac, kl))
    }

    @Test
    fun should_carry_answer_and_own_challenge_when_building_reply() {
        val device = ByteArray(16) { 1 }
        val own = ByteArray(16) { 2 }
        val reply = WppAuth.challengeReply(device, mac, kl, own)
        assertEquals(WppCmd.PROBE_CHALLENGE, reply.command)
        val answer = WppReader(reply.first(WppObj.PROBE_CHALLENGE_RESPONSE)!!.value).bytes()
        assertEquals(hex(WppAuth.answer(device, mac, kl)), hex(answer))
        val ours = ProbeChallenge.parse(reply.first(WppObj.PROBE_CHALLENGE)!!.value)
        assertEquals(mac, ours.mac)
        assertEquals(hex(own), hex(ours.challenge))
    }

    @Test
    fun should_validate_key_when_32_printable_ascii() {
        assertTrue(WppAuth.isValidKey(kl))
        assertFalse(WppAuth.isValidKey(kl.drop(1)))
        assertFalse(WppAuth.isValidKey(kl.dropLast(1) + " "))
        assertFalse(WppAuth.isValidKey(null))
    }
}
