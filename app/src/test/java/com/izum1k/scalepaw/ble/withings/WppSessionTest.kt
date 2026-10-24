package com.izum1k.scalepaw.ble.withings

import com.izum1k.scalepaw.ble.withings.WppFixtures.KL
import com.izum1k.scalepaw.ble.withings.WppFixtures.data
import com.izum1k.scalepaw.ble.withings.WppFixtures.meta
import com.izum1k.scalepaw.ble.withings.WppFixtures.nullTlv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WppSessionTest {
    private val own = ByteArray(16) { 0x42 }
    private val time = WppTime(1_800_000_000, 28_800)

    private fun session(withTime: Boolean = true) = WppSession(KL, if (withTime) ({ time }) else null, newChallenge = { own })

    private fun commands(r: WppReaction) = r.send.map { it.command }

    private fun authenticated(s: WppSession) {
        s.start()
        s.handle(WppFixtures.challenge(ByteArray(16) { 1 }))
        s.handle(WppFixtures.scaleAnswer(own))
    }

    @Test
    fun should_read_every_record_when_session_runs_to_the_end() {
        val s = session()
        assertEquals(WppCmd.PROBE, s.start().command)
        assertEquals(listOf(WppCmd.PROBE_CHALLENGE), commands(s.handle(WppFixtures.challenge(ByteArray(16) { 1 }))))
        assertEquals(listOf(WppCmd.TIME_SET), commands(s.handle(WppFixtures.scaleAnswer(own))))
        val drift = Tlv(WppObj.TIME_SET_REPLY, byteArrayOf(0, 0, 0, 5))
        assertEquals(listOf(WppCmd.CONNECT_REASON), commands(s.handle(WppMessage(WppCmd.TIME_SET, listOf(drift)))))
        val reason = Tlv(WppObj.CONNECT_REASON, byteArrayOf(0, 2))
        assertEquals(listOf(WppCmd.STORED_MEASURE), commands(s.handle(WppMessage(WppCmd.CONNECT_REASON, listOf(reason)))))
        assertEquals(listOf(WppCmd.STORED_MEASURE), commands(s.handle(WppFixtures.status(2))))
        assertTrue(s.handle(WppMessage(WppCmd.STORED_MEASURE, listOf(meta(100, listOf(1)), data(WppMeasureType.WEIGHT, 700, -1)))).send.isEmpty())
        val last = s.handle(WppMessage(WppCmd.STORED_MEASURE, listOf(meta(100, listOf(1)), data(WppMeasureType.HEART_RATE, 60, 0), nullTlv())))
        assertEquals(listOf(WppCmd.SYNC_OK), commands(last))
        val done = s.handle(WppMessage(WppCmd.SYNC_OK))
        assertEquals(listOf(WppCmd.DISCONNECT), commands(done))
        val result = (done.outcome as WppOutcome.Synced).result
        assertEquals(1311L, result.firmware)
        assertEquals(ConnectReason.WEIGH_IN, result.connectReason)
        assertEquals(5, result.clockDriftSeconds)
        assertEquals(1, result.groups.size)
        assertEquals(mapOf(WppMeasureType.WEIGHT to 70.0, WppMeasureType.HEART_RATE to 60.0), result.groups[0].values)
    }

    @Test
    fun should_skip_time_set_when_disabled() {
        val s = session(withTime = false)
        s.start()
        s.handle(WppFixtures.challenge(ByteArray(16) { 1 }))
        assertEquals(listOf(WppCmd.CONNECT_REASON), commands(s.handle(WppFixtures.scaleAnswer(own))))
    }

    @Test
    fun should_disconnect_without_reading_when_scale_cannot_prove_key() {
        val s = session()
        s.start()
        s.handle(WppFixtures.challenge(ByteArray(16) { 1 }))
        val r = s.handle(WppFixtures.scaleAnswer(own, key = KL.reversed()))
        assertEquals(listOf(WppCmd.DISCONNECT), commands(r))
        assertEquals(WppOutcome.Failed(WppFailure.ScaleUnverified), r.outcome)
    }

    @Test
    fun should_report_setup_mode_when_probe_needs_no_challenge() {
        val s = session()
        s.start()
        val r = s.handle(WppMessage(WppCmd.PROBE, listOf(WppFixtures.probeReply(firmware = 1651))))
        assertEquals(WppOutcome.Failed(WppFailure.SetupMode(1651)), r.outcome)
    }

    @Test
    fun should_stop_without_answering_when_key_is_missing() {
        val s = WppSession("", null, newChallenge = { own })
        s.start()
        val r = s.handle(WppFixtures.challenge(ByteArray(16) { 1 }))
        assertEquals(listOf(WppCmd.DISCONNECT), commands(r))
        assertEquals(WppOutcome.Failed(WppFailure.MissingKey), r.outcome)
    }

    @Test
    fun should_fail_with_error_code_when_key_is_rejected() {
        val s = session()
        s.start()
        s.handle(WppFixtures.challenge(ByteArray(16) { 1 }))
        val r = s.handle(WppFixtures.error(WppCmd.PROBE_CHALLENGE, WppError.AUTH_FAILED))
        assertEquals(WppOutcome.Failed(WppFailure.CommandError(WppCmd.PROBE_CHALLENGE, WppError.AUTH_FAILED)), r.outcome)
    }

    @Test
    fun should_fail_with_not_authenticated_when_scale_refuses_reading() {
        val s = session(withTime = false)
        authenticated(s)
        s.handle(WppMessage(WppCmd.CONNECT_REASON, listOf(Tlv(WppObj.CONNECT_REASON, byteArrayOf(0, 2)))))
        val r = s.handle(WppFixtures.error(WppCmd.STORED_MEASURE, WppError.NOT_AUTHENTICATED))
        assertEquals(WppOutcome.Failed(WppFailure.CommandError(WppCmd.STORED_MEASURE, WppError.NOT_AUTHENTICATED)), r.outcome)
    }

    @Test
    fun should_continue_when_scale_refuses_time_set() {
        val s = session()
        authenticated(s)
        val r = s.handle(WppFixtures.error(WppCmd.TIME_SET, WppError.UNSUPPORTED))
        assertEquals(listOf(WppCmd.CONNECT_REASON), commands(r))
        assertNull(r.outcome)
    }

    @Test
    fun should_echo_sync_ok_when_scale_sends_it_as_request() {
        val s = session()
        authenticated(s)
        val r = s.handle(WppMessage(WppCmd.SYNC_OK or WppCodec.DEVICE_REQUEST))
        assertEquals(listOf(WppCmd.SYNC_OK or WppCodec.DEVICE_REQUEST), commands(r))
        assertEquals(WppSession.Stage.TIME, s.stage)
    }

    @Test
    fun should_answer_unsupported_when_scale_sends_other_request() {
        val s = session()
        authenticated(s)
        val r = s.handle(WppMessage(0x0105 or WppCodec.DEVICE_REQUEST))
        assertEquals(listOf(WppCmd.ERROR or WppCodec.DEVICE_REQUEST), commands(r))
        assertEquals(CmdError(0x0105, WppError.UNSUPPORTED), CmdError.parse(r.send[0].first(WppObj.CMD_ERROR)!!.value))
    }

    @Test
    fun should_skip_record_read_when_store_is_empty() {
        val s = session(withTime = false)
        authenticated(s)
        s.handle(WppMessage(WppCmd.CONNECT_REASON, listOf(Tlv(WppObj.CONNECT_REASON, byteArrayOf(0, 1)))))
        assertEquals(listOf(WppCmd.SYNC_OK), commands(s.handle(WppFixtures.status(0))))
    }

    @Test
    fun should_ignore_unsolicited_message_when_waiting_for_reply() {
        val s = session(withTime = false)
        authenticated(s)
        val r = s.handle(WppMessage(WppCmd.STORED_MEASURE))
        assertTrue(r.send.isEmpty())
        assertEquals(WppSession.Stage.REASON, s.stage)
    }
}
