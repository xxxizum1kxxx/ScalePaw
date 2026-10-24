package com.izum1k.scalepaw.ble.withings

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Mutual challenge authentication. Each side proves it knows `kl` by answering
 * `SHA1(challenge ‖ mac ‖ kl)`, where `mac` is the string exactly as the scale sent it.
 */
object WppAuth {
    const val CHALLENGE_SIZE = 16
    const val KEY_LENGTH = 32

    private val random = SecureRandom()

    fun answer(challenge: ByteArray, mac: String, kl: String): ByteArray =
        MessageDigest.getInstance("SHA-1").digest(challenge + mac.toByteArray(Charsets.UTF_8) + kl.toByteArray(Charsets.UTF_8))

    fun newChallenge(): ByteArray = ByteArray(CHALLENGE_SIZE).also(random::nextBytes)

    /** Answers the scale's challenge and sends it one of our own. */
    fun challengeReply(deviceChallenge: ByteArray, mac: String, kl: String, own: ByteArray): WppMessage = WppMessage(
        WppCmd.PROBE_CHALLENGE,
        listOf(
            Tlv(WppObj.PROBE_CHALLENGE_RESPONSE, WppCodec.packStr(answer(deviceChallenge, mac, kl))),
            Tlv(WppObj.PROBE_CHALLENGE, WppCodec.packStr(mac) + WppCodec.packStr(own)),
        ),
    )

    fun verify(deviceAnswer: ByteArray, own: ByteArray, mac: String, kl: String): Boolean =
        MessageDigest.isEqual(deviceAnswer, answer(own, mac, kl))

    /** `kl` is 32 printable ASCII characters. */
    fun isValidKey(kl: String?): Boolean = kl != null && kl.length == KEY_LENGTH && kl.all { it in '!'..'~' }
}
