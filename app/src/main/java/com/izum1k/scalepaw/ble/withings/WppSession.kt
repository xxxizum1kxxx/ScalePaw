package com.izum1k.scalepaw.ble.withings

/** What a finished read returned. */
data class WppSyncResult(
    val firmware: Long?,
    val connectReason: Int?,
    val clockDriftSeconds: Int?,
    val storedCount: Int,
    val groups: List<WppGroup>,
)

sealed interface WppFailure {
    /** The scale answered PROBE without a challenge: it is in setup mode (button session). */
    data class SetupMode(val firmware: Long?) : WppFailure

    /** The scale asked for authentication but no valid `kl` is configured. */
    data object MissingKey : WppFailure

    /** The scale did not prove it knows `kl`; nothing is read from it. */
    data object ScaleUnverified : WppFailure

    data class CommandError(val command: Int, val error: Int) : WppFailure

    data class Malformed(val detail: String) : WppFailure
}

sealed interface WppOutcome {
    data class Synced(val result: WppSyncResult) : WppOutcome
    data class Failed(val failure: WppFailure) : WppOutcome
}

/** Messages to write next and, once the session ends, how it ended. */
data class WppReaction(val send: List<WppMessage> = emptyList(), val outcome: WppOutcome? = null)

/**
 * The read-only sync as a pure state machine: PROBE, mutual challenge, optional TIME_SET,
 * CONNECT_REASON, STORED_MEASURE (state, then all), SYNC_OK, DISCONNECT. Nothing that changes
 * the scale's data is ever sent.
 *
 * @param time the clock to send with TIME_SET, or null to skip it.
 */
