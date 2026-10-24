package com.izum1k.scalepaw.data.googlehealth

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Tokens from a completed consent; [email] comes from the ID token and is only shown to the user. */
class GoogleTokens(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long, val email: String?)

/** PKCE and loopback-request helpers, kept free of I/O so they can be unit tested. */
internal object OAuthLoopback {
    private val random = SecureRandom()
    private val url = Base64.getUrlEncoder().withoutPadding()

    fun randomToken(bytes: Int = 32): String = url.encodeToString(ByteArray(bytes).also(random::nextBytes))

    fun challenge(verifier: String): String = url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    /** Query parameters of `GET /path?query HTTP/1.1`, or null when the line isn't a request for [path]. */
    fun parseRequestLine(line: String, path: String = "/"): Map<String, String>? {
        val parts = line.split(' ')
        if (parts.size < 2 || parts[0] != "GET") return null
        val target = parts[1]
        if (target.substringBefore('?') != path) return null
        val query = target.substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
    }

    /** The `email` claim of an unverified ID token; only used as a display label. */
    fun emailFromIdToken(idToken: String?): String? {
        val payload = idToken?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
            (Json.parseToJsonElement(json).jsonObject["email"] as? JsonPrimitive)?.content
        }.getOrNull()
    }
}

/**
 * OAuth for a user-supplied "Desktop app" client: system browser, loopback redirect and PKCE.
 * Google rejects consent inside a WebView, and Desktop clients may not use custom-scheme redirects.
 */
class GoogleHealthAuth(private val store: GoogleHealthStore) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedUntil: Long = 0L

    suspend fun authorize(openBrowser: (String) -> Unit): GoogleTokens = withContext(Dispatchers.IO) {
        val clientId = store.state.value.clientId.takeIf { it.isNotBlank() }
            ?: throw GoogleHealthException(GoogleHealthError.NOT_CONFIGURED)
        val secret = store.clientSecret() ?: throw GoogleHealthException(GoogleHealthError.NOT_CONFIGURED)
        val verifier = OAuthLoopback.randomToken(48)
        val state = OAuthLoopback.randomToken(16)
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { server ->
            server.soTimeout = 1000
            val redirect = "http://$LOOPBACK:${server.localPort}"
            openBrowser(
                AUTH_URL + "?" + GoogleHttp.form(
                    mapOf(
                        "client_id" to clientId,
                        "redirect_uri" to redirect,
                        "response_type" to "code",
                        "scope" to SCOPES.joinToString(" "),
                        "code_challenge" to OAuthLoopback.challenge(verifier),
                        "code_challenge_method" to "S256",
                        "state" to state,
                        "access_type" to "offline",
                        "prompt" to "consent",
                    ),
                ),
            )
            val params = withTimeoutOrNull(TIMEOUT_MS) { awaitRedirect(server) }
                ?: throw GoogleHealthException(GoogleHealthError.TIMEOUT)
            params["error"]?.let { throw GoogleHealthException(if (it == "access_denied") GoogleHealthError.DENIED else GoogleHealthError.FAILED, it) }
            if (params["state"] != state) throw GoogleHealthException(GoogleHealthError.FAILED, "state mismatch")
            val code = params["code"] ?: throw GoogleHealthException(GoogleHealthError.FAILED, "no code")
            val tokens = tokenRequest(
                mapOf(
                    "code" to code,
                    "client_id" to clientId,
                    "client_secret" to secret,
                    "redirect_uri" to redirect,
                    "grant_type" to "authorization_code",
                    "code_verifier" to verifier,
                ),
            )
            if (tokens.refreshToken == null) throw GoogleHealthException(GoogleHealthError.FAILED, "no refresh token")
            mutex.withLock { cache(tokens) }
            tokens
        }
    }

    private suspend fun awaitRedirect(server: ServerSocket): Map<String, String> {
        while (true) {
            currentCoroutineContext().ensureActive()
            val socket = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }
            socket.use { s ->
                s.soTimeout = 5000
                val line = s.getInputStream().bufferedReader().readLine().orEmpty()
                val params = OAuthLoopback.parseRequestLine(line)
                val out = s.getOutputStream()
                if (params == null || (params["code"] == null && params["error"] == null)) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                } else {
                    val html = "<html><body><p><a href=\"$RETURN_URI\">ScalePaw</a></p></body></html>"
                    out.write(
                        (
                            "HTTP/1.1 302 Found\r\nLocation: $RETURN_URI\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html"
                            ).toByteArray(),
                    )
                }
                out.flush()
                if (params != null && (params["code"] != null || params["error"] != null)) return params
            }
        }
    }

    /** A valid access token, refreshing it with the stored refresh token when needed. */
    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { !forceRefresh && now < cachedUntil }?.let { return it }
        val refresh = store.refreshToken() ?: throw GoogleHealthException(GoogleHealthError.NOT_CONNECTED)
        val clientId = store.state.value.clientId
        val secret = store.clientSecret() ?: throw GoogleHealthException(GoogleHealthError.NOT_CONFIGURED)
        val tokens = withContext(Dispatchers.IO) {
            tokenRequest(
                mapOf(
                    "client_id" to clientId,
                    "client_secret" to secret,
                    "refresh_token" to refresh,
                    "grant_type" to "refresh_token",
                ),
            )
        }
        cache(tokens)
        tokens.accessToken
    }

    suspend fun revoke() {
        val refresh = store.refreshToken() ?: return
        withContext(Dispatchers.IO) {
            runCatching { GoogleHttp.request("POST", REVOKE_URL, GoogleHttp.form(mapOf("token" to refresh)), FORM) }
        }
        mutex.withLock {
            cachedToken = null
            cachedUntil = 0L
        }
    }

    private fun cache(tokens: GoogleTokens) {
        cachedToken = tokens.accessToken
        cachedUntil = System.currentTimeMillis() + (tokens.expiresInSeconds - 60).coerceAtLeast(0) * 1000
    }

    private fun tokenRequest(fields: Map<String, String>): GoogleTokens {
        val res = GoogleHttp.request("POST", TOKEN_URL, GoogleHttp.form(fields), FORM)
        val json = res.json()
        if (!res.ok || json == null) {
            val error = (json?.get("error") as? JsonPrimitive)?.content
            throw when {
                error == "invalid_grant" -> GoogleHealthException(GoogleHealthError.REAUTH, error)
                res.code >= 500 -> GoogleHealthException(GoogleHealthError.SERVER, res.errorMessage())
                else -> GoogleHealthException(GoogleHealthError.FAILED, res.errorMessage())
            }
        }
        fun str(key: String) = (json[key] as? JsonPrimitive)?.content
        return GoogleTokens(
            accessToken = str("access_token") ?: throw GoogleHealthException(GoogleHealthError.FAILED, "no access token"),
            refreshToken = str("refresh_token"),
            expiresInSeconds = str("expires_in")?.toLongOrNull() ?: 3600L,
            email = OAuthLoopback.emailFromIdToken(str("id_token")),
        )
    }

    companion object {
        const val RETURN_URI = "scalepaw://google-health"
        const val WRITE_SCOPE = "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.writeonly"
        private val SCOPES = listOf("openid", "email", WRITE_SCOPE)
        private const val LOOPBACK = "127.0.0.1"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        private const val FORM = "application/x-www-form-urlencoded"
        private const val TIMEOUT_MS = 5 * 60_000L
    }
}
