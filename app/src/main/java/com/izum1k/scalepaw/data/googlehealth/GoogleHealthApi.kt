package com.izum1k.scalepaw.data.googlehealth

import com.izum1k.scalepaw.domain.HealthPoint
import com.izum1k.scalepaw.domain.HealthPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The writes a sync needs, so the reconciliation can run against a fake. */
interface HealthRemote {
    /** False when a point with this id already exists. */
    suspend fun create(point: HealthPoint): Boolean

    /** False when the point doesn't exist (any more). */
    suspend fun patch(point: HealthPoint): Boolean

    suspend fun delete(dataType: String, ids: List<String>)
}

/** Google Health API v4 data point writes for the signed-in user (`users/me`). */
class GoogleHealthApi(private val auth: GoogleHealthAuth) : HealthRemote {

    /** Fails with [GoogleHealthError.ACCOUNT_NOT_LINKED] when the account has no Google Health profile yet. */
    suspend fun verify() {
        val res = call("GET", "$BASE/users/me/dataTypes/${HealthPoints.WEIGHT}/dataPoints?pageSize=1")
        if (!res.ok) throw failure(res)
    }

    override suspend fun create(point: HealthPoint): Boolean {
        val res = call("POST", "$BASE/users/me/dataTypes/${point.dataType}/dataPoints", named(point))
        return when {
            res.code == 409 -> false
            !res.ok -> throw failure(res)
            operationErrorCode(res) == ALREADY_EXISTS -> false
            operationErrorCode(res) != null -> throw GoogleHealthException(GoogleHealthError.FAILED, res.text)
            else -> true
        }
    }

    override suspend fun patch(point: HealthPoint): Boolean {
        val res = call("PATCH", "$BASE/${name(point.dataType, point.id)}", named(point))
        return when {
            res.code == 404 -> false
            !res.ok -> throw failure(res)
            operationErrorCode(res) == NOT_FOUND -> false
            operationErrorCode(res) != null -> throw GoogleHealthException(GoogleHealthError.FAILED, res.text)
            else -> true
        }
    }

    override suspend fun delete(dataType: String, ids: List<String>) {
        for (chunk in ids.chunked(MAX_BATCH)) {
            val res = batchDelete(dataType, chunk)
            if (res.code == 404 && chunk.size > 1) {
                // One missing name fails the whole batch; retry singly and skip what's already gone.
                for (id in chunk) {
                    val single = batchDelete(dataType, listOf(id))
                    if (!single.ok && single.code != 404) throw failure(single)
                }
            } else if (!res.ok && res.code != 404) {
                throw failure(res)
            }
        }
    }

    private suspend fun batchDelete(dataType: String, ids: List<String>): HttpResult {
        val body = buildJsonObject { putJsonArray("names") { ids.forEach { add(JsonPrimitive(name(dataType, it))) } } }
        return call("POST", "$BASE/users/me/dataTypes/$dataType/dataPoints:batchDelete", body.toString())
    }

    private suspend fun call(method: String, url: String, body: String? = null): HttpResult {
        var res = withContext(Dispatchers.IO) { GoogleHttp.request(method, url, body, bearer = auth.accessToken()) }
        if (res.code == 401) {
            res = withContext(Dispatchers.IO) { GoogleHttp.request(method, url, body, bearer = auth.accessToken(forceRefresh = true)) }
            if (res.code == 401) throw GoogleHealthException(GoogleHealthError.REAUTH, res.errorMessage())
        }
        return res
    }

    private fun failure(res: HttpResult): GoogleHealthException {
        val reason = res.errorReason()
        val error = when {
            reason == "ACCOUNT_NOT_LINKED" -> GoogleHealthError.ACCOUNT_NOT_LINKED
            res.code == 429 -> GoogleHealthError.RATE_LIMITED
            res.code >= 500 -> GoogleHealthError.SERVER
            else -> GoogleHealthError.FAILED
        }
        return GoogleHealthException(error, res.errorMessage())
    }

    private fun operationErrorCode(res: HttpResult): Int? {
        val error = res.json()?.get("error") as? JsonObject ?: return null
        return (error["code"] as? JsonPrimitive)?.int
    }

    private fun named(point: HealthPoint): String {
        val fields = Json.parseToJsonElement(point.body).jsonObject
        return buildJsonObject {
            put("name", name(point.dataType, point.id))
            fields.forEach { (k, v) -> put(k, v) }
        }.toString()
    }

    private fun name(dataType: String, id: String) = "users/me/dataTypes/$dataType/dataPoints/$id"

    private companion object {
        const val BASE = "https://health.googleapis.com/v4"
        const val MAX_BATCH = 10_000
        const val ALREADY_EXISTS = 6
        const val NOT_FOUND = 5
    }
}
