package com.izum1k.scalepaw.data.withings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WithingsAccountParserTest {
    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun should_find_session_when_nested_in_body() {
        val login = json("""{"status":0,"body":{"account":[{"sessionid":"abc-123","id":1}]}}""")
        assertEquals(0, WithingsAccountParser.status(login))
        assertEquals("abc-123", WithingsAccountParser.session(login))
    }

    @Test
    fun should_return_no_session_when_login_omits_it() {
        assertNull(WithingsAccountParser.session(json("""{"status":0,"body":{}}""")))
    }

    @Test
    fun should_list_only_associations_with_key_when_parsing_devices() {
        val body = json(
            """{"status":0,"body":{"associations":[
                {"deviceid":11,"kl":"0123456789abcdef0123456789ABCDEF","deviceproperties":{"model":16,"macaddress":"00:24:e4:00:00:01"}},
                {"deviceid":12,"deviceproperties":{"model":55}},
                {"deviceid":13,"kl":"ffffffffffffffffffffffffffffffff","deviceproperties":{"modelid":"5","mac":"0024e4000002"}}
            ]}}""",
        )
        val scales = WithingsAccountParser.scales(body)
        assertEquals(
            listOf(
                WithingsScale(11, 16, "00:24:e4:00:00:01", "0123456789abcdef0123456789ABCDEF"),
                WithingsScale(13, 5, "0024e4000002", "ffffffffffffffffffffffffffffffff"),
            ),
            scales,
        )
    }

    @Test
    fun should_compare_macs_without_separators_when_normalizing() {
        assertEquals("0024e4000001", WithingsAccountClient.normalizeMac("00:24:E4:00:00:01"))
        assertEquals("0024e4000001", WithingsAccountClient.normalizeMac("0024e4000001"))
        assertNull(WithingsAccountClient.normalizeMac("00:24"))
    }
}
