package com.izum1k.scalepaw.data.googlehealth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleHealthAuthTest {
    @Test
    fun should_match_rfc7636_vector_when_computing_challenge() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            OAuthLoopback.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun should_produce_url_safe_verifier_when_generating_token() {
        val token = OAuthLoopback.randomToken(48)
        assertEquals(64, token.length)
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun should_extract_code_and_state_when_parsing_redirect() {
        val params = OAuthLoopback.parseRequestLine("GET /?state=abc&code=4%2F0Ab-x&scope=email%20openid HTTP/1.1")!!
        assertEquals("4/0Ab-x", params["code"])
        assertEquals("abc", params["state"])
        assertEquals("email openid", params["scope"])
    }

    @Test
    fun should_ignore_other_paths_when_parsing_redirect() {
        assertNull(OAuthLoopback.parseRequestLine("GET /favicon.ico HTTP/1.1"))
        assertNull(OAuthLoopback.parseRequestLine("POST /?code=x HTTP/1.1"))
        assertNull(OAuthLoopback.parseRequestLine(""))
    }

    @Test
    fun should_read_email_when_id_token_has_claim() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"email":"a@b.c","sub":"1"}""".toByteArray())
        assertEquals("a@b.c", OAuthLoopback.emailFromIdToken("h.$payload.s"))
        assertNull(OAuthLoopback.emailFromIdToken(null))
        assertNull(OAuthLoopback.emailFromIdToken("garbage"))
    }
}
