package com.izum1k.scalepaw.data.withings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One device association on the account that carries a `kl` secret. */
data class WithingsScale(val deviceId: Long?, val model: Int?, val mac: String?, val kl: String)

enum class WithingsFailure { INVALID_CREDENTIALS, NO_SESSION, UNEXPECTED }

sealed interface WithingsResult {
    data class Success(val scales: List<WithingsScale>) : WithingsResult
    data class Failed(val reason: WithingsFailure, val status: Int? = null) : WithingsResult
}

/**
 * Reads each scale's `kl` from Withings' legacy account API, the same calls the phone app makes
 * when it downloads its device associations. Credentials only go to Withings over HTTPS and are
 * neither stored nor logged.
 */
class WithingsAccountClient(private val api: String = API) {

    suspend fun fetchScales(email: String, password: String): WithingsResult = withContext(Dispatchers.IO) {
        val login = post("auth", COMMON + mapOf(
            "action" to "login",
            "email" to email,
            "authprovider" to "withings",
            "password" to password,
            "duration" to "900",
            "typename" to "mobile",
            "os" to "android",
        )) ?: return@withContext WithingsResult.Failed(WithingsFailure.UNEXPECTED)
        val loginStatus = WithingsAccountParser.status(login)
        if (loginStatus != 0) return@withContext WithingsResult.Failed(WithingsFailure.INVALID_CREDENTIALS, loginStatus)
        val session = WithingsAccountParser.session(login)
            ?: return@withContext WithingsResult.Failed(WithingsFailure.NO_SESSION)
        try {
            val associations = post("association", COMMON + mapOf(
                "action" to "getbyaccountid",
                "sessionid" to session,
                "type" to "-1",
                "enrich" to "t",
            )) ?: return@withContext WithingsResult.Failed(WithingsFailure.UNEXPECTED)
            val status = WithingsAccountParser.status(associations)
            if (status != 0) return@withContext WithingsResult.Failed(WithingsFailure.UNEXPECTED, status)
            WithingsResult.Success(WithingsAccountParser.scales(associations))
        } finally {
            runCatching { post("auth", COMMON + mapOf("action" to "logout", "sessionid" to session)) }
        }
    }

    private fun post(path: String, form: Map<String, String>): JsonElement? {
        val body = form.entries.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }.toByteArray()
        val conn = (URL("$api/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setFixedLengthStreamingMode(body.size)
        }
        return try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code >= 400) conn.errorStream else conn.inputStream)?.use { String(it.readBytes()) } ?: return null
            runCatching { Json.parseToJsonElement(text) }.getOrNull()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val API = "https://scalews.withings.net/cgi-bin"
        private val COMMON = mapOf("appname" to "wiscale", "apppfm" to "android", "appliver" to "202609240")

        fun normalizeMac(mac: String?): String? =
            mac?.lowercase()?.filter { it in '0'..'9' || it in 'a'..'f' }?.takeIf { it.length == 12 }
    }
}

/** Pure JSON handling of the account API's answers, kept apart so it is unit-testable. */
object WithingsAccountParser {
    fun status(json: JsonElement): Int? = ((json as? JsonObject)?.get("status") as? JsonPrimitive)?.intOrNull

    fun session(json: JsonElement): String? =
        find(json, "sessionid").firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }

    fun scales(json: JsonElement): List<WithingsScale> =
        find(json, "associations")
            .flatMap { if (it is JsonArray) it.toList() else listOf(it) }
            .mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val kl = obj.string("kl")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val props = obj["deviceproperties"] as? JsonObject
                WithingsScale(
                    deviceId = (obj["deviceid"] as? JsonPrimitive)?.longOrNull,
                    model = (props?.get("model") ?: props?.get("modelid"))?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() },
                    mac = props?.string("macaddress") ?: props?.string("mac"),
                    kl = kl,
                )
            }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun find(json: JsonElement, key: String): List<JsonElement> = when (json) {
        is JsonObject -> json.entries.flatMap { (k, v) -> (if (k == key) listOf(v) else emptyList()) + find(v, key) }
        is JsonArray -> json.flatMap { find(it, key) }
        else -> emptyList()
    }
}
