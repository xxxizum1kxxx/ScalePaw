package com.izum1k.scalepaw.data.micloud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiCloudCryptoTest {
    private val ssecurity = "AAECAwQFBgcICQoLDA0ODw=="
    private val nonce = "ZGVmZ2hpamtsbW5v"
    private val signed = "+JfC+ScjV+jXa9PgCRtBpAfTWAAwPFTV0LabBt47GcA="

    @Test
    fun should_match_reference_when_signing_nonce() {
        assertEquals(signed, MiCloudCrypto.signedNonce(ssecurity, nonce))
    }

    @Test
    fun should_match_reference_when_signing_request() {
        val signature = MiCloudCrypto.encSignature(
            "https://de.api.io.mi.com/app/v2/homeroom/gethome",
            "POST",
            signed,
            mapOf("data" to "{\"fg\": true}"),
        )
        assertEquals("AeCEIJOE48J1BxS9D2lDFDux+Ak=", signature)
    }

    @Test
    fun should_match_standard_vector_when_rc4_without_drop() {
        val out = MiCloudCrypto.rc4("Key".toByteArray(), "Plaintext".toByteArray(), drop = 0)
        val expected = intArrayOf(0xBB, 0xF3, 0x16, 0xE8, 0xD9, 0x40, 0xAF, 0x0A, 0xD3).map { it.toByte() }.toByteArray()
        assertArrayEquals(expected, out)
    }

    @Test
    fun should_restore_payload_when_rc4_round_trips() {
        val payload = "{\"did\":\"blt.3.abc\",\"pdid\":1}"
        val encrypted = MiCloudCrypto.encryptRc4(signed, payload)
        assertEquals(payload, MiCloudCrypto.decryptRc4(signed, encrypted))
    }

    @Test
    fun should_append_signature_fields_when_building_enc_params() {
        val params = MiCloudCrypto.encParams("https://api.io.mi.com/app/v2/x", "POST", signed, nonce, mapOf("data" to "{}"), ssecurity)
        assertEquals(listOf("data", "rc4_hash__", "signature", "ssecurity", "_nonce"), params.keys.toList())
        assertEquals("{}", MiCloudCrypto.decryptRc4(signed, params.getValue("data")))
    }

    @Test
    fun should_match_reference_when_hashing_password() {
        assertEquals("5EBE2294ECD0E0F08EAB7690D2A6EE69", MiCloudCrypto.md5Upper("secret"))
    }

    @Test
    fun should_normalize_mac_when_case_and_separators_differ() {
        assertEquals("AA:BB:CC:DD:EE:0F", MiCloudCrypto.normalizeMac("aabbccddee0f"))
        assertEquals("AA:BB:CC:DD:EE:0F", MiCloudCrypto.normalizeMac("aa-bb-cc-dd-ee-0f"))
        assertNull(MiCloudCrypto.normalizeMac("zz:bb:cc:dd:ee:0f"))
        assertNull(MiCloudCrypto.normalizeMac(null))
    }
}
