package com.izum1k.scalepaw.data.micloud

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

internal class MiCloudResponse(val code: Int, val headers: Map<String, List<String>>, val body: ByteArray, val url: String) {
    val text: String get() = String(body)
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/** A minimal cookie session: cookies are matched by domain suffix only, which is all the Xiaomi login needs. */
internal class MiCloudHttp(private val userAgent: String) {
    private data class Cookie(val name: String, val value: String, val domain: String)

    private val jar = mutableListOf<Cookie>()

    fun setCookie(name: String, value: String, domain: String) {
        val d = domain.trimStart('.').lowercase()
        jar.removeAll { it.name == name && it.domain == d }
        jar += Cookie(name, value, d)
    }

    fun cookie(name: String, domain: String? = null): String? = jar.lastOrNull { c ->
        c.name == name && (domain == null || c.domain == domain.trimStart('.').lowercase())
    }?.value

    fun request(
        method: String,
        url: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
        headers: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
        readTimeoutMs: Int = 20_000,
    ): MiCloudResponse {
        var target = if (query.isEmpty()) url else url + (if ('?' in url) "&" else "?") + encode(query)
        var verb = method
        var body = form?.let { encode(it).toByteArray() }
        repeat(MAX_REDIRECTS) {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = verb
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Encoding", "identity")
                headers.forEach(::setRequestProperty)
                val host = URI(target).host.lowercase()
                val sent = jar.filter { host == it.domain || host.endsWith("." + it.domain) }.associate { it.name to it.value } + cookies
                if (sent.isNotEmpty()) setRequestProperty("Cookie", sent.entries.joinToString("; ") { "${it.key}=${it.value}" })
                if (body != null) {
                    doOutput = true
                    if (headers.keys.none { it.equals("Content-Type", true) }) {
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    }
                    outputStream.use { it.write(body) }
                } else if (verb == "POST") {
                    setFixedLengthStreamingMode(0)
                }
            }
            try {
                val code = conn.responseCode
                val fields = conn.headerFields.filterKeys { it != null }
                storeCookies(URI(target).host, fields)
                val bytes = (if (code >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
                val location = conn.getHeaderField("Location")
                if (followRedirects && code in 300..399 && location != null) {
                    target = URI(target).resolve(location).toString()
                    verb = "GET"
                    body = null
                } else {
                    return MiCloudResponse(code, fields, bytes, target)
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Too many redirects")
    }

    private fun storeCookies(host: String, fields: Map<String, List<String>>) {
        fields.entries.filter { it.key.equals("Set-Cookie", true) }.flatMap { it.value }.forEach { raw ->
            val parts = raw.split(';').map(String::trim)
            val (name, value) = parts.first().split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val domain = parts.drop(1).firstOrNull { it.startsWith("domain=", true) }?.substringAfter('=') ?: host
            val expired = value.isEmpty() || value == "EXPIRED" || parts.any { it.equals("max-age=0", true) }
            if (expired) {
                jar.removeAll { it.name == name && it.domain == domain.trimStart('.').lowercase() }
            } else {
                setCookie(name, value, domain)
            }
        }
    }

    private fun encode(params: Map<String, String>) = params.entries.joinToString("&") { (k, v) ->
        URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
    }

    private companion object {
        const val MAX_REDIRECTS = 10
    }
}
