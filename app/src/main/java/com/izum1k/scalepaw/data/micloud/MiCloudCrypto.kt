package com.izum1k.scalepaw.data.micloud

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Request signing and the RC4 envelope of the Mi Home cloud API. */
object MiCloudCrypto {
    private val random = SecureRandom()
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun generateNonce(millis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(8).also(random::nextBytes)
        val minutes = ByteBuffer.allocate(4).putInt((millis / 60_000).toInt()).array()
        return b64e.encodeToString(bytes + minutes)
    }

    fun signedNonce(ssecurity: String, nonce: String): String =
        b64e.encodeToString(sha("SHA-256", b64d.decode(ssecurity) + b64d.decode(nonce)))

    /** [url] is the full API url; the signature covers its path after `.com`, minus the `/app` prefix. */
    fun encSignature(url: String, method: String, signedNonce: String, params: Map<String, String>): String {
        val parts = buildList {
            add(method.uppercase())
            add(url.substringAfter("com").replace("/app/", "/"))
            params.forEach { (k, v) -> add("$k=$v") }
            add(signedNonce)
        }
        return b64e.encodeToString(sha("SHA-1", parts.joinToString("&").toByteArray()))
    }

    /** The query parameters of an encrypted call, in the order the server signs them. */
    fun encParams(url: String, method: String, signedNonce: String, nonce: String, params: Map<String, String>, ssecurity: String): Map<String, String> {
        val withHash = LinkedHashMap(params)
        withHash["rc4_hash__"] = encSignature(url, method, signedNonce, withHash)
        val encrypted = LinkedHashMap<String, String>()
        withHash.forEach { (k, v) -> encrypted[k] = encryptRc4(signedNonce, v) }
        encrypted["signature"] = encSignature(url, method, signedNonce, encrypted)
        encrypted["ssecurity"] = ssecurity
        encrypted["_nonce"] = nonce
        return encrypted
    }

    fun encryptRc4(key: String, payload: String): String =
        b64e.encodeToString(rc4(b64d.decode(key), payload.toByteArray()))

    fun decryptRc4(key: String, payload: String): String =
        String(rc4(b64d.decode(key), b64d.decode(payload.trim())))

    /** RC4 that discards the first [drop] keystream bytes (the cloud uses RC4-drop1024). */
    fun rc4(key: ByteArray, data: ByteArray, drop: Int = 1024): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            s[i] = s[j].also { s[j] = s[i] }
        }
        var i = 0
        j = 0
        fun next(): Int {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            s[i] = s[j].also { s[j] = s[i] }
            return s[(s[i] + s[j]) and 0xFF]
        }
        repeat(drop) { next() }
        return ByteArray(data.size) { (data[it].toInt() xor next()).toByte() }
    }

    fun md5Upper(text: String): String = sha("MD5", text.toByteArray()).joinToString("") { "%02X".format(it) }

    /** `AA:BB:..` regardless of case or separators, or null when it is not a MAC. */
    fun normalizeMac(mac: String?): String? {
        val hex = mac?.filter { it.isLetterOrDigit() }?.uppercase() ?: return null
        if (hex.length != 12 || hex.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
        return hex.chunked(2).joinToString(":")
    }

    private fun sha(algorithm: String, data: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(data)
}
