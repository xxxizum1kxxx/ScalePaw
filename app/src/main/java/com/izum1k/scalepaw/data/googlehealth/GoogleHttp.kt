package com.izum1k.scalepaw.data.googlehealth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

enum class GoogleHealthError {
    NOT_CONFIGURED,
    NOT_CONNECTED,
    CANCELLED,
    DENIED,
    TIMEOUT,
    ACCOUNT_NOT_LINKED,
    REAUTH,
    NETWORK,
    RATE_LIMITED,
    SERVER,
    FAILED,
}

class GoogleHealthException(val error: GoogleHealthError, detail: String? = null) : Exception(detail ?: error.name)

internal class HttpResult(val code: Int, val text: String) {
    val ok: Boolean get() = code in 200..299

    fun json(): JsonObject? = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()

    /** `error.status` or the `ErrorInfo.reason` of a Google API error body. */
    fun errorReason(): String? {
        val error = json()?.get("error") as? JsonObject ?: return null
        val reason = (error["details"]?.jsonArray)
            ?.firstNotNullOfOrNull { (it.jsonObject["reason"] as? JsonPrimitive)?.content }
        return reason ?: (error["status"] as? JsonPrimitive)?.content
    }

    fun errorMessage(): String {
        val error = json()?.get("error")
        val message = when (error) {
            is JsonObject -> (error["message"] as? JsonPrimitive)?.content
            is JsonPrimitive -> error.content
            else -> null
        }
        return "HTTP $code" + (message?.let { ": $it" } ?: "")
    }
}

internal object GoogleHttp {
    fun form(fields: Map<String, String>): String =
        fields.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }

    fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String = "application/json",
        bearer: String? = null,
    ): HttpResult {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw GoogleHealthException(GoogleHealthError.NETWORK, e.message)
        }
        return try {
            // HttpURLConnection has no PATCH; Google APIs honour the override header.
            conn.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            bearer?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "$contentType; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            HttpResult(code, stream?.use { String(it.readBytes()) }.orEmpty())
        } catch (e: IOException) {
            throw GoogleHealthException(GoogleHealthError.NETWORK, e.message)
        } finally {
            conn.disconnect()
        }
    }
}