class WppSession(
    private val kl: String,
    private val time: (() -> WppTime)?,
    private val osVersion: Int = 34,
    private val newChallenge: () -> ByteArray = WppAuth::newChallenge,
) {
    enum class Stage { IDLE, PROBE, CHALLENGE, TIME, REASON, STATUS, RECORDS, SYNC_OK, DONE }

    var stage: Stage = Stage.IDLE
        private set

    private var mac: String = ""
    private var own: ByteArray = ByteArray(0)
    private var firmware: Long? = null
    private var reason: Int? = null
    private var drift: Int? = null
    private var count = 0
    private val records = mutableListOf<WppMessage>()
    private val skipped = mutableListOf<String>()

    /** Records that could not be parsed, for diagnostics. */
    val skippedRecords: List<String> get() = skipped

    fun start(): WppMessage {
        stage = Stage.PROBE
        return WppRequests.probe(osVersion)
    }

    fun handle(msg: WppMessage): WppReaction {
        if (stage == Stage.DONE) return WppReaction()
        if (msg.fromDevice) return answerDeviceRequest(msg)
        if (msg.baseCommand == WppCmd.ERROR) return onError(msg)
        return try {
            when (stage) {
                Stage.PROBE -> onProbe(msg)
                Stage.CHALLENGE -> onChallengeAnswer(msg)
                Stage.TIME -> expect(msg, WppCmd.TIME_SET) {
                    drift = it.first(WppObj.TIME_SET_REPLY)?.value?.let { v -> WppReader(v).i32() }
                    askReason()
                }
                Stage.REASON -> expect(msg, WppCmd.CONNECT_REASON) {
                    reason = it.first(WppObj.CONNECT_REASON)?.value?.let { v -> WppReader(v).u16() }
                    askStatus()
                }
                Stage.STATUS -> expect(msg, WppCmd.STORED_MEASURE) {
                    val tlv = it.first(WppObj.STORED_MEASURE_STATUS) ?: throw WppException("no STORED_MEASURE_STATUS")
                    count = StoredMeasureStatus.parse(tlv.value).count
                    if (count > 0) {
                        stage = Stage.RECORDS
                        WppReaction(listOf(WppRequests.storedMeasure(MeasureAction.GET_ALL)))
                    } else {
                        askSyncOk()
                    }
                }
                Stage.RECORDS -> expect(msg, WppCmd.STORED_MEASURE) {
                    records += it
                    if (it.first(WppObj.NULL) != null) askSyncOk() else WppReaction()
                }
                Stage.SYNC_OK -> expect(msg, WppCmd.SYNC_OK) { finish() }
                Stage.IDLE, Stage.DONE -> WppReaction()
            }
        } catch (e: WppException) {
            fail(WppFailure.Malformed(e.message ?: "malformed"))
        }
    }

    private fun onProbe(msg: WppMessage): WppReaction = when (msg.baseCommand) {
        WppCmd.PROBE -> {
            readFirmware(msg)
            fail(WppFailure.SetupMode(firmware))
        }
        WppCmd.PROBE_CHALLENGE -> if (!WppAuth.isValidKey(kl)) {
            fail(WppFailure.MissingKey)
        } else {
            val tlv = msg.first(WppObj.PROBE_CHALLENGE) ?: throw WppException("no PROBE_CHALLENGE")
            val challenge = ProbeChallenge.parse(tlv.value)
            mac = challenge.mac
            own = newChallenge()
            stage = Stage.CHALLENGE
            WppReaction(listOf(WppAuth.challengeReply(challenge.challenge, mac, kl, own)))
        }
        else -> WppReaction()
    }

    private fun onChallengeAnswer(msg: WppMessage): WppReaction {
        if (msg.baseCommand != WppCmd.PROBE && msg.baseCommand != WppCmd.PROBE_CHALLENGE) return WppReaction()
        readFirmware(msg)
        val answer = msg.first(WppObj.PROBE_CHALLENGE_RESPONSE)?.value?.let { WppReader(it).bytes() }
        if (answer == null || !WppAuth.verify(answer, own, mac, kl)) return fail(WppFailure.ScaleUnverified)
        val clock = time
        return if (clock != null) {
            stage = Stage.TIME
            WppReaction(listOf(WppRequests.timeSet(clock())))
        } else {
            askReason()
        }
    }

    /** TIME_SET and CONNECT_REASON are informative; a scale that refuses them still gets read. */
    private fun onError(msg: WppMessage): WppReaction {
        val error = msg.first(WppObj.CMD_ERROR)?.value?.let { runCatching { CmdError.parse(it) }.getOrNull() }
            ?: CmdError(0, 0)
        return when (stage) {
            Stage.TIME -> askReason()
            Stage.REASON -> askStatus()
            Stage.SYNC_OK -> finish()
            else -> fail(WppFailure.CommandError(error.command, error.error))
        }
    }

    private fun answerDeviceRequest(msg: WppMessage): WppReaction = when (msg.baseCommand) {
        WppCmd.SYNC_OK -> WppReaction(listOf(WppRequests.syncOkEcho()))
        WppCmd.ERROR -> WppReaction()
        else -> WppReaction(listOf(WppRequests.unsupported(msg.baseCommand)))
    }

    private inline fun expect(msg: WppMessage, command: Int, next: (WppMessage) -> WppReaction): WppReaction =
        if (msg.baseCommand == command) next(msg) else WppReaction()

    private fun askReason(): WppReaction {
        stage = Stage.REASON
        return WppReaction(listOf(WppRequests.connectReason()))
    }

    private fun askStatus(): WppReaction {
        stage = Stage.STATUS
        return WppReaction(listOf(WppRequests.storedMeasure(MeasureAction.GET_STATE)))
    }

    private fun askSyncOk(): WppReaction {
        stage = Stage.SYNC_OK
        return WppReaction(listOf(WppRequests.syncOk()))
    }

    private fun finish(): WppReaction {
        stage = Stage.DONE
        val groups = WppRecords.mergeByTime(WppRecords.parseGroups(records) { skipped += it })
        return WppReaction(
            listOf(WppRequests.disconnect()),
            WppOutcome.Synced(WppSyncResult(firmware, reason, drift, count, groups)),
        )
    }

    private fun fail(failure: WppFailure): WppReaction {
        stage = Stage.DONE
        return WppReaction(listOf(WppRequests.disconnect()), WppOutcome.Failed(failure))
    }

    private fun readFirmware(msg: WppMessage) {
        val tlv = msg.first(WppObj.PROBE_REPLY) ?: return
        runCatching { ProbeReply.parse(tlv.value) }.getOrNull()?.let { firmware = it.firmwareVersion }
    }
}
