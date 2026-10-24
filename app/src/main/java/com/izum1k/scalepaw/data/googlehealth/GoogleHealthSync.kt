package com.izum1k.scalepaw.data.googlehealth

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.izum1k.scalepaw.container
import com.izum1k.scalepaw.data.repository.ScaleRepository
import com.izum1k.scalepaw.domain.HealthPoint
import com.izum1k.scalepaw.domain.HealthPoints
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Brings the remote side in line with [desired], using the ledger to know what was pushed before. */
class HealthReconciler(private val remote: HealthRemote, private val ledger: HealthLedger) {
    data class Outcome(val created: Int, val updated: Int, val deleted: Int)

    suspend fun reconcile(desired: List<HealthPoint>): Outcome {
        val pushed = ledger.load().toMutableMap()
        val wanted = desired.associateBy { it.id }
        var created = 0
        var updated = 0
        var deleted = 0
        try {
            pushed.filterKeys { it !in wanted }.entries.groupBy({ it.value.dataType }, { it.key }).forEach { (dataType, ids) ->
                remote.delete(dataType, ids)
                ids.forEach(pushed::remove)
                deleted += ids.size
            }
            var dirty = 0
            for (point in desired) {
                val hash = hash(point.body)
                val known = pushed[point.id]
                if (known?.hash == hash) continue
                if (known == null) {
                    if (remote.create(point)) created++ else upsertExisting(point).also { updated++ }
                } else {
                    if (remote.patch(point)) updated++ else createMissing(point).also { created++ }
                }
                pushed[point.id] = LedgerEntry(point.dataType, hash)
                if (++dirty % SAVE_EVERY == 0) ledger.save(pushed)
            }
        } finally {
            ledger.save(pushed)
        }
        return Outcome(created, updated, deleted)
    }

    private suspend fun upsertExisting(point: HealthPoint) {
        if (!remote.patch(point) && !remote.create(point)) throw GoogleHealthException(GoogleHealthError.FAILED, point.id)
    }

    private suspend fun createMissing(point: HealthPoint) {
        if (!remote.create(point) && !remote.patch(point)) throw GoogleHealthException(GoogleHealthError.FAILED, point.id)
    }

    private companion object {
        const val SAVE_EVERY = 50

        fun hash(body: String): String =
            MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

/** Everything Google Health: credentials, consent, and pushing the linked user's measurements. */
class GoogleHealth(private val context: Context, private val repository: ScaleRepository) {
    val store = GoogleHealthStore(context)
    val auth = GoogleHealthAuth(store)
    val api = GoogleHealthApi(auth)
    private val reconciler = HealthReconciler(api, store)
    private val syncing = Mutex()

    /** Runs consent in the browser, checks the account can be written to, then links it to [userId]. */
    suspend fun connect(userId: Long, openBrowser: (String) -> Unit) {
        val tokens = auth.authorize(openBrowser)
        api.verify()
        store.connect(tokens.refreshToken!!, tokens.email, userId)
        GoogleHealthScheduler.enqueue(context)
    }

    /** Revokes the grant; with [deleteRemote] the points pushed so far are removed first, best effort. */
    suspend fun disconnect(deleteRemote: Boolean) {
        if (deleteRemote && store.state.value.connected) {
            runCatching {
                store.load().entries.groupBy({ it.value.dataType }, { it.key }).forEach { (type, ids) -> api.delete(type, ids) }
            }
        }
        auth.revoke()
        store.disconnect()
    }

    fun setLinkedUser(userId: Long) {
        store.setLinkedUser(userId)
        GoogleHealthScheduler.enqueue(context)
    }

    /** Forgets what was pushed and rewrites every point. */
    suspend fun resyncAll() = syncing.withLock {
        store.save(emptyMap())
        syncLocked()
    }

    suspend fun sync() = syncing.withLock { syncLocked() }

    private suspend fun syncLocked() {
        val state = store.state.value
        if (!state.connected) return
        val userId = state.linkedUserId ?: return
        val user = repository.usersNow().firstOrNull { it.id == userId } ?: return
        try {
            val points = HealthPoints.build(user, repository.typesNow(), repository.measurementsNow(userId))
            reconciler.reconcile(points)
            store.recordSuccess(System.currentTimeMillis())
        } catch (e: GoogleHealthException) {
            store.recordError(e.error.name + (e.message?.takeIf { it != e.error.name }?.let { ": $it" } ?: ""), e.error == GoogleHealthError.REAUTH)
            throw e
        }
    }
}

object GoogleHealthScheduler {
    private const val WORK = "google_health_sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<GoogleHealthSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

class GoogleHealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        applicationContext.container.googleHealth.sync()
        Result.success()
    } catch (e: GoogleHealthException) {
        if (e.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
