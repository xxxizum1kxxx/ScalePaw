package com.izum1k.scalepaw.data.googlehealth

import android.content.Context
import android.util.Log
import com.izum1k.scalepaw.data.repository.ScaleRepository
import com.izum1k.scalepaw.domain.HealthPoint
import com.izum1k.scalepaw.domain.HealthPoints
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Brings the remote side in line with [desired], using the ledger to know what was pushed before. */
class HealthReconciler(private val remote: HealthRemote, private val ledger: HealthLedger) {
    data class Outcome(val created: Int, val updated: Int, val deleted: Int, val unchanged: Int) {
        val changed: Int get() = created + updated + deleted
    }

    suspend fun reconcile(desired: List<HealthPoint>): Outcome {
        val pushed = ledger.load().toMutableMap()
        val wanted = desired.associateBy { it.id }
        var created = 0
        var updated = 0
        var deleted = 0
        var unchanged = 0
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
                if (known?.hash == hash) {
                    unchanged++
                    continue
                }
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
        return Outcome(created, updated, deleted, unchanged)
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

sealed interface SyncResult {
    data class Done(val outcome: HealthReconciler.Outcome) : SyncResult
    data class Failed(val error: GoogleHealthException) : SyncResult
}

/** Everything Google Health: credentials, consent, and pushing the linked user's measurements on request. */
class GoogleHealth(context: Context, private val repository: ScaleRepository, private val scope: CoroutineScope) {
    val store = GoogleHealthStore(context)
    val auth = GoogleHealthAuth(store)
    val api = GoogleHealthApi(auth)
    private val reconciler = HealthReconciler(api, store)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _results = MutableSharedFlow<SyncResult>(extraBufferCapacity = 1)
    val results: SharedFlow<SyncResult> = _results.asSharedFlow()

    /** Runs consent in the browser, checks the account can be written to, then links it to [userId]. */
    suspend fun connect(userId: Long, openBrowser: (String) -> Unit) {
        val tokens = auth.authorize(openBrowser)
        api.verify()
        store.connect(tokens.refreshToken!!, tokens.email, userId)
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

    fun setLinkedUser(userId: Long) = store.setLinkedUser(userId)

    /**
     * Pushes what changed since the last sync; [everything] forgets the ledger first and rewrites every point.
     * Runs in the app scope so leaving the screen doesn't cancel it. Returns false if a sync is already running.
     */
    fun requestSync(everything: Boolean = false): Boolean {
        if (!_syncing.compareAndSet(expect = false, update = true)) return false
        scope.launch {
            try {
                if (everything) store.save(emptyMap())
                _results.emit(SyncResult.Done(sync()))
            } catch (e: GoogleHealthException) {
                _results.emit(SyncResult.Failed(e))
            } finally {
                _syncing.value = false
            }
        }
        return true
    }

    private suspend fun sync(): HealthReconciler.Outcome {
        val state = store.state.value
        if (!state.connected) throw GoogleHealthException(GoogleHealthError.NOT_CONNECTED)
        val userId = state.linkedUserId ?: throw GoogleHealthException(GoogleHealthError.NOT_CONNECTED)
        val user = repository.usersNow().firstOrNull { it.id == userId } ?: throw GoogleHealthException(GoogleHealthError.NOT_CONNECTED)
        try {
            val points = HealthPoints.build(user, repository.typesNow(), repository.measurementsNow(userId))
            val outcome = reconciler.reconcile(points)
            Log.i(TAG, "sync user=$userId points=${points.size} $outcome")
            store.recordSuccess(System.currentTimeMillis())
            return outcome
        } catch (e: GoogleHealthException) {
            Log.w(TAG, "sync failed: ${e.message}")
            store.recordError(e.error.name + (e.message?.takeIf { it != e.error.name }?.let { ": $it" } ?: ""), e.error == GoogleHealthError.REAUTH)
            throw e
        }
    }

    private companion object {
        const val TAG = "GoogleHealth"
    }
}
