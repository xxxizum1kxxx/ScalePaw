package com.izum1k.scalepaw.data.micloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder

data class MiCloudDevice(
    val name: String,
    val model: String?,
    val mac: String?,
    val did: String,
    val beaconKey: String?,
    val server: String,
)

enum class MiCloudFailure { INVALID_USER, INVALID_CREDENTIALS, INVALID_CAPTCHA, INVALID_CODE, NO_TOKEN, QR_EXPIRED, UNEXPECTED }

sealed interface MiCloudLogin {
    data object Success : MiCloudLogin
    class NeedCaptcha(val image: ByteArray) : MiCloudLogin
    data object NeedEmailCode : MiCloudLogin
    data class Failed(val reason: MiCloudFailure) : MiCloudLogin
}

class MiCloudQr(val image: ByteArray, val loginUrl: String)

/**
 * Logs in to the Xiaomi account and reads BLE beacon keys from the Mi Home cloud. A port of
 * https://github.com/PiotrMachowski/Xiaomi-cloud-tokens-extractor (MIT). Credentials and tokens only
 * live in this instance; nothing is persisted or logged.
 */
class MiCloudClient {
    private val http = MiCloudHttp(generateAgent())
    private val deviceId = randomLetters(6, 'a'..'z')

    private var ssecurity: String? = null
    private var userId: String? = null
    private var serviceToken: String? = null

    private var pendingAuth: MutableMap<String, String>? = null
    private var emailContext: String? = null
    private var qrPoll: String? = null
    private var qrTimeoutMs = 0L

    // ── Web login ──────────────────────────────────────────────────────────

    /** Continues a login finished on Xiaomi's own web page, from the `account.xiaomi.com` cookies it left. */
    suspend fun webLogin(accountCookies: Map<String, String>): MiCloudLogin = io {
        reset()
        if (accountCookies["passToken"].isNullOrEmpty()) return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        accountCookies.forEach { (name, value) -> http.setCookie(name, value, "xiaomi.com") }
        val response = http.request("GET", "$ACCOUNT/pass/serviceLogin?sid=xiaomiio&_json=true")
        val json = response.json() ?: return@io MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
        if (json.str("ssecurity") == null) return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        adopt(json)
        finishWithLocation(json.str("location"))
    }

    // ── Password login ──────────────────────────────────────────────────────

    suspend fun passwordLogin(username: String, password: String): MiCloudLogin = io {
        reset()
        for (domain in listOf("mi.com", "xiaomi.com")) {
            http.setCookie("sdkVersion", "accountsdk-18.8.15", domain)
            http.setCookie("deviceId", deviceId, domain)
        }
        val step1 = http.request(
            "GET", "$ACCOUNT/pass/serviceLogin?sid=xiaomiio&_json=true",
            cookies = mapOf("userId" to username),
        )
        val json1 = step1.json() ?: return@io MiCloudLogin.Failed(MiCloudFailure.INVALID_USER)
        val sign = json1.str("_sign")
        if (sign == null) {
            if (json1.str("ssecurity") == null) return@io MiCloudLogin.Failed(MiCloudFailure.INVALID_USER)
            adopt(json1)
            return@io finishWithLocation(json1.str("location"))
        }
        val fields = mutableMapOf(
            "sid" to "xiaomiio",
            "hash" to MiCloudCrypto.md5Upper(password),
            "callback" to "https://sts.api.io.mi.com/sts",
            "qs" to "%3Fsid%3Dxiaomiio%26_json%3Dtrue",
            "user" to username,
            "_sign" to sign,
            "_json" to "true",
        )
        pendingAuth = fields
        submitAuth(fields)
    }

    suspend fun submitCaptcha(code: String): MiCloudLogin = io {
        val fields = pendingAuth ?: return@io MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
        fields["captCode"] = code
        submitAuth(fields)
    }

    private fun submitAuth(fields: Map<String, String>): MiCloudLogin {
        val response = http.request("POST", "$ACCOUNT/pass/serviceLoginAuth2", query = fields, followRedirects = false)
        if (response.code != 200) return MiCloudLogin.Failed(MiCloudFailure.INVALID_CREDENTIALS)
        val json = response.json() ?: return MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
        if ((json.str("code")) == "87001") return MiCloudLogin.Failed(MiCloudFailure.INVALID_CAPTCHA)
        json.str("captchaUrl")?.let { captchaUrl ->
            val absolute = if (captchaUrl.startsWith("/")) ACCOUNT + captchaUrl else captchaUrl
            val image = http.request("GET", absolute)
            if (image.code != 200) return MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
            return MiCloudLogin.NeedCaptcha(image.body)
        }
        if ((json.str("ssecurity")?.length ?: 0) > 4) {
            adopt(json)
            return finishWithLocation(json.str("location"))
        }
        json.str("notificationUrl")?.let { return startEmailVerification(it) }
        return MiCloudLogin.Failed(MiCloudFailure.INVALID_CREDENTIALS)
    }

    private fun startEmailVerification(notificationUrl: String): MiCloudLogin {
        http.request("GET", notificationUrl)
        val context = queryParam(notificationUrl, "context") ?: return MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
        emailContext = context
        http.request("GET", "$ACCOUNT/identity/list", query = mapOf("sid" to "xiaomiio", "context" to context, "_locale" to "en_US"))
        http.request(
            "POST", "$ACCOUNT/identity/auth/sendEmailTicket",
            query = mapOf("_dc" to System.currentTimeMillis().toString(), "sid" to "xiaomiio", "context" to context, "mask" to "0", "_locale" to "en_US"),
            form = mapOf("retry" to "0", "icode" to "", "_json" to "true", "ick" to (http.cookie("ick") ?: "")),
        )
        return MiCloudLogin.NeedEmailCode
    }

    suspend fun submitEmailCode(code: String): MiCloudLogin = io {
        val context = emailContext ?: return@io MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
        val verify = http.request(
            "POST", "$ACCOUNT/identity/auth/verifyEmail",
            query = mapOf("_flag" to "8", "_json" to "true", "sid" to "xiaomiio", "context" to context, "mask" to "0", "_locale" to "en_US"),
            form = mapOf("_flag" to "8", "ticket" to code, "trust" to "false", "_json" to "true", "ick" to (http.cookie("ick") ?: "")),
            followRedirects = false,
        )
        if (verify.code != 200) return@io MiCloudLogin.Failed(MiCloudFailure.INVALID_CODE)
        val verifyJson = verify.json()
        if (verifyJson != null && verifyJson.str("code").let { it != null && it != "0" }) {
            return@io MiCloudLogin.Failed(MiCloudFailure.INVALID_CODE)
        }
        var finish = verifyJson?.str("location")
            ?: verify.header("Location")
            ?: Regex("""https://account\.xiaomi\.com/identity/result/check\?[^"']+""").find(verify.text)?.value
        if (finish == null) {
            val check = http.request(
                "GET", "$ACCOUNT/identity/result/check",
                query = mapOf("sid" to "xiaomiio", "context" to context, "_locale" to "en_US"),
                followRedirects = false,
            )
            if (check.code in 300..399) {
                finish = if ("serviceLoginAuth2/end" in check.url) check.url else check.header("Location")
            }
        }
        finish ?: return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)

        val endUrl = if ("identity/result/check" in finish) {
            http.request("GET", finish, followRedirects = false).header("Location")
        } else {
            finish
        } ?: return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)

        var end = http.request("GET", endUrl, followRedirects = false)
        if (end.code == 200 && "Xiaomi Account - Tips" in end.text) end = http.request("GET", endUrl, followRedirects = false)
        end.header("extension-pragma")?.let { pragma ->
            runCatching { Json.parseToJsonElement(pragma).jsonObject.str("ssecurity") }.getOrNull()?.let { ssecurity = it }
        }
        if (ssecurity == null) return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)

        val sts = end.header("Location") ?: end.text.indexOf(STS).takeIf { it >= 0 }?.let { start ->
            val stop = end.text.indexOf('"', start).takeIf { it >= 0 } ?: (start + 300).coerceAtMost(end.text.length)
            end.text.substring(start, stop)
        } ?: return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        val stsResponse = http.request("GET", sts)
        if (stsResponse.code != 200) return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        serviceToken = http.cookie("serviceToken", "sts.api.io.mi.com") ?: http.cookie("serviceToken")
            ?: return@io MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        userId = userId ?: http.cookie("userId", "xiaomi.com") ?: http.cookie("userId", "sts.api.io.mi.com")
        MiCloudLogin.Success
    }

    // ── QR login ───────────────────────────────────────────────────────────

    suspend fun startQrLogin(): MiCloudQr? = io {
        reset()
        val response = http.request(
            "GET", "$ACCOUNT/longPolling/loginUrl",
            query = mapOf(
                "_qrsize" to "480",
                "qs" to "%3Fsid%3Dxiaomiio%26_json%3Dtrue",
                "callback" to STS,
                "_hasLogo" to "false",
                "sid" to "xiaomiio",
                "serviceParam" to "",
                "_locale" to "en_GB",
                "_dc" to System.currentTimeMillis().toString(),
            ),
        )
        val json = response.json() ?: return@io null
        val qr = json.str("qr") ?: return@io null
        qrPoll = json.str("lp") ?: return@io null
        qrTimeoutMs = (json.str("timeout")?.toLongOrNull() ?: 300L) * 1000
        val image = http.request("GET", qr)
        if (image.code != 200) return@io null
        MiCloudQr(image.body, json.str("loginUrl") ?: qr)
    }

    suspend fun awaitQrLogin(): MiCloudLogin = io {
        val poll = qrPoll ?: return@io MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
        val deadline = System.currentTimeMillis() + qrTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val response = try {
                http.request("GET", poll, readTimeoutMs = 10_000)
            } catch (_: SocketTimeoutException) {
                continue
            }
            if (response.code != 200) continue
            val json = response.json() ?: return@io MiCloudLogin.Failed(MiCloudFailure.UNEXPECTED)
            adopt(json)
            return@io finishWithLocation(json.str("location"))
        }
        MiCloudLogin.Failed(MiCloudFailure.QR_EXPIRED)
    }

    // ── Devices ────────────────────────────────────────────────────────────

    /** Every device with its beacon key, across [servers]; a server that fails is skipped. */
    suspend fun devices(servers: List<String> = SERVERS): List<MiCloudDevice> = io {
        servers.flatMap { server -> runCatching { devicesOn(server) }.getOrDefault(emptyList()) }
            .distinctBy { it.did }
    }

    private suspend fun devicesOn(server: String): List<MiCloudDevice> {
        val homes = mutableListOf<Pair<String, String>>()
        api(server, "/v2/homeroom/gethome", """{"fg": true, "fetch_share": true, "fetch_share_dev": true, "limit": 300, "app_ver": 7}""")
            ?.obj("result")?.arr("homelist")?.forEach { h -> h.jsonObjectOrNull()?.str("id")?.let { homes += it to userId.orEmpty() } }
        api(server, "/v2/user/get_device_cnt", """{ "fetch_own": true, "fetch_share": true}""")
            ?.obj("result")?.obj("share")?.arr("share_family")?.forEach { h ->
                val o = h.jsonObjectOrNull() ?: return@forEach
                val id = o.str("home_id") ?: return@forEach
                homes += id to (o.str("home_owner") ?: return@forEach)
            }
        return homes.flatMap { (homeId, owner) ->
            currentCoroutineContext().ensureActive()
            val list = api(
                server, "/v2/home/home_device_list",
                """{"home_owner": $owner,"home_id": $homeId, "limit": 200, "get_split_device": true, "support_smart_home": true}""",
            )?.obj("result")?.arr("device_info").orEmpty()
            list.mapNotNull { it.jsonObjectOrNull() }.mapNotNull { d ->
                val did = d.str("did") ?: return@mapNotNull null
                val key = if ("blt" in did) {
                    api(server, "/v2/device/blt_get_beaconkey", """{"did":"$did","pdid":1}""")?.obj("result")?.str("beaconkey")
                } else {
                    null
                }
                MiCloudDevice(d.str("name") ?: did, d.str("model"), d.str("mac"), did, key, server)
            }
        }
    }

    private fun api(server: String, path: String, data: String): JsonObject? {
        val security = ssecurity ?: return null
        val url = apiUrl(server) + path
        val nonce = MiCloudCrypto.generateNonce()
        val signed = MiCloudCrypto.signedNonce(security, nonce)
        val fields = MiCloudCrypto.encParams(url, "POST", signed, nonce, mapOf("data" to data), security)
        val response = http.request(
            "POST", url,
            query = fields,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "x-xiaomi-protocal-flag-cli" to "PROTOCAL-HTTP2",
                "MIOT-ENCRYPT-ALGORITHM" to "ENCRYPT-RC4",
            ),
            cookies = mapOf(
                "userId" to userId.toString(),
                "yetAnotherServiceToken" to serviceToken.toString(),
                "serviceToken" to serviceToken.toString(),
                "locale" to "en_GB",
                "timezone" to "GMT+02:00",
                "is_daylight" to "1",
                "dst_offset" to "3600000",
                "channel" to "MI_APP_STORE",
            ),
        )
        if (response.code != 200) return null
        val decoded = MiCloudCrypto.decryptRc4(MiCloudCrypto.signedNonce(security, fields.getValue("_nonce")), response.text)
        return runCatching { Json.parseToJsonElement(decoded).jsonObject }.getOrNull()
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun reset() {
        ssecurity = null
        userId = null
        serviceToken = null
        pendingAuth = null
        emailContext = null
        qrPoll = null
    }

    private fun adopt(json: JsonObject) {
        ssecurity = json.str("ssecurity")
        userId = json.str("userId")
    }

    private fun finishWithLocation(location: String?): MiCloudLogin {
        if (location == null) return MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        val response = http.request("GET", location)
        if (response.code != 200) return MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        serviceToken = http.cookie("serviceToken") ?: return MiCloudLogin.Failed(MiCloudFailure.NO_TOKEN)
        return MiCloudLogin.Success
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        val SERVERS = listOf("cn", "de", "us", "ru", "tw", "sg", "in", "i2")

        /** Xiaomi's web sign-in for the Mi Home service; it ends on [STS_HOST] once the account is in. */
        const val ACCOUNT_URL = "https://account.xiaomi.com"
        const val WEB_LOGIN_URL = "$ACCOUNT_URL/pass/serviceLogin?sid=xiaomiio"
        const val STS_HOST = "sts.api.io.mi.com"

        private const val ACCOUNT = ACCOUNT_URL
        private const val STS = "https://sts.api.io.mi.com/sts"

        private fun apiUrl(server: String) = "https://" + (if (server == "cn") "" else "$server.") + "api.io.mi.com/app"

        private fun generateAgent(): String =
            "${randomLetters(18, 'a'..'z')}-${randomLetters(13, 'A'..'E')} APP/com.xiaomi.mihome APPV/10.5.201"

        private fun randomLetters(n: Int, range: CharRange) = (1..n).map { range.random() }.joinToString("")

        private fun queryParam(url: String, name: String): String? = runCatching { URI(url).rawQuery }.getOrNull()
            ?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == name }
            ?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }

        private fun MiCloudResponse.json(): JsonObject? =
            runCatching { Json.parseToJsonElement(text.replace("&&&START&&&", "")).jsonObject }.getOrNull()

        private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    }
}
